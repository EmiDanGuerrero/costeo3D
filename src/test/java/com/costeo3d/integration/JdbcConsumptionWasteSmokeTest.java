package com.costeo3d.integration;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

import com.costeo3d.domain.enums.PrintJobStatus;
import com.costeo3d.domain.model.EstimatedWaste;
import com.costeo3d.domain.model.FilamentConsumption;
import com.costeo3d.infrastructure.config.DatabaseConfig;
import com.costeo3d.infrastructure.logging.AppLogging;
import com.costeo3d.infrastructure.persistence.PersistenceException;
import com.costeo3d.infrastructure.persistence.dao.EstimatedWasteDao;
import com.costeo3d.infrastructure.persistence.dao.FilamentConsumptionDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcEstimatedWasteDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcFilamentConsumptionDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcSpoolDao;

/** Prueba manual de los DAO; NO descuenta stock ni finaliza trabajos. */
public final class JdbcConsumptionWasteSmokeTest {
    private static final Logger LOGGER =
            Logger.getLogger(JdbcConsumptionWasteSmokeTest.class.getName());

    private JdbcConsumptionWasteSmokeTest() { }

    public static void main(String[] args) {
        AppLogging.configure();
        JdbcConsumptionWasteSmokeData data = null;
        boolean passed = false;
        boolean cleaned = false;
        try {
            JdbcConnectionFactory factory = new JdbcConnectionFactory(DatabaseConfig.load());
            data = new JdbcConsumptionWasteSmokeData(factory);
            data.create();
            require(data.job().getStatus() == PrintJobStatus.FAILED,
                    "El trabajo temporal debe estar FALLIDO.");

            FilamentConsumptionDao consumptions = new JdbcFilamentConsumptionDao(factory);
            FilamentConsumption original = new FilamentConsumption(data.job(), data.spool(),
                    new BigDecimal("25.00"), new BigDecimal("12.50"));
            FilamentConsumption saved = consumptions.insert(original);
            require(saved.getId() != null && saved.getId() > 0 && original.getId() == null,
                    "INSERT de consumo no devolvio el ID generado.");
            System.out.println("1/6 INSERT consumo correcto. ID: " + saved.getId());

            FilamentConsumption found = consumptions.findById(saved.getId()).orElseThrow();
            require(found.getPrintJob().getId().equals(data.job().getId())
                    && found.getSpool().getId().equals(data.spool().getId())
                    && found.getSpool().getMaterial().getId().equals(
                            data.spool().getMaterial().getId())
                    && same(found.getPlannedGrams(), "25.00")
                    && same(found.getConsumedGrams(), "12.50"),
                    "FIND BY ID de consumo no reconstruyo los datos y relaciones.");
            System.out.println("2/6 FIND BY ID consumo correcto (trabajo, bobina y material).");

            FilamentConsumption optional = consumptions.insert(
                    new FilamentConsumption(data.job(), data.spool(), null, BigDecimal.ZERO));
            List<FilamentConsumption> byJob = consumptions.findByPrintJobId(data.job().getId());
            require(byJob.size() == 2
                    && byJob.stream().anyMatch(c -> saved.getId().equals(c.getId()))
                    && byJob.stream().anyMatch(c -> optional.getId().equals(c.getId())
                            && c.getPlannedGrams() == null
                            && c.getConsumedGrams().compareTo(BigDecimal.ZERO) == 0)
                    && consumptions.findByPrintJobId(Integer.MAX_VALUE).isEmpty(),
                    "FIND BY PRINT JOB ID o los gramos opcionales son incorrectos.");
            System.out.println("3/6 FIND BY PRINT JOB ID correcto (previstos nulos permitidos).");

            EstimatedWasteDao wastes = new JdbcEstimatedWasteDao(factory);
            EstimatedWaste waste = wastes.insert(new EstimatedWaste(data.job(), 120, 30));
            require(waste.getId() != null && waste.getId() > 0,
                    "INSERT de merma no devolvio el ID generado.");
            System.out.println("4/6 INSERT merma correcto. ID: " + waste.getId());

            EstimatedWaste fromId = wastes.findById(waste.getId()).orElseThrow();
            EstimatedWaste fromJob = wastes.findByPrintJobId(data.job().getId()).orElseThrow();
            require(fromId.getPrintJob().getId().equals(data.job().getId())
                    && fromJob.getId().equals(waste.getId())
                    && fromId.getTotalLayers() == 120 && fromId.getFailureLayer() == 30
                    && same(fromId.getProgressPercentage(), "25.00")
                    && wastes.findByPrintJobId(Integer.MAX_VALUE).isEmpty(),
                    "La merma no se reconstruyo correctamente.");
            System.out.println("5/6 FIND BY ID y FIND BY PRINT JOB ID de merma correctos.");

            boolean duplicateRejected = false;
            try {
                wastes.insert(new EstimatedWaste(data.job(), 150, 50));
            } catch (PersistenceException expected) {
                duplicateRejected = expected.getCause() instanceof SQLException sql
                        && sql.getErrorCode() == 1062;
            }
            require(duplicateRejected, "MySQL no rechazo la segunda merma del mismo trabajo.");
            require(same(new JdbcSpoolDao(factory).findById(data.spool().getId())
                    .orElseThrow().getCurrentWeight(), "500.00"),
                    "Los DAO aislados no deben modificar el stock.");
            System.out.println("6/6 UNIQUE de merma correcto; stock sin modificaciones.");
            passed = true;
        } catch (Exception failure) {
            System.err.println("Fallo la prueba de consumos y mermas: " + failure.getMessage());
            LOGGER.warning("Consumption/waste test failed ("
                    + failure.getClass().getSimpleName() + ").");
        } finally {
            if (data != null) cleaned = data.clean();
        }

        if (passed && cleaned) {
            System.out.println("E7.4.3 Consumos y mermas: PRUEBA COMPLETA CORRECTA.");
            LOGGER.info("Consumption/waste DAO smoke test completed successfully.");
        } else {
            System.err.println("E7.4.3: prueba incompleta; revisar errores e IDs temporales.");
        }
    }

    private static boolean same(BigDecimal value, String expected) {
        return value != null && value.compareTo(new BigDecimal(expected)) == 0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
