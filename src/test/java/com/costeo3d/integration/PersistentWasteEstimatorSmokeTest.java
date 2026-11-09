package com.costeo3d.integration;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.logging.Logger;

import com.costeo3d.application.service.PersistentWasteEstimator;
import com.costeo3d.application.service.WasteEstimator;
import com.costeo3d.domain.exception.BusinessValidationException;
import com.costeo3d.domain.model.EstimatedWaste;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.infrastructure.config.DatabaseConfig;
import com.costeo3d.infrastructure.logging.AppLogging;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcEstimatedWasteDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcFilamentConsumptionDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcPrintJobDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcSpoolDao;

/** Verifica la estimacion persistente sin volver a modificar el stock. */
public final class PersistentWasteEstimatorSmokeTest {
    private static final Logger LOGGER =
            Logger.getLogger(PersistentWasteEstimatorSmokeTest.class.getName());

    private PersistentWasteEstimatorSmokeTest() { }

    public static void main(String[] args) {
        AppLogging.configure();
        JdbcConsumptionWasteSmokeData data = null;
        JdbcConnectionFactory factory = null;
        Integer pendingJobId = null;
        boolean passed = false;
        try {
            factory = new JdbcConnectionFactory(DatabaseConfig.load());
            data = new JdbcConsumptionWasteSmokeData(factory);
            data.create();
            JdbcPrintJobDao jobDao = new JdbcPrintJobDao(factory);
            JdbcEstimatedWasteDao wasteDao = new JdbcEstimatedWasteDao(factory);
            PersistentWasteEstimator service = new PersistentWasteEstimator(
                    new WasteEstimator(), jobDao, wasteDao);

            PrintJob failedJob = data.job();
            int failedId = failedJob.getId();
            BigDecimal initialStock = new JdbcSpoolDao(factory)
                    .findById(data.spool().getId()).orElseThrow().getCurrentWeight();

            // Los trabajos que no fallaron no admiten estimacion de merma.
            PrintJob pending = jobDao.insert(new PrintJob(null, null,
                    LocalDateTime.now().withNano(0), 45));
            pendingJobId = pending.getId();
            expectBusinessRejection(() -> service.createEstimate(pending.getId(), 100, 50));
            check(service.findByPrintJobId(pending.getId()).isEmpty(),
                    "Se inserto una merma para un trabajo PENDING.");
            System.out.println("1/6 PENDING rechazado por WasteEstimator.");

            // Validacion en dominio, antes de insertar datos.
            expectInvalidLayers(() -> service.createEstimate(failedId, 0, 0));
            expectInvalidLayers(() -> service.createEstimate(failedId, 100, 101));
            check(service.findByPrintJobId(failedId).isEmpty(),
                    "Una estimacion invalida llego a MySQL.");
            System.out.println("2/6 Capas invalidas rechazadas sin INSERT.");

            EstimatedWaste saved = service.createEstimate(failedId, 100, 0);
            check(saved.getId() != null && saved.getId() > 0
                    && saved.getProgressPercentage().compareTo(BigDecimal.ZERO) == 0,
                    "No se guardo una merma con progreso del 0 por ciento.");
            System.out.println("3/6 INSERT correcto (capa falla 0; avance 0%).");

            PersistentWasteEstimator newService = new PersistentWasteEstimator(
                    new WasteEstimator(), new JdbcPrintJobDao(factory),
                    new JdbcEstimatedWasteDao(factory));
            Optional<EstimatedWaste> byId = newService.findById(saved.getId());
            Optional<EstimatedWaste> byJob = newService.findByPrintJobId(failedId);
            check(byId.isPresent() && byJob.isPresent()
                    && byJob.get().getId().equals(saved.getId())
                    && byId.get().getPrintJob().getId().equals(failedId),
                    "La merma no pudo recuperarse de MySQL.");
            System.out.println("4/6 FIND BY ID y FIND BY PRINT JOB ID correctos.");

            expectBusinessRejection(() -> newService.createEstimate(failedId, 100, 50));
            check(newService.findByPrintJobId(failedId).isPresent(),
                    "Se perdio la estimacion al rechazar un duplicado.");
            System.out.println("5/6 Estimacion duplicada rechazada.");

            BigDecimal finalStock = new JdbcSpoolDao(factory)
                    .findById(data.spool().getId()).orElseThrow().getCurrentWeight();
            check(initialStock.compareTo(finalStock) == 0
                    && new JdbcFilamentConsumptionDao(factory)
                            .findByPrintJobId(failedId).isEmpty(),
                    "La estimacion modifico el stock o genero consumos reales.");
            expectBusinessRejection(() -> newService.createEstimate(-1, 100, 20));
            System.out.println("6/6 Stock y consumos intactos; trabajo inexistente rechazado.");
            passed = true;
        } catch (Exception failure) {
            System.err.println("E8.4 merma fallo: "
                    + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            LOGGER.warning("Persistent waste test failed ("
                    + failure.getClass().getSimpleName() + ").");
        } finally {
            boolean pendingCleaned = cleanPending(factory, pendingJobId);
            boolean dataCleaned = data != null && data.clean();
            if (passed && pendingCleaned && dataCleaned) {
                System.out.println("E8.4 PersistentWasteEstimator: PRUEBA COMPLETA CORRECTA.");
            } else {
                System.err.println("E8.4 PersistentWasteEstimator: PRUEBA INCOMPLETA.");
            }
        }
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }

    private static void expectBusinessRejection(CheckedAction action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Se esperaba un rechazo de negocio.");
        } catch (BusinessValidationException expected) {
            // Caso esperado: no se modifico la base.
        }
    }

    private static void expectInvalidLayers(CheckedAction action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Se esperaban capas invalidas.");
        } catch (IllegalArgumentException expected) {
            // La entidad valida los limites de capas.
        }
    }

    private static boolean cleanPending(JdbcConnectionFactory factory, Integer id) {
        if (id == null) return true;
        try (Connection connection = factory.openConnection();
                PreparedStatement stmt = connection.prepareStatement(
                        "DELETE FROM trabajo_impresion WHERE id_trabajo = ?")) {
            stmt.setInt(1, id);
            check(stmt.executeUpdate() == 1, "No se elimino el trabajo temporal.");
            return true;
        } catch (SQLException | RuntimeException failure) {
            System.err.println("Limpieza pendiente de trabajo PENDING temporal. ID: " + id);
            return false;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
