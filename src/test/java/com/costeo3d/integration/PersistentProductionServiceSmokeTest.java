package com.costeo3d.integration;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

import com.costeo3d.application.port.ProductionFinalizationPort;
import com.costeo3d.application.service.PersistentProductionService;
import com.costeo3d.application.service.ProductionService;
import com.costeo3d.domain.enums.PrintJobStatus;
import com.costeo3d.domain.exception.InsufficientStockException;
import com.costeo3d.domain.exception.InvalidStateTransitionException;
import com.costeo3d.domain.model.FilamentConsumption;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.infrastructure.config.DatabaseConfig;
import com.costeo3d.infrastructure.logging.AppLogging;
import com.costeo3d.infrastructure.persistence.PersistenceException;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcPrintJobDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcProductionFinalizationAdapter;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcProductionTransaction;

/** Prueba E8.4: PersistentProductionService -> Port -> JDBC -> MySQL. */
public final class PersistentProductionServiceSmokeTest {
    private static final Logger LOGGER =
            Logger.getLogger(PersistentProductionServiceSmokeTest.class.getName());

    private PersistentProductionServiceSmokeTest() { }

    public static void main(String[] args) {
        AppLogging.configure();
        PersistentProductionServiceSmokeData data = null;
        boolean passed = false;
        boolean cleaned = false;
        try {
            JdbcConnectionFactory factory = new JdbcConnectionFactory(DatabaseConfig.load());
            requireStrictMode(factory);
            data = new PersistentProductionServiceSmokeData(factory);
            data.create();
            PersistentProductionService service = new PersistentProductionService(
                    new ProductionService(), new JdbcPrintJobDao(factory),
                    new JdbcProductionFinalizationAdapter(
                            new JdbcProductionTransaction(factory)));

            startJobs(data, service);
            testComplete(data, service);
            testDuplicate(data, service);
            testShortage(data, service);
            testRollback(data, service);
            testFailed(data, service);
            passed = true;
        } catch (Exception failure) {
            System.err.println("E8.4 fallo: " + failure.getClass().getSimpleName()
                    + ": " + failure.getMessage());
            LOGGER.warning("Persistent production test failed ("
                    + failure.getClass().getSimpleName() + ").");
        } finally {
            if (data != null) cleaned = data.clean();
        }
        if (passed && cleaned) {
            System.out.println("E8.4 PersistentProductionService: PRUEBA COMPLETA CORRECTA.");
        } else {
            System.err.println("E8.4 PersistentProductionService: PRUEBA INCOMPLETA.");
        }
    }

    private static void startJobs(PersistentProductionServiceSmokeData d,
            PersistentProductionService service) throws Exception {
        PrintJob original = d.success();
        check(original.getStatus() == PrintJobStatus.PENDING,
                "El trabajo original no era PENDING.");
        for (PrintJob job : List.of(d.success(), d.shortage(), d.rollback(), d.failed())) {
            PrintJob started = service.startJob(job.getId(), job.getCreatedAt().plusMinutes(1));
            check(started.getStatus() == PrintJobStatus.IN_PROGRESS
                    && started.getStartedAt() != null
                    && service.findById(job.getId()).orElseThrow().getStatus()
                            == PrintJobStatus.IN_PROGRESS,
                    "No se guardo el inicio del trabajo en MySQL.");
        }
        check(original.getStatus() == PrintJobStatus.PENDING
                && original.getStartedAt() == null,
                "El servicio modifico el objeto original durante el inicio.");
        System.out.println("1/6 START correcto (estado y fecha persistidos).");
    }

    private static void testComplete(PersistentProductionServiceSmokeData d,
            PersistentProductionService service) throws Exception {
        PrintJob job = service.findById(d.success().getId()).orElseThrow();
        ProductionFinalizationPort.Completion result = service.completeJob(job.getId(),
                List.of(consumption(job, d, "40.00", "45.00"),
                        consumption(job, d, "30.00", "35.00")), end(job));
        check(result.finishedJob().getStatus() == PrintJobStatus.COMPLETED
                && result.consumptionIds().size() == 2
                && result.consumptionIds().stream().allMatch(id -> id != null && id > 0)
                && same(result.remainingGramsBySpool().get(d.spool().getId()), "430.00")
                && same(d.stockInDatabase(), "430.00")
                && d.consumptionCount(job) == 2
                && service.findById(job.getId()).orElseThrow().getStatus()
                        == PrintJobStatus.COMPLETED
                && job.getStatus() == PrintJobStatus.IN_PROGRESS
                && same(d.spool().getCurrentWeight(), "500.00"),
                "COMPLETE no persistio correctamente o modifico objetos originales.");
        System.out.println("2/6 COMPLETE correcto (2 consumos; stock 430.00).");
    }

    private static void testDuplicate(PersistentProductionServiceSmokeData d,
            PersistentProductionService service) throws Exception {
        boolean rejected = false;
        PrintJob job = d.success();
        try {
            service.completeJob(job.getId(),
                    List.of(consumption(job, d, "1.00", null)),
                    job.getCreatedAt().plusMinutes(20));
        } catch (InvalidStateTransitionException expected) {
            rejected = true;
        }
        check(rejected && same(d.stockInDatabase(), "430.00")
                && d.consumptionCount(job) == 2,
                "Se desconto dos veces el consumo del mismo trabajo.");
        System.out.println("3/6 Finalizacion duplicada rechazada sin doble descuento.");
    }

    private static void testShortage(PersistentProductionServiceSmokeData d,
            PersistentProductionService service) throws Exception {
        PrintJob job = service.findById(d.shortage().getId()).orElseThrow();
        boolean rejected = false;
        try {
            service.completeJob(job.getId(), List.of(
                    consumption(job, d, "250.00", null),
                    consumption(job, d, "250.00", null)), end(job));
        } catch (InsufficientStockException expected) {
            rejected = true;
        }
        check(rejected && same(d.stockInDatabase(), "430.00")
                && d.consumptionCount(job) == 0
                && service.findById(job.getId()).orElseThrow().getStatus()
                        == PrintJobStatus.IN_PROGRESS,
                "Stock insuficiente dejo cambios parciales.");
        System.out.println("4/6 Stock insuficiente rechazado, sin cambios parciales.");
    }

    private static void testRollback(PersistentProductionServiceSmokeData d,
            PersistentProductionService service) throws Exception {
        PrintJob job = service.findById(d.rollback().getId()).orElseThrow();
        boolean rejected = false;
        try {
            service.completeJob(job.getId(), List.of(
                    consumption(job, d, "1.00", "1.00"),
                    consumption(job, d, "1.00", "100000000.00")), end(job));
        } catch (PersistenceException expected) {
            rejected = expected.getCause() instanceof SQLException;
        }
        check(rejected && same(d.stockInDatabase(), "430.00")
                && d.consumptionCount(job) == 0
                && service.findById(job.getId()).orElseThrow().getStatus()
                        == PrintJobStatus.IN_PROGRESS,
                "El error SQL no revirtio stock, consumos y estado.");
        System.out.println("5/6 ROLLBACK SQL correcto (estado y stock intactos).");
    }

    private static void testFailed(PersistentProductionServiceSmokeData d,
            PersistentProductionService service) throws Exception {
        PrintJob job = service.findById(d.failed().getId()).orElseThrow();
        ProductionFinalizationPort.Completion result = service.failJob(job.getId(),
                List.of(consumption(job, d, "10.00", null)), end(job));
        check(result.finishedJob().getStatus() == PrintJobStatus.FAILED
                && result.consumptionIds().size() == 1
                && same(result.remainingGramsBySpool().get(d.spool().getId()), "420.00")
                && same(d.stockInDatabase(), "420.00")
                && d.consumptionCount(job) == 1
                && service.findById(job.getId()).orElseThrow().getStatus()
                        == PrintJobStatus.FAILED,
                "FAIL no quedo registrado correctamente.");
        System.out.println("6/6 FAIL correcto (consumo registrado; stock 420.00).");
    }

    private static FilamentConsumption consumption(PrintJob job,
            PersistentProductionServiceSmokeData d, String grams, String planned) {
        return new FilamentConsumption(job, d.spool(),
                planned == null ? null : new BigDecimal(planned),
                new BigDecimal(grams));
    }

    private static LocalDateTime end(PrintJob job) {
        return job.getStartedAt().plusMinutes(10);
    }

    private static void requireStrictMode(JdbcConnectionFactory factory)
            throws SQLException {
        try (Connection connection = factory.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT @@SESSION.sql_mode")) {
            rs.next();
            String mode = rs.getString(1);
            check(mode.contains("STRICT_TRANS_TABLES") || mode.contains("STRICT_ALL_TABLES"),
                    "La prueba de overflow requiere MySQL en modo estricto.");
        }
    }

    private static boolean same(BigDecimal value, String expected) {
        return value != null && value.compareTo(new BigDecimal(expected)) == 0;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
