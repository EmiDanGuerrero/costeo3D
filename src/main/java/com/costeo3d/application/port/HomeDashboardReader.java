package com.costeo3d.application.port;

import java.math.BigDecimal;

import com.costeo3d.application.service.HomeDashboardSnapshot;

/** Consulta agregados de solo lectura para la pantalla Inicio. */
public interface HomeDashboardReader {
    HomeDashboardSnapshot load(BigDecimal lowStockThreshold, int maxAlerts);
}
