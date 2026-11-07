package com.costeo3d.domain.model;

import java.math.BigDecimal;

public class Material {

    private Integer id;
    private String brand;
    private String type;
    private String finish;
    private BigDecimal costPerGram;
    private boolean active;

    public Material(String brand, String type, String finish, BigDecimal costPerGram) {
        this(null, brand, type, finish, costPerGram, true);
    }

    public Material(Integer id, String brand, String type, String finish,
            BigDecimal costPerGram, boolean active) {
        this.id = id;
        setBrand(brand);
        setType(type);
        setFinish(finish);
        updateCostPerGram(costPerGram);
        this.active = active;
    }

    public Integer getId() {
        return id;
    }

    public String getBrand() {
        return brand;
    }

    public String getType() {
        return type;
    }

    public String getFinish() {
        return finish;
    }

    public BigDecimal getCostPerGram() {
        return costPerGram;
    }

    public boolean isActive() {
        return active;
    }

    public void setBrand(String brand) {
        this.brand = requireText(brand, "Brand");
    }

    public void setType(String type) {
        this.type = requireText(type, "Type");
    }

    public void setFinish(String finish) {
        this.finish = requireText(finish, "Finish");
    }

    public void updateCostPerGram(BigDecimal costPerGram) {
        if (costPerGram == null || costPerGram.signum() < 0) {
            throw new IllegalArgumentException(
                    "Cost per gram must be greater than or equal to zero.");
        }
        this.costPerGram = costPerGram;
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    private static String requireText(String value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " cannot be null.");
        }
        return value;
    }
}
