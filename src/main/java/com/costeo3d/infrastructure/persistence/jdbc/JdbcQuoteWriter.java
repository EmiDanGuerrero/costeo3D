package com.costeo3d.infrastructure.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;

import com.costeo3d.domain.model.Quote;
import com.costeo3d.domain.model.QuoteItem;
import com.costeo3d.domain.model.QuoteMaterialItem;

/** Internal writer: all statements use the same transaction-owned connection. */
final class JdbcQuoteWriter {

    private static final String QUOTE_SQL = """
            INSERT INTO presupuesto
                (id_cliente, fecha_emision, fecha_vencimiento, estado, total)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String ITEM_SQL = """
            INSERT INTO detalle_presupuesto
                (id_presupuesto, id_modelo, descripcion, cantidad,
                 tiempo_estimado_minutos, costo_electricidad, costo_amortizacion,
                 margen_porcentaje, precio_unitario)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String MATERIAL_SQL = """
            INSERT INTO detalle_material_presupuesto
                (id_detalle, id_material, gramos_estimados, costo_por_gramo_aplicado)
            VALUES (?, ?, ?, ?)
            """;

    private JdbcQuoteWriter() { }

    static Quote insert(Connection connection, Quote quote) throws SQLException {
        int quoteId;
        try (PreparedStatement statement = connection.prepareStatement(
                QUOTE_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, quote.getCustomer().getId());
            statement.setTimestamp(2, Timestamp.valueOf(quote.getIssuedAt()));
            statement.setTimestamp(3, Timestamp.valueOf(quote.getExpiresAt()));
            statement.setString(4, JdbcQuoteDao.toDatabaseStatus(quote.getStatus()));
            statement.setBigDecimal(5, quote.getTotal());
            statement.executeUpdate();
            quoteId = generatedId(statement);
        }

        Quote saved = new Quote(quoteId, quote.getCustomer(), quote.getIssuedAt(),
                quote.getExpiresAt(), quote.getStatus(), quote.getTotal());
        for (QuoteItem item : quote.getItems()) {
            if (item.getId() != null) {
                throw new IllegalArgumentException("New quote items must not have IDs.");
            }
            saved.addItem(insertItem(connection, quoteId, item));
        }
        return saved;
    }

    private static QuoteItem insertItem(Connection connection, int quoteId,
            QuoteItem item) throws SQLException {
        int itemId;
        try (PreparedStatement statement = connection.prepareStatement(
                ITEM_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, quoteId);
            if (item.getModel() == null) {
                statement.setNull(2, Types.INTEGER);
            } else if (item.getModel().getId() == null) {
                throw new IllegalArgumentException("Quote item model must be persisted first.");
            } else {
                statement.setInt(2, item.getModel().getId());
            }
            statement.setString(3, item.getDescription());
            statement.setInt(4, item.getQuantity());
            statement.setInt(5, item.getEstimatedPrintTimeMinutes());
            statement.setBigDecimal(6, item.getElectricityCost());
            statement.setBigDecimal(7, item.getAmortizationCost());
            statement.setBigDecimal(8, item.getMarginPercentage());
            statement.setBigDecimal(9, item.getUnitPrice());
            statement.executeUpdate();
            itemId = generatedId(statement);
        }

        QuoteItem saved = new QuoteItem(itemId, item.getModel(), item.getDescription(),
                item.getQuantity(), item.getEstimatedPrintTimeMinutes(),
                item.getElectricityCost(), item.getAmortizationCost(),
                item.getMarginPercentage(), item.getUnitPrice());
        for (QuoteMaterialItem materialItem : item.getMaterials()) {
            if (materialItem.getId() != null) {
                throw new IllegalArgumentException("New quote material details must not have IDs.");
            }
            saved.addMaterial(insertMaterial(connection, itemId, materialItem));
        }
        return saved;
    }

    private static QuoteMaterialItem insertMaterial(Connection connection,
            int itemId, QuoteMaterialItem materialItem) throws SQLException {
        if (materialItem.getMaterial() == null
                || materialItem.getMaterial().getId() == null) {
            throw new IllegalArgumentException("Quote material must be persisted first.");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                MATERIAL_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, itemId);
            statement.setInt(2, materialItem.getMaterial().getId());
            statement.setBigDecimal(3, materialItem.getEstimatedGrams());
            statement.setBigDecimal(4, materialItem.getAppliedCostPerGram());
            statement.executeUpdate();
            int id = generatedId(statement);
            return new QuoteMaterialItem(id, materialItem.getMaterial(),
                    materialItem.getEstimatedGrams(), materialItem.getAppliedCostPerGram());
        }
    }

    private static int generatedId(PreparedStatement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) {
                throw new SQLException("Quote insert did not return a generated ID.");
            }
            return keys.getInt(1);
        }
    }
}
