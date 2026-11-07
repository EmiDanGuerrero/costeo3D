package com.costeo3d.infrastructure.persistence.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.infrastructure.config.DatabaseConfig;
import com.costeo3d.infrastructure.persistence.PersistenceException;

public final class JdbcConnectionFactory {

    private static final Logger LOGGER =
            Logger.getLogger(JdbcConnectionFactory.class.getName());

    private final DatabaseConfig config;

    public JdbcConnectionFactory(DatabaseConfig config) {
        this.config = Objects.requireNonNull(config, "Database configuration is required");
    }

    public Connection openConnection() {
        try {
            return DriverManager.getConnection(
                    config.getUrl(),
                    config.getUser(),
                    config.getPassword());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "Database connection failed (SQLState: {0}, vendor code: {1}).",
                    new Object[] { e.getSQLState(), e.getErrorCode() });
            throw new PersistenceException("Cannot establish database connection.", e);
        }
    }
}
