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
        this.brand = brand;
        this.type = type;
        this.finish = finish;
        this.costPerGram = costPerGram;
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
        this.brand = brand;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setFinish(String finish) {
        this.finish = finish;
    }

    public void updateCostPerGram(BigDecimal costPerGram) {
        this.costPerGram = costPerGram;
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }
}