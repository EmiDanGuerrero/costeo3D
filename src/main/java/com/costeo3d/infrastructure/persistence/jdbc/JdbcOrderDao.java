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


    /**
     * Status transition with database-side guards. This prevents a stale GUI
     * from overwriting a newer order status, and checks job completion for READY.
     * No job or spool row is updated by this operation.
     */
    @Override
    public boolean changeStatusIfCurrent(
            int orderId, OrderStatus expected, OrderStatus next) {
        if (orderId <= 0) {
            throw new IllegalArgumentException("Order ID must be positive.");
        }
        Objects.requireNonNull(expected, "Expected status is required.");
        Objects.requireNonNull(next, "New status is required.");
        final String guards;
        if (expected == OrderStatus.PENDING && next == OrderStatus.IN_PRODUCTION) {
            guards = """
                    AND EXISTS (SELECT 1 FROM trabajo_impresion AS t
                                WHERE t.id_pedido = p.id_pedido)
                    """;
        } else if (expected == OrderStatus.IN_PRODUCTION && next == OrderStatus.READY) {
            guards = """
                    AND EXISTS (SELECT 1 FROM trabajo_impresion AS t
                                WHERE t.id_pedido = p.id_pedido)
                    AND NOT EXISTS (SELECT 1 FROM trabajo_impresion AS t
                                    WHERE t.id_pedido = p.id_pedido
                                      AND t.estado <> 'FINALIZADO')
                    """;
        } else if (expected == OrderStatus.READY && next == OrderStatus.DELIVERED) {
            guards = "";
        } else if (expected == OrderStatus.PENDING && next == OrderStatus.CANCELLED) {
            guards = """
                    AND NOT EXISTS (SELECT 1 FROM trabajo_impresion AS t
                                    WHERE t.id_pedido = p.id_pedido
                                      AND t.estado <> 'PENDIENTE')
                    """;
        } else {
            throw new IllegalArgumentException(
                    "Unsupported order transition: " + expected + " -> " + next);
        }
        String sql = """
                UPDATE pedido AS p
                SET p.estado = ?
                WHERE p.id_pedido = ? AND p.estado = ?
                """ + guards;
        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, toDatabaseStatus(next));
            statement.setInt(2, orderId);
            statement.setString(3, toDatabaseStatus(expected));
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw persistenceFailure("changeStatusIfCurrent", e);
        }
    }

    /** Atomic, narrowly scoped update. Production and inventory are untouched. */
    @Override
    public boolean updatePendingJobPlan(int orderId, int jobId,
            Integer modelId, int estimatedMinutes) {
        if (orderId <= 0 || jobId <= 0 || estimatedMinutes <= 0) {
            throw new IllegalArgumentException("IDs and estimated minutes must be positive.");
        }
        final String sql = """
                UPDATE trabajo_impresion AS t
                INNER JOIN pedido AS p ON p.id_pedido = t.id_pedido
                SET t.id_modelo = ?, t.tiempo_estimado_minutos = ?
                WHERE t.id_trabajo = ? AND t.id_pedido = ?
                  AND t.estado = 'PENDIENTE' AND p.estado = 'PENDIENTE'
                  AND (? IS NULL OR t.id_modelo <=> ? OR EXISTS (
                      SELECT 1 FROM modelo_3d AS m
                      WHERE m.id_modelo = ? AND m.activo = 1
                  ))
                """;
        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, modelId, java.sql.Types.INTEGER);
            statement.setInt(2, estimatedMinutes);
            statement.setInt(3, jobId);
            statement.setInt(4, orderId);
            statement.setObject(5, modelId, java.sql.Types.INTEGER);
            statement.setObject(6, modelId, java.sql.Types.INTEGER);
            statement.setObject(7, modelId, java.sql.Types.INTEGER);
            if (statement.executeUpdate() > 0) return true;

            // MySQL can report 0 modified rows when the values were already equal.
            // Verify eligibility before treating that case as success.
            final String unchangedSql = """
                    SELECT 1 FROM trabajo_impresion AS t
                    INNER JOIN pedido AS p ON p.id_pedido = t.id_pedido
                    WHERE t.id_trabajo = ? AND t.id_pedido = ?
                      AND t.estado = 'PENDIENTE' AND p.estado = 'PENDIENTE'
                      AND t.tiempo_estimado_minutos = ?
                      AND t.id_modelo <=> ?
                    """;
            try (PreparedStatement check = connection.prepareStatement(unchangedSql)) {
                check.setInt(1, jobId);
                check.setInt(2, orderId);
                check.setInt(3, estimatedMinutes);
                check.setObject(4, modelId, java.sql.Types.INTEGER);
                try (ResultSet results = check.executeQuery()) {
                    return results.next();
                }
            }
        } catch (SQLException exception) {
            throw persistenceFailure("updatePendingJobPlan", exception);
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
