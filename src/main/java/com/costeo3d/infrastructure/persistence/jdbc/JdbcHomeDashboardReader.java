package com.costeo3d.infrastructure.persistence.jdbc;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.application.port.HomeDashboardReader;
import com.costeo3d.application.service.HomeDashboardSnapshot;
import com.costeo3d.application.service.HomeDashboardSnapshot.SpoolAlert;
import com.costeo3d.infrastructure.persistence.PersistenceException;

/** Lee exclusivamente indicadores desde MySQL sin cargar catálogos completos. */
public final class JdbcHomeDashboardReader implements HomeDashboardReader {

    private static final Logger LOGGER = Logger.getLogger(JdbcHomeDashboardReader.class.getName());

    private static final String ACTIVE_ORDERS_SQL = """
            SELECT COUNT(*) FROM pedido
            WHERE estado IN ('PENDIENTE', 'EN_PRODUCCION')
            """;
    private static final String IN_PROGRESS_SQL = """
            SELECT COUNT(*) FROM trabajo_impresion WHERE estado = 'EN_PROCESO'
            """;
    private static final String LOW_STOCK_COUNT_SQL = """
            SELECT COUNT(*) FROM bobina b
            INNER JOIN material m ON m.id_material = b.id_material
            WHERE b.activo = 1 AND m.activo = 1 AND b.peso_actual <= ?
            """;
    private static final String WASTE_SQL = """
            SELECT COUNT(DISTINCT me.id_trabajo) AS fallas,
                   COALESCE(SUM(cf.gramos_consumidos), 0) AS gramos
            FROM merma_estimada me
            INNER JOIN trabajo_impresion t ON t.id_trabajo = me.id_trabajo
            LEFT JOIN consumo_filamento cf ON cf.id_trabajo = t.id_trabajo
            WHERE t.estado = 'FALLIDO'
            """;
    private static final String ALERTS_SQL = """
            SELECT b.id_bobina, m.marca, m.tipo, m.acabado, b.color, b.peso_actual
            FROM bobina b
            INNER JOIN material m ON m.id_material = b.id_material
            WHERE b.activo = 1 AND m.activo = 1 AND b.peso_actual <= ?
            ORDER BY b.peso_actual ASC, b.id_bobina ASC
            LIMIT ?
            """;

    private final JdbcConnectionFactory factory;

    public JdbcHomeDashboardReader(JdbcConnectionFactory factory) {
        this.factory = Objects.requireNonNull(factory, "Se requiere la fábrica de conexiones.");
    }

    @Override
    public HomeDashboardSnapshot load(BigDecimal lowStockThreshold, int maxAlerts) {
        Objects.requireNonNull(lowStockThreshold, "Se requiere umbral de stock.");
        if (lowStockThreshold.signum() < 0 || maxAlerts <= 0) {
            throw new IllegalArgumentException("Parámetros de consulta inválidos.");
        }
        try (Connection connection = factory.openConnection()) {
            int activeOrders = count(connection, ACTIVE_ORDERS_SQL);
            int jobsInProgress = count(connection, IN_PROGRESS_SQL);
            int lowStockSpools = lowStockCount(connection, lowStockThreshold);
            WasteTotals waste = wasteTotals(connection);
            List<SpoolAlert> alerts = alerts(connection, lowStockThreshold, maxAlerts);
            return new HomeDashboardSnapshot(activeOrders, jobsInProgress, lowStockSpools,
                    waste.failedJobs(), waste.grams(), lowStockThreshold, alerts);
        } catch (SQLException exception) {
            LOGGER.log(Level.SEVERE,
                    "Home dashboard read failed (SQLState: {0}, vendor code: {1}).",
                    new Object[] { exception.getSQLState(), exception.getErrorCode() });
            throw new PersistenceException("No se pudo consultar el panel de Inicio.", exception);
        }
    }

    private static int count(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet results = statement.executeQuery()) {
            if (!results.next()) throw new SQLException("No se obtuvo el indicador solicitado.");
            return results.getInt(1);
        }
    }

    private static int lowStockCount(Connection connection, BigDecimal threshold)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOW_STOCK_COUNT_SQL)) {
            statement.setBigDecimal(1, threshold);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) throw new SQLException("No se obtuvo el indicador de stock.");
                return results.getInt(1);
            }
        }
    }

    private static WasteTotals wasteTotals(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(WASTE_SQL);
                ResultSet results = statement.executeQuery()) {
            if (!results.next()) throw new SQLException("No se obtuvo el indicador de merma.");
            return new WasteTotals(results.getInt("fallas"), results.getBigDecimal("gramos"));
        }
    }

    private static List<SpoolAlert> alerts(Connection connection,
            BigDecimal threshold, int limit) throws SQLException {
        List<SpoolAlert> found = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(ALERTS_SQL)) {
            statement.setBigDecimal(1, threshold);
            statement.setInt(2, limit);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    String material = results.getString("marca") + " · "
                            + results.getString("tipo") + " · " + results.getString("acabado");
                    found.add(new SpoolAlert(results.getInt("id_bobina"), material,
                            results.getString("color"), results.getBigDecimal("peso_actual")));
                }
            }
        }
        return found;
    }

    private record WasteTotals(int failedJobs, BigDecimal grams) { }
}
