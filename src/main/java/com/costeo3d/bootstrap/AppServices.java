package com.costeo3d.bootstrap;

import java.util.Objects;

import com.costeo3d.application.service.CatalogService;
import com.costeo3d.application.service.HomeDashboardService;
import com.costeo3d.application.service.PersistentOrderService;
import com.costeo3d.application.service.PersistentProductionService;
import com.costeo3d.application.service.PersistentQuoteService;
import com.costeo3d.application.service.PersistentWasteEstimator;

/**
 * Servicios de aplicación disponibles para los controladores JavaFX.
 * No expone conexiones, DAO concretos ni detalles de infraestructura.
 */
public record AppServices(
        CatalogService catalogs,
        PersistentQuoteService quotes,
        PersistentOrderService orders,
        PersistentProductionService production,
        PersistentWasteEstimator waste,
        HomeDashboardService dashboard) {

    public AppServices {
        Objects.requireNonNull(catalogs, "CatalogService is required.");
        Objects.requireNonNull(quotes, "PersistentQuoteService is required.");
        Objects.requireNonNull(orders, "PersistentOrderService is required.");
        Objects.requireNonNull(production, "PersistentProductionService is required.");
        Objects.requireNonNull(waste, "PersistentWasteEstimator is required.");
        Objects.requireNonNull(dashboard, "HomeDashboardService is required.");
    }
}
