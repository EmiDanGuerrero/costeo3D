package com.costeo3d.bootstrap;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;

import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.domain.model.Spool;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcMaterialDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcPrintJobDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcSpoolDao;

/** Prepara y elimina solamente los registros propios de esta prueba manual. */
final class JdbcConsumptionWasteSmokeData {
    private final JdbcConnectionFactory factory;
    private Integer materialId, spoolId, jobId;
    private Spool spool;
    private PrintJob job;

    JdbcConsumptionWasteSmokeData(JdbcConnectionFactory factory) {
        this.factory = factory;
    }

    Spool spool() { return spool; }
    PrintJob job() { return job; }

    void create() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        Material material = new JdbcMaterialDao(factory).insert(
                new Material("E7_CONSUMO_" + suffix, "PLA", "Mate",
                        new BigDecimal("0.5000")));
        materialId = material.getId();

        spool = new JdbcSpoolDao(factory).insert(
                new Spool(material, "Naranja", new BigDecimal("500.00")));
        spoolId = spool.getId();

        LocalDateTime created = LocalDateTime.now().withNano(0);
        PrintJob pending = new PrintJob(null, null, created, 45);
        pending.start(created.plusMinutes(1));
        pending.fail(created.plusMinutes(8));
        job = new JdbcPrintJobDao(factory).insert(pending);
        jobId = job.getId();
    }

    boolean clean() {
        try (Connection connection = factory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                if (jobId != null) {
                    delete(connection, "DELETE FROM merma_estimada WHERE id_trabajo = ?", jobId);
                    delete(connection, "DELETE FROM consumo_filamento WHERE id_trabajo = ?", jobId);
                    delete(connection, "DELETE FROM trabajo_impresion WHERE id_trabajo = ?", jobId);
                }
                if (spoolId != null) {
                    delete(connection, "DELETE FROM bobina WHERE id_bobina = ?", spoolId);
                }
                if (materialId != null) {
                    delete(connection, "DELETE FROM material WHERE id_material = ?", materialId);
                }
                connection.commit();
                System.out.println("Limpieza: consumos, merma, trabajo, bobina y material temporales eliminados.");
                return true;
            } catch (SQLException | RuntimeException failure) {
                try { connection.rollback(); }
                catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                reportFailure(failure);
                return false;
            }
        } catch (SQLException | RuntimeException failure) {
            reportFailure(failure);
            return false;
        }
    }

    private void reportFailure(Exception failure) {
        System.err.println("No se pudo completar la limpieza ("
                + failure.getClass().getSimpleName() + ").");
        System.err.println("IDs temporales: trabajo=" + jobId + ", bobina=" + spoolId
                + ", material=" + materialId);
    }

    private static void delete(Connection connection, String sql, int id)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }
}
