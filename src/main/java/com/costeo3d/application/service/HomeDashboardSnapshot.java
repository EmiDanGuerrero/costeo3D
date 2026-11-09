package com.costeo3d.application.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Valores ya agregados para Inicio; ninguna entidad puede modificarse desde esta vista. */
public record HomeDashboardSnapshot(
        int activeOrders,
        int jobsInProgress,
        int lowStockSpools,
        int failedJobs,
        BigDecimal wastedGrams,
        BigDecimal lowStockThreshold,
        List<SpoolAlert> spoolAlerts) {

    public HomeDashboardSnapshot {
        if (activeOrders < 0 || jobsInProgress < 0 || lowStockSpools < 0 || failedJobs < 0) {
            throw new IllegalArgumentException("Los contadores no pueden ser negativos.");
        }
        Objects.requireNonNull(wastedGrams, "Se requiere el total de mermas.");
        Objects.requireNonNull(lowStockThreshold, "Se requiere el umbral de stock.");
        if (wastedGrams.signum() < 0 || lowStockThreshold.signum() < 0) {
            throw new IllegalArgumentException("Los gramos no pueden ser negativos.");
        }
        spoolAlerts = List.copyOf(Objects.requireNonNull(spoolAlerts, "Se requieren las alertas."));
        if (spoolAlerts.size() > lowStockSpools) {
            throw new IllegalArgumentException("La lista no puede superar el total de alertas.");
        }
    }

    public record SpoolAlert(int id, String material, String color, BigDecimal availableGrams) {
        public SpoolAlert {
            if (id <= 0 || Objects.requireNonNull(material).isBlank()
                    || Objects.requireNonNull(color).isBlank()
                    || Objects.requireNonNull(availableGrams).signum() < 0) {
                throw new IllegalArgumentException("Alerta de bobina inválida.");
            }
        }
    }
}
