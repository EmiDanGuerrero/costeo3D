package com.costeo3d.bootstrap;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.costeo3d.domain.enums.PrintJobStatus;
import com.costeo3d.domain.exception.InsufficientStockException;
import com.costeo3d.domain.model.FilamentConsumption;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.domain.model.Spool;
import com.costeo3d.infrastructure.config.DatabaseConfig;
import com.costeo3d.infrastructure.logging.AppLogging;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcProductionTransaction;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcSpoolDao;

/** Regresion E7.6: dos bobinas, bloqueo ordenado y rollback por stock insuficiente. */
public final class JdbcMultiSpoolTransactionSmokeTest {
    private static final Logger LOGGER =
            Logger.getLogger(JdbcMultiSpoolTransactionSmokeTest.class.getName());

    private JdbcMultiSpoolTransactionSmokeTest() { }

    public static void main(String[] args) {
        AppLogging.configure();
        JdbcProductionTransactionSmokeData data = null;
        JdbcConnectionFactory factory = null;
        Spool secondSpool = null;
        boolean passed = false;
        boolean secondCleaned = true;
        boolean dataCleaned = true;

        try {
            factory = new JdbcConnectionFactory(DatabaseConfig.load());
            data = new JdbcProductionTransactionSmokeData(factory);
            data.create();
            secondSpool = new JdbcSpoolDao(factory).insert(
                    new Spool(data.spool().getMaterial(), "E7_SEGUNDA_BOBINA",
                            new BigDecimal("120.00")));

            JdbcProductionTransaction transaction = new JdbcProductionTransaction(factory);
            int firstId = data.spool().getId();
            int secondId = secondSpool.getId();

            // Se entrega primero la bobina con ID mayor: el DAO debe bloquear por ID.
            PrintJob complete = data.success();
            var completed = transaction.completeJob(complete, List.of(
                    consumption(complete, secondSpool, "35.00"),
                    consumption(complete, data.spool(), "40.00")),
                    complete.getStartedAt().plusMinutes(10));
            require(completed.finishedJob().getStatus() == PrintJobStatus.COMPLETED
                    && completed.consumptionIds().size() == 2
                    && remaining(completed.remainingGramsBySpool(), firstId, "460.00")
                    && remaining(completed.remainingGramsBySpool(), secondId, "85.00")
                    && weight(factory, firstId).compareTo(new BigDecimal("460.00")) == 0
                    && weight(factory, secondId).compareTo(new BigDecimal("85.00")) == 0
                    && data.countConsumptions(complete) == 2,
                    "Error en el descuento de dos bobinas distintas.");
            System.out.println("1/3 COMPLETE correcto (dos bobinas: 460.00 y 85.00).");

            PrintJob shortage = data.shortage();
            boolean rejected = false;
            try {
                transaction.completeJob(shortage, List.of(
                        consumption(shortage, data.spool(), "5.00"),
                        consumption(shortage, secondSpool, "90.00")),
                        shortage.getStartedAt().plusMinutes(10));
            } catch (InsufficientStockException expected) {
                rejected = true;
            }
            require(rejected
                    && weight(factory, firstId).compareTo(new BigDecimal("460.00")) == 0
                    && weight(factory, secondId).compareTo(new BigDecimal("85.00")) == 0
                    && data.countConsumptions(shortage) == 0
                    && data.dbJob(shortage).getStatus() == PrintJobStatus.IN_PROGRESS,
                    "El stock insuficiente altero una de las bobinas o el trabajo.");
            System.out.println("2/3 Stock insuficiente: ambas bobinas sin cambios.");

            PrintJob failed = data.failed();
            var failure = transaction.failJob(failed, List.of(
                    consumption(failed, secondSpool, "10.00"),
                    consumption(failed, data.spool(), "10.00")),
                    failed.getStartedAt().plusMinutes(10));
            require(failure.finishedJob().getStatus() == PrintJobStatus.FAILED
                    && weight(factory, firstId).compareTo(new BigDecimal("450.00")) == 0
                    && weight(factory, secondId).compareTo(new BigDecimal("75.00")) == 0
                    && data.countConsumptions(failed) == 2,
                    "La finalizacion fallida no desconto ambas bobinas.");
            System.out.println("3/3 FAIL correcto (dos bobinas: 450.00 y 75.00).");
            passed = true;
        } catch (Exception error) {
            System.err.println("Fallo E7.6: " + error.getClass().getSimpleName()
                    + ": " + error.getMessage());
            LOGGER.warning("Multi-spool transaction test failed ("
                    + error.getClass().getSimpleName() + ").");
        } finally {
            // Primero eliminar los consumos de la bobina extra y la bobina;
            // despues, la limpieza existente elimina el resto de registros temporales.
            if (secondSpool != null && factory != null) {
                secondCleaned = cleanSecondSpool(factory, secondSpool.getId());
            }
            if (data != null) {
                dataCleaned = data.clean();
            }
        }
        if (passed && secondCleaned && dataCleaned) {
            System.out.println("E7.6 Dos bobinas: PRUEBA COMPLETA CORRECTA.");
        } else {
            System.err.println("E7.6 Dos bobinas: prueba incompleta. Revisar los IDs temporales.");
        }
    }

    private static FilamentConsumption consumption(PrintJob job, Spool spool, String grams) {
        return new FilamentConsumption(job, spool, null, new BigDecimal(grams));
    }

    private static boolean remaining(Map<Integer, BigDecimal> values, int id, String expected) {
        return values.get(id) != null
                && values.get(id).compareTo(new BigDecimal(expected)) == 0;
    }

    private static BigDecimal weight(JdbcConnectionFactory factory, int spoolId)
            throws SQLException {
        try (Connection c = factory.openConnection();
                PreparedStatement p = c.prepareStatement(
                        "SELECT peso_actual FROM bobina WHERE id_bobina = ?")) {
            p.setInt(1, spoolId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("Temporary spool not found.");
                return r.getBigDecimal(1);
            }
        }
    }

    private static boolean cleanSecondSpool(JdbcConnectionFactory factory, int spoolId) {
        try (Connection c = factory.openConnection()) {
            c.setAutoCommit(false);
            try {
                delete(c, "DELETE FROM consumo_filamento WHERE id_bobina = ?", spoolId);
                delete(c, "DELETE FROM bobina WHERE id_bobina = ?", spoolId);
                c.commit();
                return true;
            } catch (SQLException error) {
                try { c.rollback(); } catch (SQLException ignored) { error.addSuppressed(ignored); }
                throw error;
            }
        } catch (SQLException error) {
            System.err.println("No se pudo limpiar la segunda bobina temporal, ID=" + spoolId);
            return false;
        }
    }

    private static void delete(Connection c, String sql, int spoolId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, spoolId);
            p.executeUpdate();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
