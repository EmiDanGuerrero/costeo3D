package com.costeo3d.integration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;

/** Limpieza exclusivamente de registros creados por la prueba de E7.3. */
final class JdbcQuoteSmokeCleanup {

    private JdbcQuoteSmokeCleanup() { }

    static boolean clean(JdbcConnectionFactory factory, Integer customerId,
            Integer materialId, Integer modelId) {
        try (Connection connection = factory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                if (customerId != null) {
                    delete(connection, """
                            DELETE dm FROM detalle_material_presupuesto dm
                            JOIN detalle_presupuesto d ON d.id_detalle = dm.id_detalle
                            JOIN presupuesto p ON p.id_presupuesto = d.id_presupuesto
                            WHERE p.id_cliente = ?
                            """, customerId);
                    delete(connection, """
                            DELETE d FROM detalle_presupuesto d
                            JOIN presupuesto p ON p.id_presupuesto = d.id_presupuesto
                            WHERE p.id_cliente = ?
                            """, customerId);
                    delete(connection,
                            "DELETE FROM presupuesto WHERE id_cliente = ?", customerId);
                }
                if (modelId != null) {
                    delete(connection,
                            "DELETE FROM modelo_3d WHERE id_modelo = ?", modelId);
                }
                if (materialId != null) {
                    delete(connection,
                            "DELETE FROM material WHERE id_material = ?", materialId);
                }
                if (customerId != null) {
                    delete(connection,
                            "DELETE FROM cliente WHERE id_cliente = ?", customerId);
                }
                connection.commit();
                System.out.println("Limpieza: registros temporales eliminados.");
                return true;
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                System.err.println("Fallo la limpieza de E7.3. IDs temporales: cliente="
                        + customerId + ", material=" + materialId
                        + ", modelo=" + modelId);
                return false;
            }
        } catch (SQLException | RuntimeException failure) {
            System.err.println("No se pudo iniciar la limpieza de E7.3.");
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
