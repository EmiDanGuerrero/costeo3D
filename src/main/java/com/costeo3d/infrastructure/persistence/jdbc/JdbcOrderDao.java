package com.costeo3d.infrastructure.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.domain.enums.OrderStatus;
import com.costeo3d.domain.model.Order;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.infrastructure.persistence.PersistenceException;
import com.costeo3d.infrastructure.persistence.dao.OrderDao;

public final class JdbcOrderDao implements OrderDao {

    private static final Logger LOGGER =
            Logger.getLogger(JdbcOrderDao.class.getName());
    private static final String INSERT_SQL = """
            INSERT INTO pedido (id_presupuesto, fecha_creacion, estado)
            VALUES (?, ?, ?)
            """;
    private final JdbcConnectionFactory connectionFactory;

    public JdbcOrderDao(JdbcConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public Order insert(Order order) {
        Objects.requireNonNull(order, "Order is required");
        if (order.getId() != null) {
            throw new IllegalArgumentException("New order must not have an ID.");
        }
        if (order.getQuote() == null || order.getQuote().getId() == null) {
            throw new IllegalArgumentException("Order quote must be persisted first.");
        }
        Objects.requireNonNull(order.getCreatedAt(), "Creation date is required");

        try (Connection connection = connectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                Order saved = insertOrder(connection, order);
                for (PrintJob job : order.getPrintJobs()) {
                    Objects.requireNonNull(job, "Print job is required");
                    if (job.getId() != null) {
                        throw new IllegalArgumentException(
                                "New order cannot include an already persisted print job.");
                    }
                    // Associate each new job with the generated order ID.
                    PrintJob pending = new PrintJob(null, saved, job.getModel(),
                            job.getCreatedAt(), job.getStartedAt(), job.getFinishedAt(),
                            job.getStatus(), job.getEstimatedPrintTimeMinutes());
                    saved.addPrintJob(JdbcPrintJobDao.insert(connection, pending));
                }
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

    private static Order insertOrder(Connection connection, Order order)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, order.getQuote().getId());
            statement.setTimestamp(2, Timestamp.valueOf(order.getCreatedAt()));
            statement.setString(3, toDatabaseStatus(order.getStatus()));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Order insert did not return a generated ID.");
                }
                return new Order(keys.getInt(1), order.getQuote(),
                        order.getCreatedAt(), order.getStatus());
            }
        }
    }

    @Override
    public Optional<Order> findById(int id) {
        try (Connection connection = connectionFactory.openConnection()) {
            return JdbcOrderReader.findById(connection, id);
        } catch (SQLException e) {
            throw persistenceFailure("findById", e);
        }
    }

    @Override
    public List<Order> findAll() {
        try (Connection connection = connectionFactory.openConnection()) {
            return JdbcOrderReader.findAll(connection);
        } catch (SQLException e) {
            throw persistenceFailure("findAll", e);
        }
    }

    @Override
    public boolean updateStatus(Order order) {
        Objects.requireNonNull(order, "Order is required");
        if (order.getId() == null) {
            throw new IllegalArgumentException("Order must have an ID to update its status.");
        }
        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE pedido SET estado = ? WHERE id_pedido = ?")) {
            statement.setString(1, toDatabaseStatus(order.getStatus()));
            statement.setInt(2, order.getId());
            if (statement.executeUpdate() > 0) return true;
            try (PreparedStatement check = connection.prepareStatement(
                    "SELECT 1 FROM pedido WHERE id_pedido = ?")) {
                check.setInt(1, order.getId());
                try (ResultSet result = check.executeQuery()) {
                    return result.next();
                }
            }
        } catch (SQLException e) {
            throw persistenceFailure("updateStatus", e);
        }
    }

    private static String toDatabaseStatus(OrderStatus status) {
        return switch (Objects.requireNonNull(status, "Order status is required")) {
            case PENDING -> "PENDIENTE";
            case IN_PRODUCTION -> "EN_PRODUCCION";
            case READY -> "LISTO";
            case DELIVERED -> "ENTREGADO";
            case CANCELLED -> "CANCELADO";
        };
    }

    private static PersistenceException persistenceFailure(String operation, SQLException e) {
        LOGGER.log(Level.SEVERE,
                "Order persistence operation {0} failed (SQLState: {1}, vendor code: {2}).",
                new Object[] { operation, e.getSQLState(), e.getErrorCode() });
        return new PersistenceException("Cannot " + operation + " order data.", e);
    }
}
