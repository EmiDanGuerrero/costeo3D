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

import com.costeo3d.domain.model.Model3D;
import com.costeo3d.infrastructure.persistence.PersistenceException;
import com.costeo3d.infrastructure.persistence.dao.Model3DDao;

public final class JdbcModel3DDao implements Model3DDao {

    private static final Logger LOGGER =
            Logger.getLogger(JdbcModel3DDao.class.getName());

    private static final String INSERT_SQL = """
            INSERT INTO modelo_3d (nombre, imagen_referencia, plataforma_origen,
                                   gramos_estimados, tiempo_estimado_minutos, activo)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_SQL = """
            SELECT id_modelo, nombre, imagen_referencia, plataforma_origen,
                   gramos_estimados, tiempo_estimado_minutos, activo
            FROM modelo_3d
            """;

    private static final String UPDATE_SQL = """
            UPDATE modelo_3d
            SET nombre = ?, imagen_referencia = ?, plataforma_origen = ?,
                gramos_estimados = ?, tiempo_estimado_minutos = ?, activo = ?
            WHERE id_modelo = ?
            """;

    private final JdbcConnectionFactory connectionFactory;

    public JdbcModel3DDao(JdbcConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory, "JDBC connection factory is required");
    }
    @Override
    public Model3D insert(Model3D model) {
        Objects.requireNonNull(model, "Model3D is required");
        if (model.getId() != null) {
            throw new IllegalArgumentException("New model must not have an ID.");
        }

        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            setModelFields(statement, model);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Model insert did not return a generated ID.");
                }
                return new Model3D(keys.getInt(1), model.getName(),
                        model.getReferenceImage(), model.getSourcePlatform(),
                        model.getEstimatedGrams(), model.getEstimatedPrintTimeMinutes(),
                        model.isActive());
            }
        } catch (SQLException e) {
            throw persistenceFailure("insert", e);
        }
    }
    @Override
    public Optional<Model3D> findById(int id) {
        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        SELECT_SQL + "WHERE id_modelo = ?")) {
            statement.setInt(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(mapModel(results)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw persistenceFailure("findById", e);
        }
    }
    @Override
    public List<Model3D> findAll() {
        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        SELECT_SQL + "ORDER BY id_modelo");
                ResultSet results = statement.executeQuery()) {
            List<Model3D> models = new ArrayList<>();
            while (results.next()) {
                models.add(mapModel(results));
            }
            return models;
        } catch (SQLException e) {
            throw persistenceFailure("findAll", e);
        }
    }
    @Override
    public boolean update(Model3D model) {
        Objects.requireNonNull(model, "Model3D is required");
        if (model.getId() == null) {
            throw new IllegalArgumentException("Model must have an ID to be updated.");
        }

        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {
            setModelFields(statement, model);
            statement.setInt(7, model.getId());
            if (statement.executeUpdate() > 0) {
                return true;
            }
            // MySQL can report zero changed rows when values are unchanged.
            try (PreparedStatement check = connection.prepareStatement(
                    "SELECT 1 FROM modelo_3d WHERE id_modelo = ?")) {
                check.setInt(1, model.getId());
                try (ResultSet results = check.executeQuery()) {
                    return results.next();
                }
            }
        } catch (SQLException e) {
            throw persistenceFailure("update", e);
        }
    }
    private static void setModelFields(PreparedStatement statement, Model3D model)
            throws SQLException {
        statement.setString(1, model.getName());
        statement.setString(2, model.getReferenceImage());
        statement.setString(3, model.getSourcePlatform());
        statement.setBigDecimal(4, model.getEstimatedGrams());
        statement.setInt(5, model.getEstimatedPrintTimeMinutes());
        statement.setBoolean(6, model.isActive());
    }
    private static Model3D mapModel(ResultSet results) throws SQLException {
        return new Model3D(
                results.getInt("id_modelo"),
                results.getString("nombre"),
                results.getString("imagen_referencia"),
                results.getString("plataforma_origen"),
                results.getBigDecimal("gramos_estimados"),
                results.getInt("tiempo_estimado_minutos"),
                results.getBoolean("activo"));
    }
    private static PersistenceException persistenceFailure(String operation, SQLException e) {
        LOGGER.log(Level.SEVERE,
                "Model3D persistence operation {0} failed (SQLState: {1}, vendor code: {2}).",
                new Object[] { operation, e.getSQLState(), e.getErrorCode() });
        return new PersistenceException("Cannot " + operation + " model data.", e);
    }
}
