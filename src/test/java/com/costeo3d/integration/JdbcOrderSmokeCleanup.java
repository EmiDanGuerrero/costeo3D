package com.costeo3d.integration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;

/** Elimina solo los registros generados durante JdbcOrderDaoSmokeTest. */
final class JdbcOrderSmokeCleanup {

    private JdbcOrderSmokeCleanup() { }

    static boolean clean(JdbcConnectionFactory factory, Integer quoteId,
            Integer customerId, Integer materialId) {
        try (Connection connection = factory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                if (quoteId != null) {
                    delete(connection, "DELETE FROM pedido WHERE id_presupuesto = ?", quoteId);
                    delete(connection, """
                            DELETE dm FROM detalle_material_presupuesto dm
                            JOIN detalle_presupuesto d ON d.id_detalle = dm.id_detalle
                            WHERE d.id_presupuesto = ?
                            """, quoteId);
                    delete(connection, "DELETE FROM detalle_presupuesto WHERE id_presupuesto = ?",
                            quoteId);
                    delete(connection, "DELETE FROM presupuesto WHERE id_presupuesto = ?",
                            quoteId);
                }
                if (materialId != null) {
                    delete(connection, "DELETE FROM material WHERE id_material = ?", materialId);
                }
                if (customerId != null) {
                    delete(connection, "DELETE FROM cliente WHERE id_cliente = ?", customerId);
                }
                connection.commit();
                System.out.println("Limpieza: pedido, presupuesto y catalogos temporales eliminados.");
                return true;
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                System.err.println("Fallo la limpieza de E7.4.1. IDs: presupuesto="
                        + quoteId + ", cliente=" + customerId + ", material=" + materialId);
                return false;
            }
        } catch (SQLException | RuntimeException failure) {
            System.err.println("No se pudo iniciar la limpieza de E7.4.1. IDs: presupuesto="
                    + quoteId + ", cliente=" + customerId + ", material=" + materialId);
            return false;
        }
    }

    private static void delete(Connection connection, String sql, int id)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }
}
