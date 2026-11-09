package com.costeo3d.presentation.javafx.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.domain.calculation.PricingParameters;
import com.costeo3d.infrastructure.config.PricingParametersStore;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/** E11.2.4: edita configuración local; no modifica presupuestos emitidos. */
public final class PricingSettingsController {

    private static final Logger LOGGER = Logger.getLogger(PricingSettingsController.class.getName());
    private final PricingParametersStore store = new PricingParametersStore();

    @FXML private TextField electricityRateField;
    @FXML private TextField averageWattsField;
    @FXML private TextField printerValueField;
    @FXML private TextField usefulHoursField;
    @FXML private TextField maintenanceYearField;
    @FXML private TextField printHoursYearField;
    @FXML private TextField defaultMarginField;
    @FXML private TextField keychainMarginField;
    @FXML private TextField largeMarginField;
    @FXML private TextField keychainMinimumField;
    @FXML private Label electricityPreviewLabel;
    @FXML private Label depreciationPreviewLabel;
    @FXML private Label maintenancePreviewLabel;
    @FXML private Label totalPreviewLabel;
    @FXML private Label statusLabel;
    @FXML private Label locationLabel;

    @FXML private void initialize() {
        locationLabel.setText("Archivo local: " + store.location());
        for (TextField field : List.of(electricityRateField, averageWattsField,
                printerValueField, usefulHoursField, maintenanceYearField,
                printHoursYearField, defaultMarginField, keychainMarginField,
                largeMarginField, keychainMinimumField)) {
            field.textProperty().addListener((obs, before, after) -> renderPreview());
        }
        try {
            fill(store.load());
            statusLabel.setText("Valores cargados. Guardá para escribir la configuración local.");
        } catch (IOException exception) {
            LOGGER.log(Level.WARNING, "No se pudo leer la configuración de costeo.", exception);
            statusLabel.setText("Error al cargar: " + exception.getMessage());
            fill(PricingParameters.initialValues());
        }
    }

    private void fill(PricingParameters parameters) {
        electricityRateField.setText(format(parameters.electricityRatePerKwh()));
        averageWattsField.setText(format(parameters.averagePrinterWatts()));
        printerValueField.setText(format(parameters.printerReplacementValue()));
        usefulHoursField.setText(parameters.printerUsefulLifeHours() == null
                ? "" : Integer.toString(parameters.printerUsefulLifeHours()));
        maintenanceYearField.setText(format(parameters.annualMaintenanceCost()));
        printHoursYearField.setText(parameters.annualPrintHours() == null
                ? "" : Integer.toString(parameters.annualPrintHours()));
        defaultMarginField.setText(parameters.defaultMarginPercentage() == null
                ? "" : format(parameters.defaultMarginPercentage()));
        keychainMarginField.setText(parameters.keychainMarginPercentage() == null
                ? "" : format(parameters.keychainMarginPercentage()));
        largeMarginField.setText(parameters.largeMarginPercentage() == null
                ? "" : format(parameters.largeMarginPercentage()));
        keychainMinimumField.setText(parameters.keychainMinimumUnitPrice() == null
                ? "" : format(parameters.keychainMinimumUnitPrice()));
        renderPreview();
    }

    private static String format(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private void renderPreview() {
        try {
            PricingParameters parameters = readFields();
            electricityPreviewLabel.setText("$" + parameters.electricityCostPerHour());
            depreciationPreviewLabel.setText(parameters.depreciationCostPerHour()
                    .map(value -> "$" + value).orElse("Falta vida útil estimada"));
            maintenancePreviewLabel.setText(parameters.maintenanceCostPerHour()
                    .map(value -> "$" + value).orElse("Faltan horas de impresión por año"));
            totalPreviewLabel.setText(parameters.combinedHourlyCost()
                    .map(value -> "$" + value + " / hora")
                    .orElse("Completá ambas estimaciones de horas"));
        } catch (IllegalArgumentException exception) {
            electricityPreviewLabel.setText("—");
            depreciationPreviewLabel.setText("—");
            maintenancePreviewLabel.setText("—");
            totalPreviewLabel.setText("Revisá los números ingresados");
        }
    }

    @FXML private void save() {
        try {
            PricingParameters settings = readFields();
            store.save(settings);
            statusLabel.setText("Configuración guardada correctamente en archivo local.");
            renderPreview();
        } catch (IllegalArgumentException exception) {
            statusLabel.setText("Corregí los datos: " + exception.getMessage());
        } catch (IOException exception) {
            LOGGER.log(Level.WARNING, "No se pudo guardar la configuración de costeo.", exception);
            statusLabel.setText("No se pudo guardar el archivo: " + exception.getMessage());
        }
    }

    private PricingParameters readFields() {
        return new PricingParameters(
                requiredDecimal(electricityRateField, "Tarifa eléctrica", true),
                requiredDecimal(averageWattsField, "Potencia promedio", true),
                requiredDecimal(printerValueField, "Valor de reposición", true),
                optionalHours(usefulHoursField, "Vida útil"),
                requiredDecimal(maintenanceYearField, "Mantenimiento anual", false),
                optionalHours(printHoursYearField, "Horas de uso anual"),
                optionalMoneyDecimal(defaultMarginField, "Margen general", 4, 2),
                optionalMoneyDecimal(keychainMarginField, "Margen para llaveros", 4, 2),
                optionalMoneyDecimal(largeMarginField, "Margen para impresiones grandes", 4, 2),
                optionalMoneyDecimal(keychainMinimumField, "Precio mínimo de llavero", 10, 2));
    }

    private static BigDecimal requiredDecimal(TextField field, String name, boolean positive) {
        BigDecimal value = parseDecimal(field.getText(), name);
        if ((positive && value.signum() <= 0) || (!positive && value.signum() < 0)) {
            throw new IllegalArgumentException(name + ": valor fuera de rango.");
        }
        return value;
    }

    private static BigDecimal optionalMoneyDecimal(TextField field, String name,
            int wholeDigits, int decimalPlaces) {
        if (field.getText().isBlank()) return null;
        BigDecimal value = parseDecimal(field.getText(), name);
        if (value.signum() < 0) throw new IllegalArgumentException(name + ": no puede ser negativo.");
        if (value.scale() > decimalPlaces || value.precision() - value.scale() > wholeDigits) {
            throw new IllegalArgumentException(name + ": máximo " + wholeDigits
                    + " enteros y " + decimalPlaces + " decimales.");
        }
        return value;
    }

    private static BigDecimal parseDecimal(String input, String name) {
        try {
            BigDecimal value = new BigDecimal(input.trim().replace(',', '.'));
            if (value.scale() > 4 || value.precision() - value.scale() > 12) {
                throw new IllegalArgumentException(name + ": máximo 12 enteros y 4 decimales.");
            }
            return value;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(name + ": ingresá un número válido.");
        }
    }

    private static Integer optionalHours(TextField field, String name) {
        String raw = field.getText().trim();
        if (raw.isEmpty()) return null;
        try {
            int hours = Integer.parseInt(raw);
            if (hours > 0) return hours;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(name + ": ingresá horas enteras positivas.");
    }
}
