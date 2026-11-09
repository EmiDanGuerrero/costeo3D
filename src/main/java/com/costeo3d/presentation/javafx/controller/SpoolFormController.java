package com.costeo3d.presentation.javafx.controller;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.application.service.CatalogService;
import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.Spool;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/** Alta de bobinas. Nunca realiza descuentos: eso corresponde al proceso de producción. */
public final class SpoolFormController {
    private static final Logger LOGGER = Logger.getLogger(SpoolFormController.class.getName());
    private static final BigDecimal MAX_WEIGHT = new BigDecimal("99999999.99");

    private final CatalogService catalogService;
    private final Spool editingSpool;
    private Stage dialogStage;
    private boolean saved;
    private boolean saving;
    private boolean loadingMaterials = true;

    @FXML private Label formTitle;
    @FXML private Label formDescription;
    @FXML private Label weightHint;
    @FXML private ComboBox<Material> materialCombo;
    @FXML private TextField colorField;
    @FXML private TextField initialWeightField;
    @FXML private Label feedbackLabel;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    public SpoolFormController(CatalogService catalogService) {
        this(catalogService, null);
    }

    public SpoolFormController(CatalogService catalogService, Spool editingSpool) {
        this.catalogService = Objects.requireNonNull(catalogService, "Se requiere CatalogService.");
        this.editingSpool = editingSpool;
    }

    @FXML
    private void initialize() {
        materialCombo.setCellFactory(list -> new MaterialCell());
        materialCombo.setButtonCell(new MaterialCell());
        if (editingSpool != null) {
            loadingMaterials = false;
            formTitle.setText("Editar bobina #" + editingSpool.getId());
            formDescription.setText("Solo se puede editar el color de una bobina activa.");
            weightHint.setText("Material y pesos son de solo lectura. "
                    + "El stock se gestiona desde Producción.");
            materialCombo.getItems().setAll(editingSpool.getMaterial());
            materialCombo.setValue(editingSpool.getMaterial());
            materialCombo.setDisable(true);
            initialWeightField.setText(editingSpool.getInitialWeight().toPlainString());
            initialWeightField.setDisable(true);
            colorField.setText(editingSpool.getColor());
            saveButton.setText("Guardar cambios");
            feedbackLabel.setText("");
            return;
        }
        saveButton.setDisable(true);
        feedbackLabel.setText("Cargando materiales activos...");
        Task<List<Material>> task = new Task<>() {
            @Override protected List<Material> call() {
                return catalogService.listMaterials().stream()
                        .filter(Material::isActive)
                        .sorted(Comparator.comparing(Material::getBrand, String.CASE_INSENSITIVE_ORDER)
                                .thenComparing(Material::getId))
                        .toList();
            }
        };
        task.setOnSucceeded(event -> {
            materialCombo.getItems().setAll(task.getValue());
            loadingMaterials = false;
            if (task.getValue().isEmpty()) {
                feedbackLabel.setText("No hay materiales activos. Creá uno en Materiales primero.");
            } else {
                feedbackLabel.setText("");
                saveButton.setDisable(false);
            }
        });
        task.setOnFailed(event -> {
            loadingMaterials = false;
            Throwable error = task.getException();
            LOGGER.log(Level.WARNING, "No se pudieron cargar materiales (tipo: {0}).",
                    error == null ? "desconocido" : error.getClass().getSimpleName());
            feedbackLabel.setText("No se pudo consultar MySQL. Cerrá e intentá nuevamente.");
        });
        Thread worker = new Thread(task, "costeo3d-materiales-form-bobina");
        worker.setDaemon(true);
        worker.start();
    }

    public void setDialogStage(Stage stage) {
        dialogStage = Objects.requireNonNull(stage, "Se requiere la ventana del formulario.");
        stage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, event -> {
            if (saving) event.consume();
        });
    }

    public boolean wasSaved() { return saved; }

    @FXML
    private void saveSpool() {
        if (saving || loadingMaterials) return;
        if (editingSpool != null) {
            saveColor();
        } else {
            createSpool();
        }
    }

    private void saveColor() {
        String color = colorField.getText().trim();
        if (!validColor(color)) return;
        setSaving(true);
        feedbackLabel.setText("Guardando color...");
        Task<Boolean> task = new Task<>() {
            @Override protected Boolean call() {
                return catalogService.updateSpoolColor(editingSpool.getId(), color);
            }
        };
        task.setOnSucceeded(event -> {
            if (Boolean.TRUE.equals(task.getValue())) {
                saved = true;
                dialogStage.close();
            } else {
                feedbackLabel.setText("La bobina ya no está activa o fue eliminada. "
                        + "Cerrá y actualizá el listado.");
                setSaving(false);
            }
        });
        task.setOnFailed(event -> {
            logError("editar color", task.getException());
            feedbackLabel.setText("No se pudo actualizar el color. Revisá MySQL.");
            setSaving(false);
        });
        startTask(task, "costeo3d-editar-color-bobina");
    }

    private void createSpool() {
        Material selected = materialCombo.getValue();
        String color = colorField.getText().trim();
        String weightText = initialWeightField.getText().trim();
        if (selected == null || weightText.isEmpty()) {
            feedbackLabel.setText("Elegí un material y completá color y peso inicial.");
            return;
        }
        if (!validColor(color)) return;
        BigDecimal weight;
        try {
            if (weightText.contains(",") && weightText.contains(".")) {
                throw new NumberFormatException("Formato decimal ambiguo.");
            }
            weight = new BigDecimal(weightText.replace(',', '.'));
        } catch (NumberFormatException ex) {
            feedbackLabel.setText("Ingresá un peso válido (por ejemplo, 1000,00).");
            return;
        }
        if (weight.signum() <= 0 || weight.compareTo(MAX_WEIGHT) > 0
                || weight.stripTrailingZeros().scale() > 2) {
            feedbackLabel.setText("Peso mayor que 0, máximo 2 decimales y hasta 99999999,99 g.");
            return;
        }
        try {
            new Spool(selected, color, weight);
        } catch (IllegalArgumentException ex) {
            feedbackLabel.setText("Revisá los datos de la bobina.");
            return;
        }
        setSaving(true);
        feedbackLabel.setText("Guardando bobina...");
        Task<Spool> task = new Task<>() {
            @Override protected Spool call() {
                Material current = catalogService.findMaterialById(selected.getId()).orElse(null);
                if (current == null || !current.isActive()) {
                    throw new IllegalStateException("Material no disponible.");
                }
                return catalogService.createSpool(new Spool(current, color, weight));
            }
        };
        task.setOnSucceeded(event -> {
            saved = true;
            dialogStage.close();
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            logError("registrar", error);
            feedbackLabel.setText(error instanceof IllegalStateException
                    ? "El material ya no está activo. Cerrá y actualizá el catálogo."
                    : "No se pudo guardar la bobina. Revisá los datos o MySQL.");
            setSaving(false);
        });
        startTask(task, "costeo3d-guardar-bobina");
    }

    private boolean validColor(String color) {
        if (color.isBlank() || color.length() > 60) {
            feedbackLabel.setText("El color es obligatorio y no puede superar 60 caracteres.");
            return false;
        }
        return true;
    }

    private static void startTask(Task<?> task, String name) {
        Thread worker = new Thread(task, name);
        worker.setDaemon(true);
        worker.start();
    }

    private static void logError(String operation, Throwable error) {
        LOGGER.log(Level.WARNING, "No se pudo {0} la bobina (tipo: {1}).",
                new Object[] {operation, error == null ? "desconocido"
                        : error.getClass().getSimpleName()});
    }

    @FXML
    private void cancel() {
        if (!saving && dialogStage != null) dialogStage.close();
    }

    private void setSaving(boolean value) {
        saving = value;
        materialCombo.setDisable(value || editingSpool != null);
        colorField.setDisable(value);
        initialWeightField.setDisable(value || editingSpool != null);
        cancelButton.setDisable(value);
        saveButton.setDisable(value || loadingMaterials || materialCombo.getItems().isEmpty());
    }

    private static final class MaterialCell extends ListCell<Material> {
        @Override protected void updateItem(Material item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null
                    : "#" + item.getId() + " · " + item.getBrand()
                      + " / " + item.getType() + " / " + item.getFinish());
        }
    }
}
