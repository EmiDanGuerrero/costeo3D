package com.costeo3d.infrastructure.persistence.jdbc;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.TreeMap;

import com.costeo3d.domain.exception.BusinessValidationException;
import com.costeo3d.domain.exception.InsufficientStockException;
import com.costeo3d.domain.model.FilamentConsumption;

/** Operaciones de stock que participan en una transaccion externa. */
final class JdbcProductionStock {

    private static final String LOCK_SQL =
            "SELECT peso_actual FROM bobina WHERE id_bobina = ? FOR UPDATE";
    private static final String UPDATE_SQL =
            "UPDATE bobina SET peso_actual = ? WHERE id_bobina = ?";

    private JdbcProductionStock() { }

    static Map<Integer, BigDecimal> prepare(int jobId,
            List<FilamentConsumption> consumptions) throws BusinessValidationException {
        if (consumptions == null || consumptions.isEmpty()) {
            throw new BusinessValidationException("At least one consumption is required.");
        }
        Map<Integer, BigDecimal> required = new LinkedHashMap<>();
        for (FilamentConsumption consumption : consumptions) {
            if (consumption == null || consumption.getId() != null
                    || consumption.getPrintJob() == null
                    || !Objects.equals(jobId, consumption.getPrintJob().getId())
                    || consumption.getSpool() == null
                    || consumption.getSpool().getId() == null
                    || consumption.getSpool().getId() <= 0) {
                throw new BusinessValidationException("Invalid consumption references.");
            }
            BigDecimal grams = consumption.getConsumedGrams();
            if (grams == null || grams.signum() < 0
                    || grams.stripTrailingZeros().scale() > 2
                    || (consumption.getPlannedGrams() != null
                        && consumption.getPlannedGrams().stripTrailingZeros().scale() > 2)) {
                throw new BusinessValidationException(
                        "Consumption grams must be nonnegative with at most two decimals.");
            }
            required.merge(consumption.getSpool().getId(), grams, BigDecimal::add);
        }
        return required;
    }

    /** Bloquea todas las bobinas antes de actualizar alguna de ellas. */
    static Map<Integer, BigDecimal> discount(Connection connection,
            Map<Integer, BigDecimal> requiredBySpool)
            throws SQLException, InsufficientStockException {

        Map<Integer, BigDecimal> remaining = new LinkedHashMap<>();
        // Orden fijo: evita bloqueos cruzados entre transacciones concurrentes.
        Map<Integer, BigDecimal> ordered = new TreeMap<>(requiredBySpool);

        try (PreparedStatement lock = connection.prepareStatement(LOCK_SQL)) {
            for (Map.Entry<Integer, BigDecimal> entry : ordered.entrySet()) {
                lock.setInt(1, entry.getKey());
                try (ResultSet rows = lock.executeQuery()) {
                    if (!rows.next()) {
                        throw new SQLException("A referenced spool does not exist.");
                    }
                    BigDecimal current = rows.getBigDecimal("peso_actual");
                    if (current.compareTo(entry.getValue()) < 0) {
                        throw new InsufficientStockException(
                                "The spool does not have enough filament.");
                    }
                    remaining.put(entry.getKey(), current.subtract(entry.getValue()));
                }
            }
        }

        try (PreparedStatement update = connection.prepareStatement(UPDATE_SQL)) {
            for (Map.Entry<Integer, BigDecimal> entry : remaining.entrySet()) {
                update.setBigDecimal(1, entry.getValue());
                update.setInt(2, entry.getKey());
                if (update.executeUpdate() != 1) {
                    throw new SQLException("Cannot update spool stock.");
                }
            }
        }
        return remaining;
    }
}
