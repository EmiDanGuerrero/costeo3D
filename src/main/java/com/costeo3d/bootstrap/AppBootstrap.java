package com.costeo3d.bootstrap;

import com.costeo3d.application.port.ProductionFinalizationPort;
import com.costeo3d.application.service.CatalogService;
import com.costeo3d.application.service.OrderService;
import com.costeo3d.application.service.PersistentOrderService;
import com.costeo3d.application.service.PersistentProductionService;
import com.costeo3d.application.service.PersistentQuoteService;
import com.costeo3d.application.service.PersistentWasteEstimator;
import com.costeo3d.application.service.ProductionService;
import com.costeo3d.application.service.QuoteService;
import com.costeo3d.application.service.WasteEstimator;
import com.costeo3d.infrastructure.config.DatabaseConfig;
import com.costeo3d.infrastructure.logging.AppLogging;
import com.costeo3d.infrastructure.persistence.dao.CustomerDao;
import com.costeo3d.infrastructure.persistence.dao.EstimatedWasteDao;
import com.costeo3d.infrastructure.persistence.dao.MaterialDao;
import com.costeo3d.infrastructure.persistence.dao.Model3DDao;
import com.costeo3d.infrastructure.persistence.dao.OrderDao;
import com.costeo3d.infrastructure.persistence.dao.PrintJobDao;
import com.costeo3d.infrastructure.persistence.dao.QuoteDao;
import com.costeo3d.infrastructure.persistence.dao.SpoolDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcConnectionFactory;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcCustomerDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcEstimatedWasteDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcMaterialDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcModel3DDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcOrderDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcPrintJobDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcProductionFinalizationAdapter;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcProductionTransaction;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcQuoteDao;
import com.costeo3d.infrastructure.persistence.jdbc.JdbcSpoolDao;

/**
 * Punto de composición de la aplicación de escritorio.
 * Crea una única configuración y comparte una fábrica de conexiones entre DAO.
 */
public final class AppBootstrap {

    private AppBootstrap() {
    }

    public static AppServices createServices() {
        AppLogging.configure();
        JdbcConnectionFactory factory = new JdbcConnectionFactory(DatabaseConfig.load());

        MaterialDao materials = new JdbcMaterialDao(factory);
        SpoolDao spools = new JdbcSpoolDao(factory);
        Model3DDao models = new JdbcModel3DDao(factory);
        CustomerDao customers = new JdbcCustomerDao(factory);
        QuoteDao quoteDao = new JdbcQuoteDao(factory);
        OrderDao orderDao = new JdbcOrderDao(factory);
        PrintJobDao printJobDao = new JdbcPrintJobDao(factory);
        EstimatedWasteDao wasteDao = new JdbcEstimatedWasteDao(factory);

        CatalogService catalogs = new CatalogService(materials, spools, models, customers);
        PersistentQuoteService quotes = new PersistentQuoteService(
                new QuoteService(), quoteDao);
        PersistentOrderService orders = new PersistentOrderService(
                new OrderService(), quoteDao, orderDao);

        ProductionFinalizationPort finalization = new JdbcProductionFinalizationAdapter(
                new JdbcProductionTransaction(factory));
        PersistentProductionService production = new PersistentProductionService(
                new ProductionService(), printJobDao, finalization);
        PersistentWasteEstimator waste = new PersistentWasteEstimator(
                new WasteEstimator(), printJobDao, wasteDao);

        return new AppServices(catalogs, quotes, orders, production, waste);
    }
}
