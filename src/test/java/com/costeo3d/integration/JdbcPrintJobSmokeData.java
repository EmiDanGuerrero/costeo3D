package com.costeo3d.integration;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;

import com.costeo3d.domain.model.Customer;
import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.Model3D;
import com.costeo3d.domain.model.Order;
import com.costeo3d.domain.model.Quote;
import com.costeo3d.domain.model.QuoteItem;
import com.costeo3d.domain.model.QuoteMaterialItem;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcCustomerDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcMaterialDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcModel3DDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcOrderDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcQuoteDao;

/** Datos exclusivos de la prueba manual de trabajos de impresion. */
final class JdbcPrintJobSmokeData {
    private final JdbcConnectionFactory factory;
    private Integer customerId, materialId, modelId, quoteId, orderId;
    private Order order;
    private Model3D model;

    JdbcPrintJobSmokeData(JdbcConnectionFactory factory) {
        this.factory = factory;
    }

    Order order() { return order; }
    Model3D model() { return model; }

    void create() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        Customer customer = new JdbcCustomerDao(factory).insert(
                new Customer("E7_TRAB_CLIENTE_" + suffix, null, null));
        customerId = customer.getId();

        Material material = new JdbcMaterialDao(factory).insert(
                new Material("E7_TRAB_MATERIAL_" + suffix, "PLA", "Mate",
                        new BigDecimal("0.5000")));
        materialId = material.getId();

        model = new JdbcModel3DDao(factory).insert(new Model3D(
                "E7_TRAB_MODELO_" + suffix, null, null,
                new BigDecimal("35.00"), 90));
        modelId = model.getId();

        LocalDateTime issuedAt = LocalDateTime.now().withNano(0);
        Quote quote = new Quote(customer, issuedAt, issuedAt.plusDays(7));
        QuoteItem item = new QuoteItem(null, "Trabajo de prueba", 1, 90,
                new BigDecimal("5.00"), new BigDecimal("3.00"),
                new BigDecimal("20.00"), new BigDecimal("80.00"));
        item.addMaterial(new QuoteMaterialItem(material,
                new BigDecimal("50.00"), new BigDecimal("0.5000")));
        quote.addItem(item);
        quote.updateTotal(new BigDecimal("80.00"));
        quote.accept();
        Quote savedQuote = new JdbcQuoteDao(factory).insert(quote);
        quoteId = savedQuote.getId();

        order = new JdbcOrderDao(factory).insert(new Order(savedQuote, issuedAt));
        orderId = order.getId();
    }

    boolean clean() {
        try (Connection connection = factory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                if (orderId != null) {
                    delete(connection, "DELETE FROM trabajo_impresion WHERE id_pedido = ?", orderId);
                    delete(connection, "DELETE FROM pedido WHERE id_pedido = ?", orderId);
                }
                if (quoteId != null) {
                    delete(connection, """
                            DELETE dm FROM detalle_material_presupuesto dm
                            JOIN detalle_presupuesto d ON d.id_detalle = dm.id_detalle
                            WHERE d.id_presupuesto = ?
                            """, quoteId);
                    delete(connection, "DELETE FROM detalle_presupuesto WHERE id_presupuesto = ?", quoteId);
                    delete(connection, "DELETE FROM presupuesto WHERE id_presupuesto = ?", quoteId);
                }
                if (modelId != null) {
                    delete(connection, "DELETE FROM modelo_3d WHERE id_modelo = ?", modelId);
                }
                if (materialId != null) {
                    delete(connection, "DELETE FROM material WHERE id_material = ?", materialId);
                }
                if (customerId != null) {
                    delete(connection, "DELETE FROM cliente WHERE id_cliente = ?", customerId);
                }
                connection.commit();
                System.out.println("Limpieza: trabajo, pedido, presupuesto y catalogos temporales eliminados.");
                return true;
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                System.err.println("Fallo la limpieza de E7.4.2: " + failure.getClass().getSimpleName());
                printIds();
                return false;
            }
        } catch (SQLException | RuntimeException failure) {
            System.err.println("No se pudo limpiar E7.4.2: " + failure.getClass().getSimpleName());
            printIds();
            return false;
        }
    }

    private void printIds() {
        System.err.println("IDs temporales: pedido=" + orderId + ", presupuesto=" + quoteId
                + ", modelo=" + modelId + ", material=" + materialId
                + ", cliente=" + customerId);
    }

    private static void delete(Connection connection, String sql, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }
}
