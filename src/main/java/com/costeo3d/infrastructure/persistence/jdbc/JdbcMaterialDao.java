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
import com.costeo3d.infrastructure.persistence.PersistenceException;
import com.costeo3d.infrastructure.persistence.dao.MaterialDao;

public final class JdbcMaterialDao implements MaterialDao {

    private static final Logger LOGGER =
            Logger.getLogger(JdbcMaterialDao.class.getName());

    private static final String INSERT_SQL = """
            INSERT INTO material (marca, tipo, acabado, costo_por_gramo, activo)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String SELECT_SQL = """
            SELECT id_material, marca, tipo, acabado, costo_por_gramo, activo
            FROM material
            """;

    private static final String UPDATE_SQL = """
            UPDATE material
            SET marca = ?, tipo = ?, acabado = ?, costo_por_gramo = ?, activo = ?
            WHERE id_material = ?
            """;

    private final JdbcConnectionFactory connectionFactory;

    public JdbcMaterialDao(JdbcConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory, "JDBC connection factory is required");
    }

    @Override
    public Material insert(Material material) {
        Objects.requireNonNull(material, "Material is required");
        if (material.getId() != null) {
            throw new IllegalArgumentException("New material must not have an ID.");
        }

        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            setMaterialFields(statement, material);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Material insert did not return a generated ID.");
                }
                return new Material(
                        keys.getInt(1),
                        material.getBrand(),
                        material.getType(),
                        material.getFinish(),
                        material.getCostPerGram(),
                        material.isActive());
            }
        } catch (SQLException e) {
            throw persistenceFailure("insert", e);
        }
    }

    @Override
    public Optional<Material> findById(int id) {
        String sql = SELECT_SQL + "WHERE id_material = ?";
        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet results = statement.executeQuery()) {
                if (results.next()) {
                    return Optional.of(mapMaterial(results));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw persistenceFailure("findById", e);
        }
    }

    @Override
    public List<Material> findAll() {
        String sql = SELECT_SQL + "ORDER BY id_material";
        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet results = statement.executeQuery()) {
            List<Material> materials = new ArrayList<>();
            while (results.next()) {
                materials.add(mapMaterial(results));
            }
            return materials;
        } catch (SQLException e) {
            throw persistenceFailure("findAll", e);
        }
    }

    @Override
    public boolean update(Material material) {
        Objects.requireNonNull(material, "Material is required");
        if (material.getId() == null) {
            throw new IllegalArgumentException("Material must have an ID to be updated.");
        }

        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {
            setMaterialFields(statement, material);
            statement.setInt(6, material.getId());
            if (statement.executeUpdate() > 0) {
                return true;
            }

            // MySQL may report zero changed rows when values are unchanged.
            // Check existence rather than treating that case as a missing material.
            try (PreparedStatement check = connection.prepareStatement(
                    "SELECT 1 FROM material WHERE id_material = ?")) {
                check.setInt(1, material.getId());
                try (ResultSet results = check.executeQuery()) {
                    return results.next();
                }
            }
        } catch (SQLException e) {
            throw persistenceFailure("update", e);
        }
    }

    private static void setMaterialFields(PreparedStatement statement, Material material)
            throws SQLException {
        statement.setString(1, material.getBrand());
        statement.setString(2, material.getType());
        statement.setString(3, material.getFinish());
        statement.setBigDecimal(4, material.getCostPerGram());
        statement.setBoolean(5, material.isActive());
    }

    private static Material mapMaterial(ResultSet results) throws SQLException {
        return new Material(
                results.getInt("id_material"),
                results.getString("marca"),
                results.getString("tipo"),
                results.getString("acabado"),
                results.getBigDecimal("costo_por_gramo"),
                results.getBoolean("activo"));
    }

    private static PersistenceException persistenceFailure(String operation, SQLException e) {
        LOGGER.log(Level.SEVERE,
                "Material persistence operation {0} failed (SQLState: {1}, vendor code: {2}).",
                new Object[] { operation, e.getSQLState(), e.getErrorCode() });
        return new PersistenceException("Cannot " + operation + " material data.", e);
    }
}
