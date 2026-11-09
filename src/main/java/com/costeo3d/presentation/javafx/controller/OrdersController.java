package com.costeo3d.presentation.javafx.controller;

import java.io.IOException;
import java.net.URL;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.application.service.PersistentOrderService;
import com.costeo3d.domain.exception.BusinessRuleException;
import com.costeo3d.application.service.PersistentQuoteService;
import com.costeo3d.application.service.CatalogService;
import com.costeo3d.domain.enums.OrderStatus;
import com.costeo3d.domain.enums.PrintJobStatus;
import com.costeo3d.domain.model.Order;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.domain.model.Quote;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

/** E11.3: consulta y creación de pedidos con trabajos persistentes. */
public final class OrdersController {

    private static final Logger LOGGER = Logger.getLogger(OrdersController.class.getName());
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String ALL_STATES = "Todos los estados";
    private static final int PAGE_SIZE = 10;
    private static final double HEADER_HEIGHT = 38;
    private static final double ROW_HEIGHT = 27;
    private static final double BORDER_HEIGHT = 2;

    private static final String JOB_PLAN_RESOURCE =
            "/com/costeo3d/presentation/javafx/view/job-planning.fxml";

    private static final String FORM_RESOURCE =
            "/com/costeo3d/presentation/javafx/view/order-form.fxml";

    private final PersistentOrderService ordersService;
    private final PersistentQuoteService quotesService;
    private final CatalogService catalogService;
    private List<Order> loadedOrders = List.of();
    private int pageIndex;
    private Integer restoreSelectedId;
    private Integer restoreSelectedJobId;
    private boolean busy;
    private boolean loadFailed;
    private boolean rendering;

    @FXML private TableView<PrintJob> jobsTable;
    @FXML private TableColumn<PrintJob, Integer> jobIdColumn;
    @FXML private TableColumn<PrintJob, String> jobModelColumn;
    @FXML private TableColumn<PrintJob, Integer> jobMinutesColumn;
    @FXML private TableColumn<PrintJob, String> jobStatusColumn;
    @FXML private Label jobHintLabel;
    @FXML private Button editJobButton;
    @FXML private Button startProductionButton;
    @FXML private Button markReadyButton;
    @FXML private Button deliverButton;
    @FXML private Button cancelOrderButton;
    @FXML private Label orderActionsHintLabel;
    @FXML private TableView<Order> ordersTable;
    @FXML private TableColumn<Order, Integer> idColumn;
    @FXML private TableColumn<Order, Integer> quoteColumn;
    @FXML private TableColumn<Order, String> customerColumn;
    @FXML private TableColumn<Order, LocalDateTime> createdColumn;
    @FXML private TableColumn<Order, Integer> jobsColumn;
    @FXML private TableColumn<Order, String> statusColumn;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilterCombo;
    @FXML private Label statusLabel;
    @FXML private Label pageLabel;
    @FXML private Label detailTitle;
    @FXML private TextArea detailArea;
    @FXML private Button previousPageButton;
    @FXML private Button nextPageButton;
    @FXML private Button refreshButton;
    @FXML private Button newOrderButton;
    @FXML private ProgressIndicator loadingIndicator;

    public OrdersController(PersistentOrderService ordersService,
            PersistentQuoteService quotesService, CatalogService catalogService) {
        this.ordersService = Objects.requireNonNull(ordersService,
                "Se requiere PersistentOrderService.");
        this.quotesService = Objects.requireNonNull(quotesService,
                "Se requiere PersistentQuoteService.");
        this.catalogService = Objects.requireNonNull(catalogService,
                "Se requiere CatalogService.");
    }

    @FXML
    private void newOrder() {
        if (busy) return;
        try {
            URL resource = Objects.requireNonNull(getClass().getResource(FORM_RESOURCE),
                    "No se encontró order-form.fxml.");
            OrderFormController formController = new OrderFormController(
                    ordersService, quotesService, catalogService);
            FXMLLoader loader = new FXMLLoader(resource);
            loader.setControllerFactory(type -> {
                if (type == OrderFormController.class) return formController;
                throw new IllegalArgumentException(
                        "Controlador no registrado: " + type.getName());
            });
            Parent form = loader.load();
            Stage modal = new Stage();
            modal.setTitle("Nuevo pedido — Costeo 3D");
            modal.initOwner(ordersTable.getScene().getWindow());
            modal.initModality(Modality.WINDOW_MODAL);
            Scene scene = new Scene(form, 760, 730);
            URL css = getClass().getResource(
                    "/com/costeo3d/presentation/javafx/css/app.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            modal.setScene(scene);
            formController.setDialogStage(modal);
            modal.setOnCloseRequest(event -> {
                if (formController.isBusy()) event.consume();
            });
            modal.showAndWait();
            Integer createdId = formController.getCreatedOrderId();
            if (createdId != null) {
                searchField.clear();
                statusFilterCombo.setValue(ALL_STATES);
                pageIndex = 0;
                ordersTable.getSelectionModel().clearSelection();
                restoreSelectedId = createdId;
                reloadOrders();
            }
        } catch (IOException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING, "No se pudo abrir el formulario de pedidos.", exception);
            new Alert(Alert.AlertType.ERROR,
                    "No se pudo abrir el formulario. Revisá la consola de Eclipse "
                    + "para ver la causa detallada.", ButtonType.OK).showAndWait();
        }
    }

    @FXML
    private void editSelectedJob() {
        if (busy) return;
        Order order = ordersTable.getSelectionModel().getSelectedItem();
        PrintJob job = jobsTable.getSelectionModel().getSelectedItem();
        if (order == null || order.getId() == null || job == null || job.getId() == null
                || order.getStatus() != OrderStatus.PENDING
                || job.getStatus() != PrintJobStatus.PENDING) return;
        try {
            URL resource = Objects.requireNonNull(getClass().getResource(JOB_PLAN_RESOURCE),
                    "No se encontró job-planning.fxml.");
            JobPlanningController formController = new JobPlanningController(
                    ordersService, catalogService, order.getId(), job);
            FXMLLoader loader = new FXMLLoader(resource);
            loader.setControllerFactory(type -> {
                if (type == JobPlanningController.class) return formController;
                throw new IllegalArgumentException("Controlador no registrado: " + type.getName());
            });
            Parent form = loader.load();
            Stage modal = new Stage();
            modal.setTitle("Editar planificación de trabajo — Costeo 3D");
            modal.initOwner(ordersTable.getScene().getWindow());
            modal.initModality(Modality.WINDOW_MODAL);
            Scene scene = new Scene(form, 680, 600);
            URL css = getClass().getResource(
                    "/com/costeo3d/presentation/javafx/css/app.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            modal.setScene(scene);
            formController.setDialogStage(modal);
            modal.setOnCloseRequest(event -> {
                if (formController.isBusy()) event.consume();
            });
            modal.showAndWait();
            if (formController.isSaved()) {
                restoreSelectedId = order.getId();
                restoreSelectedJobId = job.getId();
                reloadOrders();
            }
        } catch (IOException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING, "No se pudo abrir la edición del trabajo.", exception);
            new Alert(Alert.AlertType.ERROR,
                    "No se pudo abrir el formulario. Revisá la consola de Eclipse.",
                    ButtonType.OK).showAndWait();
        }
    }


    @FXML private void startSelectedOrder() {
        applyOrderAction(OrderAction.START);
    }

    @FXML private void markSelectedReady() {
        applyOrderAction(OrderAction.READY);
    }

    @FXML private void deliverSelectedOrder() {
        applyOrderAction(OrderAction.DELIVER);
    }

    @FXML private void cancelSelectedOrder() {
        applyOrderAction(OrderAction.CANCEL);
    }

    private enum OrderAction { START, READY, DELIVER, CANCEL }

    private void applyOrderAction(OrderAction action) {
        if (busy) return;
        Order selected = ordersTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getId() == null || !canApply(action, selected)) {
            return;
        }
        String label = switch (action) {
            case START -> "Iniciar producción";
            case READY -> "Marcar como listo";
            case DELIVER -> "Entregar pedido";
            case CANCEL -> "Cancelar pedido";
        };
        String explanation = switch (action) {
            case START -> "El pedido pasará a En producción. No se inicia ningún "
                    + "trabajo ni se descuenta stock todavía.";
            case READY -> "Todos sus trabajos deben estar finalizados en Producción.";
            case DELIVER -> "El pedido quedará como Entregado.";
            case CANCEL -> "Solo se puede cancelar un pedido pendiente con "
                    + "trabajos sin iniciar. Esta acción no borra registros.";
        };
        int id = selected.getId();
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Pedido #" + id + "\n\n" + explanation,
                ButtonType.OK, ButtonType.CANCEL);
        confirmation.setTitle(label + " — Costeo 3D");
        confirmation.setHeaderText("¿Confirmás: " + label.toLowerCase(Locale.ROOT) + "?");
        confirmation.initOwner(ordersTable.getScene().getWindow());
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        setBusy(true);
        statusLabel.setText(label + " en MySQL...");
        Task<Order> task = new Task<>() {
            @Override protected Order call() throws BusinessRuleException {
                return switch (action) {
                    case START -> ordersService.startProduction(id);
                    case READY -> ordersService.markAsReady(id);
                    case DELIVER -> ordersService.deliver(id);
                    case CANCEL -> ordersService.cancelPending(id);
                };
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            restoreSelectedId = id;
            reloadOrders();
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            LOGGER.log(Level.WARNING, "No se pudo ejecutar la acción sobre el pedido #" + id, error);
            setBusy(false);
            Alert failure = new Alert(Alert.AlertType.ERROR,
                    error instanceof BusinessRuleException
                            ? error.getMessage()
                            : "No se pudo actualizar el pedido. Revisá MySQL y la consola.",
                    ButtonType.OK);
            failure.setTitle("No se pudo cambiar el estado");
            failure.initOwner(ordersTable.getScene().getWindow());
            failure.showAndWait();
            restoreSelectedId = id;
            reloadOrders();
        });
        Thread worker = new Thread(task, "costeo3d-estado-pedido-" + id);
        worker.setDaemon(true);
        worker.start();
    }

    private static boolean canApply(OrderAction action, Order order) {
        List<PrintJob> jobs = order.getPrintJobs();
        return switch (action) {
            case START -> order.getStatus() == OrderStatus.PENDING && !jobs.isEmpty();
            case READY -> order.getStatus() == OrderStatus.IN_PRODUCTION
                    && !jobs.isEmpty()
                    && jobs.stream().allMatch(job -> job.getStatus() == PrintJobStatus.COMPLETED);
            case DELIVER -> order.getStatus() == OrderStatus.READY;
            case CANCEL -> order.getStatus() == OrderStatus.PENDING
                    && jobs.stream().allMatch(job -> job.getStatus() == PrintJobStatus.PENDING);
        };
    }

    private void updateOrderActions() {
        Order order = ordersTable.getSelectionModel().getSelectedItem();
        startProductionButton.setDisable(busy || order == null
                || !canApply(OrderAction.START, order));
        markReadyButton.setDisable(busy || order == null
                || !canApply(OrderAction.READY, order));
        deliverButton.setDisable(busy || order == null
                || !canApply(OrderAction.DELIVER, order));
        cancelOrderButton.setDisable(busy || order == null
                || !canApply(OrderAction.CANCEL, order));
        if (order == null) {
            orderActionsHintLabel.setText("Seleccioná un pedido para ver las acciones disponibles.");
        } else {
            String message = switch (order.getStatus()) {
                case PENDING -> "Podés iniciar producción o cancelar, si los trabajos no comenzaron.";
                case IN_PRODUCTION -> canApply(OrderAction.READY, order)
                        ? "Todos los trabajos están finalizados. Ya podés marcar el pedido como listo."
                        : "Para marcarlo listo, finalizá todos sus trabajos desde Producción.";
                case READY -> "Pedido listo: podés registrar su entrega.";
                case DELIVERED -> "Pedido entregado: no quedan cambios de estado disponibles.";
                case CANCELLED -> "Pedido cancelado: no se puede reactivar desde esta pantalla.";
            };
            orderActionsHintLabel.setText(message);
        }
    }

    @FXML
    private void initialize() {
        jobIdColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().getId()));
        jobModelColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getModel() == null ? "Personalizado (sin modelo)" :
                        cell.getValue().getModel().getName()));
        jobMinutesColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                cell.getValue().getEstimatedPrintTimeMinutes()));
        jobStatusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                printJobStatus(cell.getValue().getStatus())));
        jobsTable.setFixedCellSize(ROW_HEIGHT);
        jobsTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, previous, current) -> updateJobActions());
        idColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().getId()));
        quoteColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                cell.getValue().getQuote() == null ? null :
                        cell.getValue().getQuote().getId()));
        customerColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                customerName(cell.getValue())));
        createdColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().getCreatedAt()));
        jobsColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                cell.getValue().getPrintJobs().size()));
        statusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                statusText(cell.getValue().getStatus())));
        createdColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime date, boolean empty) {
                super.updateItem(date, empty);
                setText(empty || date == null ? null : DATE_FORMAT.format(date));
            }
        });
        customerColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
        statusColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
        ordersTable.setFixedCellSize(ROW_HEIGHT);
        ordersTable.setSortPolicy(table -> {
            renderPage(false);
            return true;
        });
        ordersTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, current) -> updateDetail());
        statusFilterCombo.getItems().setAll(ALL_STATES, "Pendientes", "En producción",
                "Listos", "Entregados", "Cancelados");
        statusFilterCombo.setValue(ALL_STATES);
        searchField.textProperty().addListener(
                (observable, previous, value) -> renderPage(true));
        statusFilterCombo.valueProperty().addListener(
                (observable, previous, value) -> renderPage(true));
        idColumn.setSortType(TableColumn.SortType.DESCENDING);
        ordersTable.getSortOrder().setAll(idColumn);
        reloadOrders();
    }

    @FXML
    private void reloadOrders() {
        if (busy) {
            return;
        }
        Integer selectedId = selectedId();
        if (selectedId != null) {
            restoreSelectedId = selectedId;
        }
        setBusy(true);
        statusLabel.setText("Consultando pedidos en MySQL...");
        Task<List<Order>> task = new Task<>() {
            @Override
            protected List<Order> call() {
                return ordersService.findAll();
            }
        };
        task.setOnSucceeded(event -> {
            loadedOrders = List.copyOf(task.getValue());
            loadFailed = false;
            setBusy(false);
            renderPage(false);
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            LOGGER.log(Level.WARNING, "No se pudieron consultar los pedidos.", error);
            loadedOrders = List.of();
            loadFailed = true;
            setBusy(false);
            renderPage(false);
        });
        Thread worker = new Thread(task, "costeo3d-listar-pedidos");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void clearFilters() {
        searchField.clear();
        statusFilterCombo.setValue(ALL_STATES);
        renderPage(true);
    }

    private void renderPage(boolean resetPage) {
        if (rendering) {
            return;
        }
        rendering = true;
        try {
            if (resetPage) {
                pageIndex = 0;
            }
            String query = searchField.getText() == null ? "" :
                    searchField.getText().trim().toLowerCase(Locale.ROOT);
            String filter = statusFilterCombo.getValue();
            List<Order> filtered = new ArrayList<>();
            for (Order order : loadedOrders) {
                String id = order.getId() == null ? "" : order.getId().toString();
                Quote quote = order.getQuote();
                String quoteId = quote == null || quote.getId() == null ? "" :
                        quote.getId().toString();
                String customer = customerName(order).toLowerCase(Locale.ROOT);
                boolean matches = query.isEmpty() || id.contains(query)
                        || quoteId.contains(query) || customer.contains(query);
                boolean stateMatches = filter == null || ALL_STATES.equals(filter)
                        || filter.equals(statusTextPlural(order.getStatus()));
                if (matches && stateMatches) {
                    filtered.add(order);
                }
            }
            Comparator<Order> byId = Comparator.comparing(Order::getId,
                    Comparator.nullsLast(Integer::compareTo));
            Comparator<Order> selectedSort = ordersTable.getComparator();
            if (selectedSort == null || ordersTable.getSortOrder().isEmpty()) {
                filtered.sort(byId.reversed());
            } else {
                filtered.sort(selectedSort.thenComparing(byId));
            }
            int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            pageIndex = Math.min(pageIndex, pages - 1);
            int from = pageIndex * PAGE_SIZE;
            int to = Math.min(from + PAGE_SIZE, filtered.size());
            List<Order> visible = filtered.subList(from, to);

            Integer selectedId = restoreSelectedId != null
                    ? restoreSelectedId : selectedId();
            ordersTable.setItems(FXCollections.observableArrayList(visible));
            if (selectedId != null) {
                visible.stream()
                        .filter(order -> Objects.equals(order.getId(), selectedId))
                        .findFirst()
                        .ifPresent(ordersTable.getSelectionModel()::select);
            }
            restoreSelectedId = null;
            updateDetail();
            int rows = visible.isEmpty() ? 4 : visible.size();
            double height = HEADER_HEIGHT + rows * ROW_HEIGHT + BORDER_HEIGHT;
            ordersTable.setMinHeight(height);
            ordersTable.setPrefHeight(height);
            ordersTable.setMaxHeight(height);
            ordersTable.setPlaceholder(new Label(loadFailed
                    ? "No se pudieron consultar los pedidos."
                    : loadedOrders.isEmpty() ? "Todavía no hay pedidos registrados."
                    : "No hay pedidos que coincidan con los filtros."));
            pageLabel.setText("Página " + (pageIndex + 1) + " de " + pages);
            previousPageButton.setDisable(busy || pageIndex == 0);
            nextPageButton.setDisable(busy || pageIndex == pages - 1);
            statusLabel.setText(loadFailed
                    ? "No se pudo consultar MySQL. Revisá la conexión."
                    : "Coincidencias: " + filtered.size() + " de "
                            + loadedOrders.size() + " pedidos.");
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

    private void updateDetail() {
        Order selected = ordersTable.getSelectionModel().getSelectedItem();
        Integer oldJobId = restoreSelectedJobId;
        if (oldJobId == null && jobsTable.getSelectionModel().getSelectedItem() != null) {
            oldJobId = jobsTable.getSelectionModel().getSelectedItem().getId();
        }
        jobsTable.setItems(FXCollections.observableArrayList(
                selected == null ? List.of() : selected.getPrintJobs()));
        if (oldJobId != null) {
            final Integer idToRestore = oldJobId;
            jobsTable.getItems().stream()
                    .filter(j -> Objects.equals(j.getId(), idToRestore))
                    .findFirst().ifPresent(jobsTable.getSelectionModel()::select);
        }
        if (selected != null) restoreSelectedJobId = null;
        int visibleRows = Math.max(3, jobsTable.getItems().size());
        double jobsHeight = HEADER_HEIGHT + visibleRows * ROW_HEIGHT + BORDER_HEIGHT;
        jobsTable.setMinHeight(jobsHeight);
        jobsTable.setPrefHeight(jobsHeight);
        jobsTable.setMaxHeight(jobsHeight);
        jobsTable.setPlaceholder(new Label(selected == null
                ? "Seleccioná un pedido para ver sus trabajos."
                : "Este pedido no tiene trabajos registrados."));
        updateJobActions();
        updateOrderActions();
        detailTitle.setText(selected == null ? "Detalle del pedido" :
                "Pedido #" + selected.getId());
        detailArea.setText(selected == null
                ? "Seleccioná un pedido de la tabla para consultar sus detalles."
                : describe(selected));
    }

    private void updateJobActions() {
        Order order = ordersTable.getSelectionModel().getSelectedItem();
        PrintJob job = jobsTable.getSelectionModel().getSelectedItem();
        boolean canEdit = !busy && order != null && job != null
                && order.getStatus() == OrderStatus.PENDING
                && job.getStatus() == PrintJobStatus.PENDING;
        editJobButton.setDisable(!canEdit);
        if (job == null) {
            jobHintLabel.setText("Seleccioná un trabajo. Solo los trabajos de pedidos pendientes se pueden editar.");
        } else {
            jobHintLabel.setText("Trabajo #" + job.getId() + " · "
                    + printJobStatus(job.getStatus()) + " · Inicio: "
                    + formatDate(job.getStartedAt()) + " · Fin: "
                    + formatDate(job.getFinishedAt()));
        }
    }

    private static String describe(Order order) {
        StringBuilder text = new StringBuilder();
        Quote quote = order.getQuote();
        text.append("Cliente: ").append(customerName(order)).append('\n');
        text.append("Estado: ").append(statusText(order.getStatus())).append('\n');
        text.append("Creado: ").append(formatDate(order.getCreatedAt())).append('\n');
        text.append("Presupuesto: #")
                .append(quote == null ? "—" : quote.getId()).append('\n');
        text.append("Total presupuestado: $")
                .append(quote == null ? "—" : money(quote.getTotal())).append("\n\n");
        List<PrintJob> jobs = order.getPrintJobs();
        text.append("Trabajos de impresión (").append(jobs.size()).append("):\n");
        if (jobs.isEmpty()) {
            text.append("  No hay trabajos asociados. Revisar integridad del pedido.\n");
        }
        for (PrintJob job : jobs) {
            String model = job.getModel() == null ? "Sin modelo asociado" :
                    job.getModel().getName();
            text.append("  • #").append(job.getId() == null ? "—" : job.getId())
                    .append(" — ").append(model)
                    .append(" — ").append(printJobStatus(job.getStatus()))
                    .append(" — ").append(job.getEstimatedPrintTimeMinutes()).append(" min")
                    .append('\n');
            if (job.getStartedAt() != null) {
                text.append("     Inicio: ").append(formatDate(job.getStartedAt())).append('\n');
            }
            if (job.getFinishedAt() != null) {
                text.append("     Fin: ").append(formatDate(job.getFinishedAt())).append('\n');
            }
        }
        return text.toString();
    }

    private static String customerName(Order order) {
        return order.getQuote() == null || order.getQuote().getCustomer() == null
                ? "Sin cliente" : order.getQuote().getCustomer().getName();
    }

    private static String formatDate(LocalDateTime date) {
        return date == null ? "—" : DATE_FORMAT.format(date);
    }

    private static String money(BigDecimal amount) {
        return amount == null ? "—" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String statusText(OrderStatus status) {
        if (status == null) {
            return "Desconocido";
        }
        return switch (status) {
            case PENDING -> "Pendiente";
            case IN_PRODUCTION -> "En producción";
            case READY -> "Listo";
            case DELIVERED -> "Entregado";
            case CANCELLED -> "Cancelado";
        };
    }

    private static String statusTextPlural(OrderStatus status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case PENDING -> "Pendientes";
            case IN_PRODUCTION -> "En producción";
            case READY -> "Listos";
            case DELIVERED -> "Entregados";
            case CANCELLED -> "Cancelados";
        };
    }

    private static String printJobStatus(PrintJobStatus status) {
        if (status == null) {
            return "Desconocido";
        }
        return switch (status) {
            case PENDING -> "Pendiente";
            case IN_PROGRESS -> "En proceso";
            case COMPLETED -> "Finalizado";
            case FAILED -> "Fallido";
        };
    }

    private Integer selectedId() {
        Order selected = ordersTable.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.getId();
    }

    private void setBusy(boolean value) {
        busy = value;
        loadingIndicator.setVisible(value);
        loadingIndicator.setManaged(value);
        refreshButton.setDisable(value);
        newOrderButton.setDisable(value);
        searchField.setDisable(value);
        statusFilterCombo.setDisable(value);
        previousPageButton.setDisable(value || pageIndex == 0);
        nextPageButton.setDisable(value || nextPageButton.isDisabled());
        updateJobActions();
        updateOrderActions();
    }
}
