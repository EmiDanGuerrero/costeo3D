package com.costeo3d.application.service;

import java.math.BigDecimal;
import java.util.Objects;

import com.costeo3d.application.port.HomeDashboardReader;

/** Orquesta los indicadores de Inicio sin exponer JDBC a JavaFX. */
public final class HomeDashboardService {

    // Criterio inicial: se considera stock bajo una bobina activa con <= 200 g.
    public static final BigDecimal LOW_STOCK_GRAMS = new BigDecimal("200.00");
    public static final int MAX_ALERTS = 5;

    private final HomeDashboardReader reader;

    public HomeDashboardService(HomeDashboardReader reader) {
        this.reader = Objects.requireNonNull(reader, "Se requiere el lector del panel.");
    }

    public HomeDashboardSnapshot snapshot() {
        return reader.load(LOW_STOCK_GRAMS, MAX_ALERTS);
    }
}
