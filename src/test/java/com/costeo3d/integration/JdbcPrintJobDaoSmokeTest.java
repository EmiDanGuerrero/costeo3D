package com.costeo3d.integration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

import com.costeo3d.domain.enums.PrintJobStatus;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.infrastructure.config.DatabaseConfig;
import com.costeo3d.infrastructure.logging.AppLogging;
import com.costeo3d.infrastructure.persistence.dao.PrintJobDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcPrintJobDao;

/** Prueba manual de persistencia de trabajos con un pedido temporal real. */
public final class JdbcPrintJobDaoSmokeTest {
    private static final Logger LOGGER =
            Logger.getLogger(JdbcPrintJobDaoSmokeTest.class.getName());

    private JdbcPrintJobDaoSmokeTest() { }

    public static void main(String[] args) {
        AppLogging.configure();
        JdbcPrintJobSmokeData data = null;
        boolean passed = false;
        boolean cleaned = false;
        try {
            JdbcConnectionFactory factory = new JdbcConnectionFactory(DatabaseConfig.load());
            data = new JdbcPrintJobSmokeData(factory);
            data.create();
            PrintJobDao jobs = new JdbcPrintJobDao(factory);
            LocalDateTime created = LocalDateTime.now().withNano(0);

            PrintJob original = new PrintJob(data.order(), data.model(), created, 90);
            PrintJob saved = jobs.insert(original);
            require(saved.getId() != null && saved.getId() > 0 && original.getId() == null,
                    "INSERT no devolvio el ID autogenerado.");
            System.out.println("1/5 INSERT correcto. ID trabajo: " + saved.getId());

            PrintJob found = jobs.findById(saved.getId()).orElseThrow();
            require(found.getOrder() != null
                    && found.getOrder().getId().equals(data.order().getId())
                    && found.getOrder().getQuote().getId().equals(data.order().getQuote().getId())
                    && found.getModel() != null
                    && found.getModel().getId().equals(data.model().getId())
                    && found.getStatus() == PrintJobStatus.PENDING
                    && found.getStartedAt() == null && found.getFinishedAt() == null
                    && found.getEstimatedPrintTimeMinutes() == 90,
                    "FIND BY ID no reconstruyo las relaciones o atributos.");
            System.out.println("2/5 FIND BY ID correcto (pedido y modelo).");

            require(jobs.findAll().stream().anyMatch(j -> saved.getId().equals(j.getId())),
                    "FIND ALL no incluyo el trabajo temporal.");
            System.out.println("3/5 FIND ALL correcto.");

            List<PrintJob> byOrder = jobs.findByOrderId(data.order().getId());
            require(byOrder.stream().anyMatch(j -> saved.getId().equals(j.getId()))
                    && jobs.findByOrderId(Integer.MAX_VALUE).isEmpty(),
                    "FIND BY ORDER ID devolvio resultados incorrectos.");
            System.out.println("4/5 FIND BY ORDER ID correcto.");

            LocalDateTime start = created.plusMinutes(1);
            LocalDateTime finish = created.plusMinutes(12);
            saved.start(start);
            require(jobs.update(saved), "No se actualizo el inicio del trabajo.");
            PrintJob inProgress = jobs.findById(saved.getId()).orElseThrow();
            require(inProgress.getStatus() == PrintJobStatus.IN_PROGRESS
                    && start.equals(inProgress.getStartedAt())
                    && inProgress.getFinishedAt() == null,
                    "No se guardo el estado EN_PROCESO o fecha de inicio.");
            saved.complete(finish);
            require(jobs.update(saved) && jobs.update(saved),
                    "No se pudo completar o actualizar sin cambios.");
            PrintJob completed = jobs.findById(saved.getId()).orElseThrow();
            require(completed.getStatus() == PrintJobStatus.COMPLETED
                    && start.equals(completed.getStartedAt())
                    && finish.equals(completed.getFinishedAt())
                    && completed.getModel().getId().equals(data.model().getId()),
                    "UPDATE no persistio estado, fechas y modelo.");
            System.out.println("5/5 UPDATE correcto (inicio, fin y sin cambios).");
            passed = true;
        } catch (Exception failure) {
            System.err.println("Fallo la prueba de PrintJobDao: " + failure.getMessage());
            LOGGER.warning("PrintJob DAO test failed (" + failure.getClass().getSimpleName() + ").");
        } finally {
            if (data != null) {
                cleaned = data.clean();
            }
        }

        if (passed && cleaned) {
            System.out.println("E7.4.2 PrintJob: PRUEBA COMPLETA CORRECTA.");
            LOGGER.info("PrintJob DAO smoke test completed successfully.");
        } else {
            System.err.println("E7.4.2 PrintJob: prueba incompleta. Revisar mensajes e IDs.");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
