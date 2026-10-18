package com.costeo3d.domain.model;

import java.math.BigDecimal;

public class Spool {

    private Integer id;
    private Material material;
    private String color;
    private BigDecimal initialWeight;
    private BigDecimal currentWeight;
    private boolean active;

    public Spool(Material material, String color, BigDecimal initialWeight) {
        this(null, material, color, initialWeight, initialWeight, true);
    }

    public Spool(Integer id, Material material, String color,
            BigDecimal initialWeight, BigDecimal currentWeight, boolean active) {
        this.id = id;
        this.material = material;
        this.color = color;
        this.initialWeight = initialWeight;
        this.currentWeight = currentWeight;
        this.active = active;
    }

    public Integer getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    public String getColor() {
        return color;
    }

    public BigDecimal getInitialWeight() {
        return initialWeight;
    }

    public BigDecimal getCurrentWeight() {
        return currentWeight;
    }

    public boolean isActive() {
        return active;
    }

    public void setMaterial(Material material) {
        this.material = material;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public void updateCurrentWeight(BigDecimal currentWeight) {
        this.currentWeight = currentWeight;
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }
}