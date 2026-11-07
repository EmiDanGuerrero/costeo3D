package com.costeo3d.infrastructure.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.domain.enums.PrintJobStatus;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.infrastructure.persistence.PersistenceException;
import com.costeo3d.infrastructure.persistence.dao.PrintJobDao;

public final class JdbcPrintJobDao implements PrintJobDao {

    private static final Logger LOGGER =
            Logger.getLogger(JdbcPrintJobDao.class.getName());

    private static final String INSERT_SQL = """
            INSERT INTO trabajo_impresion
                (id_pedido, id_modelo, fecha_creacion, fecha_inicio,
                 fecha_fin, estado, tiempo_estimado_minutos)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_SQL = """
            UPDATE trabajo_impresion
            SET id_pedido = ?, id_modelo = ?, fecha_creacion = ?,
                fecha_inicio = ?, fecha_fin = ?, estado = ?,
                tiempo_estimado_minutos = ?
            WHERE id_trabajo = ?
            """;

    private final JdbcConnectionFactory connectionFactory;
    public JdbcPrintJobDao(JdbcConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }
    @Override
    public PrintJob insert(PrintJob job) {
        try (Connection connection = connectionFactory.openConnection()) {
            return insert(connection, job);
        } catch (SQLException e) {
            throw persistenceFailure("insert", e);
        }
    }

    // Reusable from the future order transaction: never opens its own connection.
    static PrintJob insert(Connection connection, PrintJob job) throws SQLException {
        Objects.requireNonNull(job, "Print job is required");
        if (job.getId() != null) {
            throw new IllegalArgumentException("New print job must not have an ID.");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            setFields(statement, job);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Print job insert did not return an ID.");
                }
                return new PrintJob(keys.getInt(1), job.getOrder(), job.getModel(),
                        job.getCreatedAt(), job.getStartedAt(), job.getFinishedAt(),
                        job.getStatus(), job.getEstimatedPrintTimeMinutes());
            }
        }
    }
    @Override
    public Optional<PrintJob> findById(int id) {
        try (Connection connection = connectionFactory.openConnection()) {
            return JdbcPrintJobReader.findById(connection, id);
        } catch (SQLException e) {
            throw persistenceFailure("findById", e);
        }
    }
    @Override
    public List<PrintJob> findAll() {
        try (Connection connection = connectionFactory.openConnection()) {
            return JdbcPrintJobReader.findAll(connection);
        } catch (SQLException e) {
            throw persistenceFailure("findAll", e);
        }
    }
    @Override
    public List<PrintJob> findByOrderId(int orderId) {
        try (Connection connection = connectionFactory.openConnection()) {
            return JdbcPrintJobReader.findByOrderId(connection, orderId);
        } catch (SQLException e) {
            throw persistenceFailure("findByOrderId", e);
        }
    }
    @Override
    public boolean update(PrintJob job) {
        Objects.requireNonNull(job, "Print job is required");
        if (job.getId() == null) {
            throw new IllegalArgumentException("Print job must have an ID to update.");
        }
        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {
            setFields(statement, job);
            statement.setInt(8, job.getId());
            if (statement.executeUpdate() > 0) {
                return true;
            }
            try (PreparedStatement check = connection.prepareStatement(
                    "SELECT 1 FROM trabajo_impresion WHERE id_trabajo = ?")) {
                check.setInt(1, job.getId());
                try (ResultSet results = check.executeQuery()) {
                    return results.next();
                }
            }
        } catch (SQLException e) {
            throw persistenceFailure("update", e);
        }
    }

    private static void setFields(PreparedStatement stmt, PrintJob job)
            throws SQLException {
        Integer orderId = job.getOrder() == null ? null : job.getOrder().getId();
        Integer modelId = job.getModel() == null ? null : job.getModel().getId();
        if (job.getOrder() != null && orderId == null) {
            throw new IllegalArgumentException("Print job order must be persisted first.");
        }
        if (job.getModel() != null && modelId == null) {
            throw new IllegalArgumentException("Print job model must be persisted first.");
        }
        stmt.setObject(1, orderId, Types.INTEGER);
        stmt.setObject(2, modelId, Types.INTEGER);
        stmt.setTimestamp(3, Timestamp.valueOf(job.getCreatedAt()));
        stmt.setTimestamp(4, job.getStartedAt() == null ? null
                : Timestamp.valueOf(job.getStartedAt()));
        stmt.setTimestamp(5, job.getFinishedAt() == null ? null
                : Timestamp.valueOf(job.getFinishedAt()));
        stmt.setString(6, toDbStatus(job.getStatus()));
        stmt.setInt(7, job.getEstimatedPrintTimeMinutes());
    }

    private static String toDbStatus(PrintJobStatus status) {
        return switch (Objects.requireNonNull(status, "Print job status is required")) {
            case PENDING -> "PENDIENTE";
            case IN_PROGRESS -> "EN_PROCESO";
            case COMPLETED -> "FINALIZADO";
            case FAILED -> "FALLIDO";
        };
    }

    private static PersistenceException persistenceFailure(String operation, SQLException e) {
        LOGGER.log(Level.SEVERE,
                "Print job persistence {0} failed (SQLState: {1}, vendor code: {2}).",
                new Object[] {operation, e.getSQLState(), e.getErrorCode()});
        return new PersistenceException("Cannot " + operation + " print job data.", e);
    }
}
