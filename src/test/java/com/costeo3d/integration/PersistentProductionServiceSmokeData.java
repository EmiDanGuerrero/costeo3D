package com.costeo3d.integration;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.domain.model.Spool;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcMaterialDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcPrintJobDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcSpoolDao;

/** Registros propios de E8.4; los trabajos empiezan en PENDING. */
final class PersistentProductionServiceSmokeData {

    private final JdbcConnectionFactory factory;
    private final List<Integer> jobIds = new ArrayList<>();
    private Integer materialId;
    private Integer spoolId;
    private Spool spool;
    private PrintJob success;
    private PrintJob shortage;
    private PrintJob rollback;
    private PrintJob failed;

    PersistentProductionServiceSmokeData(JdbcConnectionFactory factory) {
        this.factory = factory;
    }

    Spool spool() { return spool; }
    PrintJob success() { return success; }
    PrintJob shortage() { return shortage; }
    PrintJob rollback() { return rollback; }
    PrintJob failed() { return failed; }

    void create() {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        Material material = new JdbcMaterialDao(factory).insert(
                new Material("E8_PRODUCCION_" + suffix, "PLA", "Mate",
                        new BigDecimal("0.5000")));
        materialId = material.getId();
        spool = new JdbcSpoolDao(factory).insert(
                new Spool(material, "Azul", new BigDecimal("500.00")));
        spoolId = spool.getId();

        LocalDateTime created = LocalDateTime.now().withNano(0);
        success = createJob(created);
        shortage = createJob(created.plusMinutes(2));
        rollback = createJob(created.plusMinutes(4));
        failed = createJob(created.plusMinutes(6));
    }

    private PrintJob createJob(LocalDateTime createdAt) {
        PrintJob job = new JdbcPrintJobDao(factory).insert(
                new PrintJob(null, null, createdAt, 60));
        jobIds.add(job.getId());
        return job;
    }

    BigDecimal stockInDatabase() {
        try (Connection connection = factory.openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT peso_actual FROM bobina WHERE id_bobina = ?")) {
            statement.setInt(1, spoolId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Bobina de prueba no encontrada.");
                return rs.getBigDecimal(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo consultar el stock temporal.", e);
        }
    }

    int consumptionCount(PrintJob job) {
        try (Connection connection = factory.openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM consumo_filamento WHERE id_trabajo = ?")) {
            statement.setInt(1, job.getId());
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron contar consumos temporales.", e);
        }
    }

    boolean clean() {
        try (Connection connection = factory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                for (Integer id : jobIds) {
                    delete(connection, "DELETE FROM consumo_filamento WHERE id_trabajo = ?", id);
                    delete(connection, "DELETE FROM trabajo_impresion WHERE id_trabajo = ?", id);
                }
                if (spoolId != null) {
                    delete(connection, "DELETE FROM bobina WHERE id_bobina = ?", spoolId);
                }
                if (materialId != null) {
                    delete(connection, "DELETE FROM material WHERE id_material = ?", materialId);
                }
                connection.commit();
                System.out.println("Limpieza: consumos, trabajos, bobina y material temporales eliminados.");
                return true;
            } catch (SQLException | RuntimeException e) {
                try { connection.rollback(); }
                catch (SQLException rollbackError) { e.addSuppressed(rollbackError); }
                report(e);
                return false;
            }
        } catch (SQLException | RuntimeException e) {
            report(e);
            return false;
        }
    }

    private void report(Exception e) {
        System.err.println("Limpieza incompleta: " + e.getClass().getSimpleName()
                + ". IDs: trabajos=" + jobIds + ", bobina=" + spoolId
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
