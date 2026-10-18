package com.costeo3d.domain.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class QuoteItem {

    private Integer id;
    private Model3D model;
    private String description;
    private int quantity;
    private int estimatedPrintTimeMinutes;
    private BigDecimal electricityCost;
    private BigDecimal amortizationCost;
    private BigDecimal marginPercentage;
    private BigDecimal unitPrice;

    private final List<QuoteMaterialItem> materials;

    public QuoteItem(Model3D model, String description, int quantity,
            int estimatedPrintTimeMinutes, BigDecimal electricityCost,
            BigDecimal amortizationCost, BigDecimal marginPercentage,
            BigDecimal unitPrice) {

        this(null, model, description, quantity, estimatedPrintTimeMinutes,
                electricityCost, amortizationCost, marginPercentage, unitPrice);
    }

    public QuoteItem(Integer id, Model3D model, String description, int quantity,
            int estimatedPrintTimeMinutes, BigDecimal electricityCost,
            BigDecimal amortizationCost, BigDecimal marginPercentage,
            BigDecimal unitPrice) {

        this.id = id;
        this.model = model;
        this.description = description;
        this.quantity = quantity;
        this.estimatedPrintTimeMinutes = estimatedPrintTimeMinutes;
        this.electricityCost = electricityCost;
        this.amortizationCost = amortizationCost;
        this.marginPercentage = marginPercentage;
        this.unitPrice = unitPrice;
        this.materials = new ArrayList<>();
    }

    public Integer getId() {
        return id;
    }

    public Model3D getModel() {
        return model;
    }

    public String getDescription() {
        return description;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getEstimatedPrintTimeMinutes() {
        return estimatedPrintTimeMinutes;
    }

    public BigDecimal getElectricityCost() {
        return electricityCost;
    }

    public BigDecimal getAmortizationCost() {
        return amortizationCost;
    }

    public BigDecimal getMarginPercentage() {
        return marginPercentage;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public List<QuoteMaterialItem> getMaterials() {
        return List.copyOf(materials);
    }

    public void addMaterial(QuoteMaterialItem materialItem) {
        materials.add(materialItem);
    }

    public void setModel(Model3D model) {
        this.model = model;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setEstimatedPrintTimeMinutes(int estimatedPrintTimeMinutes) {
        this.estimatedPrintTimeMinutes = estimatedPrintTimeMinutes;
    }

    public void setElectricityCost(BigDecimal electricityCost) {
        this.electricityCost = electricityCost;
    }

    public void setAmortizationCost(BigDecimal amortizationCost) {
        this.amortizationCost = amortizationCost;
    }

    public void setMarginPercentage(BigDecimal marginPercentage) {
        this.marginPercentage = marginPercentage;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }
}