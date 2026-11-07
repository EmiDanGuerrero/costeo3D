package com.costeo3d.infrastructure.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.costeo3d.domain.enums.PrintJobStatus;
import com.costeo3d.domain.model.Model3D;
import com.costeo3d.domain.model.Order;
import com.costeo3d.domain.model.PrintJob;

/** Reconstructs print jobs without recursively loading an order's jobs. */
final class JdbcPrintJobReader {

    private static final String SELECT_SQL = """
            SELECT t.id_trabajo, t.id_pedido, t.fecha_creacion, t.fecha_inicio,
                   t.fecha_fin, t.estado, t.tiempo_estimado_minutos,
                   m.id_modelo, m.nombre, m.imagen_referencia, m.plataforma_origen,
                   m.gramos_estimados, m.tiempo_estimado_minutos AS modelo_tiempo,
                   m.activo AS modelo_activo
            FROM trabajo_impresion t
            LEFT JOIN modelo_3d m ON m.id_modelo = t.id_modelo
            WHERE t.id_trabajo = ?
            """;

    private JdbcPrintJobReader() { }

    static Optional<PrintJob> findById(Connection connection, int id)
            throws SQLException {
        Optional<JobRow> row = readRow(connection, id);
        if (row.isEmpty()) {
            return Optional.empty();
        }
        Order order = null;
        if (row.get().orderId() != null) {
            order = JdbcOrderReader.findById(connection, row.get().orderId())
                    .orElseThrow(() -> new SQLException("Print job refers to a missing order."));
        }
        return Optional.of(toJob(row.get(), order));
    }

    static List<PrintJob> findAll(Connection connection) throws SQLException {
        List<PrintJob> jobs = new ArrayList<>();
        for (int id : readIds(connection,
                "SELECT id_trabajo FROM trabajo_impresion ORDER BY id_trabajo", null)) {
            findById(connection, id).ifPresent(jobs::add);
        }
        return jobs;
    }

    static List<PrintJob> findByOrderId(Connection connection, int orderId)
            throws SQLException {
        Optional<Order> order = JdbcOrderReader.findById(connection, orderId);
        return order.isPresent() ? findForOrder(connection, order.get()) : List.of();
    }

    // Used by the future JdbcOrderReader integration to avoid recursion.
    static List<PrintJob> findForOrder(Connection connection, Order order)
            throws SQLException {
        List<PrintJob> jobs = new ArrayList<>();
        for (int id : readIds(connection,
                "SELECT id_trabajo FROM trabajo_impresion "
                + "WHERE id_pedido = ? ORDER BY id_trabajo", order.getId())) {
            JobRow row = readRow(connection, id).orElseThrow(
                    () -> new SQLException("Print job disappeared during the read."));
            jobs.add(toJob(row, order));
        }
        return jobs;
    }

    private static List<Integer> readIds(Connection connection, String sql, Integer orderId)
            throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            if (orderId != null) {
                stmt.setInt(1, orderId);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }

    private static Optional<JobRow> readRow(Connection connection, int id)
            throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(SELECT_SQL)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                int orderId = rs.getInt("id_pedido");
                Integer associatedOrder = rs.wasNull() ? null : orderId;
                int modelId = rs.getInt("id_modelo");
                Model3D model = rs.wasNull() ? null : new Model3D(
                        modelId, rs.getString("nombre"),
                        rs.getString("imagen_referencia"),
                        rs.getString("plataforma_origen"),
                        rs.getBigDecimal("gramos_estimados"),
                        rs.getInt("modelo_tiempo"), rs.getBoolean("modelo_activo"));
                return Optional.of(new JobRow(rs.getInt("id_trabajo"), associatedOrder,
                        model, rs.getTimestamp("fecha_creacion").toLocalDateTime(),
                        dateOrNull(rs.getTimestamp("fecha_inicio")),
                        dateOrNull(rs.getTimestamp("fecha_fin")),
                        fromDbStatus(rs.getString("estado")),
                        rs.getInt("tiempo_estimado_minutos")));
            }
        }
    }

    private static PrintJob toJob(JobRow row, Order order) {
        return new PrintJob(row.id(), order, row.model(), row.createdAt(),
                row.startedAt(), row.finishedAt(), row.status(), row.minutes());
    }

    private static LocalDateTime dateOrNull(Timestamp date) {
        return date == null ? null : date.toLocalDateTime();
    }

    private static PrintJobStatus fromDbStatus(String status) throws SQLException {
        if (status == null) {
            throw new SQLException("Missing print job status in database.");
        }
        return switch (status) {
            case "PENDIENTE" -> PrintJobStatus.PENDING;
            case "EN_PROCESO" -> PrintJobStatus.IN_PROGRESS;
            case "FINALIZADO" -> PrintJobStatus.COMPLETED;
            case "FALLIDO" -> PrintJobStatus.FAILED;
            default -> throw new SQLException("Unknown print job status in database.");
        };
    }

    private record JobRow(int id, Integer orderId, Model3D model,
            LocalDateTime createdAt, LocalDateTime startedAt,
            LocalDateTime finishedAt, PrintJobStatus status, int minutes) { }
}
