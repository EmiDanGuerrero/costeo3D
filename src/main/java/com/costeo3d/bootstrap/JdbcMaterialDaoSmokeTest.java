package com.costeo3d.bootstrap;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

import com.costeo3d.domain.model.Material;
import com.costeo3d.infrastructure.config.DatabaseConfig;
import com.costeo3d.infrastructure.logging.AppLogging;
import com.costeo3d.infrastructure.persistence.PersistenceException;
import com.costeo3d.infrastructure.persistence.dao.MaterialDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcMaterialDao;

/** Prueba manual de las cuatro operaciones DAO contra MySQL local. */
public final class JdbcMaterialDaoSmokeTest {

    private static final Logger LOGGER =
            Logger.getLogger(JdbcMaterialDaoSmokeTest.class.getName());

    private JdbcMaterialDaoSmokeTest() {
    }

    public static void main(String[] args) {
        AppLogging.configure();

        // Marca irrepetible para identificar exclusivamente el registro de prueba.
        String testBrand = "E6_JDBC_" + UUID.randomUUID().toString().substring(0, 12);
        JdbcConnectionFactory factory = null;
        boolean insertAttempted = false;
        boolean testsPassed = false;
        boolean cleanupPassed = true;

        try {
            factory = new JdbcConnectionFactory(DatabaseConfig.load());
            MaterialDao dao = new JdbcMaterialDao(factory);

            Material newMaterial = new Material(
                    testBrand, "PLA", "Mate", new BigDecimal("1.25"));

            insertAttempted = true;
            Material saved = dao.insert(newMaterial);
            require(saved.getId() != null && saved.getId() > 0,
                    "INSERT did not return a valid ID.");
            require(newMaterial.getId() == null,
                    "INSERT should not modify the original object.");
            System.out.println("1/4 INSERT correcto. ID generado: " + saved.getId());

            Material fetched = dao.findById(saved.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "FIND BY ID did not return the inserted material."));
            require(Objects.equals(fetched.getBrand(), testBrand)
                            && fetched.getCostPerGram().compareTo(new BigDecimal("1.25")) == 0,
                    "FIND BY ID returned unexpected values.");
            System.out.println("2/4 FIND BY ID correcto.");

            require(dao.findAll().stream().anyMatch(material ->
                            Objects.equals(material.getId(), saved.getId())),
                    "FIND ALL did not include the inserted material.");
            System.out.println("3/4 FIND ALL correcto.");

            Material edited = new Material(
                    saved.getId(), testBrand, "PLA", "Mate",
                    new BigDecimal("1.75"), true);
            require(dao.update(edited), "UPDATE returned false for an existing material.");
            require(dao.update(edited), "UPDATE returned false when values were unchanged.");
            require(dao.findById(saved.getId()).orElseThrow()
                            .getCostPerGram().compareTo(new BigDecimal("1.75")) == 0,
                    "UPDATE did not persist the new cost.");

            edited.deactivate();
            require(dao.update(edited), "UPDATE could not deactivate the material.");
            require(!dao.findById(saved.getId()).orElseThrow().isActive(),
                    "The logical deactivation was not persisted.");
            System.out.println("4/4 UPDATE correcto (costo y baja logica).");
            System.out.println("Comprobacion adicional: UPDATE sin cambios, correcta.");
            testsPassed = true;
        } catch (RuntimeException e) {
            System.err.println("Fallo la prueba de MaterialDao: " + e.getMessage());
            LOGGER.warning("Material DAO smoke test failed (" +
                    e.getClass().getSimpleName() + ").");
        } finally {
            // Limpieza EXCLUSIVA del material temporal de esta ejecucion.
            // No es una operacion de baja fisica del DAO de negocio.
            if (insertAttempted && factory != null) {
                cleanupPassed = removeTestMaterial(factory, testBrand);
            }
        }

        if (testsPassed && cleanupPassed) {
            System.out.println("E6.6: PRUEBA COMPLETA CORRECTA.");
            LOGGER.info("Material DAO integration smoke test completed successfully.");
        } else {
            System.err.println("E6.6: prueba incompleta. Revisar los mensajes anteriores.");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static boolean removeTestMaterial(
            JdbcConnectionFactory factory, String testBrand) {
        String sql = "DELETE FROM material WHERE marca = ? AND tipo = ? AND acabado = ? LIMIT 1";
        try (Connection connection = factory.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, testBrand);
            statement.setString(2, "PLA");
            statement.setString(3, "Mate");
            int deleted = statement.executeUpdate();
            System.out.println(deleted == 1
                    ? "Limpieza: material temporal eliminado correctamente."
                    : "Limpieza: no habia un material temporal que eliminar.");
            return true;
        } catch (SQLException | PersistenceException e) {
            System.err.println("No se pudo limpiar el material temporal de la prueba.");
            LOGGER.warning("Material DAO smoke test cleanup failed.");
            System.err.println("Marca del registro a revisar manualmente: " + testBrand);
            return false;
        }
    }
}
