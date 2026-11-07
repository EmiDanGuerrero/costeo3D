package com.costeo3d.infrastructure.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.domain.enums.QuoteStatus;
import com.costeo3d.domain.model.Quote;
import com.costeo3d.infrastructure.persistence.PersistenceException;
import com.costeo3d.infrastructure.persistence.dao.QuoteDao;

public final class JdbcQuoteDao implements QuoteDao {

    private static final Logger LOGGER =
            Logger.getLogger(JdbcQuoteDao.class.getName());

    private final JdbcConnectionFactory connectionFactory;

    public JdbcQuoteDao(JdbcConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory, "JDBC connection factory is required");
    }

    @Override
    public Quote insert(Quote quote) {
        Objects.requireNonNull(quote, "Quote is required");
        if (quote.getId() != null) {
            throw new IllegalArgumentException("New quote must not have an ID.");
        }
        if (quote.getCustomer() == null || quote.getCustomer().getId() == null) {
            throw new IllegalArgumentException("Quote customer must be persisted first.");
        }

        try (Connection connection = connectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                Quote saved = JdbcQuoteWriter.insert(connection, quote);
                connection.commit();
                return saved;
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                if (failure instanceof SQLException sqlFailure) {
                    throw persistenceFailure("insert", sqlFailure);
                }
                throw failure;
            }
        } catch (SQLException e) {
            throw persistenceFailure("insert", e);
        }
    }

    @Override
    public Optional<Quote> findById(int id) {
        try (Connection connection = connectionFactory.openConnection()) {
            return JdbcQuoteReader.findById(connection, id);
        } catch (SQLException e) {
            throw persistenceFailure("findById", e);
        }
    }

    @Override
    public List<Quote> findAll() {
        try (Connection connection = connectionFactory.openConnection()) {
            return JdbcQuoteReader.findAll(connection);
        } catch (SQLException e) {
            throw persistenceFailure("findAll", e);
        }
    }

    @Override
    public boolean updateStatus(Quote quote) {
        Objects.requireNonNull(quote, "Quote is required");
        if (quote.getId() == null) {
            throw new IllegalArgumentException("Quote must have an ID to update its status.");
        }
        String sql = "UPDATE presupuesto SET estado = ? WHERE id_presupuesto = ?";
        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, toDatabaseStatus(quote.getStatus()));
            statement.setInt(2, quote.getId());
            if (statement.executeUpdate() > 0) {
                return true;
            }
            try (PreparedStatement check = connection.prepareStatement(
                    "SELECT 1 FROM presupuesto WHERE id_presupuesto = ?")) {
                check.setInt(1, quote.getId());
                try (ResultSet results = check.executeQuery()) {
                    return results.next();
                }
            }
        } catch (SQLException e) {
            throw persistenceFailure("updateStatus", e);
        }
    }

    static String toDatabaseStatus(QuoteStatus status) {
        return switch (Objects.requireNonNull(status, "Quote status is required")) {
            case VALID -> "VIGENTE";
            case EXPIRED -> "VENCIDO";
            case ACCEPTED -> "ACEPTADO";
            case CANCELLED -> "CANCELADO";
        };
    }

    static QuoteStatus fromDatabaseStatus(String status) throws SQLException {
        return switch (status) {
            case "VIGENTE" -> QuoteStatus.VALID;
            case "VENCIDO" -> QuoteStatus.EXPIRED;
            case "ACEPTADO" -> QuoteStatus.ACCEPTED;
            case "CANCELADO" -> QuoteStatus.CANCELLED;
            default -> throw new SQLException("Unknown quote status in database.");
        };
    }

    private static PersistenceException persistenceFailure(String operation, SQLException e) {
        LOGGER.log(Level.SEVERE,
                "Quote persistence operation {0} failed (SQLState: {1}, vendor code: {2}).",
                new Object[] { operation, e.getSQLState(), e.getErrorCode() });
        return new PersistenceException("Cannot " + operation + " quote data.", e);
    }
}
