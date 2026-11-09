package com.costeo3d.presentation.javafx.controller;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.application.service.CatalogService;
import com.costeo3d.application.service.PersistentProductionService;
import com.costeo3d.domain.exception.BusinessRuleException;
import com.costeo3d.domain.enums.OrderStatus;
import com.costeo3d.domain.enums.PrintJobStatus;
import com.costeo3d.domain.model.Order;
import com.costeo3d.domain.model.PrintJob;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;

/** E11.4.3: consulta, inicio y finalización transaccional de trabajos. */
public final class ProductionController {

    private static final Logger LOGGER = Logger.getLogger(ProductionController.class.getName());
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String ALL_STATES = "Todos los estados";
    private static final String COMPLETION_FXML =
            "/com/costeo3d/presentation/javafx/view/job-completion.fxml";
    private static final String FAILURE_FXML =
            "/com/costeo3d/presentation/javafx/view/job-failure.fxml";
    private static final int PAGE_SIZE = 10;
    private static final double HEADER_HEIGHT = 38;
    private static final double ROW_HEIGHT = 27;
    private static final double BORDER_HEIGHT = 2;

    private final PersistentProductionService productionService;
    private final CatalogService catalogService;
    private List<PrintJob> loadedJobs = List.of();
    private Integer restoreSelectedId;
    private int pageIndex;
    private boolean busy;
    private boolean loadFailed;
    private boolean rendering;

    @FXML private TableView<PrintJob> jobsTable;
    @FXML private TableColumn<PrintJob, Integer> idColumn;
    @FXML private TableColumn<PrintJob, Integer> orderColumn;
    @FXML private TableColumn<PrintJob, String> customerColumn;
    @FXML private TableColumn<PrintJob, String> modelColumn;
    @FXML private TableColumn<PrintJob, Integer> minutesColumn;
    @FXML private TableColumn<PrintJob, String> statusColumn;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilterCombo;
    @FXML private Button refreshButton;
    @FXML private Button clearFiltersButton;
    @FXML private Button previousPageButton;
    @FXML private Button nextPageButton;
    @FXML private Label statusLabel;
    @FXML private Label pageLabel;
    @FXML private Label detailTitle;
    @FXML private Label jobActionHintLabel;
    @FXML private Button startJobButton;
    @FXML private Button completeJobButton;
    @FXML private Button failJobButton;
    @FXML private Label summaryLabel;
    @FXML private TextArea detailArea;
    @FXML private ProgressIndicator loadingIndicator;

    public ProductionController(PersistentProductionService productionService,
            CatalogService catalogService) {
        this.productionService = Objects.requireNonNull(productionService,
                "Se requiere PersistentProductionService.");
        this.catalogService = Objects.requireNonNull(catalogService,
                "Se requiere CatalogService.");
    }

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().getId()));
        orderColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(orderId(cell.getValue())));
        customerColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(customerName(cell.getValue())));
        modelColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(modelName(cell.getValue())));
        minutesColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().getEstimatedPrintTimeMinutes()));
        statusColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(statusText(cell.getValue().getStatus())));
        customerColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
        modelColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
        statusColumn.setComparator(String.CASE_INSENSITIVE_ORDER);

        jobsTable.setFixedCellSize(ROW_HEIGHT);
        jobsTable.setSortPolicy(table -> {
            renderPage(false);
            return true;
        });
        jobsTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, before, after) -> updateDetail());
        statusFilterCombo.getItems().setAll(ALL_STATES,
                "Pendientes", "En proceso", "Finalizados", "Fallidos");
        statusFilterCombo.setValue(ALL_STATES);
        searchField.textProperty().addListener(
                (observable, before, after) -> renderPage(true));
        statusFilterCombo.valueProperty().addListener(
                (observable, before, after) -> renderPage(true));
        idColumn.setSortType(TableColumn.SortType.DESCENDING);
        jobsTable.getSortOrder().setAll(idColumn);
        reloadJobs();
    }

    @FXML
    private void reloadJobs() {
        if (busy) return;
        Integer selected = selectedId();
        if (selected != null) restoreSelectedId = selected;
        setBusy(true);
        statusLabel.setText("Consultando trabajos en MySQL...");
        Task<List<PrintJob>> task = new Task<>() {
            @Override
            protected List<PrintJob> call() {
                return productionService.findAll();
            }
        };
        task.setOnSucceeded(event -> {
            loadedJobs = List.copyOf(task.getValue());
            loadFailed = false;
            setBusy(false);
            renderPage(false);
        });
        task.setOnFailed(event -> {
            LOGGER.log(Level.WARNING, "No se pudieron consultar los trabajos de impresión.",
                    task.getException());
            loadedJobs = List.of();
            loadFailed = true;
            setBusy(false);
            renderPage(false);
        });
        Thread worker = new Thread(task, "costeo3d-listar-produccion");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void clearFilters() {
        if (busy) return;
        searchField.clear();
        statusFilterCombo.setValue(ALL_STATES);
        renderPage(true);
    }

    private void renderPage(boolean resetPage) {
        if (rendering) return;
        rendering = true;
        try {
            if (resetPage) pageIndex = 0;
            String query = searchField.getText() == null ? ""
                    : searchField.getText().trim().toLowerCase(Locale.ROOT);
            String filter = statusFilterCombo.getValue();
            List<PrintJob> filtered = new ArrayList<>();
            for (PrintJob job : loadedJobs) {
                boolean matches = query.isEmpty()
                        || idText(job.getId()).contains(query)
                        || idText(orderId(job)).contains(query)
                        || customerName(job).toLowerCase(Locale.ROOT).contains(query)
                        || modelName(job).toLowerCase(Locale.ROOT).contains(query);
                boolean matchesState = filter == null || ALL_STATES.equals(filter)
                        || filter.equals(statusFilterText(job.getStatus()));
                if (matches && matchesState) filtered.add(job);
            }
            Comparator<PrintJob> byId = Comparator.comparing(PrintJob::getId,
                    Comparator.nullsLast(Integer::compareTo));
            Comparator<PrintJob> chosenComparator = jobsTable.getComparator();
            if (chosenComparator == null || jobsTable.getSortOrder().isEmpty()) {
                filtered.sort(byId.reversed());
            } else {
                filtered.sort(chosenComparator.thenComparing(byId));
            }

            int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            pageIndex = Math.min(pageIndex, pages - 1);
            int from = pageIndex * PAGE_SIZE;
            int to = Math.min(from + PAGE_SIZE, filtered.size());
            List<PrintJob> visible = filtered.subList(from, to);
            Integer selectedId = restoreSelectedId != null ? restoreSelectedId : selectedId();
            jobsTable.setItems(FXCollections.observableArrayList(visible));
            if (selectedId != null) {
                visible.stream()
                        .filter(job -> Objects.equals(job.getId(), selectedId))
                        .findFirst().ifPresent(jobsTable.getSelectionModel()::select);
            }
            restoreSelectedId = null;
            updateDetail();
            int rows = visible.isEmpty() ? 4 : visible.size();
            double height = HEADER_HEIGHT + rows * ROW_HEIGHT + BORDER_HEIGHT;
            jobsTable.setMinHeight(height);
            jobsTable.setPrefHeight(height);
            jobsTable.setMaxHeight(height);
            jobsTable.setPlaceholder(new Label(loadFailed
                    ? "No se pudieron consultar los trabajos."
                    : loadedJobs.isEmpty() ? "Todavía no hay trabajos registrados."
                    : "No hay trabajos que coincidan con los filtros."));
            pageLabel.setText("Página " + (pageIndex + 1) + " de " + pages);
            previousPageButton.setDisable(busy || pageIndex == 0);
            nextPageButton.setDisable(busy || pageIndex >= pages - 1);
            statusLabel.setText(loadFailed
                    ? "No se pudo consultar MySQL. Revisá la conexión y la consola."
                    : "Coincidencias: " + filtered.size() + " de "
                            + loadedJobs.size() + " trabajos.");
            updateSummary();
        } finally {
            rendering = false;
        }
    }

    @FXML
    private void previousPage() {
        if (!busy && pageIndex > 0) {
            pageIndex--;
            renderPage(false);
        }
    }

    @FXML
    private void nextPage() {
        if (!busy && !nextPageButton.isDisabled()) {
            pageIndex++;
            renderPage(false);
        }
    }

    private void updateSummary() {
        long pending = loadedJobs.stream()
                .filter(job -> job.getStatus() == PrintJobStatus.PENDING).count();
        long inProgress = loadedJobs.stream()
                .filter(job -> job.getStatus() == PrintJobStatus.IN_PROGRESS).count();
        long completed = loadedJobs.stream()
                .filter(job -> job.getStatus() == PrintJobStatus.COMPLETED).count();
        long failed = loadedJobs.stream()
                .filter(job -> job.getStatus() == PrintJobStatus.FAILED).count();
        summaryLabel.setText("Pendientes: " + pending + "     En proceso: " + inProgress
                + "     Finalizados: " + completed + "     Fallidos: " + failed);
    }

    private void updateDetail() {
        PrintJob selected = jobsTable.getSelectionModel().getSelectedItem();
        detailTitle.setText(selected == null ? "Detalle del trabajo"
                : "Trabajo #" + selected.getId());
        detailArea.setText(selected == null
                ? "Seleccioná un trabajo de la tabla para consultar su detalle."
                : describe(selected));
        updateJobAction();
    }

    private static boolean canStart(PrintJob job) {
        return job != null && job.getId() != null
                && job.getStatus() == PrintJobStatus.PENDING
                && job.getOrder() != null
                && job.getOrder().getStatus() == OrderStatus.IN_PRODUCTION;
    }

    private static boolean canComplete(PrintJob job) {
        return job != null && job.getId() != null
                && job.getStatus() == PrintJobStatus.IN_PROGRESS
                && job.getOrder() != null
                && job.getOrder().getStatus() == OrderStatus.IN_PRODUCTION;
    }

    private void updateJobAction() {
        PrintJob job = jobsTable.getSelectionModel().getSelectedItem();
        startJobButton.setDisable(busy || !canStart(job));
        completeJobButton.setDisable(busy || !canComplete(job));
        failJobButton.setDisable(busy || !canComplete(job));
        if (job == null) {
            jobActionHintLabel.setText("Seleccioná un trabajo para ver las acciones disponibles.");
        } else if (canComplete(job)) {
            jobActionHintLabel.setText("El trabajo está En proceso. Podés finalizarlo "
                    + "o registrar una falla con las capas del slicer.");
        } else if (job.getStatus() == PrintJobStatus.FAILED) {
            jobActionHintLabel.setText("Intento fallido registrado. Seleccioná el nuevo trabajo "
                    + "pendiente del mismo pedido para iniciar el reintento manualmente.");
        } else if (job.getStatus() != PrintJobStatus.PENDING) {
            jobActionHintLabel.setText("El trabajo ya fue cerrado o su pedido no está En producción.");
        } else if (job.getOrder() == null
                || job.getOrder().getStatus() != OrderStatus.IN_PRODUCTION) {
            jobActionHintLabel.setText("Primero poné el pedido En producción desde Pedidos.");
        } else {
            jobActionHintLabel.setText("Podés iniciar este trabajo. No se descontará stock todavía.");
        }
    }

    @FXML
    private void startSelectedJob() {
        if (busy) return;
        PrintJob selected = jobsTable.getSelectionModel().getSelectedItem();
        if (!canStart(selected)) return;
        int jobId = selected.getId();
        int orderId = selected.getOrder().getId();
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Trabajo #" + jobId + " del pedido #" + orderId
                + "\n\nSe registrará la fecha de inicio y el estado En proceso."
                + " No se descontará filamento todavía.",
                ButtonType.OK, ButtonType.CANCEL);
        confirmation.setTitle("Iniciar trabajo — Costeo 3D");
        confirmation.setHeaderText("¿Confirmás el inicio del trabajo?");
        confirmation.initOwner(jobsTable.getScene().getWindow());
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        setBusy(true);
        statusLabel.setText("Iniciando trabajo #" + jobId + " en MySQL...");
        Task<PrintJob> task = new Task<>() {
            @Override protected PrintJob call() throws BusinessRuleException {
                return productionService.startJob(jobId, LocalDateTime.now());
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            restoreSelectedId = jobId;
            reloadJobs();
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            LOGGER.log(Level.WARNING, "No se pudo iniciar el trabajo #" + jobId, error);
            setBusy(false);
            Alert failure = new Alert(Alert.AlertType.ERROR,
                    error instanceof BusinessRuleException ? error.getMessage()
                            : "No se pudo iniciar el trabajo. Revisá la consola y MySQL.",
                    ButtonType.OK);
            failure.setTitle("No se pudo iniciar el trabajo");
            failure.initOwner(jobsTable.getScene().getWindow());
            failure.showAndWait();
            restoreSelectedId = jobId;
            reloadJobs();
        });
        Thread worker = new Thread(task, "costeo3d-iniciar-trabajo-" + jobId);
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void completeSelectedJob() {
        if (busy) return;
        PrintJob selected = jobsTable.getSelectionModel().getSelectedItem();
        if (!canComplete(selected)) return;
        int jobId = selected.getId();
        try {
            URL resource = Objects.requireNonNull(getClass().getResource(COMPLETION_FXML),
                    "No se encontró job-completion.fxml.");
            JobCompletionController formController = new JobCompletionController(
                    productionService, catalogService, selected);
            FXMLLoader loader = new FXMLLoader(resource);
            loader.setControllerFactory(type -> {
                if (type == JobCompletionController.class) return formController;
                throw new IllegalArgumentException(
                        "Controlador JavaFX no registrado: " + type.getName());
            });
            Parent form = loader.load();
            Stage modal = new Stage();
            modal.setTitle("Finalizar trabajo #" + jobId + " — Costeo 3D");
            modal.initOwner(jobsTable.getScene().getWindow());
            modal.initModality(Modality.WINDOW_MODAL);
            Scene scene = new Scene(form, 790, 685);
            URL css = getClass().getResource(
                    "/com/costeo3d/presentation/javafx/css/app.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            modal.setScene(scene);
            modal.setMinWidth(690);
            modal.setMinHeight(540);
            formController.setDialogStage(modal);
            modal.setOnCloseRequest(event -> {
                if (formController.isBusy()) event.consume();
            });
            modal.showAndWait();
            if (formController.wasCompleted()) {
                var result = formController.getCompletionResult();
                StringBuilder detail = new StringBuilder("Se guardaron ")
                        .append(result.consumptionIds().size())
                        .append(" consumo(s) y se descontó el stock de MySQL.\n\n")
                        .append("Gramos restantes por bobina:\n");
                result.remainingGramsBySpool().entrySet().stream()
                        .sorted(java.util.Map.Entry.comparingByKey())
                        .forEach(entry -> detail.append("Bobina #")
                                .append(entry.getKey()).append(": ")
                                .append(entry.getValue().toPlainString())
                                .append(" g\n"));
                Alert success = new Alert(Alert.AlertType.INFORMATION,
                        detail.toString(), ButtonType.OK);
                success.setTitle("Trabajo finalizado");
                success.setHeaderText("Finalización confirmada");
                success.initOwner(jobsTable.getScene().getWindow());
                success.showAndWait();
                restoreSelectedId = jobId;
                reloadJobs();
            }
        } catch (IOException | IllegalStateException | IllegalArgumentException exception) {
            LOGGER.log(Level.WARNING, "No se pudo abrir el formulario de finalización.",
                    exception);
            Alert failure = new Alert(Alert.AlertType.ERROR,
                    "No se pudo abrir el formulario. Revisá la consola de Eclipse.",
                    ButtonType.OK);
            failure.initOwner(jobsTable.getScene().getWindow());
            failure.showAndWait();
        }
    }

    @FXML
    private void failSelectedJob() {
        if (busy) return;
        PrintJob selected = jobsTable.getSelectionModel().getSelectedItem();
        if (!canComplete(selected)) return;
        int jobId = selected.getId();
        try {
            URL resource = Objects.requireNonNull(getClass().getResource(FAILURE_FXML),
                    "No se encontró job-failure.fxml.");
            JobFailureController formController = new JobFailureController(
                    productionService, catalogService, selected);
            FXMLLoader loader = new FXMLLoader(resource);
            loader.setControllerFactory(type -> {
                if (type == JobFailureController.class) return formController;
                throw new IllegalArgumentException(
                        "Controlador JavaFX no registrado: " + type.getName());
            });
            Parent form = loader.load();
            Stage modal = new Stage();
            modal.setTitle("Registrar falla del trabajo #" + jobId + " — Costeo 3D");
            modal.initOwner(jobsTable.getScene().getWindow());
            modal.initModality(Modality.WINDOW_MODAL);
            Scene scene = new Scene(form, 790, 685);
            URL css = getClass().getResource(
                    "/com/costeo3d/presentation/javafx/css/app.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            modal.setScene(scene);
            modal.setMinWidth(690);
            modal.setMinHeight(540);
            formController.setDialogStage(modal);
            modal.setOnCloseRequest(event -> {
                if (formController.isBusy()) event.consume();
            });
            modal.showAndWait();
            if (formController.wasFailed()) {
                var result = formController.getFailureResult();
                StringBuilder detail = new StringBuilder("Se registró la falla y la merma "
                        + "por capas junto a " + result.consumptionIds().size()
                        + " consumo(s). Se descontó el stock de MySQL.\n\n"
                        + "Se creó el trabajo #" + result.retryJobId()
                        + " como PENDIENTE para el mismo pedido.\n"
                        + "Inicialo manualmente desde Producción.\n\n"
                        + "Gramos restantes por bobina:\n");
                result.remainingGramsBySpool().entrySet().stream()
                        .sorted(java.util.Map.Entry.comparingByKey())
                        .forEach(entry -> detail.append("Bobina #")
                                .append(entry.getKey()).append(": ")
                                .append(entry.getValue().toPlainString())
                                .append(" g\n"));
                Alert success = new Alert(Alert.AlertType.INFORMATION,
                        detail.toString(), ButtonType.OK);
                success.setTitle("Impresión fallida registrada");
                success.setHeaderText("Falla y descuento confirmados");
                success.initOwner(jobsTable.getScene().getWindow());
                success.showAndWait();
                searchField.clear();
                statusFilterCombo.setValue(ALL_STATES);
                restoreSelectedId = result.retryJobId() == null ? jobId : result.retryJobId();
                pageIndex = 0;
                reloadJobs();
            }
        } catch (IOException | IllegalStateException | IllegalArgumentException exception) {
            LOGGER.log(Level.WARNING, "No se pudo abrir el formulario de falla.", exception);
            Alert failure = new Alert(Alert.AlertType.ERROR,
                    "No se pudo abrir el formulario. Revisá la consola de Eclipse.",
                    ButtonType.OK);
            failure.initOwner(jobsTable.getScene().getWindow());
            failure.showAndWait();
        }
    }

    private static String describe(PrintJob job) {
        StringBuilder text = new StringBuilder();
        Order order = job.getOrder();
        text.append("Estado del trabajo: ").append(statusText(job.getStatus())).append('\n');
        text.append("Pedido asociado: #").append(orderId(job) == null ? "—" : orderId(job))
                .append('\n');
        text.append("Cliente: ").append(customerName(job)).append('\n');
        text.append("Estado del pedido: ")
                .append(order == null ? "—" : orderStatusText(order.getStatus())).append('\n');
        text.append("Modelo: ").append(modelName(job)).append('\n');
        if (job.getRetryOfJobId() != null) {
            text.append("Reintento del trabajo #").append(job.getRetryOfJobId()).append('\n');
        }
        text.append("Duración estimada: ").append(job.getEstimatedPrintTimeMinutes())
                .append(" minutos\n");
        text.append("Creado: ").append(formatDate(job.getCreatedAt())).append('\n');
        text.append("Inicio: ").append(formatDate(job.getStartedAt())).append('\n');
        text.append("Finalización: ").append(formatDate(job.getFinishedAt())).append('\n');
        text.append("\nLos consumos se registran al finalizar o al fallar. "
                + "Ambas operaciones descuentan stock mediante una transacción MySQL.");
        return text.toString();
    }

    private static String idText(Integer id) {
        return id == null ? "" : id.toString();
    }

    private static Integer orderId(PrintJob job) {
        return job.getOrder() == null ? null : job.getOrder().getId();
    }

    private static String customerName(PrintJob job) {
        Order order = job.getOrder();
        return order == null || order.getQuote() == null
                || order.getQuote().getCustomer() == null
                ? "Sin cliente" : order.getQuote().getCustomer().getName();
    }

    private static String modelName(PrintJob job) {
        return job.getModel() == null ? "Personalizado (sin modelo)"
                : job.getModel().getName();
    }

    private static String formatDate(LocalDateTime date) {
        return date == null ? "—" : DATE_FORMAT.format(date);
    }

    private static String statusText(PrintJobStatus status) {
        if (status == null) return "Desconocido";
        return switch (status) {
            case PENDING -> "Pendiente";
            case IN_PROGRESS -> "En proceso";
            case COMPLETED -> "Finalizado";
            case FAILED -> "Fallido";
        };
    }

    private static String statusFilterText(PrintJobStatus status) {
        if (status == null) return "";
        return switch (status) {
            case PENDING -> "Pendientes";
            case IN_PROGRESS -> "En proceso";
            case COMPLETED -> "Finalizados";
            case FAILED -> "Fallidos";
        };
    }

    private static String orderStatusText(OrderStatus status) {
        if (status == null) return "Desconocido";
        return switch (status) {
            case PENDING -> "Pendiente";
            case IN_PRODUCTION -> "En producción";
            case READY -> "Listo";
            case DELIVERED -> "Entregado";
            case CANCELLED -> "Cancelado";
        };
    }

    private Integer selectedId() {
        PrintJob selected = jobsTable.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.getId();
    }

    private void setBusy(boolean value) {
        busy = value;
        loadingIndicator.setVisible(value);
        loadingIndicator.setManaged(value);
        refreshButton.setDisable(value);
        clearFiltersButton.setDisable(value);
        searchField.setDisable(value);
        statusFilterCombo.setDisable(value);
        updateJobAction();
        if (value) {
            previousPageButton.setDisable(true);
            nextPageButton.setDisable(true);
        }
    }
}
