package com.costeo3d.bootstrap;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;

import com.costeo3d.domain.model.Customer;
import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.Model3D;
import com.costeo3d.domain.model.Quote;
import com.costeo3d.domain.model.QuoteItem;
import com.costeo3d.domain.model.QuoteMaterialItem;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcCustomerDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcMaterialDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcModel3DDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcQuoteDao;

/** Datos temporales exclusivos de la prueba de transaccion pedido-trabajos. */
final class JdbcOrderTransactionSmokeData {
    private final JdbcConnectionFactory factory;
    private Integer customerId, materialId, modelId, rollbackModelId;
    private Integer successQuoteId, rollbackQuoteId;
    private Model3D model, rollbackModel;
    private Quote successQuote, rollbackQuote;

    JdbcOrderTransactionSmokeData(JdbcConnectionFactory factory) {
        this.factory = factory;
    }

    Model3D model() { return model; }
    Model3D rollbackModel() { return rollbackModel; }
    Quote successQuote() { return successQuote; }
    Quote rollbackQuote() { return rollbackQuote; }

    void create() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        Customer customer = new JdbcCustomerDao(factory).insert(
                new Customer("E7_TX_CLIENTE_" + suffix, null, null));
        customerId = customer.getId();
        Material material = new JdbcMaterialDao(factory).insert(
                new Material("E7_TX_MATERIAL_" + suffix, "PLA", "Mate",
                        new BigDecimal("0.5000")));
        materialId = material.getId();
        JdbcModel3DDao models = new JdbcModel3DDao(factory);
        model = models.insert(new Model3D("E7_TX_MODELO_" + suffix,
                null, null, new BigDecimal("25.00"), 90));
        modelId = model.getId();
        rollbackModel = models.insert(new Model3D("E7_TX_ROLLBACK_" + suffix,
                null, null, new BigDecimal("15.00"), 60));
        rollbackModelId = rollbackModel.getId();
        JdbcQuoteDao quotes = new JdbcQuoteDao(factory);
        successQuote = quotes.insert(buildQuote(customer, material, model));
        successQuoteId = successQuote.getId();
        rollbackQuote = quotes.insert(buildQuote(customer, material, rollbackModel));
        rollbackQuoteId = rollbackQuote.getId();
    }

    private static Quote buildQuote(Customer customer, Material material, Model3D model)
            throws Exception {
        LocalDateTime issued = LocalDateTime.now().withNano(0);
        Quote quote = new Quote(customer, issued, issued.plusDays(7));
        QuoteItem item = new QuoteItem(model, "Prueba de transaccion", 1, 90,
                new BigDecimal("5.00"), new BigDecimal("3.00"),
                new BigDecimal("20.00"), new BigDecimal("80.00"));
        item.addMaterial(new QuoteMaterialItem(material, new BigDecimal("50.00"),
                new BigDecimal("0.5000")));
        quote.addItem(item);
        quote.updateTotal(new BigDecimal("80.00"));
        quote.accept();
        return quote;
    }

    boolean clean() {
        try (Connection connection = factory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                for (Integer quoteId : new Integer[] {successQuoteId, rollbackQuoteId}) {
                    if (quoteId == null) continue;
                    delete(connection, """
                            DELETE t FROM trabajo_impresion t
                            JOIN pedido p ON p.id_pedido = t.id_pedido
                            WHERE p.id_presupuesto = ?
                            """, quoteId);
                    delete(connection, "DELETE FROM pedido WHERE id_presupuesto = ?", quoteId);
                    delete(connection, """
                            DELETE dm FROM detalle_material_presupuesto dm
                            JOIN detalle_presupuesto d ON d.id_detalle = dm.id_detalle
                            WHERE d.id_presupuesto = ?
                            """, quoteId);
                    delete(connection, "DELETE FROM detalle_presupuesto WHERE id_presupuesto = ?", quoteId);
                    delete(connection, "DELETE FROM presupuesto WHERE id_presupuesto = ?", quoteId);
                }
                if (modelId != null)
                    delete(connection, "DELETE FROM modelo_3d WHERE id_modelo = ?", modelId);
                if (rollbackModelId != null)
                    delete(connection, "DELETE FROM modelo_3d WHERE id_modelo = ?", rollbackModelId);
                if (materialId != null)
                    delete(connection, "DELETE FROM material WHERE id_material = ?", materialId);
                if (customerId != null)
                    delete(connection, "DELETE FROM cliente WHERE id_cliente = ?", customerId);
                connection.commit();
                System.out.println("Limpieza: pedidos, trabajos, presupuestos y catalogos temporales eliminados.");
                return true;
            } catch (SQLException | RuntimeException failure) {
                try { connection.rollback(); }
                catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                System.err.println("Fallo la limpieza: " + failure.getClass().getSimpleName());
                printIds();
                return false;
            }
        } catch (SQLException | RuntimeException failure) {
            System.err.println("No se pudo limpiar: " + failure.getClass().getSimpleName());
            printIds();
            return false;
        }
    }

    private void printIds() {
        System.err.println("IDs temporales: presupuestos=" + successQuoteId + ", "
                + rollbackQuoteId + "; modelos=" + modelId + ", " + rollbackModelId
                + "; material=" + materialId + "; cliente=" + customerId);
    }

    private static void delete(Connection connection, String sql, int id)
            throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }
}
