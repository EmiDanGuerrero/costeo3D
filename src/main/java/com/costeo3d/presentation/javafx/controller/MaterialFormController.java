package com.costeo3d.presentation.javafx.controller;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.application.service.CatalogService;
import com.costeo3d.domain.model.Material;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/** Formulario modal para crear o editar materiales sin exponer JDBC a JavaFX. */
public final class MaterialFormController {

    private static final Logger LOGGER = Logger.getLogger(MaterialFormController.class.getName());
    // MySQL: DECIMAL(12,4) permite hasta ocho enteros y cuatro decimales.
    private static final BigDecimal MAX_COST = new BigDecimal("99999999.9999");

    private final CatalogService catalogService;
    private final Material original;
    private Stage dialogStage;
    private boolean saved;
    private boolean saving;

    @FXML private Label headingLabel;
    @FXML private Label feedbackLabel;
    @FXML private TextField brandField;
    @FXML private TextField typeField;
    @FXML private TextField finishField;
    @FXML private TextField costField;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    public MaterialFormController(CatalogService catalogService, Material original) {
        this.catalogService = Objects.requireNonNull(catalogService, "Se requiere CatalogService.");
        this.original = original;
    }

    @FXML
    private void initialize() {
        if (original == null) {
            headingLabel.setText("Nuevo material");
        } else {
            headingLabel.setText("Editar material #" + original.getId());
            brandField.setText(original.getBrand());
            typeField.setText(original.getType());
            finishField.setText(original.getFinish());
            costField.setText(original.getCostPerGram().toPlainString());
        }
    }

    public void setDialogStage(Stage stage) {
        this.dialogStage = Objects.requireNonNull(stage, "Se requiere la ventana del formulario.");
        // Una escritura en curso no debe parecer cancelada mientras continúa en MySQL.
        stage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, event -> {
            if (saving) {
                event.consume();
            }
        });
    }

    public boolean wasSaved() {
        return saved;
    }

    @FXML
    private void saveMaterial() {
        if (saving) return;
        String brand = brandField.getText().trim();
        String type = typeField.getText().trim();
        String finish = finishField.getText().trim();
        String costText = costField.getText().trim();

        // Validación de completitud del formulario, sin agregar reglas al dominio.
        if (brand.isEmpty() || type.isEmpty() || finish.isEmpty() || costText.isEmpty()) {
            showError("Completá los cuatro campos antes de guardar.");
            return;
        }

        // Coincide con material(marca VARCHAR(100), tipo VARCHAR(50), acabado VARCHAR(50)).
        if (brand.length() > 100 || type.length() > 50 || finish.length() > 50) {
            showError("Marca: máximo 100 caracteres. Tipo y acabado: máximo 50.");
            return;
        }

        BigDecimal cost;
        try {
            if (costText.contains(",") && costText.contains(".")) {
                throw new NumberFormatException("Separador decimal ambiguo.");
            }
            cost = new BigDecimal(costText.replace(',', '.'));
        } catch (NumberFormatException exception) {
            showError("El costo por gramo debe ser un número (ej.: 0,1234).");
            return;
        }

        if (cost.signum() < 0 || cost.compareTo(MAX_COST) > 0
                || cost.stripTrailingZeros().scale() > 4) {
            showError("El costo debe estar entre 0 y 99999999,9999, con máximo 4 decimales.");
            return;
        }

        Material candidate;
        try {
            if (original == null) {
                candidate = new Material(brand, type, finish, cost);
            } else {
                // Se conserva ID y estado; no se modifica el objeto original si falla MySQL.
                candidate = new Material(original.getId(), brand, type, finish,
                        cost, original.isActive());
            }
        } catch (IllegalArgumentException exception) {
            showError("Revisá el costo: debe ser mayor o igual a cero.");
            return;
        }

        setSaving(true);
        feedbackLabel.setText("Guardando material...");
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                if (original == null) {
                    catalogService.createMaterial(candidate);
                    return true;
                }
                return catalogService.updateMaterial(candidate);
            }
        };
        task.setOnSucceeded(event -> {
            if (Boolean.TRUE.equals(task.getValue())) {
                saved = true;
                dialogStage.close();
            } else {
                showError("El material ya no existe en MySQL. Actualizá el listado.");
                setSaving(false);
            }
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            LOGGER.log(Level.WARNING, "No se pudo guardar el material (tipo: {0}).",
                    error == null ? "desconocido" : error.getClass().getSimpleName());
            showError("No se pudo guardar. Revisá los datos y la conexión a MySQL.");
            setSaving(false);
        });

        Thread worker = new Thread(task, "costeo3d-guardar-material");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void cancel() {
        if (!saving) {
            dialogStage.close();
        }
    }

    private void setSaving(boolean value) {
        saving = value;
        saveButton.setDisable(value);
        cancelButton.setDisable(value);
        brandField.setDisable(value);
        typeField.setDisable(value);
        finishField.setDisable(value);
        costField.setDisable(value);
    }

    private void showError(String message) {
        feedbackLabel.setText(message);
    }
}
