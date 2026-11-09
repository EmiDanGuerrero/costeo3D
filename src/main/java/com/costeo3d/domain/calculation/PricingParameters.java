package com.costeo3d.domain.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/** Parámetros de costeo local. No representa importes históricos de presupuestos. */
public record PricingParameters(
        BigDecimal electricityRatePerKwh,
        BigDecimal averagePrinterWatts,
        BigDecimal printerReplacementValue,
        Integer printerUsefulLifeHours,
        BigDecimal annualMaintenanceCost,
        Integer annualPrintHours,
        BigDecimal defaultMarginPercentage,
        BigDecimal keychainMarginPercentage,
        BigDecimal largeMarginPercentage,
        BigDecimal keychainMinimumUnitPrice) {

    /** Compatibilidad con las llamadas anteriores de siete argumentos. */
    public PricingParameters(BigDecimal electricityRatePerKwh, BigDecimal averagePrinterWatts,
            BigDecimal printerReplacementValue, Integer printerUsefulLifeHours,
            BigDecimal annualMaintenanceCost, Integer annualPrintHours,
            BigDecimal defaultMarginPercentage) {
        this(electricityRatePerKwh, averagePrinterWatts, printerReplacementValue,
                printerUsefulLifeHours, annualMaintenanceCost, annualPrintHours,
                defaultMarginPercentage, null, null, null);
    }

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);

    public PricingParameters {
        positive(electricityRatePerKwh, "Tarifa eléctrica");
        positive(averagePrinterWatts, "Potencia promedio");
        positive(printerReplacementValue, "Valor de la impresora");
        nonNegative(annualMaintenanceCost, "Mantenimiento anual");
        if (printerUsefulLifeHours != null && printerUsefulLifeHours <= 0) {
            throw new IllegalArgumentException("La vida útil debe expresarse en horas positivas.");
        }
        if (annualPrintHours != null && annualPrintHours <= 0) {
            throw new IllegalArgumentException("Las horas anuales deben ser positivas.");
        }
        if (defaultMarginPercentage != null) {
            nonNegative(defaultMarginPercentage, "Margen predeterminado");
        }
        if (keychainMarginPercentage != null) {
            nonNegative(keychainMarginPercentage, "Margen para llaveros");
        }
        if (largeMarginPercentage != null) {
            nonNegative(largeMarginPercentage, "Margen para impresiones grandes");
        }
        if (keychainMinimumUnitPrice != null) {
            nonNegative(keychainMinimumUnitPrice, "Precio mínimo de llavero");
        }
    }

    public static PricingParameters initialValues() {
        // Datos del usuario; vida útil 8.000 h y uso anual 4.380 h son estimaciones
        // configurables, NO especificaciones oficiales de Bambu Lab.
        return new PricingParameters(new BigDecimal("309"), new BigDecimal("100"),
                new BigDecimal("1200000"), 8000, new BigDecimal("300000"),
                4380, null, null, null, null);
    }

    /** Normal usa el margen general; Llavero y Grande lo heredan si no tienen uno propio. */
    public Optional<BigDecimal> marginFor(PrintProfile profile) {
        Objects.requireNonNull(profile, "Perfil de impresión");
        BigDecimal specialized = switch (profile) {
            case LLAVERO -> keychainMarginPercentage;
            case NORMAL -> null;
            case GRANDE -> largeMarginPercentage;
        };
        return Optional.ofNullable(specialized != null ? specialized : defaultMarginPercentage);
    }

    /** Solo Llavero admite un mínimo; no modifica la fórmula de CostCalculator. */
    public BigDecimal applyMinimum(PrintProfile profile, BigDecimal calculatedUnitPrice) {
        Objects.requireNonNull(profile, "Perfil de impresión");
        Objects.requireNonNull(calculatedUnitPrice, "Precio calculado");
        if (profile == PrintProfile.LLAVERO && keychainMinimumUnitPrice != null) {
            return calculatedUnitPrice.max(keychainMinimumUnitPrice.setScale(2, RoundingMode.HALF_UP));
        }
        return calculatedUnitPrice;
    }

    public BigDecimal electricityCostPerHour() {
        return averagePrinterWatts.multiply(electricityRatePerKwh)
                .divide(THOUSAND, 2, RoundingMode.HALF_UP);
    }

    public Optional<BigDecimal> depreciationCostPerHour() {
        if (printerUsefulLifeHours == null) return Optional.empty();
        return Optional.of(printerReplacementValue.divide(
                BigDecimal.valueOf(printerUsefulLifeHours), 2, RoundingMode.HALF_UP));
    }

    public Optional<BigDecimal> maintenanceCostPerHour() {
        if (annualPrintHours == null) return Optional.empty();
        return Optional.of(annualMaintenanceCost.divide(
                BigDecimal.valueOf(annualPrintHours), 2, RoundingMode.HALF_UP));
    }

    public Optional<BigDecimal> combinedHourlyCost() {
        if (printerUsefulLifeHours == null || annualPrintHours == null) {
            return Optional.empty();
        }
        return Optional.of(electricityCostPerHour()
                .add(depreciationCostPerHour().orElseThrow())
                .add(maintenanceCostPerHour().orElseThrow()));
    }

    /** Cálculos por trabajo sin redondeo horario intermedio. */
    public BigDecimal electricityCostForMinutes(int minutes) {
        requireMinutes(minutes);
        return averagePrinterWatts.multiply(electricityRatePerKwh)
                .multiply(BigDecimal.valueOf(minutes))
                .divide(THOUSAND.multiply(SIXTY), 2, RoundingMode.HALF_UP);
    }

    /** Costo de equipo y mantenimiento por trabajo. Vacío hasta completar ambas horas. */
    public Optional<BigDecimal> equipmentCostForMinutes(int minutes) {
        requireMinutes(minutes);
        if (printerUsefulLifeHours == null || annualPrintHours == null) {
            return Optional.empty();
        }
        BigDecimal hours = BigDecimal.valueOf(minutes).divide(SIXTY, 12, RoundingMode.HALF_UP);
        BigDecimal depreciation = printerReplacementValue
                .divide(BigDecimal.valueOf(printerUsefulLifeHours), 12, RoundingMode.HALF_UP)
                .multiply(hours);
        BigDecimal maintenance = annualMaintenanceCost
                .divide(BigDecimal.valueOf(annualPrintHours), 12, RoundingMode.HALF_UP)
                .multiply(hours);
        return Optional.of(depreciation.add(maintenance).setScale(2, RoundingMode.HALF_UP));
    }

    private static void requireMinutes(int minutes) {
        if (minutes <= 0) throw new IllegalArgumentException("Los minutos deben ser positivos.");
    }
    private static void positive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() <= 0) throw new IllegalArgumentException(name + ": debe ser positivo.");
    }
    private static void nonNegative(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() < 0) throw new IllegalArgumentException(name + ": no puede ser negativo.");
    }
}
