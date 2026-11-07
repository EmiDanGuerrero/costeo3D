package com.costeo3d.bootstrap;

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

/** Datos exclusivos de las pruebas E7.5; no toca registros ajenos. */
final class JdbcProductionTransactionSmokeData {
    private final JdbcConnectionFactory factory;
    private final List<Integer> jobIds = new ArrayList<>();
    private Integer materialId, spoolId;
    private Spool spool;
    private PrintJob success, shortage, sqlFailure, failed;

    JdbcProductionTransactionSmokeData(JdbcConnectionFactory factory) {
        this.factory = factory;
    }

    Spool spool() { return spool; }
    PrintJob success() { return success; }
    PrintJob shortage() { return shortage; }
    PrintJob sqlFailure() { return sqlFailure; }
    PrintJob failed() { return failed; }

    void create() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        Material material = new JdbcMaterialDao(factory).insert(
                new Material("E7_STOCK_" + suffix, "PLA", "Mate",
                        new BigDecimal("0.5000")));
        materialId = material.getId();
        spool = new JdbcSpoolDao(factory).insert(
                new Spool(material, "Verde", new BigDecimal("500.00")));
        spoolId = spool.getId();

        LocalDateTime created = LocalDateTime.now().withNano(0);
        success = newJob(created);
        shortage = newJob(created.plusMinutes(2));
        sqlFailure = newJob(created.plusMinutes(4));
        failed = newJob(created.plusMinutes(6));
    }

    private PrintJob newJob(LocalDateTime created) throws Exception {
        PrintJob pending = new PrintJob(null, null, created, 60);
        pending.start(created.plusMinutes(1));
        PrintJob saved = new JdbcPrintJobDao(factory).insert(pending);
        jobIds.add(saved.getId());
        return saved;
    }

    BigDecimal dbWeight() {
        try (Connection c = factory.openConnection();
                PreparedStatement p = c.prepareStatement(
                        "SELECT peso_actual FROM bobina WHERE id_bobina = ?")) {
            p.setInt(1, spoolId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new IllegalStateException("No existe la bobina temporal.");
                return r.getBigDecimal(1);
            }
        } catch (SQLException e) { throw new IllegalStateException("No se pudo leer stock.", e); }
    }

    PrintJob dbJob(PrintJob job) {
        return new JdbcPrintJobDao(factory).findById(job.getId()).orElseThrow();
    }

    int countConsumptions(PrintJob job) {
        try (Connection c = factory.openConnection();
                PreparedStatement p = c.prepareStatement(
                        "SELECT COUNT(*) FROM consumo_filamento WHERE id_trabajo = ?")) {
            p.setInt(1, job.getId());
            try (ResultSet r = p.executeQuery()) { r.next(); return r.getInt(1); }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron contar los consumos.", e);
        }
    }

    boolean clean() {
        try (Connection c = factory.openConnection()) {
            c.setAutoCommit(false);
            try {
                for (Integer id : jobIds) {
                    delete(c, "DELETE FROM consumo_filamento WHERE id_trabajo = ?", id);
                    delete(c, "DELETE FROM trabajo_impresion WHERE id_trabajo = ?", id);
                }
                if (spoolId != null)
                    delete(c, "DELETE FROM bobina WHERE id_bobina = ?", spoolId);
                if (materialId != null)
                    delete(c, "DELETE FROM material WHERE id_material = ?", materialId);
                c.commit();
                System.out.println("Limpieza: trabajos, consumos, bobina y material temporales eliminados.");
                return true;
            } catch (SQLException | RuntimeException e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                report(e);
                return false;
            }
        } catch (SQLException | RuntimeException e) { report(e); return false; }
    }

    private void report(Exception e) {
        System.err.println("Error de limpieza: " + e.getClass().getSimpleName()
                + ". IDs de prueba: trabajos=" + jobIds
                + ", bobina=" + spoolId + ", material=" + materialId);
    }

    private static void delete(Connection c, String sql, int id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, id);
            p.executeUpdate();
        }
    }
}
