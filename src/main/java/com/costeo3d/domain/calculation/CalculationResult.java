package com.costeo3d.domain.calculation;

import java.math.BigDecimal;

public class CalculationResult {

    private final BigDecimal filamentCost;
    private final BigDecimal electricityCost;
    private final BigDecimal amortizationCost;
    private final BigDecimal totalCost;
    private final BigDecimal marginPercentage;
    private final BigDecimal marginAmount;
    private final BigDecimal finalPrice;

    public CalculationResult(BigDecimal filamentCost,
            BigDecimal electricityCost,
            BigDecimal amortizationCost,
            BigDecimal totalCost,
            BigDecimal marginPercentage,
            BigDecimal marginAmount,
            BigDecimal finalPrice) {

        this.filamentCost = filamentCost;
        this.electricityCost = electricityCost;
        this.amortizationCost = amortizationCost;
        this.totalCost = totalCost;
        this.marginPercentage = marginPercentage;
        this.marginAmount = marginAmount;
        this.finalPrice = finalPrice;
    }

    public BigDecimal getFilamentCost() {
        return filamentCost;
    }

    public BigDecimal getElectricityCost() {
        return electricityCost;
    }

    public BigDecimal getAmortizationCost() {
        return amortizationCost;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public BigDecimal getMarginPercentage() {
        return marginPercentage;
    }

    public BigDecimal getMarginAmount() {
        return marginAmount;
    }

    public BigDecimal getFinalPrice() {
        return finalPrice;
    }
}