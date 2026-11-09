package com.costeo3d.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * E11.4.4 - Calcula una estimacion de merma usando las capas del slicer.
 *
 * Los gramos aprobados se obtienen del presupuesto; esta clase nunca los
 * reemplaza ni los recalcula. La proporcion por capas es una aproximacion:
 * no presupone que el volumen impreso sea uniforme en altura.
 */
public final class LayerFailureEstimator {

    private LayerFailureEstimator() { }

    /**
     * @param approvedGrams gramos aprobados del presupuesto para este trabajo
     * @param totalLayers capas totales del slicer (debe ser > 0)
     * @param failedAtLayer capa alcanzada al fallar, entre 0 y totalLayers
     * @return merma estimada con dos decimales, sin superar approvedGrams
     */
    public static BigDecimal estimate(BigDecimal approvedGrams,
            int totalLayers, int failedAtLayer) {
        Objects.requireNonNull(approvedGrams, "Faltan los gramos aprobados.");
        if (approvedGrams.signum() < 0
                || approvedGrams.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(
                    "Los gramos aprobados deben ser no negativos y tener hasta dos decimales.");
        }
        if (totalLayers <= 0) {
            throw new IllegalArgumentException("Las capas totales deben ser mayores que cero.");
        }
        if (failedAtLayer < 0 || failedAtLayer > totalLayers) {
            throw new IllegalArgumentException(
                    "La capa de falla debe estar entre 0 y las capas totales.");
        }
        // Division por entero con escala fija; no se introducen gramos manuales.
        return approvedGrams.multiply(BigDecimal.valueOf(failedAtLayer))
                .divide(BigDecimal.valueOf(totalLayers), 2, RoundingMode.HALF_UP);
    }
}
