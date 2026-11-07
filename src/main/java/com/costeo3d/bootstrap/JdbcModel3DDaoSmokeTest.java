package com.costeo3d.bootstrap;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

import com.costeo3d.domain.model.Model3D;
import com.costeo3d.infrastructure.config.DatabaseConfig;
import com.costeo3d.infrastructure.logging.AppLogging;
import com.costeo3d.infrastructure.persistence.dao.Model3DDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcModel3DDao;

/** Prueba manual de Model3DDao con un registro temporal en MySQL. */
public final class JdbcModel3DDaoSmokeTest {

    private static final Logger LOGGER =
            Logger.getLogger(JdbcModel3DDaoSmokeTest.class.getName());

    private JdbcModel3DDaoSmokeTest() {
    }

    public static void main(String[] args) {
        AppLogging.configure();
        String testName = "E7_MODELO_" + UUID.randomUUID().toString().substring(0, 12);
        JdbcConnectionFactory factory = null;
        boolean passed = false;
        boolean cleaned = false;

        try {
            factory = new JdbcConnectionFactory(DatabaseConfig.load());
            Model3DDao models = new JdbcModel3DDao(factory);

            Model3D original = new Model3D(
                    testName, null, null, new BigDecimal("45.50"), 90);
            Model3D saved = models.insert(original);
            require(saved.getId() != null && saved.getId() > 0 && original.getId() == null,
                    "INSERT did not return a new model with a generated ID.");
            System.out.println("1/4 INSERT correcto. ID de modelo: " + saved.getId());

            Model3D fetched = models.findById(saved.getId()).orElseThrow();
            require(Objects.equals(fetched.getName(), testName)
                    && fetched.getEstimatedGrams().compareTo(new BigDecimal("45.50")) == 0
                    && fetched.getEstimatedPrintTimeMinutes() == 90
                    && fetched.getReferenceImage() == null
                    && fetched.getSourcePlatform() == null,
                    "FIND BY ID returned incorrect data or optional fields.");
            System.out.println("2/4 FIND BY ID correcto (incluye campos opcionales nulos).");

            require(models.findAll().stream().anyMatch(
                    model -> Objects.equals(model.getId(), saved.getId())),
                    "FIND ALL did not include the temporary model.");
            System.out.println("3/4 FIND ALL correcto.");

            Model3D updated = new Model3D(saved.getId(), testName,
                    "referencia-prueba.png", "Plataforma de prueba",
                    new BigDecimal("50.25"), 100, true);
            require(models.update(updated), "UPDATE returned false for an existing model.");
            require(models.update(updated), "UPDATE returned false for unchanged values.");
            Model3D reloaded = models.findById(saved.getId()).orElseThrow();
            require(reloaded.getEstimatedGrams().compareTo(new BigDecimal("50.25")) == 0
                    && reloaded.getEstimatedPrintTimeMinutes() == 100
                    && Objects.equals(reloaded.getReferenceImage(), "referencia-prueba.png")
                    && Objects.equals(reloaded.getSourcePlatform(), "Plataforma de prueba"),
                    "UPDATE did not persist all modified fields.");
            updated.deactivate();
            require(models.update(updated), "UPDATE could not deactivate the model.");
            require(!models.findById(saved.getId()).orElseThrow().isActive(),
                    "Logical deactivation was not persisted.");
            require(models.findAll().stream().anyMatch(model ->
                    Objects.equals(model.getId(), saved.getId()) && !model.isActive()),
                    "FIND ALL did not include the inactive model.");
            System.out.println("4/4 UPDATE correcto (datos y baja logica).");
            System.out.println("Comprobacion adicional: UPDATE sin cambios, correcta.");
            passed = true;
        } catch (RuntimeException e) {
            System.err.println("Fallo la prueba de Model3DDao: " + e.getMessage());
            LOGGER.warning("Model3D DAO test failed (" + e.getClass().getSimpleName() + ").");
        } finally {
            if (factory != null) {
                cleaned = cleanTestData(factory, testName);
            }
        }

        if (passed && cleaned) {
            System.out.println("E7.2 Model3D: PRUEBA COMPLETA CORRECTA.");
            LOGGER.info("Model3D DAO smoke test completed successfully.");
        } else {
            System.err.println("E7.2 Model3D: prueba incompleta. Revisar los mensajes.");
        }
    }

    private static boolean cleanTestData(JdbcConnectionFactory factory, String name) {
        try (Connection connection = factory.openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM modelo_3d WHERE nombre = ?")) {
            statement.setString(1, name);
            int deleted = statement.executeUpdate();
            System.out.println("Limpieza: " + deleted + " modelo(s) temporal(es) eliminado(s).");
            return deleted == 1;
        } catch (SQLException | RuntimeException e) {
            System.err.println("No se pudo limpiar el modelo temporal: " + name);
            LOGGER.warning("Model3D DAO test cleanup failed.");
            return false;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
