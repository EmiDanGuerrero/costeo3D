package com.costeo3d.infrastructure.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.costeo3d.domain.model.Customer;
import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.Model3D;
import com.costeo3d.domain.model.Quote;
import com.costeo3d.domain.model.QuoteItem;
import com.costeo3d.domain.model.QuoteMaterialItem;

/** Internal reader: reconstructs each quote with its original applied amounts. */
final class JdbcQuoteReader {

    private static final String QUOTE_SQL = """
            SELECT p.*, c.nombre, c.telefono, c.email, c.activo AS cliente_activo
            FROM presupuesto p JOIN cliente c ON c.id_cliente = p.id_cliente
            WHERE p.id_presupuesto = ?
            """;

    private static final String ITEM_SQL = """
            SELECT d.*, m.nombre AS modelo_nombre, m.imagen_referencia,
                   m.plataforma_origen, m.gramos_estimados AS modelo_gramos,
                   m.tiempo_estimado_minutos AS modelo_tiempo,
                   m.activo AS modelo_activo
            FROM detalle_presupuesto d
            LEFT JOIN modelo_3d m ON m.id_modelo = d.id_modelo
            WHERE d.id_presupuesto = ? ORDER BY d.id_detalle
            """;

    private static final String MATERIAL_SQL = """
            SELECT dm.*, m.marca, m.tipo, m.acabado,
                   m.costo_por_gramo, m.activo AS material_activo
            FROM detalle_material_presupuesto dm
            JOIN material m ON m.id_material = dm.id_material
            WHERE dm.id_detalle = ? ORDER BY dm.id_detalle_material
            """;

    private JdbcQuoteReader() { }

    static Optional<Quote> findById(Connection connection, int id) throws SQLException {
        Quote quote;
        try (PreparedStatement statement = connection.prepareStatement(QUOTE_SQL)) {
            statement.setInt(1, id);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return Optional.empty();
                }
                Customer customer = new Customer(results.getInt("id_cliente"),
                        results.getString("nombre"), results.getString("telefono"),
                        results.getString("email"), results.getBoolean("cliente_activo"));
                quote = new Quote(results.getInt("id_presupuesto"), customer,
                        results.getTimestamp("fecha_emision").toLocalDateTime(),
                        results.getTimestamp("fecha_vencimiento").toLocalDateTime(),
                        JdbcQuoteDao.fromDatabaseStatus(results.getString("estado")),
                        results.getBigDecimal("total"));
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(ITEM_SQL)) {
            statement.setInt(1, id);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    Model3D model = null;
                    int modelId = results.getInt("id_modelo");
                    if (!results.wasNull()) {
                        model = new Model3D(modelId, results.getString("modelo_nombre"),
                                results.getString("imagen_referencia"),
                                results.getString("plataforma_origen"),
                                results.getBigDecimal("modelo_gramos"),
                                results.getInt("modelo_tiempo"),
                                results.getBoolean("modelo_activo"));
                    }
                    QuoteItem item = new QuoteItem(results.getInt("id_detalle"), model,
                            results.getString("descripcion"), results.getInt("cantidad"),
                            results.getInt("tiempo_estimado_minutos"),
                            results.getBigDecimal("costo_electricidad"),
                            results.getBigDecimal("costo_amortizacion"),
                            results.getBigDecimal("margen_porcentaje"),
                            results.getBigDecimal("precio_unitario"));
                    readMaterials(connection, item);
                    quote.addItem(item);
                }
            }
        }
        return Optional.of(quote);
    }

    static List<Quote> findAll(Connection connection) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id_presupuesto FROM presupuesto ORDER BY id_presupuesto");
                ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                ids.add(results.getInt(1));
            }
        }
        List<Quote> quotes = new ArrayList<>();
        for (int id : ids) {
            findById(connection, id).ifPresent(quotes::add);
        }
        return quotes;
    }

    private static void readMaterials(Connection connection, QuoteItem item)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(MATERIAL_SQL)) {
            statement.setInt(1, item.getId());
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    Material material = new Material(results.getInt("id_material"),
                            results.getString("marca"), results.getString("tipo"),
                            results.getString("acabado"),
                            results.getBigDecimal("costo_por_gramo"),
                            results.getBoolean("material_activo"));
                    item.addMaterial(new QuoteMaterialItem(
                            results.getInt("id_detalle_material"), material,
                            results.getBigDecimal("gramos_estimados"),
                            results.getBigDecimal("costo_por_gramo_aplicado")));
                }
            }
        }
    }
}
