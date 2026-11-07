package com.costeo3d.bootstrap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

import com.costeo3d.infrastructure.config.DatabaseConfig;
import com.costeo3d.infrastructure.logging.AppLogging;
import com.costeo3d.infrastructure.persistence.PersistenceException;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;

/** Verifica la conexion JDBC sin modificar datos de MySQL. */
public final class JdbcSmokeTest {

    private static final Logger LOGGER = Logger.getLogger(JdbcSmokeTest.class.getName());

    private JdbcSmokeTest() {
    }

    public static void main(String[] args) {
        AppLogging.configure();

        try {
            DatabaseConfig config = DatabaseConfig.load();
            JdbcConnectionFactory connectionFactory = new JdbcConnectionFactory(config);

            try (Connection connection = connectionFactory.openConnection()) {
                if (!connection.isValid(2)) {
                    throw new SQLException("La conexion no supero la verificacion.");
                }

                System.out.println("Conexion JDBC establecida correctamente.");
                System.out.println("Base de datos: " + connection.getCatalog());
                System.out.println("Motor: " + connection.getMetaData().getDatabaseProductName());
                LOGGER.info("JDBC smoke test completed successfully.");
            }
        } catch (IllegalStateException | PersistenceException | SQLException e) {
            System.err.println("Fallo la prueba de conexion JDBC: " + e.getMessage());
            LOGGER.warning("JDBC smoke test did not complete successfully.");
        }
    }
}
