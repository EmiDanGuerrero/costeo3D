package com.costeo3d.presentation.javafx.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.application.service.CatalogService;
import com.costeo3d.application.service.PersistentOrderService;
import com.costeo3d.application.service.PersistentQuoteService;
import com.costeo3d.domain.enums.QuoteStatus;
import com.costeo3d.domain.model.Model3D;
import com.costeo3d.domain.model.Order;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.domain.model.Quote;
import com.costeo3d.domain.model.QuoteItem;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;

/** E11.3.2: crea pedidos con sus trabajos en una sola transacción de aplicación. */
public final class OrderFormController {

    private static final Logger LOGGER = Logger.getLogger(OrderFormController.class.getName());

    private record JobDraft(Integer modelId, String modelName, int minutes) {
        @Override public String toString() {
            return (modelName == null ? "Trabajo personalizado (sin modelo)" : modelName)
                    + " — " + minutes + " min";
        }
    }

    private record FormData(int quoteId, List<JobDraft> jobs) { }
    private record CatalogueData(List<Quote> quotes, List<Model3D> models) { }

    private final PersistentOrderService orders;
    private final PersistentQuoteService quotes;
    private final CatalogService catalogs;
    private final ObservableList<JobDraft> draftJobs = FXCollections.observableArrayList();

    private Stage dialogStage;
    private Integer createdOrderId;
    private boolean busy;
    private boolean initializingSelection;

    @FXML private ScrollPane formScroll;
    @FXML private ComboBox<Quote> quoteCombo;
    @FXML private ComboBox<Model3D> modelCombo;
    @FXML private TextField minutesField;
    @FXML private ListView<JobDraft> jobsList;
    @FXML private Label quoteHintLabel;
    @FXML private Label modelHintLabel;
    @FXML private Label feedbackLabel;
    @FXML private Label jobsCountLabel;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Button addJobButton;
    @FXML private Button removeJobButton;
    @FXML private Button clearModelButton;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    public OrderFormController(PersistentOrderService orders,
            PersistentQuoteService quotes, CatalogService catalogs) {
        this.orders = Objects.requireNonNull(orders, "Se requieren los pedidos.");
        this.quotes = Objects.requireNonNull(quotes, "Se requieren los presupuestos.");
        this.catalogs = Objects.requireNonNull(catalogs, "Se requieren los catálogos.");
    }

    @FXML
    private void initialize() {
        quoteCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Quote q) {
                if (q == null) return "";
                String client = q.getCustomer() == null ? "Sin cliente" : q.getCustomer().getName();
                return "#" + q.getId() + " — " + client + " — $" + q.getTotal();
            }
            @Override public Quote fromString(String value) { return null; }
        });
        modelCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Model3D model) {
                return model == null ? "" : model.getName() + " (#" + model.getId() + ")";
            }
            @Override public Model3D fromString(String value) { return null; }
        });
        jobsList.setItems(draftJobs);
        jobsList.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldValue, newValue) -> updateButtons());
        quoteCombo.valueProperty().addListener((obs, before, value) -> selectedQuoteChanged(value));
        modelCombo.valueProperty().addListener((obs, before, value) -> {
            if (!initializingSelection && value != null) {
                minutesField.setText(Integer.toString(value.getEstimatedPrintTimeMinutes()));
            }
        });
        updateButtons();
        loadCatalogues();
        Platform.runLater(() -> formScroll.setVvalue(0.0));
    }

    public void setDialogStage(Stage stage) {
        this.dialogStage = Objects.requireNonNull(stage);
        Platform.runLater(() -> {
            formScroll.setVvalue(0.0);
            stage.getScene().getRoot().requestFocus();
        });
    }

    public Integer getCreatedOrderId() { return createdOrderId; }

    public boolean isBusy() { return busy; }

    private void loadCatalogues() {
        setBusy(true);
        feedbackLabel.setText("Consultando presupuestos y modelos en MySQL...");
        Task<CatalogueData> task = new Task<>() {
            @Override protected CatalogueData call() {
                List<Quote> allQuotes = quotes.findAll();
                List<Order> currentOrders = orders.findAll();
                List<Model3D> activeModels = catalogs.listModels3D().stream()
                        .filter(Model3D::isActive).toList();
                Set<Integer> alreadyUsed = new HashSet<>();
                for (Order order : currentOrders) {
                    if (order.getQuote() != null && order.getQuote().getId() != null) {
                        alreadyUsed.add(order.getQuote().getId());
                    }
                }
                List<Quote> available = allQuotes.stream()
                        .filter(q -> q.getId() != null && q.getStatus() == QuoteStatus.ACCEPTED)
                        .filter(q -> !alreadyUsed.contains(q.getId()))
                        .sorted(Comparator.comparing(Quote::getId).reversed()).toList();
                return new CatalogueData(available, activeModels);
            }
        };
        task.setOnSucceeded(event -> {
            CatalogueData result = task.getValue();
            quoteCombo.getItems().setAll(result.quotes());
            modelCombo.getItems().setAll(result.models());
            if (result.quotes().size() == 1) quoteCombo.setValue(result.quotes().get(0));
            feedbackLabel.setText(result.quotes().isEmpty()
                    ? "No hay presupuestos aceptados sin pedido. Aceptá uno en Presupuestos."
                    : "Seleccioná un presupuesto y agregá los trabajos necesarios.");
            setBusy(false);
        });
        task.setOnFailed(event -> {
            LOGGER.log(Level.WARNING, "No se pudieron cargar los datos para crear pedido.",
                    task.getException());
            feedbackLabel.setText("No se pudieron consultar los datos. Revisá MySQL y la consola.");
            setBusy(false);
            quoteCombo.setDisable(true);
            addJobButton.setDisable(true);
            saveButton.setDisable(true);
        });
        Thread thread = new Thread(task, "costeo3d-cargar-formulario-pedido");
        thread.setDaemon(true);
        thread.start();
    }

    private void selectedQuoteChanged(Quote quote) {
        draftJobs.clear();
        initializingSelection = true;
        try {
            modelCombo.getSelectionModel().clearSelection();
            minutesField.clear();
            if (quote == null) {
                quoteHintLabel.setText("Seleccioná un presupuesto aceptado y sin pedido.");
            } else {
                int quantity = quote.getItems().stream().mapToInt(QuoteItem::getQuantity).sum();
                quoteHintLabel.setText("Presupuesto #" + quote.getId() + " · "
                        + quote.getItems().size() + " concepto(s) · "
                        + quantity + " unidad(es). Agregá los trabajos a realizar.");
                if (!quote.getItems().isEmpty()) {
                    QuoteItem first = quote.getItems().get(0);
                    minutesField.setText(Integer.toString(first.getEstimatedPrintTimeMinutes()));
                    if (first.getModel() != null && first.getModel().getId() != null) {
                        modelCombo.getItems().stream()
                                .filter(m -> Objects.equals(m.getId(), first.getModel().getId()))
                                .findFirst().ifPresent(modelCombo::setValue);
                    }
                }
            }
        } finally {
            initializingSelection = false;
        }
        updateButtons();
    }

    @FXML
    private void clearModel() {
        modelCombo.getSelectionModel().clearSelection();
        modelHintLabel.setText("Sin modelo: trabajo personalizado.");
    }

    @FXML
    private void addJob() {
        if (busy) return;
        if (quoteCombo.getValue() == null) {
            feedbackLabel.setText("Seleccioná primero el presupuesto de origen.");
            return;
        }
        Integer minutes = parsePositiveMinutes(minutesField.getText());
        if (minutes == null) {
            feedbackLabel.setText("Ingresá minutos enteros mayores que cero.");
            return;
        }
        Model3D model = modelCombo.getValue();
        draftJobs.add(new JobDraft(model == null ? null : model.getId(),
                model == null ? null : model.getName(), minutes));
        feedbackLabel.setText("Trabajo agregado. Podés agregar más antes de guardar.");
        updateButtons();
    }

    @FXML
    private void removeJob() {
        if (busy) return;
        int index = jobsList.getSelectionModel().getSelectedIndex();
        if (index >= 0) draftJobs.remove(index);
        updateButtons();
    }

    @FXML
    private void saveOrder() {
        if (busy) return;
        Quote quote = quoteCombo.getValue();
        if (quote == null || quote.getId() == null) {
            feedbackLabel.setText("Elegí un presupuesto aceptado.");
            return;
        }
        if (draftJobs.isEmpty()) {
            feedbackLabel.setText("Agregá al menos un trabajo de impresión.");
            return;
        }
        FormData data = new FormData(quote.getId(), List.copyOf(draftJobs));
        setBusy(true);
        feedbackLabel.setText("Guardando pedido y trabajos en MySQL...");
        Task<Order> task = new Task<>() {
            @Override protected Order call() throws Exception {
                LocalDateTime now = LocalDateTime.now().withNano(0);
                List<PrintJob> printJobs = new ArrayList<>();
                for (JobDraft draft : data.jobs()) {
                    Model3D model = null;
                    if (draft.modelId() != null) {
                        model = catalogs.findModel3DById(draft.modelId()).orElseThrow(
                                () -> new IllegalStateException("Un modelo ya no está disponible."));
                        if (!model.isActive()) {
                            throw new IllegalStateException("Un modelo fue desactivado. Revisá los trabajos.");
                        }
                    }
                    printJobs.add(new PrintJob(null, model, now, draft.minutes()));
                }
                return orders.createOrder(data.quoteId(), now, printJobs);
            }
        };
        task.setOnSucceeded(event -> {
            createdOrderId = task.getValue().getId();
            feedbackLabel.setText("Pedido #" + createdOrderId + " creado correctamente.");
            setBusy(false);
            dialogStage.close();
        });
        task.setOnFailed(event -> {
            LOGGER.log(Level.WARNING, "No se pudo crear el pedido.", task.getException());
            Throwable cause = task.getException();
            feedbackLabel.setText("No se pudo crear el pedido: "
                    + (cause == null || cause.getMessage() == null
                        ? "revisá la consola de Eclipse." : cause.getMessage()));
            setBusy(false);
        });
        Thread thread = new Thread(task, "costeo3d-guardar-pedido");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void cancel() {
        if (!busy && dialogStage != null) dialogStage.close();
    }

    private void setBusy(boolean value) {
        busy = value;
        loadingIndicator.setVisible(value);
        loadingIndicator.setManaged(value);
        quoteCombo.setDisable(value);
        modelCombo.setDisable(value);
        minutesField.setDisable(value);
        jobsList.setDisable(value);
        addJobButton.setDisable(value);
        clearModelButton.setDisable(value);
        cancelButton.setDisable(value);
        updateButtons();
    }

    private void updateButtons() {
        removeJobButton.setDisable(busy || jobsList.getSelectionModel().getSelectedItem() == null);
        saveButton.setDisable(busy || quoteCombo.getValue() == null || draftJobs.isEmpty());
        jobsCountLabel.setText("Trabajos agregados: " + draftJobs.size());
    }

    private static Integer parsePositiveMinutes(String value) {
        if (value == null || !value.trim().matches("[1-9][0-9]*")) return null;
        try {
            int number = Integer.parseInt(value.trim());
            return number > 0 ? number : null;
        } catch (NumberFormatException error) {
            return null;
        }
    }
}
