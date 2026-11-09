package com.costeo3d.presentation.javafx.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.application.port.ProductionFinalizationPort;
import com.costeo3d.application.service.CatalogService;
import com.costeo3d.application.service.PersistentProductionService;
import com.costeo3d.domain.exception.BusinessRuleException;
import com.costeo3d.domain.model.FilamentConsumption;
import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.domain.model.Spool;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.StringConverter;

/**
 * E11.4.3: registro de consumos de una impresion exitosa a partir de los
 * gramos ya guardados en el presupuesto. No solicita pesar las bobinas ni
 * permite cambiar los gramos al finalizar. Las fallas se tratan en E11.4.4.
 */
public final class JobCompletionController {

    private static final Logger LOGGER = Logger.getLogger(JobCompletionController.class.getName());

    private record Assignment(JobConsumptionPlan.Line line, ComboBox<Spool> spoolCombo) { }

    private final PersistentProductionService productionService;
    private final CatalogService catalogService;
    private final PrintJob job;
    private final List<Assignment> assignments = new ArrayList<>();

    private Stage dialogStage;
    private boolean busy;
    private boolean completed;
    private ProductionFinalizationPort.Completion completionResult;
    private JobConsumptionPlan.Plan plan;

    @FXML private ScrollPane formScroll;
    @FXML private Label jobTitleLabel;
    @FXML private Label sourceLabel;
    @FXML private Label stockStatusLabel;
    @FXML private Label summaryLabel;
    @FXML private VBox assignmentsBox;
    @FXML private Button cancelButton;
    @FXML private Button completeButton;
    @FXML private ProgressIndicator progressIndicator;

    public JobCompletionController(PersistentProductionService productionService,
            CatalogService catalogService, PrintJob job) {
        this.productionService = Objects.requireNonNull(productionService);
        this.catalogService = Objects.requireNonNull(catalogService);
        this.job = Objects.requireNonNull(job);
    }

    public void setDialogStage(Stage stage) {
        dialogStage = Objects.requireNonNull(stage);
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, event -> scrollToTop());
        Platform.runLater(this::scrollToTop);
    }

    private void scrollToTop() {
        if (formScroll == null) return;
        if (dialogStage != null && dialogStage.getScene() != null) {
            dialogStage.getScene().getRoot().setFocusTraversable(true);
            dialogStage.getScene().getRoot().requestFocus();
        }
        formScroll.setVvalue(0.0);
    }

    public boolean isBusy() { return busy; }
    public boolean wasCompleted() { return completed; }
    public ProductionFinalizationPort.Completion getCompletionResult() { return completionResult; }

    @FXML
    private void initialize() {
        jobTitleLabel.setText("Trabajo #" + job.getId() + " · Pedido #"
                + (job.getOrder() == null ? "—" : job.getOrder().getId()));
        try {
            plan = JobConsumptionPlan.fromJob(job);
            sourceLabel.setText(plan.sourceDescription());
            summaryLabel.setText("Consumo aprobado a descontar: "
                    + format(totalGrams()) + " g");
            loadSpools();
        } catch (IllegalArgumentException exception) {
            sourceLabel.setText("El trabajo no pudo asociarse inequívocamente al consumo presupuestado.");
            stockStatusLabel.setText(exception.getMessage());
            summaryLabel.setText("No se descontará stock hasta resolver esta relación.");
            setBusy(false);
        }
        Platform.runLater(this::scrollToTop);
    }

    private void loadSpools() {
        setBusy(true);
        stockStatusLabel.setText("Consultando bobinas activas en MySQL...");
        Task<List<Spool>> task = new Task<>() {
            @Override protected List<Spool> call() {
                return catalogService.listSpools();
            }
        };
        task.setOnSucceeded(event -> {
            List<Spool> available = task.getValue().stream()
                    .filter(spool -> spool.isActive() && spool.getId() != null
                            && spool.getCurrentWeight().signum() > 0)
                    .sorted(Comparator.comparing(Spool::getId)).toList();
            renderAssignments(available);
            stockStatusLabel.setText("Seleccioná una bobina compatible por material. "
                    + "Los gramos provienen del presupuesto y no se pueden modificar aquí. "
                    + "MySQL verificará el stock antes del descuento.");
            setBusy(false);
            Platform.runLater(this::scrollToTop);
        });
        task.setOnFailed(event -> {
            LOGGER.log(Level.WARNING, "No se pudieron cargar las bobinas para finalizar.",
                    task.getException());
            assignments.clear();
            assignmentsBox.getChildren().clear();
            stockStatusLabel.setText("No se pudieron cargar las bobinas. Revisá MySQL.");
            setBusy(false);
            showError("No se pudieron cargar las bobinas.", false);
        });
        Thread worker = new Thread(task, "costeo3d-bobinas-finalizacion");
        worker.setDaemon(true);
        worker.start();
    }

    private void renderAssignments(List<Spool> spools) {
        assignments.clear();
        assignmentsBox.getChildren().clear();
        for (JobConsumptionPlan.Line line : plan.lines()) {
            Material material = line.material();
            String name = material.getBrand() + " · " + material.getType()
                    + " · " + material.getFinish();

            Label materialLabel = new Label("Material: " + name);
            materialLabel.getStyleClass().add("form-label");
            Label gramsLabel = new Label("Gramos aprobados: " + format(line.grams()) + " g");
            gramsLabel.getStyleClass().add("catalog-status");
            VBox info = new VBox(5, materialLabel, gramsLabel);
            info.setMinWidth(235);

            ComboBox<Spool> combo = new ComboBox<>();
            combo.setMaxWidth(Double.MAX_VALUE);
            combo.setPromptText("Seleccioná bobina con stock suficiente...");
            combo.getStyleClass().add("catalog-filter-combo");
            combo.setConverter(new StringConverter<>() {
                @Override public String toString(Spool value) {
                    return value == null ? "" : spoolLabel(value);
                }
                @Override public Spool fromString(String value) { return null; }
            });
            combo.getItems().setAll(spools.stream()
                    .filter(spool -> spool.getMaterial() != null
                            && Objects.equals(spool.getMaterial().getId(), material.getId())
                            && spool.getCurrentWeight().compareTo(line.grams()) >= 0)
                    .toList());
            if (combo.getItems().size() == 1) combo.getSelectionModel().selectFirst();
            combo.valueProperty().addListener((observable, before, after) -> updateActions());
            VBox selection = new VBox(5, new Label("Bobina a descontar"), combo);
            HBox.setHgrow(selection, Priority.ALWAYS);
            selection.setMaxWidth(Double.MAX_VALUE);
            HBox row = new HBox(15, info, selection);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-padding: 11; -fx-background-color: #f5f7fb; "
                    + "-fx-background-radius: 8;");
            assignmentsBox.getChildren().add(row);
            if (combo.getItems().isEmpty()) {
                Label warning = new Label("No hay bobinas activas de este material "
                        + "con al menos " + format(line.grams()) + " g disponibles.");
                warning.setWrapText(true);
                warning.getStyleClass().add("section-description");
                assignmentsBox.getChildren().add(warning);
            }
            assignments.add(new Assignment(line, combo));
        }
        updateActions();
    }

    @FXML
    private void completeJob() {
        if (busy || !readyToComplete()) return;
        List<FilamentConsumption> consumptions = new ArrayList<>();
        for (Assignment assignment : assignments) {
            BigDecimal grams = assignment.line().grams();
            // En una impresion exitosa usamos el dato del slicer para ambos campos.
            consumptions.add(new FilamentConsumption(job, assignment.spoolCombo().getValue(),
                    grams, grams));
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Trabajo #" + job.getId() + "\n"
                + "Bobinas: " + consumptions.size() + "\n"
                + "Gramos aprobados a descontar: " + format(totalGrams()) + " g\n\n"
                + "La operación guardará los consumos y descontará el stock de MySQL. "
                + "No se puede deshacer desde esta pantalla.",
                ButtonType.OK, ButtonType.CANCEL);
        confirmation.setTitle("Finalizar trabajo — Costeo 3D");
        confirmation.setHeaderText("¿Confirmás la finalización y el descuento de filamento?");
        confirmation.initOwner(dialogStage);
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        setBusy(true);
        stockStatusLabel.setText("Finalizando trabajo y descontando stock en MySQL...");
        Task<ProductionFinalizationPort.Completion> task = new Task<>() {
            @Override protected ProductionFinalizationPort.Completion call() throws BusinessRuleException {
                return productionService.completeJob(job.getId(), consumptions, LocalDateTime.now());
            }
        };
        task.setOnSucceeded(event -> {
            completionResult = task.getValue();
            completed = true;
            setBusy(false);
            dialogStage.close();
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            LOGGER.log(Level.WARNING, "No se pudo finalizar el trabajo #" + job.getId(), error);
            setBusy(false);
            stockStatusLabel.setText("La finalización no se completó. El stock no debe cambiar.");
            showError(error instanceof BusinessRuleException ? error.getMessage()
                    : "La operación falló. Revisá la consola; se revirtió la transacción.", true);
        });
        Thread worker = new Thread(task, "costeo3d-finalizar-trabajo-" + job.getId());
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void cancel() {
        if (!busy && dialogStage != null) dialogStage.close();
    }

    private BigDecimal totalGrams() {
        return plan == null ? BigDecimal.ZERO : plan.lines().stream()
                .map(JobConsumptionPlan.Line::grams)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean readyToComplete() {
        if (plan == null || assignments.size() != plan.lines().size()) return false;
        for (Assignment assignment : assignments) {
            Spool spool = assignment.spoolCombo().getValue();
            if (spool == null || spool.getCurrentWeight().compareTo(assignment.line().grams()) < 0)
                return false;
        }
        return true;
    }

    private void updateActions() {
        if (completeButton != null) completeButton.setDisable(busy || !readyToComplete());
    }

    private void setBusy(boolean value) {
        busy = value;
        for (Assignment assignment : assignments) assignment.spoolCombo().setDisable(value);
        cancelButton.setDisable(value);
        progressIndicator.setVisible(value);
        progressIndicator.setManaged(value);
        updateActions();
    }

    private void showError(String message, boolean serviceFailure) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(serviceFailure ? "No se pudo finalizar el trabajo" : "Revisá los datos");
        alert.setHeaderText(null);
        if (dialogStage != null) alert.initOwner(dialogStage);
        alert.showAndWait();
    }

    private static String spoolLabel(Spool spool) {
        return "#" + spool.getId() + " · " + spool.getColor()
                + " (" + format(spool.getCurrentWeight()) + " g disponibles)";
    }

    private static String format(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
