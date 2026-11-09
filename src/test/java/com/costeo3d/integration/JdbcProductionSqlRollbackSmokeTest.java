package com.costeo3d.integration;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import com.costeo3d.domain.enums.PrintJobStatus;
import com.costeo3d.domain.model.FilamentConsumption;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.infrastructure.persistence.PersistenceException;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcProductionTransaction;

/** Prueba de rollback DESPUES de descontar stock e insertar un consumo. */
final class JdbcProductionSqlRollbackSmokeTest {
    private JdbcProductionSqlRollbackSmokeTest() { }

    static void requireStrictMode(JdbcConnectionFactory factory) throws SQLException {
        try (Connection c = factory.openConnection();
                Statement s = c.createStatement();
                ResultSet r = s.executeQuery("SELECT @@SESSION.sql_mode")) {
            r.next();
            String mode = r.getString(1);
            require(mode.contains("STRICT_TRANS_TABLES") || mode.contains("STRICT_ALL_TABLES"),
                    "La prueba de overflow requiere MySQL en modo estricto.");
        }
    }

    static void run(JdbcProductionTransactionSmokeData d,
            JdbcProductionTransaction tx) throws Exception {
        PrintJob job = d.sqlFailure();
        boolean rejected = false;
        try {
            tx.completeJob(job, List.of(
                    new FilamentConsumption(job, d.spool(),
                            new BigDecimal("1.00"), new BigDecimal("1.00")),
                    new FilamentConsumption(job, d.spool(),
                            new BigDecimal("100000000.00"), new BigDecimal("1.00"))),
                    job.getStartedAt().plusMinutes(10));
        } catch (PersistenceException expected) {
            rejected = expected.getCause() instanceof SQLException;
        }
        require(rejected && same(d.dbWeight(), "430.00")
                && d.countConsumptions(job) == 0
                && d.dbJob(job).getStatus() == PrintJobStatus.IN_PROGRESS,
                "Fallo SQL no revirtio el descuento y los consumos.");
        System.out.println("5/6 ROLLBACK SQL correcto (stock, consumos y estado intactos).");
    }

    private static boolean same(BigDecimal actual, String expected) {
        return actual != null && actual.compareTo(new BigDecimal(expected)) == 0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
