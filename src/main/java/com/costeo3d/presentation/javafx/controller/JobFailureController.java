package com.costeo3d.presentation.javafx.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.StringConverter;

/**
 * E11.4.4: se estiman las mermas por capas a partir del presupuesto aprobado.
 * Solo se editan las capas y la bobina; los gramos aprobados nunca se modifican.
 */
public final class JobFailureController {

    private static final Logger LOGGER = Logger.getLogger(JobFailureController.class.getName());
    private record Assignment(JobFailureConsumptionPlan.Line line, ComboBox<Spool> combo) { }

    private final PersistentProductionService productionService;
    private final CatalogService catalogService;
    private final PrintJob job;
    private final List<Assignment> assignments = new ArrayList<>();

    private List<Spool> spools = List.of();
    private JobConsumptionPlan.Plan approvedPlan;
    private JobFailureConsumptionPlan.Plan failurePlan;
    private Stage dialogStage;
    private boolean busy;
    private boolean failed;
    private boolean spoolsLoaded;
    private ProductionFinalizationPort.Completion failureResult;

    @FXML private ScrollPane formScroll;
    @FXML private Label jobTitleLabel;
    @FXML private Label sourceLabel;
    @FXML private Label summaryLabel;
    @FXML private Label stockStatusLabel;
    @FXML private Label validationLabel;
    @FXML private VBox assignmentsBox;
    @FXML private TextField totalLayersField;
    @FXML private TextField failureLayerField;
    @FXML private Button cancelButton;
    @FXML private Button failButton;
    @FXML private ProgressIndicator progressIndicator;

    public JobFailureController(PersistentProductionService productionService,
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
            dialogStage.getScene().getRoot().requestFocus();
        }
        formScroll.setVvalue(0.0);
    }

    public boolean isBusy() { return busy; }
    public boolean wasFailed() { return failed; }
    public ProductionFinalizationPort.Completion getFailureResult() { return failureResult; }

    @FXML
    private void initialize() {
        jobTitleLabel.setText("Trabajo #" + job.getId() + " · Pedido #"
                + (job.getOrder() == null ? "—" : job.getOrder().getId()));
        totalLayersField.textProperty().addListener((obs, before, after) -> recalculate());
        failureLayerField.textProperty().addListener((obs, before, after) -> recalculate());
        try {
            approvedPlan = JobConsumptionPlan.fromJob(job);
            sourceLabel.setText(approvedPlan.sourceDescription());
            summaryLabel.setText("Consumo aprobado: " + format(totalApprovedGrams())
                    + " g. Ingresá las capas para estimar la merma.");
            loadSpools();
        } catch (IllegalArgumentException exception) {
            sourceLabel.setText("El trabajo no pudo asociarse al consumo presupuestado.");
            stockStatusLabel.setText(exception.getMessage());
            summaryLabel.setText("No se descontará stock hasta resolver esa relación.");
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
            spools = task.getValue().stream()
                    .filter(spool -> spool.isActive() && spool.getId() != null)
                    .sorted(Comparator.comparing(Spool::getId)).toList();
            spoolsLoaded = true;
            setBusy(false);
            recalculate();
            Platform.runLater(this::scrollToTop);
        });
        task.setOnFailed(event -> {
            LOGGER.log(Level.WARNING, "No se pudieron cargar las bobinas para fallar.",
                    task.getException());
            spoolsLoaded = false;
            stockStatusLabel.setText("No se pudieron cargar las bobinas. Revisá MySQL.");
            setBusy(false);
            showError("No se pudieron cargar las bobinas. Revisá MySQL.", false);
        });
        Thread worker = new Thread(task, "costeo3d-bobinas-merma");
        worker.setDaemon(true);
        worker.start();
    }

    private void recalculate() {
        failurePlan = null;
        if (approvedPlan == null || !spoolsLoaded) {
            updateActions();
            return;
        }
        String totalText = totalLayersField.getText().trim();
        String layerText = failureLayerField.getText().trim();
        if (totalText.isEmpty() || layerText.isEmpty()) {
            assignments.clear();
            assignmentsBox.getChildren().clear();
            validationLabel.setText("Ingresá las capas totales y la capa donde falló.");
            summaryLabel.setText("Consumo aprobado: " + format(totalApprovedGrams()) + " g");
            stockStatusLabel.setText("Seleccioná una bobina por material después de indicar las capas.");
            updateActions();
            return;
        }
        try {
            int totalLayers = Integer.parseInt(totalText);
            int failureLayer = Integer.parseInt(layerText);
            JobFailureConsumptionPlan.Plan next = JobFailureConsumptionPlan.fromJob(
                    job, totalLayers, failureLayer);
            failurePlan = next;
            validationLabel.setText("Avance estimado: " + format(
                    BigDecimal.valueOf(failureLayer).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalLayers), 2, RoundingMode.HALF_UP)) + " %");
            summaryLabel.setText("Aprobado: " + format(totalApprovedGrams())
                    + " g · Merma estimada a descontar: "
                    + format(next.totalEstimatedWaste()) + " g");
            renderAssignments(next);
            stockStatusLabel.setText("Elegí la bobina realmente utilizada para cada material. "
                    + "MySQL verificará el stock antes de descontar.");
        } catch (NumberFormatException error) {
            invalidate("Ingresá números enteros válidos para las capas.");
        } catch (IllegalArgumentException error) {
            invalidate(error.getMessage());
        }
        updateActions();
    }

    private void invalidate(String message) {
        failurePlan = null;
        assignments.clear();
        assignmentsBox.getChildren().clear();
        validationLabel.setText(message);
        summaryLabel.setText("Consumo aprobado: " + format(totalApprovedGrams())
                + " g · Merma sin calcular.");
        stockStatusLabel.setText("Corregí las capas para elegir bobinas.");
    }

    private void renderAssignments(JobFailureConsumptionPlan.Plan next) {
        Map<Integer, Integer> priorSpools = new HashMap<>();
        for (Assignment previous : assignments) {
            Spool selected = previous.combo().getValue();
            if (selected != null) {
                priorSpools.put(previous.line().material().getId(), selected.getId());
            }
        }
        assignments.clear();
        assignmentsBox.getChildren().clear();
        for (JobFailureConsumptionPlan.Line line : next.lines()) {
            Material material = line.material();
            String name = material.getBrand() + " · " + material.getType()
                    + " · " + material.getFinish();
            Label materialLabel = new Label("Material: " + name);
            materialLabel.getStyleClass().add("form-label");
            Label plannedLabel = new Label("Aprobado: " + format(line.approvedGrams()) + " g");
            Label wasteLabel = new Label("Merma a descontar: "
                    + format(line.estimatedWasteGrams()) + " g");
            plannedLabel.getStyleClass().add("catalog-status");
            wasteLabel.getStyleClass().add("catalog-status");
            VBox info = new VBox(5, materialLabel, plannedLabel, wasteLabel);
            info.setMinWidth(245);

            ComboBox<Spool> combo = new ComboBox<>();
            combo.setMaxWidth(Double.MAX_VALUE);
            combo.setPromptText("Seleccioná bobina compatible...");
            combo.getStyleClass().add("catalog-filter-combo");
            combo.setConverter(new StringConverter<>() {
                @Override public String toString(Spool spool) {
                    return spool == null ? "" : spoolLabel(spool);
                }
                @Override public Spool fromString(String value) { return null; }
            });
            combo.getItems().setAll(spools.stream()
                    .filter(spool -> spool.getMaterial() != null
                            && Objects.equals(spool.getMaterial().getId(), material.getId())
                            && spool.getCurrentWeight().compareTo(line.estimatedWasteGrams()) >= 0)
                    .toList());
            Integer previouslySelected = priorSpools.get(material.getId());
            if (previouslySelected != null) {
                combo.getItems().stream()
                        .filter(spool -> Objects.equals(spool.getId(), previouslySelected))
                        .findFirst().ifPresent(combo.getSelectionModel()::select);
            }
            if (combo.getValue() == null && combo.getItems().size() == 1) {
                combo.getSelectionModel().selectFirst();
            }
            combo.valueProperty().addListener((obs, before, after) -> updateActions());
            VBox selection = new VBox(5, new Label("Bobina utilizada"), combo);
            HBox.setHgrow(selection, Priority.ALWAYS);
            selection.setMaxWidth(Double.MAX_VALUE);
            HBox row = new HBox(15, info, selection);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-padding: 11; -fx-background-color: #f5f7fb; "
                    + "-fx-background-radius: 8;");
            assignmentsBox.getChildren().add(row);
            if (combo.getItems().isEmpty()) {
                Label warning = new Label("No hay bobinas activas compatibles con stock "
                        + "para descontar " + format(line.estimatedWasteGrams()) + " g.");
                warning.setWrapText(true);
                warning.getStyleClass().add("section-description");
                assignmentsBox.getChildren().add(warning);
            }
            assignments.add(new Assignment(line, combo));
        }
    }

    private BigDecimal totalApprovedGrams() {
        return approvedPlan == null ? BigDecimal.ZERO : approvedPlan.lines().stream()
                .map(JobConsumptionPlan.Line::grams)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean readyToFail() {
        if (failurePlan == null || !spoolsLoaded
                || assignments.size() != failurePlan.lines().size()) return false;
        for (Assignment assignment : assignments) {
            Spool spool = assignment.combo().getValue();
            if (spool == null
                    || spool.getCurrentWeight().compareTo(
                            assignment.line().estimatedWasteGrams()) < 0) return false;
        }
        return true;
    }

    @FXML
    private void failJob() {
        if (busy || !readyToFail()) return;
        JobFailureConsumptionPlan.Plan snapshot = failurePlan;
        List<FilamentConsumption> consumptions = new ArrayList<>();
        for (Assignment assignment : assignments) {
            JobFailureConsumptionPlan.Line line = assignment.line();
            consumptions.add(new FilamentConsumption(job, assignment.combo().getValue(),
                    line.approvedGrams(), line.estimatedWasteGrams()));
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Trabajo #" + job.getId() + "\n"
                + "Capa de falla: " + snapshot.failedAtLayer() + " de "
                + snapshot.totalLayers() + "\n"
                + "Merma estimada: " + format(snapshot.totalEstimatedWaste()) + " g\n"
                + "Bobinas: " + consumptions.size() + "\n\n"
                + "Se guardarán la falla, sus consumos y el descuento de stock. "
                + "Esta operación no puede deshacerse desde la pantalla.",
                ButtonType.OK, ButtonType.CANCEL);
        confirmation.setTitle("Registrar falla — Costeo 3D");
        confirmation.setHeaderText("¿Confirmás la falla y el descuento estimado?");
        confirmation.initOwner(dialogStage);
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        setBusy(true);
        stockStatusLabel.setText("Registrando falla y descontando stock en MySQL...");
        Task<ProductionFinalizationPort.Completion> task = new Task<>() {
            @Override protected ProductionFinalizationPort.Completion call()
                    throws BusinessRuleException {
                return productionService.failJobWithEstimate(job.getId(), consumptions,
                        LocalDateTime.now(), snapshot.totalLayers(), snapshot.failedAtLayer());
            }
        };
        task.setOnSucceeded(event -> {
            failureResult = task.getValue();
            failed = true;
            setBusy(false);
            dialogStage.close();
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            LOGGER.log(Level.WARNING, "No se pudo registrar la falla del trabajo #"
                    + job.getId(), error);
            setBusy(false);
            stockStatusLabel.setText("La falla no se registró; se revirtió la transacción.");
            showError(error instanceof BusinessRuleException ? error.getMessage()
                    : "La operación falló. Revisá la consola y MySQL.", true);
        });
        Thread worker = new Thread(task, "costeo3d-fallar-trabajo-" + job.getId());
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void cancel() {
        if (!busy && dialogStage != null) dialogStage.close();
    }

    private void setBusy(boolean value) {
        busy = value;
        totalLayersField.setDisable(value);
        failureLayerField.setDisable(value);
        for (Assignment assignment : assignments) assignment.combo().setDisable(value);
        cancelButton.setDisable(value);
        progressIndicator.setVisible(value);
        progressIndicator.setManaged(value);
        updateActions();
    }

    private void updateActions() {
        if (failButton != null) failButton.setDisable(busy || !readyToFail());
    }

    private void showError(String message, boolean serviceFailure) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(serviceFailure ? "No se pudo registrar la falla" : "Revisá los datos");
        alert.setHeaderText(null);
        if (dialogStage != null) alert.initOwner(dialogStage);
        alert.showAndWait();
    }

    private static String spoolLabel(Spool spool) {
        return "#" + spool.getId() + " · " + spool.getColor()
                + " (" + format(spool.getCurrentWeight()) + " g disponibles)";
    }

    private static String format(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
