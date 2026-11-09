package com.costeo3d.integration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

import com.costeo3d.domain.model.Customer;
import com.costeo3d.infrastructure.config.DatabaseConfig;
import com.costeo3d.infrastructure.logging.AppLogging;
import com.costeo3d.infrastructure.persistence.dao.CustomerDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcCustomerDao;

/** Prueba manual de CustomerDao con un registro temporal en MySQL. */
public final class JdbcCustomerDaoSmokeTest {

    private static final Logger LOGGER =
            Logger.getLogger(JdbcCustomerDaoSmokeTest.class.getName());

    private JdbcCustomerDaoSmokeTest() {
    }

    public static void main(String[] args) {
        AppLogging.configure();
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        String testName = "E7_CLIENTE_" + suffix;
        String testEmail = "test-" + suffix + "@example.invalid";
        String updatedEmail = "updated-" + suffix + "@example.invalid";
        JdbcConnectionFactory factory = null;
        boolean passed = false;
        boolean cleaned = false;

        try {
            factory = new JdbcConnectionFactory(DatabaseConfig.load());
            CustomerDao customers = new JdbcCustomerDao(factory);

            Customer original = new Customer(testName, "1111111111", testEmail);
            Customer saved = customers.insert(original);
            require(saved.getId() != null && saved.getId() > 0 && original.getId() == null,
                    "INSERT did not return a new customer with a generated ID.");
            System.out.println("1/4 INSERT correcto. ID de cliente: " + saved.getId());

            Customer fetched = customers.findById(saved.getId()).orElseThrow();
            require(Objects.equals(fetched.getName(), testName)
                    && Objects.equals(fetched.getPhone(), "1111111111")
                    && Objects.equals(fetched.getEmail(), testEmail)
                    && fetched.isActive(),
                    "FIND BY ID returned incorrect customer data.");
            System.out.println("2/4 FIND BY ID correcto.");

            require(customers.findAll().stream().anyMatch(
                    customer -> Objects.equals(customer.getId(), saved.getId())),
                    "FIND ALL did not include the temporary customer.");
            System.out.println("3/4 FIND ALL correcto.");

            Customer updated = new Customer(saved.getId(), testName,
                    "2222222222", updatedEmail, true);
            require(customers.update(updated), "UPDATE returned false for an existing customer.");
            require(customers.update(updated), "UPDATE returned false for unchanged values.");
            Customer reloaded = customers.findById(saved.getId()).orElseThrow();
            require(Objects.equals(reloaded.getPhone(), "2222222222")
                    && Objects.equals(reloaded.getEmail(), updatedEmail),
                    "UPDATE did not persist modified contact details.");
            updated.deactivate();
            require(customers.update(updated), "UPDATE could not deactivate the customer.");
            require(!customers.findById(saved.getId()).orElseThrow().isActive(),
                    "Logical deactivation was not persisted.");
            require(customers.findAll().stream().anyMatch(customer ->
                    Objects.equals(customer.getId(), saved.getId()) && !customer.isActive()),
                    "FIND ALL did not include the inactive customer.");
            System.out.println("4/4 UPDATE correcto (contacto y baja logica).");
            System.out.println("Comprobacion adicional: UPDATE sin cambios, correcta.");
            passed = true;
        } catch (RuntimeException e) {
            System.err.println("Fallo la prueba de CustomerDao: " + e.getMessage());
            LOGGER.warning("Customer DAO test failed (" + e.getClass().getSimpleName() + ").");
        } finally {
            if (factory != null) {
                cleaned = cleanTestData(factory, testName);
            }
        }

        if (passed && cleaned) {
            System.out.println("E7.2 Customer: PRUEBA COMPLETA CORRECTA.");
            LOGGER.info("Customer DAO smoke test completed successfully.");
        } else {
            System.err.println("E7.2 Customer: prueba incompleta. Revisar los mensajes.");
        }
    }

    private static boolean cleanTestData(JdbcConnectionFactory factory, String name) {
        try (Connection connection = factory.openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM cliente WHERE nombre = ?")) {
            statement.setString(1, name);
            int deleted = statement.executeUpdate();
            System.out.println("Limpieza: " + deleted + " cliente(s) temporal(es) eliminado(s).");
            return deleted == 1;
        } catch (SQLException | RuntimeException e) {
            System.err.println("No se pudo limpiar el cliente temporal: " + name);
            LOGGER.warning("Customer DAO test cleanup failed.");
            return false;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
