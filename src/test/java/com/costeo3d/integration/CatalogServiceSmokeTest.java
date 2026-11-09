package com.costeo3d.integration;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

import com.costeo3d.application.service.CatalogService;
import com.costeo3d.domain.model.Customer;
import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.Model3D;
import com.costeo3d.domain.model.Spool;
import com.costeo3d.infrastructure.config.DatabaseConfig;
import com.costeo3d.infrastructure.logging.AppLogging;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcCustomerDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcMaterialDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcModel3DDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcSpoolDao;

/** Prueba manual de CatalogService contra los cuatro DAO JDBC reales. */
public final class CatalogServiceSmokeTest {

    private static final Logger LOGGER =
            Logger.getLogger(CatalogServiceSmokeTest.class.getName());

    private CatalogServiceSmokeTest() {
    }

    public static void main(String[] args) {
        AppLogging.configure();
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        String brand = "E8_MATERIAL_" + suffix;
        String color = "E8_COLOR_" + suffix;
        String modelName = "E8_MODELO_" + suffix;
        String customerName = "E8_CLIENTE_" + suffix;

        JdbcConnectionFactory factory = null;
        boolean passed = false;
        boolean cleaned = false;

        try {
            factory = new JdbcConnectionFactory(DatabaseConfig.load());
            CatalogService catalogs = createService(factory);

            // 1. Altas: se usan los IDs devueltos por MySQL.
            Material material = catalogs.createMaterial(new Material(
                    brand, "PLA", "Mate", new BigDecimal("1.2500")));
            Spool spool = catalogs.createSpool(new Spool(
                    material, color, new BigDecimal("750.00")));
            Model3D model = catalogs.createModel3D(new Model3D(
                    modelName, null, null, new BigDecimal("45.50"), 90));
            Customer customer = catalogs.createCustomer(new Customer(
                    customerName, "1111111111", "e8-" + suffix + "@example.invalid"));

            require(validId(material.getId()) && validId(spool.getId())
                    && validId(model.getId()) && validId(customer.getId()),
                    "Alguno de los catalogos no recibio un ID de MySQL.");
            System.out.println("1/4 CREATE correcto (material, bobina, modelo y cliente).");

            // 2. Consultas: verificar objetos, relaciones y listados.
            require(Objects.equals(catalogs.findMaterialById(material.getId())
                    .orElseThrow().getBrand(), brand), "No se recupero el material.");
            Spool loadedSpool = catalogs.findSpoolById(spool.getId()).orElseThrow();
            require(Objects.equals(loadedSpool.getMaterial().getId(), material.getId()),
                    "No se recupero la relacion bobina-material.");
            require(Objects.equals(catalogs.findModel3DById(model.getId())
                    .orElseThrow().getName(), modelName), "No se recupero el modelo.");
            require(Objects.equals(catalogs.findCustomerById(customer.getId())
                    .orElseThrow().getName(), customerName), "No se recupero el cliente.");

            require(catalogs.listMaterials().stream().anyMatch(m ->
                    Objects.equals(m.getId(), material.getId())), "Falta material en lista.");
            require(catalogs.listSpools().stream().anyMatch(s ->
                    Objects.equals(s.getId(), spool.getId())), "Falta bobina en lista.");
            require(catalogs.listModels3D().stream().anyMatch(m ->
                    Objects.equals(m.getId(), model.getId())), "Falta modelo en lista.");
            require(catalogs.listCustomers().stream().anyMatch(c ->
                    Objects.equals(c.getId(), customer.getId())), "Falta cliente en lista.");
            System.out.println("2/4 FIND BY ID y LIST correctos (4 catalogos y relacion).");

            // 3. Cambios de datos y baja logica, sin eliminaciones fisicas.
            Material editedMaterial = new Material(material.getId(), brand, "PETG",
                    "Brillante", new BigDecimal("1.7500"), false);
            Spool editedSpool = new Spool(spool.getId(), material, color,
                    new BigDecimal("750.00"), new BigDecimal("625.00"), false);
            Model3D editedModel = new Model3D(model.getId(), modelName,
                    "prueba-e8.png", "Plataforma E8", new BigDecimal("50.25"), 100, false);
            Customer editedCustomer = new Customer(customer.getId(), customerName,
                    "2222222222", "actualizado-" + suffix + "@example.invalid", false);
            require(catalogs.updateMaterial(editedMaterial), "No se actualizo el material.");
            require(catalogs.updateSpool(editedSpool), "No se actualizo la bobina.");
            require(catalogs.updateModel3D(editedModel), "No se actualizo el modelo.");
            require(catalogs.updateCustomer(editedCustomer), "No se actualizo el cliente.");
            System.out.println("3/4 UPDATE correcto (datos y baja logica de 4 catalogos).");

            // 4. Nuevas instancias: todos los datos deben venir de MySQL.
            CatalogService reopened = createService(factory);
            Material readMaterial = reopened.findMaterialById(material.getId()).orElseThrow();
            Spool readSpool = reopened.findSpoolById(spool.getId()).orElseThrow();
            Model3D readModel = reopened.findModel3DById(model.getId()).orElseThrow();
            Customer readCustomer = reopened.findCustomerById(customer.getId()).orElseThrow();

            require(!readMaterial.isActive() && Objects.equals(readMaterial.getType(), "PETG")
                    && readMaterial.getCostPerGram().compareTo(new BigDecimal("1.7500")) == 0,
                    "El material no conserva sus cambios.");
            require(!readSpool.isActive()
                    && readSpool.getCurrentWeight().compareTo(new BigDecimal("625.00")) == 0
                    && Objects.equals(readSpool.getMaterial().getId(), material.getId()),
                    "La bobina no conserva los cambios o su relacion.");
            require(!readModel.isActive()
                    && readModel.getEstimatedPrintTimeMinutes() == 100
                    && readModel.getEstimatedGrams().compareTo(new BigDecimal("50.25")) == 0,
                    "El modelo no conserva los cambios.");
            require(!readCustomer.isActive()
                    && Objects.equals(readCustomer.getPhone(), "2222222222"),
                    "El cliente no conserva los cambios.");

            require(reopened.listMaterials().stream().anyMatch(m ->
                    Objects.equals(m.getId(), material.getId()) && !m.isActive()),
                    "La lista no conserva el material inactivo.");
            require(reopened.listSpools().stream().anyMatch(s ->
                    Objects.equals(s.getId(), spool.getId()) && !s.isActive()),
                    "La lista no conserva la bobina inactiva.");
            require(reopened.listModels3D().stream().anyMatch(m ->
                    Objects.equals(m.getId(), model.getId()) && !m.isActive()),
                    "La lista no conserva el modelo inactivo.");
            require(reopened.listCustomers().stream().anyMatch(c ->
                    Objects.equals(c.getId(), customer.getId()) && !c.isActive()),
                    "La lista no conserva el cliente inactivo.");
            System.out.println("4/4 Persistencia correcta tras reinstanciar CatalogService.");
            passed = true;
        } catch (RuntimeException e) {
            System.err.println("Fallo la prueba de CatalogService: " + e.getMessage());
            LOGGER.warning("CatalogService smoke test failed ("
                    + e.getClass().getSimpleName() + ").");
        } finally {
            if (factory != null) {
                cleaned = cleanTemporaryData(
                        factory, brand, color, modelName, customerName, passed);
            }
        }

        if (passed && cleaned) {
            System.out.println("E8.2 CatalogService: PRUEBA COMPLETA CORRECTA.");
            LOGGER.info("CatalogService smoke test completed successfully.");
        } else {
            System.err.println("E8.2 CatalogService: prueba incompleta; revisar consola.");
        }
    }

    private static CatalogService createService(JdbcConnectionFactory factory) {
        return new CatalogService(
                new JdbcMaterialDao(factory), new JdbcSpoolDao(factory),
                new JdbcModel3DDao(factory), new JdbcCustomerDao(factory));
    }

    /** Limpieza exclusiva por marcas aleatorias, en orden de claves foraneas. */
    private static boolean cleanTemporaryData(JdbcConnectionFactory factory,
            String brand, String color, String modelName, String customerName,
            boolean testPassed) {
        try (Connection connection = factory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                int spools = delete(connection, """
                        DELETE FROM bobina WHERE color = ? AND id_material IN
                        (SELECT id_material FROM material WHERE marca = ?)
                        """, color, brand);
                int models = delete(connection,
                        "DELETE FROM modelo_3d WHERE nombre = ?", modelName);
                int customers = delete(connection,
                        "DELETE FROM cliente WHERE nombre = ?", customerName);
                int materials = delete(connection,
                        "DELETE FROM material WHERE marca = ?", brand);
                require(spools <= 1 && models <= 1 && customers <= 1 && materials <= 1,
                        "La limpieza encontro multiples registros; se cancela.");
                if (testPassed) {
                    require(spools == 1 && models == 1 && customers == 1
                            && materials == 1,
                            "La limpieza no elimino todos los registros de prueba.");
                }
                connection.commit();
                System.out.println("Limpieza: " + spools + " bobina(s), " + materials
                        + " material(es), " + models + " modelo(s) y " + customers
                        + " cliente(s) temporales eliminados.");
                return true;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException | RuntimeException e) {
            System.err.println("No se completo la limpieza. Buscar registros temporales:");
            System.err.println(brand + " / " + color + " / " + modelName
                    + " / " + customerName);
            LOGGER.warning("CatalogService test cleanup failed.");
            return false;
        }
    }

    private static int delete(Connection connection, String sql, String... values)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setString(i + 1, values[i]);
            }
            return statement.executeUpdate();
        }
    }

    private static boolean validId(Integer id) {
        return id != null && id > 0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
