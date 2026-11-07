package com.costeo3d.bootstrap;

import java.math.BigDecimal;
import java.util.List;
import java.util.logging.Logger;

import com.costeo3d.domain.enums.PrintJobStatus;
import com.costeo3d.domain.exception.InsufficientStockException;
import com.costeo3d.domain.exception.InvalidStateTransitionException;
import com.costeo3d.domain.model.FilamentConsumption;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.infrastructure.config.DatabaseConfig;
import com.costeo3d.infrastructure.logging.AppLogging;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcProductionTransaction;

/** Prueba manual de transacciones de stock E7.5 sobre datos temporales. */
public final class JdbcProductionTransactionSmokeTest {
    private static final Logger LOGGER =
            Logger.getLogger(JdbcProductionTransactionSmokeTest.class.getName());

    private JdbcProductionTransactionSmokeTest() { }

    public static void main(String[] args) {
        AppLogging.configure();
        JdbcProductionTransactionSmokeData data = null;
        boolean passed = false;
        boolean cleaned = false;
        try {
            JdbcConnectionFactory factory = new JdbcConnectionFactory(DatabaseConfig.load());
            data = new JdbcProductionTransactionSmokeData(factory);
            JdbcProductionSqlRollbackSmokeTest.requireStrictMode(factory);
            data.create();
            JdbcProductionTransaction transaction = new JdbcProductionTransaction(factory);
            testSuccess(data, transaction);
            testDuplicate(data, transaction);
            testShortage(data, transaction);
            testInvalidDate(data, transaction);
            JdbcProductionSqlRollbackSmokeTest.run(data, transaction);
            testFailedJob(data, transaction);
            passed = true;
        } catch (Exception failure) {
            System.err.println("Fallo E7.5: " + failure.getClass().getSimpleName()
                    + ": " + failure.getMessage());
            LOGGER.warning("Production transaction test failed ("
                    + failure.getClass().getSimpleName() + ").");
        } finally {
            if (data != null) cleaned = data.clean();
        }
        if (passed && cleaned) {
            System.out.println("E7.5 Stock y produccion: PRUEBA COMPLETA CORRECTA.");
        } else {
            System.err.println("E7.5: prueba incompleta; revisar errores e IDs temporales.");
        }
    }

    private static void testSuccess(JdbcProductionTransactionSmokeData d,
            JdbcProductionTransaction tx) throws Exception {
        JdbcProductionTransaction.Result result = tx.completeJob(d.success(), List.of(
                consumption(d.success(), d, "40.00", "45.00"),
                consumption(d.success(), d, "30.00", "35.00")), end(d.success()));
        require(result.finishedJob().getStatus() == PrintJobStatus.COMPLETED
                && result.consumptionIds().size() == 2
                && same(result.remainingGramsBySpool().get(d.spool().getId()), "430.00")
                && same(d.dbWeight(), "430.00") && d.countConsumptions(d.success()) == 2
                && d.dbJob(d.success()).getStatus() == PrintJobStatus.COMPLETED
                && d.success().getStatus() == PrintJobStatus.IN_PROGRESS
                && same(d.spool().getCurrentWeight(), "500.00"),
                "La finalizacion no fue atomica o altero objetos originales.");
        System.out.println("1/6 COMPLETE correcto (2 consumos de la misma bobina; stock 430.00).");
    }

    private static void testDuplicate(JdbcProductionTransactionSmokeData d,
            JdbcProductionTransaction tx) throws Exception {
        boolean rejected = false;
        try {
            tx.completeJob(d.success(), List.of(
                    consumption(d.success(), d, "1.00", null)), end(d.success()));
        } catch (InvalidStateTransitionException expected) { rejected = true; }
        require(rejected && same(d.dbWeight(), "430.00")
                && d.countConsumptions(d.success()) == 2,
                "Se permitio finalizar dos veces o descontar stock duplicado.");
        System.out.println("2/6 Segunda finalizacion rechazada sin descuento duplicado.");
    }

    private static void testShortage(JdbcProductionTransactionSmokeData d,
            JdbcProductionTransaction tx) throws Exception {
        boolean rejected = false;
        try {
            tx.completeJob(d.shortage(), List.of(
                    consumption(d.shortage(), d, "220.00", null),
                    consumption(d.shortage(), d, "220.00", null)), end(d.shortage()));
        } catch (InsufficientStockException expected) { rejected = true; }
        require(rejected && same(d.dbWeight(), "430.00")
                && d.countConsumptions(d.shortage()) == 0
                && d.dbJob(d.shortage()).getStatus() == PrintJobStatus.IN_PROGRESS,
                "Stock insuficiente no fue rechazado de forma atomica.");
        System.out.println("3/6 Stock insuficiente rechazado (consumos agrupados).");
    }

    private static void testInvalidDate(JdbcProductionTransactionSmokeData d,
            JdbcProductionTransaction tx) throws Exception {
        boolean rejected = false;
        try {
            tx.failJob(d.shortage(), List.of(consumption(d.shortage(), d, "1.00", null)),
                    d.shortage().getStartedAt().minusSeconds(1));
        } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected && same(d.dbWeight(), "430.00")
                && d.countConsumptions(d.shortage()) == 0,
                "Fecha invalida produjo cambios de stock.");
        System.out.println("4/6 Fecha invalida rechazada sin cambios.");
    }

    private static void testFailedJob(JdbcProductionTransactionSmokeData d,
            JdbcProductionTransaction tx) throws Exception {
        var result = tx.failJob(d.failed(), List.of(
                consumption(d.failed(), d, "10.00", null)), end(d.failed()));
        require(result.finishedJob().getStatus() == PrintJobStatus.FAILED
                && d.dbJob(d.failed()).getStatus() == PrintJobStatus.FAILED
                && d.countConsumptions(d.failed()) == 1 && same(d.dbWeight(), "420.00"),
                "La finalizacion FALLIDA no persistio correctamente.");
        System.out.println("6/6 FAIL correcto (stock 420.00, consumo registrado).");
    }

    private static FilamentConsumption consumption(PrintJob job,
            JdbcProductionTransactionSmokeData d, String grams, String planned) {
        return new FilamentConsumption(job, d.spool(),
                planned == null ? null : new BigDecimal(planned), new BigDecimal(grams));
    }

    private static java.time.LocalDateTime end(PrintJob job) {
        return job.getStartedAt().plusMinutes(10);
    }

    private static boolean same(BigDecimal actual, String expected) {
        return actual != null && actual.compareTo(new BigDecimal(expected)) == 0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

}
