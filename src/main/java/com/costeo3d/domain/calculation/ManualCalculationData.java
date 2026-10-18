package com.costeo3d.domain.calculation;

import java.math.BigDecimal;

import com.costeo3d.domain.contracts.Quotable;

public class ManualCalculationData implements Quotable {

    private String description;
    private BigDecimal estimatedGrams;
    private int estimatedPrintTimeMinutes;

    public ManualCalculationData(String description, BigDecimal estimatedGrams,
            int estimatedPrintTimeMinutes) {
        this.description = description;
        this.estimatedGrams = estimatedGrams;
        this.estimatedPrintTimeMinutes = estimatedPrintTimeMinutes;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public BigDecimal getEstimatedGrams() {
        return estimatedGrams;
    }

    @Override
    public int getEstimatedPrintTimeMinutes() {
        return estimatedPrintTimeMinutes;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setEstimatedGrams(BigDecimal estimatedGrams) {
        this.estimatedGrams = estimatedGrams;
    }

    public void setEstimatedPrintTimeMinutes(int estimatedPrintTimeMinutes) {
        this.estimatedPrintTimeMinutes = estimatedPrintTimeMinutes;
    }
}