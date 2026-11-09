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

import com.costeo3d.application.service.PersistentQuoteService;
import com.costeo3d.application.service.CatalogService;
import com.costeo3d.domain.enums.QuoteStatus;
import com.costeo3d.domain.exception.BusinessRuleException;
import com.costeo3d.domain.model.Quote;
import com.costeo3d.domain.model.QuoteItem;
import com.costeo3d.domain.model.QuoteMaterialItem;

import javafx.application.Platform;
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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;

/** E11.1: consulta y seguimiento de presupuestos; el cálculo queda en CostCalculator. */
public final class QuotesController {

    private static final Logger LOGGER = Logger.getLogger(QuotesController.class.getName());
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String ALL_STATES = "Todos los estados";
    private static final String FORM_RESOURCE =
            "/com/costeo3d/presentation/javafx/view/quote-form.fxml";
    private static final int PAGE_SIZE = 10;
    private static final double HEADER_HEIGHT = 38;
    private static final double ROW_HEIGHT = 27;
    private static final double BORDER_HEIGHT = 2;

    private final PersistentQuoteService quotesService;
    private final CatalogService catalogService;
    private List<Quote> loadedQuotes = List.of();
    private int pageIndex;
    private boolean busy;
    private boolean loadFailed;
    private boolean rendering;
    private String feedback;
    private Integer restoreSelectedId;

    @FXML private TableView<Quote> quotesTable;
    @FXML private TableColumn<Quote, Integer> idColumn;
    @FXML private TableColumn<Quote, String> customerColumn;
    @FXML private TableColumn<Quote, LocalDateTime> issuedColumn;
    @FXML private TableColumn<Quote, LocalDateTime> expiresColumn;
    @FXML private TableColumn<Quote, BigDecimal> totalColumn;
    @FXML private TableColumn<Quote, String> statusColumn;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilterCombo;
    @FXML private Label statusLabel;
    @FXML private Label pageLabel;
    @FXML private Label detailTitle;
    @FXML private TextArea detailArea;
    @FXML private Button previousPageButton;
    @FXML private Button nextPageButton;
    @FXML private Button refreshButton;
    @FXML private Button newQuoteButton;
    @FXML private Button expiryButton;
    @FXML private Button acceptButton;
    @FXML private Button cancelButton;
    @FXML private ProgressIndicator loadingIndicator;

    public QuotesController(PersistentQuoteService quotesService, CatalogService catalogService) {
        this.quotesService = Objects.requireNonNull(quotesService,
                "Se requiere PersistentQuoteService.");
        this.catalogService = Objects.requireNonNull(catalogService,
                "Se requiere CatalogService.");
    }

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().getId()));
        customerColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getCustomer() == null ? "" :
                        cell.getValue().getCustomer().getName()));
        issuedColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().getIssuedAt()));
        expiresColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().getExpiresAt()));
        totalColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().getTotal()));
        statusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                statusText(cell.getValue().getStatus())));
        issuedColumn.setCellFactory(column -> dateCell());
        expiresColumn.setCellFactory(column -> dateCell());
        totalColumn.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal amount, boolean empty) {
                super.updateItem(amount, empty);
                setText(empty || amount == null ? null : money(amount));
            }
        });
        customerColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
        statusColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
        quotesTable.setFixedCellSize(ROW_HEIGHT);
        quotesTable.setSortPolicy(table -> {
            renderPage(false);
            return true;
        });
        quotesTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, current) -> updateDetailAndActions());
        statusFilterCombo.getItems().setAll(ALL_STATES, "Vigentes", "Aceptados",
                "Vencidos", "Cancelados");
        statusFilterCombo.setValue(ALL_STATES);
        searchField.textProperty().addListener((obs, oldValue, value) -> renderPage(true));
        statusFilterCombo.valueProperty().addListener(
                (obs, oldValue, value) -> renderPage(true));
        idColumn.setSortType(TableColumn.SortType.DESCENDING);
        quotesTable.getSortOrder().setAll(idColumn);
        reloadQuotes();
    }

    /** Abre el formulario de un concepto/material sin alterar el listado. */
    @FXML
    private void newQuote() {
        if (busy) return;
        try {
            URL resource = Objects.requireNonNull(getClass().getResource(FORM_RESOURCE),
                    "No se encontró quote-form.fxml.");
            QuoteFormController controller = new QuoteFormController(catalogService, quotesService);
            FXMLLoader loader = new FXMLLoader(resource);
            loader.setControllerFactory(type -> {
                if (type == QuoteFormController.class) return controller;
                throw new IllegalArgumentException("Controlador no registrado: " + type.getName());
            });
            Parent form = loader.load();
            Stage modal = new Stage();
            modal.setTitle("Nuevo presupuesto — Costeo 3D");
            modal.initOwner(quotesTable.getScene().getWindow());
            modal.initModality(Modality.WINDOW_MODAL);
            Scene scene = new Scene(form, 780, 710);
            URL css = getClass().getResource(
                    "/com/costeo3d/presentation/javafx/css/app.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            modal.setScene(scene);
            controller.setDialogStage(modal);
            // Evitar que el foco inicial de un control desplace el formulario
            // hacia abajo al abrir el dialogo.
            if (form instanceof ScrollPane scrollPane) {
                modal.setOnShown(event -> Platform.runLater(() -> {
                    scrollPane.requestFocus();
                    scrollPane.setVvalue(scrollPane.getVmin());
                }));
            }
            modal.showAndWait();
            if (controller.getCreatedQuoteId() != null) {
                restoreSelectedId = controller.getCreatedQuoteId();
                searchField.clear();
                statusFilterCombo.setValue(ALL_STATES);
                pageIndex = 0;
                feedback = "Presupuesto #" + controller.getCreatedQuoteId() + " emitido correctamente.";
                reloadQuotes();
            }
        } catch (IOException | IllegalStateException exception) {
            // Registrar la causa completa: LoadException suele envolver el error real.
            LOGGER.log(Level.WARNING, "No se pudo abrir el formulario de presupuestos.", exception);
            new Alert(Alert.AlertType.ERROR,
                    "No se pudo abrir el formulario. Revisá la consola de Eclipse "
                    + "para ver la causa detallada.",
                    ButtonType.OK).showAndWait();
        }
    }

    private static TableCell<Quote, LocalDateTime> dateCell() {
        return new TableCell<>() {
            @Override protected void updateItem(LocalDateTime date, boolean empty) {
                super.updateItem(date, empty);
                setText(empty || date == null ? null : DATE_FORMAT.format(date));
            }
        };
    }

    @FXML
    private void reloadQuotes() {
        if (busy) return;
        Integer selectedId = selectedId();
        if (selectedId != null) restoreSelectedId = selectedId;
        setBusy(true);
        statusLabel.setText("Consultando presupuestos en MySQL...");
        Task<List<Quote>> task = new Task<>() {
            @Override protected List<Quote> call() {
                return quotesService.findAll();
            }
        };
        task.setOnSucceeded(event -> {
            loadedQuotes = List.copyOf(task.getValue());
            loadFailed = false;
            setBusy(false);
            renderPage(false);
        });
        task.setOnFailed(event -> {
            loadedQuotes = List.of();
            loadFailed = true;
            Throwable error = task.getException();
            LOGGER.log(Level.WARNING, "Error al consultar presupuestos ({0}).",
                    error == null ? "desconocido" : error.getClass().getSimpleName());
            setBusy(false);
            renderPage(false);
        });
        start(task, "costeo3d-listar-presupuestos");
    }

    /** Actualiza en MySQL los presupuestos vigentes cuyo plazo ya se cumplió. */
    @FXML
    private void checkExpirations() {
        if (busy) return;
        setBusy(true);
        statusLabel.setText("Comprobando vencimientos...");
        Task<Integer> task = new Task<>() {
            @Override protected Integer call() throws BusinessRuleException {
                int count = 0;
                LocalDateTime now = LocalDateTime.now();
                for (Quote quote : loadedQuotes) {
                    if (quote.getStatus() == QuoteStatus.VALID
                            && !now.isBefore(quote.getExpiresAt())) {
                        Quote result = quotesService.expireIfNecessary(quote.getId(), now);
                        if (result.getStatus() == QuoteStatus.EXPIRED) count++;
                    }
                }
                return count;
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            feedback = "Vencimientos actualizados: " + task.getValue() + ".";
            reloadQuotes();
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            LOGGER.log(Level.WARNING, "No se pudieron verificar vencimientos ({0}).",
                    error == null ? "desconocido" : error.getClass().getSimpleName());
            setBusy(false);
            feedback = error instanceof BusinessRuleException ? error.getMessage()
                    : "No se pudieron verificar todos los vencimientos.";
            reloadQuotes();
        });
        start(task, "costeo3d-verificar-vencimientos");
    }

    @FXML private void clearFilters() {
        searchField.clear();
        statusFilterCombo.setValue(ALL_STATES);
        renderPage(true);
    }

    private void renderPage(boolean resetPage) {
        if (rendering) return;
        rendering = true;
        try {
            if (resetPage) pageIndex = 0;
            String query = searchField.getText() == null ? "" :
                    searchField.getText().trim().toLowerCase(Locale.ROOT);
            String filter = statusFilterCombo.getValue();
            List<Quote> filtered = new ArrayList<>();
            for (Quote quote : loadedQuotes) {
                String id = quote.getId() == null ? "" : quote.getId().toString();
                String customer = quote.getCustomer() == null ? "" :
                        quote.getCustomer().getName();
                boolean matches = query.isEmpty() || id.contains(query) ||
                        customer != null && customer.toLowerCase(Locale.ROOT).contains(query);
                boolean stateMatches = filter == null || ALL_STATES.equals(filter) ||
                        filter.equals(switch (quote.getStatus()) {
                            case VALID -> "Vigentes";
                            case ACCEPTED -> "Aceptados";
                            case EXPIRED -> "Vencidos";
                            case CANCELLED -> "Cancelados";
                        });
                if (matches && stateMatches) filtered.add(quote);
            }
            Comparator<Quote> byId = Comparator.comparing(Quote::getId,
                    Comparator.nullsLast(Integer::compareTo));
            Comparator<Quote> currentSort = quotesTable.getComparator();
            if (currentSort == null || quotesTable.getSortOrder().isEmpty()) {
                filtered.sort(byId.reversed());
            } else {
                filtered.sort(currentSort.thenComparing(byId));
            }
            int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            pageIndex = Math.min(pageIndex, pages - 1);
            int from = pageIndex * PAGE_SIZE;
            int to = Math.min(from + PAGE_SIZE, filtered.size());
            Integer selectedId = restoreSelectedId != null ? restoreSelectedId : selectedId();
            List<Quote> visible = filtered.subList(from, to);
            quotesTable.setItems(FXCollections.observableArrayList(visible));
            if (selectedId != null) {
                visible.stream().filter(q -> Objects.equals(q.getId(), selectedId))
                        .findFirst().ifPresent(quotesTable.getSelectionModel()::select);
            }
            restoreSelectedId = null;
            updateDetailAndActions();
            expiryButton.setDisable(busy || loadedQuotes.stream().noneMatch(q ->
                    q.getStatus() == QuoteStatus.VALID
                    && !LocalDateTime.now().isBefore(q.getExpiresAt())));
            int rows = visible.isEmpty() ? 4 : visible.size();
            double height = HEADER_HEIGHT + rows * ROW_HEIGHT + BORDER_HEIGHT;
            quotesTable.setMinHeight(height);
            quotesTable.setPrefHeight(height);
            quotesTable.setMaxHeight(height);
            quotesTable.setPlaceholder(new Label(loadFailed
                    ? "No se pudieron consultar los presupuestos."
                    : loadedQuotes.isEmpty() ? "Todavía no hay presupuestos registrados."
                    : "Ningún presupuesto coincide con los filtros."));
            pageLabel.setText("Página " + (pageIndex + 1) + " de " + pages);
            previousPageButton.setDisable(busy || pageIndex == 0);
            nextPageButton.setDisable(busy || pageIndex == pages - 1);
            if (loadFailed) {
                statusLabel.setText("No se pudo consultar MySQL. Revisá la conexión.");
            } else {
                String summary = "Coincidencias: " + filtered.size() + " de "
                        + loadedQuotes.size() + " presupuestos.";
                statusLabel.setText(feedback == null ? summary : feedback + " " + summary);
                feedback = null;
            }
        } finally {
            rendering = false;
        }
    }

    @FXML private void previousPage() {
        if (!busy && pageIndex > 0) { pageIndex--; renderPage(false); }
    }

    @FXML private void nextPage() {
        if (!busy && !nextPageButton.isDisabled()) { pageIndex++; renderPage(false); }
    }

    @FXML private void acceptSelected() {
        Quote quote = quotesTable.getSelectionModel().getSelectedItem();
        if (quote == null || quote.getStatus() != QuoteStatus.VALID) return;
        if (!LocalDateTime.now().isBefore(quote.getExpiresAt())) {
            // Persistir la expiración con la regla existente, sin aceptar el presupuesto.
            updateQuote(quote, "vencer");
            return;
        }
        if (confirm("Aceptar presupuesto",
                "¿Aceptar el presupuesto #" + quote.getId() + " de "
                        + quote.getCustomer().getName() + "?")) {
            updateQuote(quote, "aceptar");
        }
    }

    @FXML private void cancelSelected() {
        Quote quote = quotesTable.getSelectionModel().getSelectedItem();
        // No cancelar desde esta pantalla presupuestos ya aceptados: podrían tener pedidos.
        if (quote == null || quote.getStatus() != QuoteStatus.VALID) return;
        if (confirm("Cancelar presupuesto",
                "¿Cancelar el presupuesto #" + quote.getId()
                        + "? Esta acción no se puede revertir.")) {
            updateQuote(quote, "cancelar");
        }
    }

    private void updateQuote(Quote quote, String operation) {
        if (busy) return;
        restoreSelectedId = quote.getId();
        setBusy(true);
        statusLabel.setText("Actualizando el presupuesto #" + quote.getId() + "...");
        Task<Quote> task = new Task<>() {
            @Override protected Quote call() throws BusinessRuleException {
                return switch (operation) {
                    case "aceptar" -> quotesService.acceptQuote(quote.getId(), LocalDateTime.now());
                    case "cancelar" -> quotesService.cancelQuote(quote.getId());
                    case "vencer" -> quotesService.expireIfNecessary(quote.getId(), LocalDateTime.now());
                    default -> throw new IllegalArgumentException("Operación no admitida.");
                };
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            feedback = switch (operation) {
                case "aceptar" -> "Presupuesto aceptado.";
                case "cancelar" -> "Presupuesto cancelado.";
                default -> "Vencimiento actualizado.";
            };
            reloadQuotes();
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            LOGGER.log(Level.WARNING, "No se pudo {0} el presupuesto ({1}).",
                    new Object[] { operation,
                            error == null ? "desconocido" : error.getClass().getSimpleName() });
            setBusy(false);
            feedback = error instanceof BusinessRuleException ? error.getMessage() :
                    "No se pudo actualizar el presupuesto. Revisá la conexión.";
            reloadQuotes();
        });
        start(task, "costeo3d-estado-presupuesto");
    }

    private boolean confirm(String title, String message) {
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION, message,
                ButtonType.OK, ButtonType.CANCEL);
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.initOwner(quotesTable.getScene().getWindow());
        return dialog.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void updateDetailAndActions() {
        Quote selected = quotesTable.getSelectionModel().getSelectedItem();
        detailTitle.setText(selected == null ? "Detalle del presupuesto" :
                "Presupuesto #" + selected.getId());
        detailArea.setText(selected == null ?
                "Seleccioná un presupuesto de la tabla para consultar sus detalles." :
                describe(selected));
        boolean valid = selected != null && selected.getStatus() == QuoteStatus.VALID;
        boolean beforeExpiry = valid && LocalDateTime.now().isBefore(selected.getExpiresAt());
        acceptButton.setDisable(busy || !beforeExpiry);
        cancelButton.setDisable(busy || !beforeExpiry);
    }

    private static String describe(Quote quote) {
        StringBuilder result = new StringBuilder();
        result.append("Cliente: ").append(quote.getCustomer().getName()).append('\n');
        result.append("Estado: ").append(statusText(quote.getStatus())).append('\n');
        result.append("Emitido: ").append(DATE_FORMAT.format(quote.getIssuedAt())).append('\n');
        result.append("Vence: ").append(DATE_FORMAT.format(quote.getExpiresAt())).append('\n');
        result.append("Total: $").append(money(quote.getTotal())).append("\n\n");
        int position = 1;
        for (QuoteItem item : quote.getItems()) {
            result.append(position++).append(". ").append(item.getDescription())
                    .append("  × ").append(item.getQuantity())
                    .append("  |  Precio unitario: $").append(money(item.getUnitPrice()))
                    .append('\n');
            result.append("    Tiempo: ").append(item.getEstimatedPrintTimeMinutes())
                    .append(" min | Electricidad: $").append(money(item.getElectricityCost()))
                    .append(" | Amortización: $").append(money(item.getAmortizationCost()))
                    .append(" | Margen: ").append(item.getMarginPercentage().toPlainString())
                    .append("%\n");
            for (QuoteMaterialItem material : item.getMaterials()) {
                result.append("    • ").append(material.getMaterial().getBrand())
                        .append(" / ").append(material.getMaterial().getType())
                        .append(" — ").append(material.getEstimatedGrams().toPlainString())
                        .append(" g a $").append(material.getAppliedCostPerGram().toPlainString())
                        .append("/g\n");
            }
        }
        return result.toString();
    }

    private Integer selectedId() {
        Quote selected = quotesTable.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.getId();
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        loadingIndicator.setVisible(busy);
        loadingIndicator.setManaged(busy);
        refreshButton.setDisable(busy);
        newQuoteButton.setDisable(busy);
        expiryButton.setDisable(busy || loadedQuotes.stream().noneMatch(q ->
                q.getStatus() == QuoteStatus.VALID
                && !LocalDateTime.now().isBefore(q.getExpiresAt())));
        searchField.setDisable(busy);
        statusFilterCombo.setDisable(busy);
        updateDetailAndActions();
    }

    private static String money(BigDecimal amount) {
        return amount == null ? "—" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String statusText(QuoteStatus status) {
        return switch (status) {
            case VALID -> "Vigente";
            case ACCEPTED -> "Aceptado";
            case EXPIRED -> "Vencido";
            case CANCELLED -> "Cancelado";
        };
    }

    private static void start(Task<?> task, String name) {
        Thread worker = new Thread(task, name);
        worker.setDaemon(true);
        worker.start();
    }
}
