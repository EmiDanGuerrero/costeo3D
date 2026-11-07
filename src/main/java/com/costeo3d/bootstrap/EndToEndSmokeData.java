package com.costeo3d.bootstrap;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.costeo3d.application.service.CatalogService;
import com.costeo3d.domain.model.Customer;
import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.Model3D;
import com.costeo3d.domain.model.Quote;
import com.costeo3d.domain.model.Spool;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;

/** Datos exclusivos de .5. Nunca borra registros ajenos a esta ejecucion. */
final class EndToEndSmokeData {

    private final JdbcConnectionFactory factory;
    private final List<Integer> quoteIds = new ArrayList<>();
    private Customer customer;
    private Material material;
    private Model3D model;
    private Spool spool;

    EndToEndSmokeData(JdbcConnectionFactory factory) {
        this.factory = factory;
    }

    Customer customer() { return customer; }
    Material material() { return material; }
    Model3D model() { return model; }
    Spool spool() { return spool; }

    void createCatalogs(CatalogService catalogs) {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        customer = catalogs.createCustomer(new Customer(
                "5_CLIENTE_" + suffix, null, null));
        material = catalogs.createMaterial(new Material(
                "5_MATERIAL_" + suffix, "PLA", "Mate", new BigDecimal("0.5000")));
        model = catalogs.createModel3D(new Model3D(
                "5_MODELO_" + suffix, null, null, new BigDecimal("45.00"), 90));
        spool = catalogs.createSpool(new Spool(
                material, "5_COLOR_" + suffix, new BigDecimal("500.00")));
    }

    void trackQuote(Quote quote) {
        if (quote.getId() == null) {
            throw new IllegalStateException("El presupuesto de prueba no recibio ID.");
        }
        quoteIds.add(quote.getId());
    }

    /** Orden inverso de dependencias. Usa IDs de esta ejecucion, nunca TRUNCATE. */
    boolean clean() {
        try (Connection connection = factory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                for (Integer quoteId : quoteIds) {
                    delete(connection, """
                            DELETE FROM merma_estimada WHERE id_trabajo IN (
                              SELECT t.id_trabajo FROM trabajo_impresion t
                              JOIN pedido p ON p.id_pedido = t.id_pedido
                              WHERE p.id_presupuesto = ?)
                            """, quoteId);
                    delete(connection, """
                            DELETE FROM consumo_filamento WHERE id_trabajo IN (
                              SELECT t.id_trabajo FROM trabajo_impresion t
                              JOIN pedido p ON p.id_pedido = t.id_pedido
                              WHERE p.id_presupuesto = ?)
                            """, quoteId);
                    delete(connection, """
                            DELETE FROM trabajo_impresion WHERE id_pedido IN (
                              SELECT id_pedido FROM pedido WHERE id_presupuesto = ?)
                            """, quoteId);
                    delete(connection, "DELETE FROM pedido WHERE id_presupuesto = ?", quoteId);
                    delete(connection, """
                            DELETE FROM detalle_material_presupuesto WHERE id_detalle IN (
                              SELECT id_detalle FROM detalle_presupuesto
                              WHERE id_presupuesto = ?)
                            """, quoteId);
                    delete(connection, "DELETE FROM detalle_presupuesto WHERE id_presupuesto = ?",
                            quoteId);
                    delete(connection, "DELETE FROM presupuesto WHERE id_presupuesto = ?", quoteId);
                }
                if (spool != null) {
                    delete(connection, "DELETE FROM bobina WHERE id_bobina = ?", spool.getId());
                }
                if (model != null) {
                    delete(connection, "DELETE FROM modelo_3d WHERE id_modelo = ?", model.getId());
                }
                if (material != null) {
                    delete(connection, "DELETE FROM material WHERE id_material = ?", material.getId());
                }
                if (customer != null) {
                    delete(connection, "DELETE FROM cliente WHERE id_cliente = ?", customer.getId());
                }
                connection.commit();
                System.out.println("Limpieza: presupuestos, pedidos, trabajos, consumos, merma "
                        + "y catalogos temporales eliminados.");
                return true;
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                reportCleanupFailure(failure);
                return false;
            }
        } catch (SQLException | RuntimeException failure) {
            reportCleanupFailure(failure);
            return false;
        }
    }

    private void reportCleanupFailure(Exception failure) {
        System.err.println("Limpieza incompleta (" + failure.getClass().getSimpleName()
                + "). IDs: presupuestos=" + quoteIds
                + ", bobina=" + idOf(spool) + ", modelo=" + idOf(model)
                + ", material=" + idOf(material) + ", cliente=" + idOf(customer));
    }

    private Integer idOf(Object object) {
        if (object instanceof Spool x) return x.getId();
        if (object instanceof Model3D x) return x.getId();
        if (object instanceof Material x) return x.getId();
        if (object instanceof Customer x) return x.getId();
        return null;
    }

    private static void delete(Connection connection, String sql, Integer id)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }
}
