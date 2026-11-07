package com.costeo3d.infrastructure.persistence.jdbc;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.domain.enums.PrintJobStatus;
import com.costeo3d.domain.exception.BusinessRuleException;
import com.costeo3d.domain.exception.BusinessValidationException;
import com.costeo3d.domain.exception.InvalidStateTransitionException;
import com.costeo3d.domain.model.FilamentConsumption;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.infrastructure.persistence.PersistenceException;

/** Finaliza un trabajo, registra sus consumos y descuenta stock atomicamente. */
public final class JdbcProductionTransaction {
    private static final Logger LOGGER =
            Logger.getLogger(JdbcProductionTransaction.class.getName());
    private static final String LOCK_JOB = """
            SELECT fecha_creacion, fecha_inicio, estado, tiempo_estimado_minutos
            FROM trabajo_impresion WHERE id_trabajo = ? FOR UPDATE
            """;
    private static final String FINISH_JOB = """
            UPDATE trabajo_impresion SET estado = ?, fecha_fin = ?
            WHERE id_trabajo = ? AND estado = 'EN_PROCESO'
            """;

    private final JdbcConnectionFactory connectionFactory;

    public JdbcProductionTransaction(JdbcConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    public Result completeJob(PrintJob job, List<FilamentConsumption> consumptions,
            LocalDateTime finishedAt) throws BusinessRuleException {
        return finish(job, consumptions, finishedAt, PrintJobStatus.COMPLETED);
    }

    public Result failJob(PrintJob job, List<FilamentConsumption> consumptions,
            LocalDateTime finishedAt) throws BusinessRuleException {
        return finish(job, consumptions, finishedAt, PrintJobStatus.FAILED);
    }

    private Result finish(PrintJob job, List<FilamentConsumption> consumptions,
            LocalDateTime finishedAt, PrintJobStatus target) throws BusinessRuleException {
        Objects.requireNonNull(job, "Print job is required");
        Objects.requireNonNull(finishedAt, "Finish date is required");
        if (job.getId() == null || job.getId() <= 0) {
            throw new IllegalArgumentException("Print job must have a persisted ID.");
        }
        if (job.getStatus() != PrintJobStatus.IN_PROGRESS) {
            throw new InvalidStateTransitionException(
                    "Only a print job in progress can be finished.");
        }
        if (job.getStartedAt() != null && finishedAt.isBefore(job.getStartedAt())) {
            throw new IllegalArgumentException("Finish date cannot precede start date.");
        }
        Map<Integer, BigDecimal> required =
                JdbcProductionStock.prepare(job.getId(), consumptions);

        try (Connection connection = connectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                PrintJob finished = lockAndFinish(job, finishedAt, target, connection);
                Map<Integer, BigDecimal> remaining =
                        JdbcProductionStock.discount(connection, required);
                List<Integer> ids = new ArrayList<>();
                for (FilamentConsumption consumption : consumptions) {
                    ids.add(JdbcFilamentConsumptionDao.insert(connection, consumption).getId());
                }
                try (PreparedStatement update = connection.prepareStatement(FINISH_JOB)) {
                    update.setString(1, target == PrintJobStatus.COMPLETED
                            ? "FINALIZADO" : "FALLIDO");
                    update.setTimestamp(2, Timestamp.valueOf(finishedAt));
                    update.setInt(3, job.getId());
                    if (update.executeUpdate() != 1) {
                        throw new SQLException("Print job could not be finalized.");
                    }
                }
                Result result = new Result(finished, List.copyOf(ids), Map.copyOf(remaining));
                connection.commit();
                return result;
            } catch (SQLException | BusinessRuleException | RuntimeException error) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    error.addSuppressed(rollbackError);
                }
                throw error;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "Production transaction failed (SQLState: {0}, vendor code: {1}).",
                    new Object[] {e.getSQLState(), e.getErrorCode()});
            throw new PersistenceException("Cannot finalize print job.", e);
        }
    }

    private static PrintJob lockAndFinish(PrintJob job, LocalDateTime date,
            PrintJobStatus target, Connection connection)
            throws SQLException, BusinessRuleException {
        try (PreparedStatement lock = connection.prepareStatement(LOCK_JOB)) {
            lock.setInt(1, job.getId());
            try (ResultSet row = lock.executeQuery()) {
                if (!row.next()) {
                    throw new BusinessValidationException("Print job does not exist.");
                }
                if (!"EN_PROCESO".equals(row.getString("estado"))) {
                    throw new InvalidStateTransitionException(
                            "Only a print job in progress can be finished.");
                }
                Timestamp start = row.getTimestamp("fecha_inicio");
                PrintJob snapshot = new PrintJob(job.getId(), job.getOrder(), job.getModel(),
                        row.getTimestamp("fecha_creacion").toLocalDateTime(),
                        start == null ? null : start.toLocalDateTime(), null,
                        PrintJobStatus.IN_PROGRESS, row.getInt("tiempo_estimado_minutos"));
                if (target == PrintJobStatus.COMPLETED) {
                    snapshot.complete(date);
                } else {
                    snapshot.fail(date);
                }
                return snapshot;
            }
        }
    }

    /** No modifica los objetos originales: E8 coordinara el estado en memoria. */
    public record Result(PrintJob finishedJob, List<Integer> consumptionIds,
            Map<Integer, BigDecimal> remainingGramsBySpool) { }
}
