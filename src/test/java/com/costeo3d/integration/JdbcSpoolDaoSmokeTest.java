package com.costeo3d.integration;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.Spool;
import com.costeo3d.infrastructure.config.DatabaseConfig;
import com.costeo3d.infrastructure.logging.AppLogging;
import com.costeo3d.infrastructure.persistence.dao.MaterialDao;
import com.costeo3d.infrastructure.persistence.dao.SpoolDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcMaterialDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcSpoolDao;

/** Prueba manual de SpoolDao usando registros temporales en MySQL local. */
public final class JdbcSpoolDaoSmokeTest {

    private static final Logger LOGGER = Logger.getLogger(JdbcSpoolDaoSmokeTest.class.getName());

    private JdbcSpoolDaoSmokeTest() {
    }

    public static void main(String[] args) {
        AppLogging.configure();
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        String testBrand = "E7_JDBC_" + suffix;
        String testColor = "E7_TEST_" + suffix;
        JdbcConnectionFactory factory = null;
        boolean passed = false;
        boolean cleaned = false;

        try {
            factory = new JdbcConnectionFactory(DatabaseConfig.load());
            MaterialDao materials = new JdbcMaterialDao(factory);
            SpoolDao spools = new JdbcSpoolDao(factory);
            Material material = materials.insert(new Material(
                    testBrand, "PLA", "Mate", new BigDecimal("1.25")));
            require(material.getId() != null && material.getId() > 0,
                    "The temporary material has no valid ID.");

            Spool original = new Spool(material, testColor, new BigDecimal("750.00"));
            Spool saved = spools.insert(original);
            require(saved.getId() != null && saved.getId() > 0 && original.getId() == null,
                    "INSERT did not return a new spool with a generated ID.");
            System.out.println("1/4 INSERT correcto. ID de bobina: " + saved.getId());

            Spool fetched = spools.findById(saved.getId()).orElseThrow();
            require(Objects.equals(fetched.getMaterial().getId(), material.getId())
                    && Objects.equals(fetched.getMaterial().getBrand(), testBrand)
                    && Objects.equals(fetched.getColor(), testColor)
                    && fetched.getInitialWeight().compareTo(new BigDecimal("750.00")) == 0,
                    "FIND BY ID returned incorrect spool or material data.");
            System.out.println("2/4 FIND BY ID correcto (incluye Material).");

            require(spools.findAll().stream().anyMatch(
                    spool -> Objects.equals(spool.getId(), saved.getId())),
                    "FIND ALL did not include the temporary spool.");
            System.out.println("3/4 FIND ALL correcto.");

            Spool edited = new Spool(saved.getId(), material, testColor,
                    new BigDecimal("750.00"), new BigDecimal("700.00"), true);
            require(spools.update(edited), "UPDATE returned false for an existing spool.");
            require(spools.update(edited), "UPDATE returned false for unchanged values.");
            require(spools.findById(saved.getId()).orElseThrow().getCurrentWeight()
                    .compareTo(new BigDecimal("700.00")) == 0,
                    "UPDATE did not persist the current weight.");
            edited.deactivate();
            require(spools.update(edited), "UPDATE could not deactivate the spool.");
            require(!spools.findById(saved.getId()).orElseThrow().isActive(),
                    "The logical deactivation was not persisted.");
            require(spools.findAll().stream().anyMatch(spool ->
                    Objects.equals(spool.getId(), saved.getId()) && !spool.isActive()),
                    "FIND ALL did not include the inactive spool.");
            System.out.println("4/4 UPDATE correcto (peso y baja logica).");
            System.out.println("Comprobacion adicional: UPDATE sin cambios, correcta.");
            passed = true;
        } catch (RuntimeException e) {
            System.err.println("Fallo la prueba de SpoolDao: " + e.getMessage());
            LOGGER.warning("Spool DAO test failed (" + e.getClass().getSimpleName() + ").");
        } finally {
            if (factory != null) {
                cleaned = cleanTestData(factory, testBrand, testColor);
            }
        }

        if (passed && cleaned) {
            System.out.println("E7.1: PRUEBA COMPLETA CORRECTA.");
            LOGGER.info("Spool DAO smoke test completed successfully.");
        } else {
            System.err.println("E7.1: prueba incompleta. Revisar los mensajes anteriores.");
        }
    }

    private static boolean cleanTestData(
            JdbcConnectionFactory factory, String brand, String color) {
        String deleteSpool = """
                DELETE FROM bobina WHERE color = ? AND id_material IN
                (SELECT id_material FROM material WHERE marca = ?)
                """;
        String deleteMaterial = "DELETE FROM material WHERE marca = ?";
        try (Connection connection = factory.openConnection()) {
            int spoolCount;
            int materialCount;
            try (PreparedStatement statement = connection.prepareStatement(deleteSpool)) {
                statement.setString(1, color);
                statement.setString(2, brand);
                spoolCount = statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(deleteMaterial)) {
                statement.setString(1, brand);
                materialCount = statement.executeUpdate();
            }
            System.out.println("Limpieza: " + spoolCount + " bobina(s) y "
                    + materialCount + " material(es) temporales eliminados.");
            return spoolCount == 1 && materialCount == 1;
        } catch (SQLException | RuntimeException e) {
            System.err.println("No se pudo completar la limpieza de registros temporales.");
            System.err.println("Revisar manualmente la marca: " + brand
                    + " y el color: " + color);
            LOGGER.warning("Spool DAO test cleanup failed.");
            return false;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
