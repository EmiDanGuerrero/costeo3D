package com.costeo3d.presentation.javafx.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.application.service.CatalogService;
import com.costeo3d.application.service.PersistentOrderService;
import com.costeo3d.domain.exception.BusinessRuleException;
import com.costeo3d.domain.model.Model3D;
import com.costeo3d.domain.model.PrintJob;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;

/** Edita exclusivamente la planificación de un trabajo pendiente de un pedido pendiente. */
public final class JobPlanningController {

    private static final Logger LOGGER = Logger.getLogger(JobPlanningController.class.getName());

    private final PersistentOrderService orders;
    private final CatalogService catalogs;
    private final int orderId;
    private final PrintJob currentJob;

    private Stage dialogStage;
    private boolean saved;
    private boolean busy;

    @FXML private Label jobTitle;
    @FXML private ComboBox<Model3D> modelCombo;
    @FXML private TextField minutesField;
    @FXML private Label feedbackLabel;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Button clearModelButton;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    public JobPlanningController(PersistentOrderService orders, CatalogService catalogs,
            int orderId, PrintJob currentJob) {
        this.orders = Objects.requireNonNull(orders, "Se requiere el servicio de pedidos.");
        this.catalogs = Objects.requireNonNull(catalogs, "Se requieren los catálogos.");
        this.orderId = orderId;
        this.currentJob = Objects.requireNonNull(currentJob, "Se requiere el trabajo.");
    }

    @FXML
    private void initialize() {
        jobTitle.setText("Pedido #" + orderId + " · Trabajo #" + currentJob.getId());
        minutesField.setText(Integer.toString(currentJob.getEstimatedPrintTimeMinutes()));
        modelCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Model3D model) {
                if (model == null) return "";
                return model.getName() + " (#" + model.getId() + ")"
                        + (model.isActive() ? "" : " · inactivo (actual)");
            }
            @Override public Model3D fromString(String text) { return null; }
        });
        loadModels();
    }

    public void setDialogStage(Stage stage) {
        dialogStage = Objects.requireNonNull(stage);
    }

    public boolean isBusy() { return busy; }
    public boolean isSaved() { return saved; }

    private void loadModels() {
        setBusy(true);
        feedbackLabel.setText("Cargando modelos disponibles...");
        Task<List<Model3D>> task = new Task<>() {
            @Override protected List<Model3D> call() {
                return catalogs.listModels3D();
            }
        };
        task.setOnSucceeded(event -> {
            List<Model3D> models = new ArrayList<>();
            for (Model3D model : task.getValue()) {
                boolean isCurrent = currentJob.getModel() != null
                        && Objects.equals(model.getId(), currentJob.getModel().getId());
                if (model.isActive() || isCurrent) models.add(model);
            }
            models.sort(Comparator.comparing(Model3D::getName,
                    String.CASE_INSENSITIVE_ORDER));
            modelCombo.getItems().setAll(models);
            Model3D oldModel = currentJob.getModel();
            if (oldModel != null) {
                models.stream()
                        .filter(model -> Objects.equals(model.getId(), oldModel.getId()))
                        .findFirst().ifPresent(modelCombo::setValue);
            }
            setBusy(false);
            feedbackLabel.setText("Podés corregir el modelo o los minutos mientras el pedido siga pendiente.");
        });
        task.setOnFailed(event -> {
            LOGGER.log(Level.WARNING, "No se pudieron listar los modelos del trabajo.",
                    task.getException());
            setBusy(false);
            saveButton.setDisable(true);
            modelCombo.setDisable(true);
            feedbackLabel.setText("No se pudieron consultar los modelos. Cerrá y reintentá.");
        });
        Thread worker = new Thread(task, "costeo3d-modelos-planificacion");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void clearModel() {
        if (!busy) modelCombo.getSelectionModel().clearSelection();
    }

    @FXML
    private void save() {
        if (busy || currentJob.getId() == null) return;
        final int minutes;
        try {
            minutes = Integer.parseInt(minutesField.getText().trim());
            if (minutes <= 0) throw new NumberFormatException("Se espera un número positivo.");
        } catch (NumberFormatException | NullPointerException error) {
            feedbackLabel.setText("Ingresá minutos enteros mayores que cero.");
            return;
        }
        Model3D model = modelCombo.getValue();
        final Integer modelId = model == null ? null : model.getId();
        setBusy(true);
        feedbackLabel.setText("Guardando planificación en MySQL...");
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws BusinessRuleException {
                orders.updatePendingJobPlan(orderId, currentJob.getId(), modelId, minutes);
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            saved = true;
            setBusy(false);
            dialogStage.close();
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            LOGGER.log(Level.WARNING, "No se pudo modificar el trabajo pendiente.", error);
            setBusy(false);
            feedbackLabel.setText(error instanceof BusinessRuleException
                    ? error.getMessage()
                    : "No se pudo guardar. Revisá MySQL y la consola de Eclipse.");
        });
        Thread worker = new Thread(task, "costeo3d-actualizar-planificacion");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void cancel() {
        if (!busy && dialogStage != null) dialogStage.close();
    }

    private void setBusy(boolean value) {
        busy = value;
        loadingIndicator.setVisible(value);
        loadingIndicator.setManaged(value);
        modelCombo.setDisable(value);
        minutesField.setDisable(value);
        clearModelButton.setDisable(value);
        saveButton.setDisable(value);
        cancelButton.setDisable(value);
    }
}
