package com.costeo3d.infrastructure.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.Spool;
import com.costeo3d.infrastructure.persistence.PersistenceException;
import com.costeo3d.infrastructure.persistence.dao.SpoolDao;

public final class JdbcSpoolDao implements SpoolDao {
    private static final Logger LOGGER = Logger.getLogger(JdbcSpoolDao.class.getName());
    private static final String INSERT_SQL = """
            INSERT INTO bobina (id_material, color, peso_inicial, peso_actual, activo)
            VALUES (?, ?, ?, ?, ?)
            """;
    private static final String SELECT_SQL = """
            SELECT b.id_bobina, b.color, b.peso_inicial, b.peso_actual,
                   b.activo AS bobina_activa, m.id_material, m.marca, m.tipo,
                   m.acabado, m.costo_por_gramo, m.activo AS material_activo
            FROM bobina b
            JOIN material m ON m.id_material = b.id_material
            """;
    private static final String UPDATE_SQL = """
            UPDATE bobina
            SET id_material = ?, color = ?, peso_inicial = ?, peso_actual = ?, activo = ?
            WHERE id_bobina = ?
            """;

    private final JdbcConnectionFactory connectionFactory;
    public JdbcSpoolDao(JdbcConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory, "JDBC connection factory is required");
    }
    @Override
    public Spool insert(Spool spool) {
        Objects.requireNonNull(spool, "Spool is required");
        if (spool.getId() != null) {
            throw new IllegalArgumentException("New spool must not have an ID.");
        }
        int materialId = materialIdOf(spool);

        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            setSpoolFields(statement, spool, materialId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Spool insert did not return a generated ID.");
                }
                return new Spool(keys.getInt(1), spool.getMaterial(), spool.getColor(),
                        spool.getInitialWeight(), spool.getCurrentWeight(), spool.isActive());
            }
        } catch (SQLException e) {
            throw persistenceFailure("insert", e);
        }
    }
    @Override
    public Optional<Spool> findById(int id) {
        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        SELECT_SQL + "WHERE b.id_bobina = ?")) {
            statement.setInt(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(mapSpool(results)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw persistenceFailure("findById", e);
        }
    }
    @Override
    public List<Spool> findAll() {
        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        SELECT_SQL + "ORDER BY b.id_bobina");
                ResultSet results = statement.executeQuery()) {
            List<Spool> spools = new ArrayList<>();
            while (results.next()) {
                spools.add(mapSpool(results));
            }
            return spools;
        } catch (SQLException e) {
            throw persistenceFailure("findAll", e);
        }
    }
    @Override
    public boolean update(Spool spool) {
        Objects.requireNonNull(spool, "Spool is required");
        if (spool.getId() == null) {
            throw new IllegalArgumentException("Spool must have an ID to be updated.");
        }
        int materialId = materialIdOf(spool);

        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {
            setSpoolFields(statement, spool, materialId);
            statement.setInt(6, spool.getId());
            if (statement.executeUpdate() > 0) {
                return true;
            }
            // MySQL can report zero changed rows even when the spool exists.
            try (PreparedStatement check = connection.prepareStatement(
                    "SELECT 1 FROM bobina WHERE id_bobina = ?")) {
                check.setInt(1, spool.getId());
                try (ResultSet results = check.executeQuery()) {
                    return results.next();
                }
            }
        } catch (SQLException e) {
            throw persistenceFailure("update", e);
        }
    }
    private static int materialIdOf(Spool spool) {
        if (spool.getMaterial() == null || spool.getMaterial().getId() == null) {
            throw new IllegalArgumentException("Spool material must have a persisted ID.");
        }
        return spool.getMaterial().getId();
    }
    private static void setSpoolFields(PreparedStatement statement, Spool spool,
            int materialId) throws SQLException {
        statement.setInt(1, materialId);
        statement.setString(2, spool.getColor());
        statement.setBigDecimal(3, spool.getInitialWeight());
        statement.setBigDecimal(4, spool.getCurrentWeight());
        statement.setBoolean(5, spool.isActive());
    }
    private static Spool mapSpool(ResultSet results) throws SQLException {
        Material material = new Material(
                results.getInt("id_material"), results.getString("marca"),
                results.getString("tipo"), results.getString("acabado"),
                results.getBigDecimal("costo_por_gramo"), results.getBoolean("material_activo"));
        return new Spool(
                results.getInt("id_bobina"), material, results.getString("color"),
                results.getBigDecimal("peso_inicial"), results.getBigDecimal("peso_actual"),
                results.getBoolean("bobina_activa"));
    }
    private static PersistenceException persistenceFailure(String operation, SQLException e) {
        LOGGER.log(Level.SEVERE,
                "Spool persistence operation {0} failed (SQLState: {1}, vendor code: {2}).",
                new Object[] {operation, e.getSQLState(), e.getErrorCode()});
        return new PersistenceException("Cannot " + operation + " spool data.", e);
    }
}
