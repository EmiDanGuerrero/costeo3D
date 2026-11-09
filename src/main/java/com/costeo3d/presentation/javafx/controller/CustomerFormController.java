package com.costeo3d.presentation.javafx.controller;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.application.service.CatalogService;
import com.costeo3d.domain.model.Customer;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/** E10.5: alta y edición de clientes, sin modificar su estado desde el formulario. */
public final class CustomerFormController {

    private static final Logger LOGGER = Logger.getLogger(CustomerFormController.class.getName());
    private final CatalogService catalogService;
    private final Customer original;
    private Stage dialogStage;
    private boolean saved;
    private boolean saving;

    @FXML private Label formTitleLabel;
    @FXML private Label formDescriptionLabel;
    @FXML private Label formHintLabel;
    @FXML private TextField nameField;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private Label feedbackLabel;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    public CustomerFormController(CatalogService catalogService) {
        this(catalogService, null);
    }

    public CustomerFormController(CatalogService catalogService, Customer original) {
        this.catalogService = Objects.requireNonNull(catalogService, "Se requiere CatalogService.");
        this.original = original;
    }

    @FXML
    private void initialize() {
        if (original == null) return;
        formTitleLabel.setText("Editar cliente");
        formDescriptionLabel.setText("Modificá los datos del cliente #" + original.getId()
                + ". El estado se cambia desde el listado.");
        formHintLabel.setText("Cliente #" + original.getId()
                + ". El identificador y el estado se conservan.");
        saveButton.setText("Guardar cambios");
        nameField.setText(original.getName());
        phoneField.setText(original.getPhone() == null ? "" : original.getPhone());
        emailField.setText(original.getEmail() == null ? "" : original.getEmail());
    }

    public void setDialogStage(Stage stage) {
        dialogStage = Objects.requireNonNull(stage, "Se requiere la ventana del formulario.");
        dialogStage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, event -> {
            if (saving) event.consume();
        });
    }

    public boolean wasSaved() {
        return saved;
    }

    @FXML
    private void saveCustomer() {
        if (saving) return;
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        String phone = optional(phoneField.getText());
        String email = optional(emailField.getText());
        if (name.isEmpty()) {
            feedbackLabel.setText("Ingresá el nombre del cliente.");
            return;
        }
        // Longitudes según las columnas de Inicializacion.sql.
        if (name.length() > 150) {
            feedbackLabel.setText("El nombre no puede superar los 150 caracteres.");
            return;
        }
        if (phone != null && phone.length() > 50) {
            feedbackLabel.setText("El teléfono no puede superar los 50 caracteres.");
            return;
        }
        if (email != null && email.length() > 150) {
            feedbackLabel.setText("El correo no puede superar los 150 caracteres.");
            return;
        }
        if (email != null && (!email.contains("@") || email.contains(" ")
                || email.startsWith("@") || email.endsWith("@"))) {
            feedbackLabel.setText("Ingresá un correo válido o dejá el campo vacío.");
            return;
        }

        setSaving(true);
        feedbackLabel.setText(original == null ? "Guardando cliente..." : "Actualizando cliente...");
        Task<Customer> task = new Task<>() {
            @Override protected Customer call() {
                if (original == null) {
                    return catalogService.createCustomer(new Customer(name, phone, email));
                }
                // Recarga el estado actual para que la edición de contacto no
                // revierta una activación/desactivación realizada por otro proceso.
                Customer current = catalogService.findCustomerById(original.getId())
                        .orElseThrow(() -> new IllegalStateException(
                                "El cliente ya no existe en MySQL. Actualizá el listado."));
                Customer updated = new Customer(current.getId(), name, phone, email,
                        current.isActive());
                if (!catalogService.updateCustomer(updated)) {
                    throw new IllegalStateException(
                            "El cliente ya no existe en MySQL. Actualizá el listado.");
                }
                return updated;
            }
        };
        task.setOnSucceeded(event -> {
            saved = true;
            dialogStage.close();
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            LOGGER.log(Level.WARNING, "No se pudo guardar el cliente ({0}).",
                    error == null ? "desconocido" : error.getClass().getSimpleName());
            feedbackLabel.setText(error instanceof IllegalStateException
                    ? error.getMessage()
                    : "No se pudo guardar el cliente. Revisá los datos y MySQL.");
            setSaving(false);
        });
        Thread worker = new Thread(task, "costeo3d-guardar-cliente");
        worker.setDaemon(true);
        worker.start();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @FXML private void cancel() {
        if (!saving && dialogStage != null) dialogStage.close();
    }

    private void setSaving(boolean value) {
        saving = value;
        saveButton.setDisable(value);
        cancelButton.setDisable(value);
        nameField.setDisable(value);
        phoneField.setDisable(value);
        emailField.setDisable(value);
    }
}
