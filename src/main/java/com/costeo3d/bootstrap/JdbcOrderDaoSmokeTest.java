package com.costeo3d.bootstrap;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Logger;

import com.costeo3d.domain.enums.OrderStatus;
import com.costeo3d.domain.enums.QuoteStatus;
import com.costeo3d.domain.exception.InvalidStateTransitionException;
import com.costeo3d.domain.model.Customer;
import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.Order;
import com.costeo3d.domain.model.Quote;
import com.costeo3d.domain.model.QuoteItem;
import com.costeo3d.domain.model.QuoteMaterialItem;
import com.costeo3d.infrastructure.config.DatabaseConfig;
import com.costeo3d.infrastructure.logging.AppLogging;
import com.costeo3d.infrastructure.persistence.PersistenceException;
import com.costeo3d.infrastructure.persistence.dao.OrderDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcCustomerDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcMaterialDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcOrderDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcQuoteDao;

/** Prueba manual de la cabecera del pedido (trabajos: E7.4.2). */
public final class JdbcOrderDaoSmokeTest {
    private static final Logger LOGGER =
            Logger.getLogger(JdbcOrderDaoSmokeTest.class.getName());

    private JdbcOrderDaoSmokeTest() { }

    public static void main(String[] args) {
        AppLogging.configure();
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        JdbcConnectionFactory factory = null;
        Integer customerId = null, materialId = null, quoteId = null;
        boolean passed = false, cleaned = false;
        try {
            factory = new JdbcConnectionFactory(DatabaseConfig.load());
            Customer customer = new JdbcCustomerDao(factory).insert(
                    new Customer("E7_PED_CLIENTE_" + suffix, null, null));
            customerId = customer.getId();
            Material material = new JdbcMaterialDao(factory).insert(
                    new Material("E7_PED_MATERIAL_" + suffix, "PLA", "Mate",
                            new BigDecimal("0.5000")));
            materialId = material.getId();
            Quote quote = new JdbcQuoteDao(factory).insert(buildAcceptedQuote(customer, material));
            quoteId = quote.getId();

            OrderDao orders = new JdbcOrderDao(factory);
            LocalDateTime createdAt = LocalDateTime.now().withNano(0);
            Order original = new Order(quote, createdAt);
            Order saved = orders.insert(original);
            require(saved.getId() != null && saved.getId() > 0
                    && original.getId() == null, "INSERT no devolvio un ID nuevo.");
            System.out.println("1/5 INSERT correcto. ID pedido: " + saved.getId());

            Order found = orders.findById(saved.getId()).orElseThrow();
            require(found.getQuote().getId().equals(quoteId)
                    && found.getQuote().getStatus() == QuoteStatus.ACCEPTED
                    && found.getQuote().getItems().size() == 1
                    && found.getQuote().getItems().get(0).getMaterials().size() == 1
                    && found.getCreatedAt().equals(createdAt)
                    && found.getStatus() == OrderStatus.PENDING,
                    "FIND BY ID no reconstruyo el pedido y su presupuesto.");
            System.out.println("2/5 FIND BY ID correcto (presupuesto completo).");

            require(orders.findAll().stream().anyMatch(
                    order -> saved.getId().equals(order.getId())),
                    "FIND ALL no incluyo el pedido temporal.");
            System.out.println("3/5 FIND ALL correcto.");

            found.startProduction();
            require(orders.updateStatus(found), "No se actualizo el estado.");
            Order updated = orders.findById(saved.getId()).orElseThrow();
            require(updated.getStatus() == OrderStatus.IN_PRODUCTION
                    && updated.getQuote().getId().equals(quoteId)
                    && updated.getQuote().getTotal().compareTo(new BigDecimal("80.00")) == 0
                    && orders.updateStatus(updated), "UPDATE STATUS incorrecto.");
            System.out.println("4/5 UPDATE STATUS correcto (tambien sin cambios).");

            boolean duplicateRejected = false;
            try {
                orders.insert(new Order(quote, LocalDateTime.now().withNano(0)));
            } catch (PersistenceException expected) {
                duplicateRejected = expected.getCause() instanceof SQLException sql
                        && "23000".equals(sql.getSQLState());
            }
            require(duplicateRejected && countOrders(factory, quoteId) == 1,
                    "La restriccion UNIQUE de id_presupuesto no funciono.");
            System.out.println("5/5 UNIQUE correcto (un pedido por presupuesto).");
            passed = true;
        } catch (Exception e) {
            System.err.println("Fallo la prueba de OrderDao: " + e.getMessage());
            LOGGER.warning("Order DAO test failed (" + e.getClass().getSimpleName() + ").");
        } finally {
            if (factory != null) {
                cleaned = JdbcOrderSmokeCleanup.clean(factory, quoteId, customerId, materialId);
            }
        }
        if (passed && cleaned) {
            System.out.println("E7.4.1 Order: PRUEBA COMPLETA CORRECTA.");
            LOGGER.info("Order DAO smoke test completed successfully.");
        } else {
            System.err.println("E7.4.1 Order: prueba incompleta. Revisar mensajes e IDs.");
        }
    }

    private static Quote buildAcceptedQuote(Customer customer, Material material)
            throws InvalidStateTransitionException {
        LocalDateTime issuedAt = LocalDateTime.now().withNano(0);
        Quote quote = new Quote(customer, issuedAt, issuedAt.plusDays(7));
        QuoteItem item = new QuoteItem(null, "Prueba de pedido", 1, 60,
                new BigDecimal("5.00"), new BigDecimal("3.00"),
                new BigDecimal("20.00"), new BigDecimal("80.00"));
        item.addMaterial(new QuoteMaterialItem(material, new BigDecimal("50.00"),
                new BigDecimal("0.5000")));
        quote.addItem(item);
        quote.updateTotal(new BigDecimal("80.00"));
        quote.accept();
        return quote;
    }

    private static int countOrders(JdbcConnectionFactory factory, int quoteId)
            throws SQLException {
        try (Connection connection = factory.openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM pedido WHERE id_presupuesto = ?")) {
            statement.setInt(1, quoteId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
