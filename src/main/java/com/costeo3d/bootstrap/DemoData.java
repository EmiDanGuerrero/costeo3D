package com.costeo3d.bootstrap;

import java.util.List;

import com.costeo3d.domain.model.Customer;
import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.Model3D;
import com.costeo3d.domain.model.Quote;
import com.costeo3d.domain.model.Spool;
import com.costeo3d.domain.model.Order;
import com.costeo3d.domain.model.FilamentConsumption;
import com.costeo3d.domain.model.EstimatedWaste;

public class DemoData {

    private final List<Material> materials;
    private final List<Spool> spools;
    private final List<Model3D> models;
    private final List<Customer> customers;
    private final List<Quote> quotes;
    private final List<Order> orders;
    private final List<FilamentConsumption> consumptions;
    private final List<EstimatedWaste> wasteEstimates;

    public DemoData(
            List<Material> materials,
            List<Spool> spools,
            List<Model3D> models,
            List<Customer> customers,
            List<Quote> quotes,
            List<Order> orders,
            List<FilamentConsumption> consumptions,
            List<EstimatedWaste> wasteEstimates) {

        this.materials = materials;
        this.spools = spools;
        this.models = models;
        this.customers = customers;
        this.quotes = quotes;
        this.orders = orders;
        this.consumptions = consumptions;
        this.wasteEstimates = wasteEstimates;
    }

    public List<Material> getMaterials() {
        return materials;
    }

    public List<Spool> getSpools() {
        return spools;
    }

    public List<Model3D> getModels() {
        return models;
    }

    public List<Customer> getCustomers() {
        return customers;
    }

    public List<Quote> getQuotes() {
        return quotes;
    }
    
    public List<Order> getOrders() {
        return orders;
    }
    
    public List<FilamentConsumption> getConsumptions() {
        return consumptions;
    }
    
    public List<EstimatedWaste> getWasteEstimates() {
        return wasteEstimates;
    }
}