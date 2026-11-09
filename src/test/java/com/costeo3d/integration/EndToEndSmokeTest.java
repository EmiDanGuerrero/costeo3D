package com.costeo3d.integration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

import com.costeo3d.application.port.ProductionFinalizationPort;
import com.costeo3d.application.service.CatalogService;
import com.costeo3d.application.service.OrderService;
import com.costeo3d.application.service.PersistentOrderService;
import com.costeo3d.application.service.PersistentProductionService;
import com.costeo3d.application.service.PersistentQuoteService;
import com.costeo3d.application.service.PersistentWasteEstimator;
import com.costeo3d.application.service.ProductionService;
import com.costeo3d.application.service.QuoteService;
import com.costeo3d.application.service.WasteEstimator;
import com.costeo3d.domain.enums.OrderStatus;
import com.costeo3d.domain.enums.PrintJobStatus;
import com.costeo3d.domain.enums.QuoteStatus;
import com.costeo3d.domain.exception.BusinessValidationException;
import com.costeo3d.domain.model.EstimatedWaste;
import com.costeo3d.domain.model.FilamentConsumption;
import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.Order;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.domain.model.Quote;
import com.costeo3d.domain.model.QuoteItem;
import com.costeo3d.domain.model.QuoteMaterialItem;
import com.costeo3d.infrastructure.config.DatabaseConfig;
import com.costeo3d.infrastructure.logging.AppLogging;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcCustomerDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcEstimatedWasteDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcFilamentConsumptionDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcMaterialDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcModel3DDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcOrderDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcPrintJobDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcProductionFinalizationAdapter;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcProductionTransaction;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcQuoteDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcSpoolDao;

/** E8.5: flujo integral real, con dos pedidos y limpieza exclusiva por IDs. */
public final class EndToEndSmokeTest {
    private static final Logger LOGGER = Logger.getLogger(EndToEndSmokeTest.class.getName());
    private static final BigDecimal HISTORICAL_COST = new BigDecimal("0.3700");
    private static final BigDecimal QUOTE_TOTAL = new BigDecimal("144.00");

    private EndToEndSmokeTest() { }

    public static void main(String[] args) {
        AppLogging.configure();
        EndToEndSmokeData data = null;
        boolean passed = false;
        boolean cleaned = false;
        try {
            JdbcConnectionFactory factory = new JdbcConnectionFactory(DatabaseConfig.load());
            data = new EndToEndSmokeData(factory);
            run(factory, data);
            passed = true;
        } catch (Exception | AssertionError failure) {
            System.err.println("E8.5 fallo: " + failure.getClass().getSimpleName()
                    + ": " + failure.getMessage());
            LOGGER.warning("End-to-end test failed (" + failure.getClass().getSimpleName() + ").");
        } finally {
            if (data != null) cleaned = data.clean();
        }
        if (passed && cleaned) {
            System.out.println("E8.5 Flujo integral: PRUEBA COMPLETA CORRECTA.");
        } else {
            System.err.println("E8.5 Flujo integral: PRUEBA INCOMPLETA.");
        }
    }

    private static void run(JdbcConnectionFactory factory, EndToEndSmokeData d)
            throws Exception {
        CatalogService catalogs = catalogs(factory);
        PersistentQuoteService quotes = quotes(factory);
        PersistentOrderService orders = orders(factory);
        PersistentProductionService production = production(factory);
        PersistentWasteEstimator waste = waste(factory);
        LocalDateTime issuedAt = LocalDateTime.now().withNano(0);
        LocalDateTime orderedAt = issuedAt.plusMinutes(2);

        // 1. Catalogos reales: solo registros de esta ejecucion.
        d.createCatalogs(catalogs);
        check(d.customer().getId() != null && d.material().getId() != null
                && d.model().getId() != null && d.spool().getId() != null,
                "Los catalogos no recibieron identificadores.");
        System.out.println("1/8 Catalogos correctos (cliente, material, bobina y modelo).");

        // 2. Dos presupuestos completos; historial independiente del precio actual.
        Quote firstQuote = quotes.createQuote(d.customer(),
                List.of(item(d, "Produccion correcta")), issuedAt);
        d.trackQuote(firstQuote);
        Quote failedQuote = quotes.createQuote(d.customer(),
                List.of(item(d, "Produccion fallida")), issuedAt);
        d.trackQuote(failedQuote);
        quotes.acceptQuote(firstQuote.getId(), issuedAt.plusMinutes(1));
        quotes.acceptQuote(failedQuote.getId(), issuedAt.plusMinutes(1));
        Material edited = new Material(d.material().getId(), d.material().getBrand(),
                d.material().getType(), d.material().getFinish(),
                new BigDecimal("0.9000"), true);
        check(catalogs.updateMaterial(edited), "No se actualizo el costo del catalogo.");
        checkHistory(quotes.findById(firstQuote.getId()).orElseThrow());
        checkHistory(quotes.findById(failedQuote.getId()).orElseThrow());
        System.out.println("2/8 Presupuestos aceptados; valores historicos conservados.");

        // 3. Cada presupuesto genera su propio pedido y trabajo.
        Order completedOrder = orders.createOrder(firstQuote.getId(), orderedAt,
                List.of(new PrintJob(null, d.model(), orderedAt, 90)));
        Order failedOrder = orders.createOrder(failedQuote.getId(), orderedAt,
                List.of(new PrintJob(null, d.model(), orderedAt, 90)));
        check(completedOrder.getPrintJobs().size() == 1
                && failedOrder.getPrintJobs().size() == 1
                && completedOrder.getPrintJobs().get(0).getId() != null
                && failedOrder.getPrintJobs().get(0).getId() != null,
                "Los pedidos no guardaron sus trabajos.");
        System.out.println("3/8 Pedidos y trabajos creados desde presupuestos aceptados.");

        // 4. Orden en produccion, inicio y fin correcto con consumo real.
        orders.startProduction(completedOrder.getId());
        int completedJobId = completedOrder.getPrintJobs().get(0).getId();
        PrintJob active = production.startJob(completedJobId, orderedAt.plusMinutes(1));
        ProductionFinalizationPort.Completion completion = production.completeJob(
                completedJobId, List.of(consumption(active, d, "45.00", "40.00")),
                orderedAt.plusMinutes(11));
        check(completion.finishedJob().getStatus() == PrintJobStatus.COMPLETED
                && completion.consumptionIds().size() == 1
                && same(stock(factory, d), "460.00"),
                "La finalizacion correcta no desconto 40 gramos.");
        System.out.println("4/8 Trabajo completado; stock persistido en 460.00 g.");

        // 5. El pedido solo se entrega despues de terminar su trabajo.
        Order ready = orders.markAsReady(completedOrder.getId());
        Order delivered = orders.deliver(completedOrder.getId());
        check(ready.getStatus() == OrderStatus.READY
                && delivered.getStatus() == OrderStatus.DELIVERED,
                "No se persistieron READY y DELIVERED.");
        System.out.println("5/8 Pedido listo y entregado; estados persistidos.");

        // 6. Segundo pedido: trabajo fallido, consumo real, sin doble descuento.
        orders.startProduction(failedOrder.getId());
        int failedJobId = failedOrder.getPrintJobs().get(0).getId();
        PrintJob failedStarted = production.startJob(failedJobId, orderedAt.plusMinutes(1));
        ProductionFinalizationPort.Completion failedResult = production.failJob(
                failedJobId, List.of(consumption(failedStarted, d, "45.00", "10.00")),
                orderedAt.plusMinutes(11));
        check(failedResult.finishedJob().getStatus() == PrintJobStatus.FAILED
                && same(stock(factory, d), "450.00"),
                "El trabajo fallido no desconto exactamente 10 gramos.");
        System.out.println("6/8 Trabajo fallido registrado; stock en 450.00 g.");

        // 7. Estimar merma no agrega consumos ni altera el stock.
        EstimatedWaste estimate = waste.createEstimate(failedJobId, 100, 40);
        check(estimate.getId() != null
                && same(estimate.getProgressPercentage(), "40.00"),
                "Merma incorrecta.");
        boolean duplicateRejected = false;
        try {
            waste.createEstimate(failedJobId, 100, 50);
        } catch (BusinessValidationException expected) {
            duplicateRejected = true;
        }
        check(duplicateRejected && same(stock(factory, d), "450.00")
                && new JdbcFilamentConsumptionDao(factory)
                        .findByPrintJobId(failedJobId).size() == 1,
                "La merma altero stock/consumos o admitio un duplicado.");
        System.out.println("7/8 Merma persistida sin descuento adicional ni duplicados.");

        // 8. Nuevas instancias de TODOS los servicios (no se reutiliza el cache local).
        CatalogService reopenedCatalogs = catalogs(factory);
        PersistentQuoteService reopenedQuotes = quotes(factory);
        PersistentOrderService reopenedOrders = orders(factory);
        PersistentProductionService reopenedProduction = production(factory);
        PersistentWasteEstimator reopenedWaste = waste(factory);
        check(reopenedCatalogs.findCustomerById(d.customer().getId()).isPresent()
                && reopenedCatalogs.findModel3DById(d.model().getId()).isPresent()
                && same(reopenedCatalogs.findSpoolById(d.spool().getId())
                        .orElseThrow().getCurrentWeight(), "450.00")
                && same(reopenedCatalogs.findMaterialById(d.material().getId())
                        .orElseThrow().getCostPerGram(), "0.9000"),
                "Los catalogos no conservaron el estado real.");
        checkHistory(reopenedQuotes.findById(firstQuote.getId()).orElseThrow());
        checkHistory(reopenedQuotes.findById(failedQuote.getId()).orElseThrow());
        check(reopenedOrders.findById(completedOrder.getId()).orElseThrow().getStatus()
                        == OrderStatus.DELIVERED
                && reopenedOrders.findById(failedOrder.getId()).orElseThrow().getStatus()
                        == OrderStatus.IN_PRODUCTION
                && reopenedProduction.findById(completedJobId).orElseThrow().getStatus()
                        == PrintJobStatus.COMPLETED
                && reopenedProduction.findById(failedJobId).orElseThrow().getStatus()
                        == PrintJobStatus.FAILED
                && reopenedWaste.findByPrintJobId(failedJobId).isPresent()
                && new JdbcFilamentConsumptionDao(factory)
                        .findByPrintJobId(completedJobId).size() == 1,
                "Las relaciones o los estados no sobrevivieron a la recarga.");
        System.out.println("8/8 Recarga integral correcta desde nuevas instancias.");
    }

    private static CatalogService catalogs(JdbcConnectionFactory factory) {
        return new CatalogService(new JdbcMaterialDao(factory), new JdbcSpoolDao(factory),
                new JdbcModel3DDao(factory), new JdbcCustomerDao(factory));
    }

    private static PersistentQuoteService quotes(JdbcConnectionFactory factory) {
        return new PersistentQuoteService(new QuoteService(), new JdbcQuoteDao(factory));
    }

    private static PersistentOrderService orders(JdbcConnectionFactory factory) {
        return new PersistentOrderService(new OrderService(), new JdbcQuoteDao(factory),
                new JdbcOrderDao(factory));
    }

    private static PersistentProductionService production(JdbcConnectionFactory factory) {
        return new PersistentProductionService(new ProductionService(),
                new JdbcPrintJobDao(factory), new JdbcProductionFinalizationAdapter(
                        new JdbcProductionTransaction(factory)));
    }

    private static PersistentWasteEstimator waste(JdbcConnectionFactory factory) {
        return new PersistentWasteEstimator(new WasteEstimator(),
                new JdbcPrintJobDao(factory), new JdbcEstimatedWasteDao(factory));
    }

    private static QuoteItem item(EndToEndSmokeData d, String description) {
        QuoteItem item = new QuoteItem(d.model(), description, 2, 90,
                new BigDecimal("8.00"), new BigDecimal("10.00"),
                new BigDecimal("20.00"), new BigDecimal("72.00"));
        item.addMaterial(new QuoteMaterialItem(d.material(),
                new BigDecimal("45.00"), HISTORICAL_COST));
        return item;
    }

    private static FilamentConsumption consumption(PrintJob job,
            EndToEndSmokeData d, String planned, String used) {
        return new FilamentConsumption(job, d.spool(),
                new BigDecimal(planned), new BigDecimal(used));
    }

    private static void checkHistory(Quote quote) {
        check(quote.getStatus() == QuoteStatus.ACCEPTED
                && quote.getTotal().compareTo(QUOTE_TOTAL) == 0
                && quote.getItems().size() == 1
                && quote.getItems().get(0).getMaterials().size() == 1
                && quote.getItems().get(0).getMaterials().get(0).getAppliedCostPerGram()
                        .compareTo(HISTORICAL_COST) == 0,
                "Se perdio el presupuesto o su costo historico.");
    }

    private static BigDecimal stock(JdbcConnectionFactory factory, EndToEndSmokeData d) {
        return new JdbcSpoolDao(factory).findById(d.spool().getId())
                .orElseThrow().getCurrentWeight();
    }

    private static boolean same(BigDecimal actual, String expected) {
        return actual != null && actual.compareTo(new BigDecimal(expected)) == 0;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
