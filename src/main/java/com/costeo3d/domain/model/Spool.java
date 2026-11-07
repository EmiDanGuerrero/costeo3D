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
        if (initialWeight == null || initialWeight.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Initial weight must be greater than zero.");
        }

        this.id = id;
        setMaterial(material);
        setColor(color);
        this.initialWeight = initialWeight;
        updateCurrentWeight(currentWeight);
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
        if (material == null) {
            throw new IllegalArgumentException("Material cannot be null.");
        }
        this.material = material;
    }

    public void setColor(String color) {
        if (color == null) {
            throw new IllegalArgumentException("Color cannot be null.");
        }
        this.color = color;
    }

    public void updateCurrentWeight(BigDecimal currentWeight) {
        if (currentWeight == null
                || currentWeight.signum() < 0
                || currentWeight.compareTo(initialWeight) > 0) {
            throw new IllegalArgumentException(
                    "Current weight must be between zero and initial weight.");
        }

        this.currentWeight = currentWeight;
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }
}
