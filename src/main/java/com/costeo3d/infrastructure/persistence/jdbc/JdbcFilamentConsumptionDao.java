package com.costeo3d.infrastructure.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.domain.model.FilamentConsumption;
import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.domain.model.Spool;
import com.costeo3d.infrastructure.persistence.PersistenceException;
import com.costeo3d.infrastructure.persistence.dao.FilamentConsumptionDao;

public final class JdbcFilamentConsumptionDao implements FilamentConsumptionDao {
    private static final Logger LOGGER =
            Logger.getLogger(JdbcFilamentConsumptionDao.class.getName());
    private static final String INSERT_SQL = """
            INSERT INTO consumo_filamento
                (id_trabajo, id_bobina, gramos_previstos, gramos_consumidos)
            VALUES (?, ?, ?, ?)
            """;
    private static final String SELECT_SQL = """
            SELECT c.id_consumo, c.id_trabajo, c.gramos_previstos,
                   c.gramos_consumidos, b.id_bobina, b.color,
                   b.peso_inicial, b.peso_actual, b.activo AS bobina_activa,
                   m.id_material, m.marca, m.tipo, m.acabado,
                   m.costo_por_gramo, m.activo AS material_activo
            FROM consumo_filamento c
            JOIN bobina b ON b.id_bobina = c.id_bobina
            JOIN material m ON m.id_material = b.id_material
            """;

    private final JdbcConnectionFactory connectionFactory;

    public JdbcFilamentConsumptionDao(JdbcConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public FilamentConsumption insert(FilamentConsumption consumption) {
        try (Connection connection = connectionFactory.openConnection()) {
            return insert(connection, consumption);
        } catch (SQLException e) {
            throw persistenceFailure("insert", e);
        }
    }

    // Para la futura transaccion de produccion: no abre ni cierra la conexion.
    static FilamentConsumption insert(Connection connection,
            FilamentConsumption consumption) throws SQLException {
        Objects.requireNonNull(consumption, "Filament consumption is required");
        if (consumption.getId() != null) {
            throw new IllegalArgumentException("New consumption must not have an ID.");
        }
        int jobId = requiredId(consumption.getPrintJob().getId(), "Print job");
        int spoolId = requiredId(consumption.getSpool().getId(), "Spool");
        try (PreparedStatement stmt = connection.prepareStatement(
                INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, jobId);
            stmt.setInt(2, spoolId);
            if (consumption.getPlannedGrams() == null) {
                stmt.setNull(3, Types.DECIMAL);
            } else {
                stmt.setBigDecimal(3, consumption.getPlannedGrams());
            }
            stmt.setBigDecimal(4, consumption.getConsumedGrams());
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Consumption insert did not return an ID.");
                }
                return new FilamentConsumption(keys.getInt(1),
                        consumption.getPrintJob(), consumption.getSpool(),
                        consumption.getPlannedGrams(), consumption.getConsumedGrams());
            }
        }
    }

    @Override
    public Optional<FilamentConsumption> findById(int id) {
        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement stmt = connection.prepareStatement(
                        SELECT_SQL + "WHERE c.id_consumo = ?")) {
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
    public List<FilamentConsumption> findByPrintJobId(int printJobId) {
        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement stmt = connection.prepareStatement(
                        SELECT_SQL + "WHERE c.id_trabajo = ? ORDER BY c.id_consumo")) {
            stmt.setInt(1, printJobId);
            List<FilamentConsumption> consumptions = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    consumptions.add(mapRow(connection, rs));
                }
            }
            return consumptions;
        } catch (SQLException e) {
            throw persistenceFailure("findByPrintJobId", e);
        }
    }

    private static FilamentConsumption mapRow(Connection connection, ResultSet rs)
            throws SQLException {
        PrintJob job = JdbcPrintJobReader.findById(connection, rs.getInt("id_trabajo"))
                .orElseThrow(() -> new SQLException("Consumption refers to a missing job."));
        Material material = new Material(rs.getInt("id_material"), rs.getString("marca"),
                rs.getString("tipo"), rs.getString("acabado"),
                rs.getBigDecimal("costo_por_gramo"), rs.getBoolean("material_activo"));
        Spool spool = new Spool(rs.getInt("id_bobina"), material, rs.getString("color"),
                rs.getBigDecimal("peso_inicial"), rs.getBigDecimal("peso_actual"),
                rs.getBoolean("bobina_activa"));
        return new FilamentConsumption(rs.getInt("id_consumo"), job, spool,
                rs.getBigDecimal("gramos_previstos"),
                rs.getBigDecimal("gramos_consumidos"));
    }

    private static int requiredId(Integer id, String entity) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(entity + " must have a persisted ID.");
        }
        return id;
    }

    private static PersistenceException persistenceFailure(String operation, SQLException e) {
        LOGGER.log(Level.SEVERE,
                "Consumption persistence {0} failed (SQLState: {1}, vendor code: {2}).",
                new Object[] {operation, e.getSQLState(), e.getErrorCode()});
        return new PersistenceException("Cannot " + operation + " consumption data.", e);
    }
}
