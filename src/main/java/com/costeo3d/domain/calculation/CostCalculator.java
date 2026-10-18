package com.costeo3d.domain.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import com.costeo3d.domain.contracts.Quotable;
import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.validation.ValidationUtils;

public class CostCalculator {

    private static final int MONEY_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public CalculationResult calculate(
            Quotable quotable,
            Material material,
            BigDecimal electricityCost,
            BigDecimal amortizationCost,
            BigDecimal marginPercentage) {

        validateQuotable(quotable);
        ValidationUtils.requireNotNull(material, "Material");
        ValidationUtils.requireNonNegative(
                material.getCostPerGram(), "Material cost per gram");
        validateCosts(electricityCost, amortizationCost, marginPercentage);

        BigDecimal filamentCost = quotable.getEstimatedGrams()
                .multiply(material.getCostPerGram());

        return buildResult(
                filamentCost,
                electricityCost,
                amortizationCost,
                marginPercentage);
    }

    public CalculationResult calculate(
            Quotable quotable,
            Map<Material, BigDecimal> materialUsage,
            BigDecimal electricityCost,
            BigDecimal amortizationCost,
            BigDecimal marginPercentage) {

        validateQuotable(quotable);
        validateMaterialUsage(materialUsage);
        validateCosts(electricityCost, amortizationCost, marginPercentage);

        BigDecimal filamentCost = calculateFilamentCost(materialUsage);

        return buildResult(
                filamentCost,
                electricityCost,
                amortizationCost,
                marginPercentage);
    }

    private void validateQuotable(Quotable quotable) {
        ValidationUtils.requireNotNull(quotable, "Quotable");
        ValidationUtils.requirePositive(
                quotable.getEstimatedGrams(), "Estimated grams");
        ValidationUtils.requirePositive(
                quotable.getEstimatedPrintTimeMinutes(),
                "Estimated print time");
    }

    private void validateMaterialUsage(
            Map<Material, BigDecimal> materialUsage) {

        ValidationUtils.requireNotNull(materialUsage, "Material usage");

        if (materialUsage.isEmpty()) {
            throw new IllegalArgumentException(
                    "Material usage cannot be empty.");
        }

        for (Map.Entry<Material, BigDecimal> entry : materialUsage.entrySet()) {
            Material material = ValidationUtils.requireNotNull(
                    entry.getKey(), "Material");

            ValidationUtils.requirePositive(
                    entry.getValue(), "Material grams");

            ValidationUtils.requireNonNegative(
                    material.getCostPerGram(),
                    "Material cost per gram");
        }
    }

    private void validateCosts(
            BigDecimal electricityCost,
            BigDecimal amortizationCost,
            BigDecimal marginPercentage) {

        ValidationUtils.requireNonNegative(
                electricityCost, "Electricity cost");

        ValidationUtils.requireNonNegative(
                amortizationCost, "Amortization cost");

        ValidationUtils.requireNonNegative(
                marginPercentage, "Margin percentage");
    }

    private BigDecimal calculateFilamentCost(
            Map<Material, BigDecimal> materialUsage) {

        BigDecimal total = BigDecimal.ZERO;

        for (Map.Entry<Material, BigDecimal> entry : materialUsage.entrySet()) {
            Material material = entry.getKey();
            BigDecimal grams = entry.getValue();

            total = total.add(
                    grams.multiply(material.getCostPerGram()));
        }

        return total;
    }

    private CalculationResult buildResult(
            BigDecimal filamentCost,
            BigDecimal electricityCost,
            BigDecimal amortizationCost,
            BigDecimal marginPercentage) {

        BigDecimal totalCost = filamentCost
                .add(electricityCost)
                .add(amortizationCost);

        BigDecimal marginAmount = totalCost
                .multiply(marginPercentage)
                .divide(ONE_HUNDRED);

        BigDecimal finalPrice = totalCost.add(marginAmount);

        return new CalculationResult(
                toMoney(filamentCost),
                toMoney(electricityCost),
                toMoney(amortizationCost),
                toMoney(totalCost),
                marginPercentage,
                toMoney(marginAmount),
                toMoney(finalPrice));
    }

    private BigDecimal toMoney(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}