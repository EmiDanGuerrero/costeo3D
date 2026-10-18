package com.costeo3d.domain.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.Model3D;

class CostCalculatorTest {

    private final CostCalculator calculator = new CostCalculator();

    @Test
    void shouldCalculateFinalPrice() {
        Material material = new Material(
                "Elegoo",
                "PLA",
                "Matte",
                new BigDecimal("18.50"));

        Model3D model = new Model3D(
                "Geometric Planter",
                null,
                "Printables",
                new BigDecimal("125"),
                300);

        CalculationResult result = calculator.calculate(
                model,
                material,
                new BigDecimal("100"),
                new BigDecimal("200"),
                new BigDecimal("30"));

        assertEquals(
                new BigDecimal("2312.50"),
                result.getFilamentCost());

        assertEquals(
                new BigDecimal("2612.50"),
                result.getTotalCost());

        assertEquals(
                new BigDecimal("783.75"),
                result.getMarginAmount());

        assertEquals(
                new BigDecimal("3396.25"),
                result.getFinalPrice());
    }

    @Test
    void shouldRejectNegativeElectricityCost() {
        Material material = new Material(
                "Elegoo",
                "PLA",
                "Matte",
                new BigDecimal("18.50"));

        Model3D model = new Model3D(
                "Geometric Planter",
                null,
                "Printables",
                new BigDecimal("125"),
                300);

        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(
                        model,
                        material,
                        new BigDecimal("-10"),
                        new BigDecimal("200"),
                        new BigDecimal("30")));
    }
}