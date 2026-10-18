package com.costeo3d.domain.model;

import java.math.BigDecimal;

public class QuoteMaterialItem {

    private Integer id;
    private Material material;
    private BigDecimal estimatedGrams;
    private BigDecimal appliedCostPerGram;

    public QuoteMaterialItem(Material material, BigDecimal estimatedGrams,
            BigDecimal appliedCostPerGram) {
        this(null, material, estimatedGrams, appliedCostPerGram);
    }

    public QuoteMaterialItem(Integer id, Material material,
            BigDecimal estimatedGrams, BigDecimal appliedCostPerGram) {
        this.id = id;
        this.material = material;
        this.estimatedGrams = estimatedGrams;
        this.appliedCostPerGram = appliedCostPerGram;
    }

    public Integer getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    public BigDecimal getEstimatedGrams() {
        return estimatedGrams;
    }

    public BigDecimal getAppliedCostPerGram() {
        return appliedCostPerGram;
    }

    public void setMaterial(Material material) {
        this.material = material;
    }

    public void setEstimatedGrams(BigDecimal estimatedGrams) {
        this.estimatedGrams = estimatedGrams;
    }

    public void setAppliedCostPerGram(BigDecimal appliedCostPerGram) {
        this.appliedCostPerGram = appliedCostPerGram;
    }
}