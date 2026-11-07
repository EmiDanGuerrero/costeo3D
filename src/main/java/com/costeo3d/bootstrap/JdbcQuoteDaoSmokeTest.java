package com.costeo3d.bootstrap;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Logger;

import com.costeo3d.domain.enums.QuoteStatus;
import com.costeo3d.domain.model.Customer;
import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.Model3D;
import com.costeo3d.domain.model.Quote;
import com.costeo3d.domain.model.QuoteItem;
import com.costeo3d.domain.model.QuoteMaterialItem;
import com.costeo3d.infrastructure.config.DatabaseConfig;
import com.costeo3d.infrastructure.logging.AppLogging;
import com.costeo3d.infrastructure.persistence.PersistenceException;
import com.costeo3d.infrastructure.persistence.dao.QuoteDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcCustomerDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcMaterialDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcModel3DDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcQuoteDao;

/** Prueba manual de presupuesto completo y rollback, con datos temporales. */
public final class JdbcQuoteDaoSmokeTest {

    private static final Logger LOGGER =
            Logger.getLogger(JdbcQuoteDaoSmokeTest.class.getName());
    private static final BigDecimal APPLIED_COST = new BigDecimal("0.3700");

    private JdbcQuoteDaoSmokeTest() { }
    public static void main(String[] args) {
        AppLogging.configure();
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        JdbcConnectionFactory factory = null;
        Integer customerId = null, materialId = null, modelId = null;
        boolean passed = false, cleaned = false;
        try {
            factory = new JdbcConnectionFactory(DatabaseConfig.load());
            Customer customer = new JdbcCustomerDao(factory).insert(
                    new Customer("E7_COT_CLIENTE_" + suffix, null, null));
            customerId = customer.getId();
            Material material = new JdbcMaterialDao(factory).insert(
                    new Material("E7_COT_MAT_" + suffix, "PLA", "Mate",
                            new BigDecimal("0.5000")));
            materialId = material.getId();
            Model3D model = new JdbcModel3DDao(factory).insert(
                    new Model3D("E7_COT_MODELO_" + suffix, null, null,
                            new BigDecimal("75.00"), 90));
            modelId = model.getId();
            QuoteDao quotes = new JdbcQuoteDao(factory);
            Quote original = buildQuote(customer, model, material,
                    "Presupuesto de prueba " + suffix, new BigDecimal("72.50"));
            Quote saved = quotes.insert(original);
            require(saved.getId() != null && saved.getId() > 0
                    && saved.getItems().size() == 1
                    && saved.getItems().get(0).getId() != null
                    && saved.getItems().get(0).getMaterials().get(0).getId() != null
                    && original.getId() == null, "INSERT incompleto o sin IDs.");
            System.out.println("1/5 INSERT completo. ID presupuesto: " + saved.getId());
            Quote fetched = quotes.findById(saved.getId()).orElseThrow();
            checkHistoricalData(fetched, customerId, modelId, materialId);
            System.out.println("2/5 FIND BY ID correcto (concepto y material incluidos).");
            require(quotes.findAll().stream().anyMatch(q -> saved.getId().equals(q.getId())),
                    "FIND ALL no incluyo el presupuesto temporal.");
            System.out.println("3/5 FIND ALL correcto.");
            material.updateCostPerGram(new BigDecimal("0.9000"));
            require(new JdbcMaterialDao(factory).update(material),
                    "No se pudo cambiar el costo actual del material temporal.");
            Quote historical = quotes.findById(saved.getId()).orElseThrow();
            checkHistoricalData(historical, customerId, modelId, materialId);
            historical.accept();
            require(quotes.updateStatus(historical) && quotes.updateStatus(historical),
                    "UPDATE STATUS no confirmo el estado existente.");
            Quote accepted = quotes.findById(saved.getId()).orElseThrow();
            require(accepted.getStatus() == QuoteStatus.ACCEPTED,
                    "El nuevo estado no se guardo.");
            checkHistoricalData(accepted, customerId, modelId, materialId);
            System.out.println("4/5 UPDATE STATUS correcto; importes historicos conservados.");
            int before = countQuotes(factory, customerId);
            Quote invalid = buildQuote(customer, model, material,
                    "E7_ROLLBACK_" + suffix, BigDecimal.ZERO);
            boolean rejected = false;
            try {
                quotes.insert(invalid);
            } catch (PersistenceException expected) {
                rejected = true; // CHECK gramos_estimados > 0 en MySQL.
            }
            require(rejected, "MySQL no rechazo el detalle con gramos iguales a cero.");
            require(countQuotes(factory, customerId) == before,
                    "ROLLBACK fallo: se conservo una cabecera incompleta.");
            System.out.println("5/5 ROLLBACK correcto (sin presupuesto incompleto).");
            passed = true;
        } catch (Exception e) {
            System.err.println("Fallo la prueba de QuoteDao: " + e.getMessage());
            LOGGER.warning("Quote DAO test failed (" + e.getClass().getSimpleName() + ").");
        } finally {
            if (factory != null) {
                cleaned = JdbcQuoteSmokeCleanup.clean(
                        factory, customerId, materialId, modelId);
            }
        }
        if (passed && cleaned) {
            System.out.println("E7.3 Quote: PRUEBA COMPLETA CORRECTA.");
            LOGGER.info("Quote DAO smoke test completed successfully.");
        } else {
            System.err.println("E7.3 Quote: prueba incompleta. Revisar los mensajes.");
        }
    }
    private static Quote buildQuote(Customer customer, Model3D model,
            Material material, String description, BigDecimal grams) {
        LocalDateTime issuedAt = LocalDateTime.now().withNano(0);
        Quote quote = new Quote(customer, issuedAt, issuedAt.plusDays(7));
        QuoteItem item = new QuoteItem(model, description, 2, 90,
                new BigDecimal("12.50"), new BigDecimal("8.00"),
                new BigDecimal("30.00"), new BigDecimal("120.00"));
        item.addMaterial(new QuoteMaterialItem(material, grams, APPLIED_COST));
        quote.addItem(item);
        quote.updateTotal(new BigDecimal("240.00"));
        return quote;
    }
    private static void checkHistoricalData(Quote quote,
            Integer customerId, Integer modelId, Integer materialId) {
        require(quote.getCustomer().getId().equals(customerId)
                && quote.getTotal().compareTo(new BigDecimal("240.00")) == 0
                && quote.getItems().size() == 1, "Cabecera incorrecta.");
        QuoteItem item = quote.getItems().get(0);
        require(item.getModel() != null && item.getModel().getId().equals(modelId)
                && item.getUnitPrice().compareTo(new BigDecimal("120.00")) == 0
                && item.getMaterials().size() == 1, "Detalle incorrecto.");
        QuoteMaterialItem detail = item.getMaterials().get(0);
        require(detail.getMaterial().getId().equals(materialId)
                && detail.getAppliedCostPerGram().compareTo(APPLIED_COST) == 0,
                "El costo por gramo historico fue modificado.");
    }

    private static int countQuotes(JdbcConnectionFactory factory, int customerId)
            throws SQLException {
        try (Connection connection = factory.openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM presupuesto WHERE id_cliente = ?")) {
            statement.setInt(1, customerId);
            try (ResultSet results = statement.executeQuery()) {
                results.next();
                return results.getInt(1);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
