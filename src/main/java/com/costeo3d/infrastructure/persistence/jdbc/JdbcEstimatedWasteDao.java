package com.costeo3d.infrastructure.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.domain.model.EstimatedWaste;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.infrastructure.persistence.PersistenceException;
import com.costeo3d.infrastructure.persistence.dao.EstimatedWasteDao;

public final class JdbcEstimatedWasteDao implements EstimatedWasteDao {
    private static final Logger LOGGER =
            Logger.getLogger(JdbcEstimatedWasteDao.class.getName());
    private static final String INSERT_SQL = """
            INSERT INTO merma_estimada (id_trabajo, capas_totales, capa_falla)
            VALUES (?, ?, ?)
            """;
    private static final String SELECT_SQL = """
            SELECT id_merma, id_trabajo, capas_totales, capa_falla
            FROM merma_estimada
            """;

    private final JdbcConnectionFactory connectionFactory;

    public JdbcEstimatedWasteDao(JdbcConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public EstimatedWaste insert(EstimatedWaste waste) {
        try (Connection connection = connectionFactory.openConnection()) {
            return insert(connection, waste);
        } catch (SQLException e) {
            throw persistenceFailure("insert", e);
        }
    }

    // Reutilizable en una transaccion ajena; no administra la conexion.
    static EstimatedWaste insert(Connection connection, EstimatedWaste waste)
            throws SQLException {
        Objects.requireNonNull(waste, "Estimated waste is required");
        if (waste.getId() != null) {
            throw new IllegalArgumentException("New waste estimate must not have an ID.");
        }
        Integer jobId = Objects.requireNonNull(waste.getPrintJob(),
                "Print job is required").getId();
        if (jobId == null || jobId <= 0) {
            throw new IllegalArgumentException("Print job must have a persisted ID.");
        }
        try (PreparedStatement stmt = connection.prepareStatement(
                INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, jobId);
            stmt.setInt(2, waste.getTotalLayers());
            stmt.setInt(3, waste.getFailureLayer());
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Waste insert did not return an ID.");
                }
                return new EstimatedWaste(keys.getInt(1), waste.getPrintJob(),
                        waste.getTotalLayers(), waste.getFailureLayer());
            }
        }
    }

    @Override
    public Optional<EstimatedWaste> findById(int id) {
        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement stmt = connection.prepareStatement(
                        SELECT_SQL + "WHERE id_merma = ?")) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(connection, rs))
                        : Optional.empty();
            }
        } catch (SQLException e) {
            throw persistenceFailure("findById", e);
        }
    }

    @Override
    public Optional<EstimatedWaste> findByPrintJobId(int printJobId) {
        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement stmt = connection.prepareStatement(
                        SELECT_SQL + "WHERE id_trabajo = ?")) {
            stmt.setInt(1, printJobId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(connection, rs))
                        : Optional.empty();
            }
        } catch (SQLException e) {
            throw persistenceFailure("findByPrintJobId", e);
        }
    }

    private static EstimatedWaste mapRow(Connection connection, ResultSet rs)
            throws SQLException {
        PrintJob job = JdbcPrintJobReader.findById(connection, rs.getInt("id_trabajo"))
                .orElseThrow(() -> new SQLException("Waste refers to a missing job."));
        return new EstimatedWaste(rs.getInt("id_merma"), job,
                rs.getInt("capas_totales"), rs.getInt("capa_falla"));
    }

    private static PersistenceException persistenceFailure(String operation, SQLException e) {
        LOGGER.log(Level.SEVERE,
                "Waste persistence {0} failed (SQLState: {1}, vendor code: {2}).",
                new Object[] {operation, e.getSQLState(), e.getErrorCode()});
        return new PersistenceException("Cannot " + operation + " waste data.", e);
    }
}
