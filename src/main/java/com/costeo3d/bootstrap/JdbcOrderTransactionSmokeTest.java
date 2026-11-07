package com.costeo3d.bootstrap;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

import com.costeo3d.domain.enums.PrintJobStatus;
import com.costeo3d.domain.model.Model3D;
import com.costeo3d.domain.model.Order;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.infrastructure.config.DatabaseConfig;
import com.costeo3d.infrastructure.logging.AppLogging;
import com.costeo3d.infrastructure.persistence.PersistenceException;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcOrderDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcPrintJobDao;

/** Prueba manual: pedido y dos trabajos en una transaccion; rollback al fallar el segundo. */
public final class JdbcOrderTransactionSmokeTest {
    private static final Logger LOGGER =
            Logger.getLogger(JdbcOrderTransactionSmokeTest.class.getName());
    private JdbcOrderTransactionSmokeTest() { }

    public static void main(String[] args) {
        AppLogging.configure();
        JdbcOrderTransactionSmokeData data = null;
        boolean passed = false, cleaned = false;
        try {
            JdbcConnectionFactory factory = new JdbcConnectionFactory(DatabaseConfig.load());
            data = new JdbcOrderTransactionSmokeData(factory);
            data.create();
            JdbcOrderDao orders = new JdbcOrderDao(factory);
            int expectedModelId = data.model().getId();
            LocalDateTime created = LocalDateTime.now().withNano(0);

            Order original = new Order(data.successQuote(), created);
            original.addPrintJob(new PrintJob(null, data.model(), created, 90));
            original.addPrintJob(new PrintJob(null, data.model(), created, 120));
            Order saved = orders.insert(original);
            require(saved.getId() != null && saved.getId() > 0
                    && original.getId() == null && saved.getPrintJobs().size() == 2,
                    "INSERT no guardo el pedido con dos trabajos.");
            List<PrintJob> inserted = saved.getPrintJobs();
            require(inserted.stream().allMatch(j -> j.getId() != null && j.getId() > 0
                    && j.getOrder() == saved)
                    && !inserted.get(0).getId().equals(inserted.get(1).getId()),
                    "No se asignaron correctamente los IDs o el pedido a los trabajos.");
            System.out.println("1/4 INSERT transaccional correcto (pedido y 2 trabajos).");

            Order found = orders.findById(saved.getId()).orElseThrow();
            require(found.getQuote().getId().equals(data.successQuote().getId())
                    && found.getQuote().getItems().size() == 1
                    && found.getPrintJobs().size() == 2,
                    "FIND BY ID no reconstruyo el pedido completo.");
            require(found.getPrintJobs().stream().allMatch(j -> j.getOrder() == found
                    && j.getModel() != null
                    && j.getModel().getId().equals(expectedModelId)
                    && j.getStatus() == PrintJobStatus.PENDING)
                    && found.getPrintJobs().stream().map(PrintJob::getEstimatedPrintTimeMinutes)
                            .sorted().toList().equals(List.of(90, 120)),
                    "FIND BY ID no reconstruyo correctamente los dos trabajos.");
            System.out.println("2/4 FIND BY ID correcto (pedido y trabajos completos).");

            require(orders.findAll().stream().anyMatch(o -> saved.getId().equals(o.getId())
                    && o.getPrintJobs().size() == 2),
                    "FIND ALL no recupero los dos trabajos.");
            require(new JdbcPrintJobDao(factory).findByOrderId(saved.getId()).size() == 2,
                    "FIND BY ORDER ID no encontro los dos trabajos.");
            System.out.println("3/4 FIND ALL y FIND BY ORDER ID correctos.");

            // El segundo trabajo usara un modelo inexistente: MySQL rechazara su FK.
            int missingModelId = Integer.MAX_VALUE;
            require(count(factory, "SELECT COUNT(*) FROM modelo_3d WHERE id_modelo = ?",
                    missingModelId) == 0, "El ID reservado para provocar el error ya existe.");
            Order invalid = new Order(data.rollbackQuote(), created);
            invalid.addPrintJob(new PrintJob(null, data.rollbackModel(), created, 60));
            invalid.addPrintJob(new PrintJob(null,
                    new Model3D(missingModelId, "MODELO_INEXISTENTE", null, null,
                            BigDecimal.ONE, 30, true), created, 30));
            boolean foreignKeyRejected = false;
            try {
                orders.insert(invalid);
            } catch (PersistenceException expected) {
                foreignKeyRejected = expected.getCause() instanceof SQLException sql
                        && "23000".equals(sql.getSQLState()) && sql.getErrorCode() == 1452;
            }
            require(foreignKeyRejected, "No se produjo el error de FK esperado en el segundo trabajo.");
            require(count(factory, "SELECT COUNT(*) FROM pedido WHERE id_presupuesto = ?",
                    data.rollbackQuote().getId()) == 0,
                    "ROLLBACK fallo: el pedido incompleto quedo guardado.");
            require(count(factory, "SELECT COUNT(*) FROM trabajo_impresion WHERE id_modelo = ?",
                    data.rollbackModel().getId()) == 0,
                    "ROLLBACK fallo: el primer trabajo quedo guardado.");
            System.out.println("4/4 ROLLBACK correcto (sin pedido ni primer trabajo).");
            passed = true;
        } catch (Exception failure) {
            System.err.println("Fallo la prueba transaccional: " + failure.getMessage());
            LOGGER.warning("Order transaction smoke test failed ("
                    + failure.getClass().getSimpleName() + ").");
        } finally {
            if (data != null) cleaned = data.clean();
        }
        if (passed && cleaned) {
            System.out.println("E7.4.2 Pedido y trabajos: PRUEBA COMPLETA CORRECTA.");
            LOGGER.info("Order transaction smoke test completed successfully.");
        } else {
            System.err.println("E7.4.2 Pedido y trabajos: prueba incompleta. Revisar errores e IDs.");
        }
    }

    private static int count(JdbcConnectionFactory factory, String sql, int id)
            throws SQLException {
        try (Connection connection = factory.openConnection();
                PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }
}
