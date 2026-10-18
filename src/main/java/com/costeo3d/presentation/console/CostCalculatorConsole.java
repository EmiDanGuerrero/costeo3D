package com.costeo3d.presentation.console;

import java.math.BigDecimal;
import java.util.List;
import java.util.Scanner;

import com.costeo3d.bootstrap.DemoData;
import com.costeo3d.domain.calculation.CalculationResult;
import com.costeo3d.domain.calculation.CostCalculator;
import com.costeo3d.domain.calculation.ManualCalculationData;
import com.costeo3d.domain.contracts.Quotable;
import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.Model3D;

public class CostCalculatorConsole {

    private final DemoData data;
    private final ConsoleInput input;
    private final CostCalculator calculator;

    public CostCalculatorConsole(
            DemoData data,
            Scanner scanner) {

        this.data = data;
        this.input = new ConsoleInput(scanner);
        this.calculator = new CostCalculator();
    }

    public void show() {
        boolean running = true;

        while (running) {
            printMenu();

            int option = input.readIntBetween(
                    "Seleccione una opción: ",
                    0,
                    2);

            switch (option) {
                case 1 -> calculateFromModel();
                case 2 -> calculateFromManualData();
                case 0 -> running = false;
                default -> throw new IllegalStateException(
                        "Unexpected calculator option: " + option);
            }
        }
    }

    private void printMenu() {
        System.out.println();
        System.out.println("--- CALCULADORA DE COSTOS ---");
        System.out.println("1. Usar modelo registrado");
        System.out.println("2. Ingresar datos manualmente");
        System.out.println("0. Volver");
    }

    private void calculateFromModel() {
        Quotable quotable = selectModel();
        calculate(quotable);
    }

    private void calculateFromManualData() {
        String description = input.readRequiredText(
                "Descripción: ");

        BigDecimal estimatedGrams =
                input.readPositiveBigDecimal(
                        "Gramos estimados: ");

        int estimatedMinutes =
                input.readPositiveInt(
                        "Tiempo estimado en minutos: ");

        Quotable quotable = new ManualCalculationData(
                description,
                estimatedGrams,
                estimatedMinutes);

        calculate(quotable);
    }

    private Model3D selectModel() {
        List<Model3D> models = data.getModels();

        System.out.println();
        System.out.println("Modelos disponibles:");

        for (int i = 0; i < models.size(); i++) {
            Model3D model = models.get(i);

            System.out.printf(
                    "%d. %s | %s g | %d min%n",
                    i + 1,
                    model.getName(),
                    model.getEstimatedGrams(),
                    model.getEstimatedPrintTimeMinutes());
        }

        int option = input.readIntBetween(
                "Seleccione un modelo: ",
                1,
                models.size());

        return models.get(option - 1);
    }

    private Material selectMaterial() {
        List<Material> materials = data.getMaterials();

        System.out.println();
        System.out.println("Materiales disponibles:");

        for (int i = 0; i < materials.size(); i++) {
            Material material = materials.get(i);

            System.out.printf(
                    "%d. %s | %s | %s | $%s/g%n",
                    i + 1,
                    material.getBrand(),
                    material.getType(),
                    material.getFinish(),
                    material.getCostPerGram());
        }

        int option = input.readIntBetween(
                "Seleccione un material: ",
                1,
                materials.size());

        return materials.get(option - 1);
    }

    private void calculate(Quotable quotable) {
        Material material = selectMaterial();

        BigDecimal electricityCost =
                input.readNonNegativeBigDecimal(
                        "Costo de electricidad: $");

        BigDecimal amortizationCost =
                input.readNonNegativeBigDecimal(
                        "Costo de amortización: $");

        BigDecimal marginPercentage =
                input.readNonNegativeBigDecimal(
                        "Margen (%): ");

        CalculationResult result = calculator.calculate(
                quotable,
                material,
                electricityCost,
                amortizationCost,
                marginPercentage);

        showResult(result);
    }

    private void showResult(CalculationResult result) {
        System.out.println();
        System.out.println("--- RESULTADO ---");

        System.out.printf(
                "Costo de filamento:   $%s%n",
                result.getFilamentCost());

        System.out.printf(
                "Costo de electricidad: $%s%n",
                result.getElectricityCost());

        System.out.printf(
                "Amortización:          $%s%n",
                result.getAmortizationCost());

        System.out.printf(
                "Costo total:           $%s%n",
                result.getTotalCost());

        System.out.printf(
                "Margen:                 %s%%%n",
                result.getMarginPercentage());

        System.out.printf(
                "Importe del margen:    $%s%n",
                result.getMarginAmount());

        System.out.printf(
                "PRECIO FINAL:          $%s%n",
                result.getFinalPrice());
    }
}