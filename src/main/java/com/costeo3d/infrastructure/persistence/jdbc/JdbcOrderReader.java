package com.costeo3d.infrastructure.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.costeo3d.domain.enums.OrderStatus;
import com.costeo3d.domain.model.Order;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.domain.model.Quote;

/** Reads a complete order, quote and jobs using one JDBC connection. */
final class JdbcOrderReader {

    private static final String SELECT_SQL = """
            SELECT id_presupuesto, fecha_creacion, estado
            FROM pedido WHERE id_pedido = ?
            """;

    private JdbcOrderReader() { }

    static Optional<Order> findById(Connection connection, int id)
            throws SQLException {
        int quoteId;
        LocalDateTime createdAt;
        OrderStatus status;
        try (PreparedStatement statement = connection.prepareStatement(SELECT_SQL)) {
            statement.setInt(1, id);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) return Optional.empty();
                quoteId = results.getInt("id_presupuesto");
                createdAt = results.getTimestamp("fecha_creacion").toLocalDateTime();
                status = fromDatabaseStatus(results.getString("estado"));
            }
        }
        Quote quote = JdbcQuoteReader.findById(connection, quoteId)
                .orElseThrow(() -> new SQLException("Order refers to a missing quote."));
        Order order = new Order(id, quote, createdAt, status);
        for (PrintJob job : JdbcPrintJobReader.findForOrder(connection, order)) {
            order.addPrintJob(job);
        }
        return Optional.of(order);
    }

    static List<Order> findAll(Connection connection) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id_pedido FROM pedido ORDER BY id_pedido");
                ResultSet results = statement.executeQuery()) {
            while (results.next()) ids.add(results.getInt(1));
        }
        List<Order> orders = new ArrayList<>();
        for (int id : ids) {
            findById(connection, id).ifPresent(orders::add);
        }
        return orders;
    }

    private static OrderStatus fromDatabaseStatus(String status) throws SQLException {
        if (status == null) {
            throw new SQLException("Missing order status in database.");
        }
        return switch (status) {
            case "PENDIENTE" -> OrderStatus.PENDING;
            case "EN_PRODUCCION" -> OrderStatus.IN_PRODUCTION;
            case "LISTO" -> OrderStatus.READY;
            case "ENTREGADO" -> OrderStatus.DELIVERED;
            case "CANCELADO" -> OrderStatus.CANCELLED;
            default -> throw new SQLException("Unknown order status in database.");
        };
    }
}
