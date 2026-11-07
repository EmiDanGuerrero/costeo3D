package com.costeo3d.bootstrap;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import com.costeo3d.application.service.PersistentQuoteService;
import com.costeo3d.application.service.QuoteService;
import com.costeo3d.domain.enums.QuoteStatus;
import com.costeo3d.domain.exception.BusinessValidationException;
import com.costeo3d.domain.model.Customer;
import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.Model3D;
import com.costeo3d.domain.model.Quote;
import com.costeo3d.domain.model.QuoteItem;
import com.costeo3d.domain.model.QuoteMaterialItem;
import com.costeo3d.infrastructure.config.DatabaseConfig;
import com.costeo3d.infrastructure.logging.AppLogging;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcCustomerDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcMaterialDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcModel3DDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcQuoteDao;

/**
 * Prueba manual de E8.3: QuoteService -> PersistentQuoteService -> MySQL.
 * Solo crea datos con identificadores nuevos; usa la limpieza de E7.3.
 * Las fechas pasadas/futuras son simulaciones para validar el vencimiento.
 */
public final class PersistentQuoteServiceSmokeTest {

    private static final Logger LOGGER =
            Logger.getLogger(PersistentQuoteServiceSmokeTest.class.getName());
    private static final BigDecimal HISTORICAL_COST = new BigDecimal("0.3700");
    private static final BigDecimal TOTAL = new BigDecimal("144.00");

    private PersistentQuoteServiceSmokeTest() { }

    public static void main(String[] args) {
        AppLogging.configure();
        JdbcConnectionFactory factory = null;
        Integer customerId = null;
        Integer materialId = null;
        Integer modelId = null;
        boolean passed = false;
        boolean cleaned = false;

        try {
            factory = new JdbcConnectionFactory(DatabaseConfig.load());
            String suffix = UUID.randomUUID().toString().substring(0, 12);
            Customer customer = new JdbcCustomerDao(factory).insert(
                    new Customer("E8_COT_CLIENTE_" + suffix, null, null));
            customerId = customer.getId();
            Material material = new JdbcMaterialDao(factory).insert(
                    new Material("E8_COT_MAT_" + suffix, "PLA", "Mate",
                            new BigDecimal("0.5000")));
            materialId = material.getId();
            Model3D model = new JdbcModel3DDao(factory).insert(
                    new Model3D("E8_COT_MODELO_" + suffix, null, null,
                            new BigDecimal("75.00"), 90));
            modelId = model.getId();

            QuoteService business = new QuoteService();
            PersistentQuoteService service = new PersistentQuoteService(
                    business, new JdbcQuoteDao(factory));
            LocalDateTime issuedAt = LocalDateTime.now().withNano(0);

            // 1. Verificar el calculo de 72 horas sin sabado ni domingo.
            LocalDateTime friday = LocalDateTime.of(2026, 11, 6, 10, 0);
            check(business.calculateExpiration(friday).equals(
                    LocalDateTime.of(2026, 11, 11, 10, 0)),
                    "La vigencia de 72 horas sin fin de semana cambio.");
            Quote acceptedDraft = service.createQuote(customer,
                    List.of(newItem(model, material, "Aceptacion " + suffix)), issuedAt);
            check(acceptedDraft.getId() != null && acceptedDraft.getId() > 0
                    && acceptedDraft.getItems().size() == 1
                    && acceptedDraft.getItems().get(0).getId() != null
                    && acceptedDraft.getItems().get(0).getMaterials().get(0).getId() != null
                    && acceptedDraft.getExpiresAt().equals(
                            business.calculateExpiration(issuedAt)),
                    "CREATE no guardo la cotizacion completa o su vencimiento.");
            System.out.println("1/7 CREATE correcto (detalle, IDs y 72 horas habiles).");

            // 2. Verificar recuperacion desde una instancia diferente.
            PersistentQuoteService another = new PersistentQuoteService(
                    new QuoteService(), new JdbcQuoteDao(factory));
            Quote retrieved = another.findById(acceptedDraft.getId()).orElseThrow();
            checkHistorical(retrieved, customerId, modelId, materialId);
            check(another.findAll().stream().anyMatch(quote ->
                    acceptedDraft.getId().equals(quote.getId())
                            && quote.getItems().size() == 1),
                    "FIND ALL no devolvio los detalles del presupuesto.");
            System.out.println("2/7 FIND BY ID y FIND ALL correctos (nueva instancia).");

            // 3. Los costos historicos no cambian por actualizar el catalogo.
            material.updateCostPerGram(new BigDecimal("0.9000"));
            check(new JdbcMaterialDao(factory).update(material),
                    "No se actualizo el costo actual del material temporal.");
            Quote accepted = another.acceptQuote(acceptedDraft.getId(),
                    issuedAt.plusMinutes(1));
            check(accepted.getStatus() == QuoteStatus.ACCEPTED,
                    "No se guardo el estado ACCEPTED.");
            checkHistorical(service.findById(accepted.getId()).orElseThrow(),
                    customerId, modelId, materialId);
            System.out.println("3/7 ACCEPT correcto; importes historicos conservados.");

            // 4. Cancelar un presupuesto vigente sin alterar detalles.
            Quote cancellable = create(service, customer, model, material,
                    issuedAt, "Cancelacion " + suffix);
            Quote cancelled = service.cancelQuote(cancellable.getId());
            check(cancelled.getStatus() == QuoteStatus.CANCELLED
                    && another.findById(cancelled.getId()).orElseThrow().getStatus()
                            == QuoteStatus.CANCELLED,
                    "CANCELLED no quedo persistido.");
            checkHistorical(cancelled, customerId, modelId, materialId);
            System.out.println("4/7 CANCEL correcto (estado persistido).");

            // 5. expireIfNecessary no cambia nada antes del plazo; si al vencer.
            Quote expirable = create(service, customer, model, material,
                    issuedAt, "Vencimiento " + suffix);
            check(service.expireIfNecessary(expirable.getId(),
                    expirable.getExpiresAt().minusSeconds(1)).getStatus()
                            == QuoteStatus.VALID,
                    "El presupuesto vencio antes del plazo.");
            check(service.expireIfNecessary(expirable.getId(),
                    expirable.getExpiresAt()).getStatus() == QuoteStatus.EXPIRED,
                    "No vencio al alcanzar el plazo.");
            check(another.findById(expirable.getId()).orElseThrow().getStatus()
                            == QuoteStatus.EXPIRED,
                    "La expiracion no se guardo en MySQL.");
            System.out.println("5/7 EXPIRE IF NECESSARY correcto.");

            // 6. Aceptar fuera de plazo rechaza, pero persiste EXPIRED.
            Quote late = create(service, customer, model, material,
                    issuedAt, "Aceptacion tardia " + suffix);
            boolean rejected = false;
            try {
                another.acceptQuote(late.getId(), late.getExpiresAt());
            } catch (BusinessValidationException expected) {
                rejected = true;
            }
            check(rejected && service.findById(late.getId()).orElseThrow().getStatus()
                    == QuoteStatus.EXPIRED,
                    "La aceptacion tardia no fue rechazada y persistida como vencida.");
            System.out.println("6/7 Aceptacion vencida rechazada y EXPIRED persistido.");

            // 7. Cliente sin ID: no escribir un presupuesto incompleto.
            boolean noCustomerRejected = false;
            try {
                service.createQuote(new Customer("Sin guardar", null, null),
                        List.of(newItem(model, material, "Sin cliente " + suffix)),
                        issuedAt);
            } catch (BusinessValidationException expected) {
                noCustomerRejected = true;
            }
            check(noCustomerRejected,
                    "Se permitio registrar un presupuesto con cliente sin persistir.");
            check(service.findAll().stream().filter(quote ->
                    customer.getId().equals(quote.getCustomer().getId())).count() == 4,
                    "Se detectaron presupuestos temporales inesperados.");
            System.out.println("7/7 Cliente no persistido rechazado sin INSERT.");

            passed = true;
        } catch (Exception error) {
            System.err.println("Fallo E8.3 presupuestos: " + error.getMessage());
            LOGGER.warning("Persistent quote service smoke test failed ("
                    + error.getClass().getSimpleName() + ").");
        } finally {
            if (factory != null) {
                cleaned = JdbcQuoteSmokeCleanup.clean(
                        factory, customerId, materialId, modelId);
            }
        }

        if (passed && cleaned) {
            System.out.println("E8.3 PersistentQuoteService: PRUEBA COMPLETA CORRECTA.");
            LOGGER.info("Persistent quote service smoke test completed successfully.");
        } else {
            System.err.println("E8.3 PersistentQuoteService: PRUEBA INCOMPLETA.");
        }
    }

    private static Quote create(PersistentQuoteService service, Customer customer,
            Model3D model, Material material, LocalDateTime issuedAt,
            String description) throws BusinessValidationException {
        return service.createQuote(customer,
                List.of(newItem(model, material, description)), issuedAt);
    }

    private static QuoteItem newItem(Model3D model, Material material,
            String description) {
        QuoteItem item = new QuoteItem(model, description, 2, 90,
                new BigDecimal("8.00"), new BigDecimal("10.00"),
                new BigDecimal("20.00"), new BigDecimal("72.00"));
        item.addMaterial(new QuoteMaterialItem(
                material, new BigDecimal("75.00"), HISTORICAL_COST));
        return item;
    }

    private static void checkHistorical(Quote quote, Integer customerId,
            Integer modelId, Integer materialId) {
        check(quote.getCustomer().getId().equals(customerId)
                && quote.getTotal().compareTo(TOTAL) == 0
                && quote.getItems().size() == 1,
                "Los datos de cabecera cambiaron.");
        QuoteItem item = quote.getItems().get(0);
        check(item.getModel() != null && item.getModel().getId().equals(modelId)
                && item.getQuantity() == 2
                && item.getUnitPrice().compareTo(new BigDecimal("72.00")) == 0
                && item.getMaterials().size() == 1,
                "Los datos historicos del concepto cambiaron.");
        QuoteMaterialItem detail = item.getMaterials().get(0);
        check(detail.getMaterial().getId().equals(materialId)
                && detail.getAppliedCostPerGram().compareTo(HISTORICAL_COST) == 0,
                "El costo historico aplicado fue modificado.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
