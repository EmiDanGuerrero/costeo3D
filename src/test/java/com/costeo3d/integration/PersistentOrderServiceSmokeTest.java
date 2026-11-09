package com.costeo3d.integration;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

import com.costeo3d.application.service.OrderService;
import com.costeo3d.application.service.PersistentOrderService;
import com.costeo3d.domain.enums.OrderStatus;
import com.costeo3d.domain.enums.PrintJobStatus;
import com.costeo3d.domain.exception.BusinessValidationException;
import com.costeo3d.domain.model.Model3D;
import com.costeo3d.domain.model.Order;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.infrastructure.config.DatabaseConfig;
import com.costeo3d.infrastructure.logging.AppLogging;
import com.costeo3d.infrastructure.persistence.PersistenceException;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcOrderDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcPrintJobDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcQuoteDao;

/**
 * Prueba manual de E8.3: OrderService -> PersistentOrderService -> JDBC -> MySQL.
 * Reutiliza los datos temporales y la limpieza de E7.4.2.
 * IMPORTANTE: se simula la finalizacion de trabajos solo dentro de esta prueba.
 */
public final class PersistentOrderServiceSmokeTest {

    private static final Logger LOGGER =
            Logger.getLogger(PersistentOrderServiceSmokeTest.class.getName());

    private PersistentOrderServiceSmokeTest() { }

    public static void main(String[] args) {
        AppLogging.configure();
        JdbcOrderTransactionSmokeData data = null;
        boolean passed = false;
        boolean cleaned = false;

        try {
            JdbcConnectionFactory factory =
                    new JdbcConnectionFactory(DatabaseConfig.load());
            data = new JdbcOrderTransactionSmokeData(factory);
            data.create();

            PersistentOrderService service = new PersistentOrderService(
                    new OrderService(), new JdbcQuoteDao(factory),
                    new JdbcOrderDao(factory));
            JdbcPrintJobDao jobs = new JdbcPrintJobDao(factory);
            LocalDateTime created = LocalDateTime.now().withNano(0);

            // 1. Crear el pedido y sus trabajos desde el servicio de aplicacion.
            PrintJob firstDraft = new PrintJob(null, data.model(), created, 90);
            PrintJob secondDraft = new PrintJob(null, data.model(), created, 120);
            Order inserted = service.createOrder(data.successQuote().getId(),
                    created, List.of(firstDraft, secondDraft));
            require(inserted.getId() != null && inserted.getId() > 0
                    && inserted.getPrintJobs().size() == 2
                    && firstDraft.getOrder() == null && secondDraft.getOrder() == null,
                    "CREATE no persistio los dos trabajos o modifico los borradores.");
            require(inserted.getPrintJobs().stream().allMatch(job ->
                    job.getId() != null && job.getOrder() == inserted),
                    "Los trabajos guardados no tienen ID o pedido correcto.");
            System.out.println("1/6 CREATE correcto (pedido y dos trabajos, borradores intactos).");

            // 2. Recuperar desde otra instancia de servicio y comprobar relaciones.
            PersistentOrderService anotherService = new PersistentOrderService(
                    new OrderService(), new JdbcQuoteDao(factory),
                    new JdbcOrderDao(factory));
            Order recovered = anotherService.findById(inserted.getId()).orElseThrow();
            require(recovered.getQuote().getId().equals(data.successQuote().getId())
                    && recovered.getPrintJobs().size() == 2
                    && recovered.getPrintJobs().stream().allMatch(job ->
                            job.getOrder() == recovered && job.getModel() != null),
                    "FIND BY ID no reconstruyo el pedido completo.");
            require(anotherService.findAll().stream().anyMatch(order ->
                    inserted.getId().equals(order.getId())
                            && order.getPrintJobs().size() == 2),
                    "FIND ALL no recupero los trabajos persistidos.");
            System.out.println("2/6 FIND BY ID y FIND ALL correctos (pedido completo).");

            // 3. Reglas de negocio: un presupuesto no puede generar dos pedidos.
            boolean duplicatedRejected = false;
            try {
                service.createOrder(data.successQuote().getId(), created,
                        List.of(new PrintJob(null, data.model(), created, 60)));
            } catch (BusinessValidationException expected) {
                duplicatedRejected = true;
            }
            require(duplicatedRejected && count(factory,
                    "SELECT COUNT(*) FROM pedido WHERE id_presupuesto = ?",
                    data.successQuote().getId()) == 1,
                    "El servicio permitio un pedido duplicado.");
            System.out.println("3/6 Pedido duplicado rechazado por OrderService.");

            // 4. Solo puede pasar a READY con todos los trabajos completos.
            Order inProduction = anotherService.startProduction(inserted.getId());
            require(inProduction.getStatus() == OrderStatus.IN_PRODUCTION,
                    "No se guardo el inicio de produccion.");
            boolean earlyReadyRejected = false;
            try {
                anotherService.markAsReady(inserted.getId());
            } catch (BusinessValidationException expected) {
                earlyReadyRejected = true;
            }
            require(earlyReadyRejected
                    && service.findById(inserted.getId()).orElseThrow().getStatus()
                            == OrderStatus.IN_PRODUCTION,
                    "La transicion prematura modifico el estado persistido.");
            System.out.println("4/6 IN_PRODUCTION persistido; READY prematuro rechazado.");

            // 5. Simulacion exclusiva de prueba: terminar trabajos por DAO.
            // En el flujo real E8.4 los completara la transaccion de stock.
            for (PrintJob attached : inProduction.getPrintJobs()) {
                PrintJob job = jobs.findById(attached.getId()).orElseThrow();
                job.start(created.plusMinutes(1));
                job.complete(created.plusMinutes(2));
                require(jobs.update(job), "No se pudo completar el trabajo temporal.");
            }
            Order ready = service.markAsReady(inserted.getId());
            require(ready.getStatus() == OrderStatus.READY
                    && ready.getPrintJobs().stream().allMatch(job ->
                            job.getStatus() == PrintJobStatus.COMPLETED),
                    "READY no respeto los trabajos terminados.");
            Order delivered = anotherService.deliver(inserted.getId());
            require(delivered.getStatus() == OrderStatus.DELIVERED
                    && service.findById(inserted.getId()).orElseThrow().getStatus()
                            == OrderStatus.DELIVERED,
                    "DELIVERED no quedo persistido en MySQL.");
            System.out.println("5/6 READY y DELIVERED correctos (estados persistidos).");

            // 6. Error de FK en el segundo trabajo: el pedido se revierte entero.
            int missingModelId = Integer.MAX_VALUE;
            require(count(factory,
                    "SELECT COUNT(*) FROM modelo_3d WHERE id_modelo = ?",
                    missingModelId) == 0,
                    "El ID reservado para el error de FK ya existe.");
            PrintJob validDraft = new PrintJob(null, data.rollbackModel(), created, 60);
            PrintJob invalidDraft = new PrintJob(null,
                    new Model3D(missingModelId, "E8_MODELO_NO_EXISTE", null, null,
                            BigDecimal.ONE, 30, true), created, 30);
            boolean foreignKeyRejected = false;
            try {
                service.createOrder(data.rollbackQuote().getId(), created,
                        List.of(validDraft, invalidDraft));
            } catch (PersistenceException expected) {
                foreignKeyRejected = expected.getCause() instanceof SQLException sql
                        && "23000".equals(sql.getSQLState())
                        && sql.getErrorCode() == 1452;
            }
            require(foreignKeyRejected
                    && validDraft.getOrder() == null && invalidDraft.getOrder() == null
                    && count(factory,
                            "SELECT COUNT(*) FROM pedido WHERE id_presupuesto = ?",
                            data.rollbackQuote().getId()) == 0
                    && count(factory,
                            "SELECT COUNT(*) FROM trabajo_impresion WHERE id_modelo = ?",
                            data.rollbackModel().getId()) == 0,
                    "ROLLBACK fallo o se modificaron los trabajos originales.");
            System.out.println("6/6 ROLLBACK correcto (sin pedido ni trabajos parciales).");

            passed = true;
        } catch (Exception failure) {
            System.err.println("Fallo la prueba E8.3: " + failure.getMessage());
            LOGGER.warning("Persistent order service smoke test failed ("
                    + failure.getClass().getSimpleName() + ").");
        } finally {
            if (data != null) cleaned = data.clean();
        }

        if (passed && cleaned) {
            System.out.println("E8.3 PersistentOrderService: PRUEBA COMPLETA CORRECTA.");
            LOGGER.info("Persistent order service smoke test completed successfully.");
        } else {
            System.err.println("E8.3 PersistentOrderService: PRUEBA INCOMPLETA.");
        }
    }

    private static int count(JdbcConnectionFactory factory, String sql, int id)
            throws SQLException {
        try (Connection connection = factory.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
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
