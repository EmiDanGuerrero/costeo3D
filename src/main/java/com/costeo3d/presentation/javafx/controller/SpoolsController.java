package com.costeo3d.presentation.javafx.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.application.service.CatalogService;
import com.costeo3d.domain.model.Spool;

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
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;

/** Vista de bobinas. El stock se consulta; los descuentos corresponden a Producción. */
public final class SpoolsController {
    private static final Logger LOGGER = Logger.getLogger(SpoolsController.class.getName());
    private static final String FORM_RESOURCE =
            "/com/costeo3d/presentation/javafx/view/spool-form.fxml";
    private static final String ALL_STATES = "Todos los estados";
    private static final int PAGE_SIZE = 10;
    private static final double HEADER_HEIGHT = 38;
    private static final double ROW_HEIGHT = 24;
    private static final double BORDER_HEIGHT = 2;

    private final CatalogService catalogService;
    private final Runnable showMaterials;
    private List<Spool> loadedSpools = List.of();
    private int pageIndex;
    private boolean loading;
    private boolean loadFailed;
    private boolean rendering;
    private String successMessage;

    @FXML private TableView<Spool> spoolsTable;
    @FXML private TableColumn<Spool, Integer> idColumn;
    @FXML private TableColumn<Spool, String> materialColumn;
    @FXML private TableColumn<Spool, String> colorColumn;
    @FXML private TableColumn<Spool, BigDecimal> initialWeightColumn;
    @FXML private TableColumn<Spool, BigDecimal> currentWeightColumn;
    @FXML private TableColumn<Spool, String> statusColumn;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilterCombo;
    @FXML private Label statusLabel;
    @FXML private Label pageLabel;
    @FXML private Button previousPageButton;
    @FXML private Button nextPageButton;
    @FXML private Button refreshButton;
    @FXML private Button newSpoolButton;
    @FXML private Button editSpoolButton;
    @FXML private Button spoolStatusButton;
    @FXML private ProgressIndicator loadingIndicator;

    public SpoolsController(CatalogService catalogService, Runnable showMaterials) {
        this.catalogService = Objects.requireNonNull(catalogService, "Se requiere CatalogService.");
        this.showMaterials = Objects.requireNonNull(showMaterials, "Se requiere navegación.");
    }

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getId()));
        materialColumn.setCellValueFactory(cell -> {
            var m = cell.getValue().getMaterial();
            return text(m == null ? "—" : m.getBrand() + " · " + m.getType());
        });
        colorColumn.setCellValueFactory(cell -> text(cell.getValue().getColor()));
        initialWeightColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().getInitialWeight()));
        currentWeightColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().getCurrentWeight()));
        statusColumn.setCellValueFactory(cell ->
                text(cell.getValue().isActive() ? "Activo" : "Inactivo"));
        materialColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
        colorColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
        statusColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
        spoolsTable.setSortPolicy(table -> { renderPage(false); return true; });
        spoolsTable.setFixedCellSize(ROW_HEIGHT);
        spoolsTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, previous, selected) -> updateActionAvailability());
        spoolsTable.setPlaceholder(new Label("No hay bobinas registradas."));
        statusFilterCombo.getItems().setAll(ALL_STATES, "Activas", "Inactivas");
        statusFilterCombo.setValue(ALL_STATES);
        searchField.textProperty().addListener((obs, oldValue, newValue) -> renderPage(true));
        statusFilterCombo.valueProperty().addListener((obs, oldValue, newValue) -> renderPage(true));
        reloadSpools();
    }

    @FXML
    private void returnToMaterials() {
        if (!loading) {
            showMaterials.run();
        }
    }

    @FXML
    private void reloadSpools() {
        if (loading) return;
        setLoading(true);
        statusLabel.setText("Consultando bobinas en MySQL...");
        Task<List<Spool>> task = new Task<>() {
            @Override protected List<Spool> call() { return catalogService.listSpools(); }
        };
        task.setOnSucceeded(event -> {
            loadedSpools = List.copyOf(task.getValue());
            loadFailed = false;
            setLoading(false);
            renderPage(false);
        });
        task.setOnFailed(event -> {
            loadedSpools = List.of();
            loadFailed = true;
            successMessage = null;
            Throwable error = task.getException();
            LOGGER.log(Level.WARNING, "Error al consultar bobinas (tipo: {0}).",
                    error == null ? "desconocido" : error.getClass().getSimpleName());
            setLoading(false);
            renderPage(true);
        });
        Thread worker = new Thread(task, "costeo3d-consulta-bobinas");
        worker.setDaemon(true);
        worker.start();
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
            String q = searchField.getText() == null ? "" :
                    searchField.getText().trim().toLowerCase(Locale.ROOT);
            String state = statusFilterCombo.getValue();
            List<Spool> filtered = new ArrayList<>();
            for (Spool s : loadedSpools) {
                boolean matches = q.isEmpty() || Integer.toString(s.getId()).contains(q)
                        || includes(s.getColor(), q)
                        || includes(s.getMaterial().getBrand(), q)
                        || includes(s.getMaterial().getType(), q);
                boolean stateMatches = state == null || ALL_STATES.equals(state)
                        || ("Activas".equals(state) && s.isActive())
                        || ("Inactivas".equals(state) && !s.isActive());
                if (matches && stateMatches) filtered.add(s);
            }
            Comparator<Spool> comparator = spoolsTable.getComparator();
            if (comparator != null && !spoolsTable.getSortOrder().isEmpty()) {
                filtered.sort(comparator.thenComparing(Spool::getId));
            } else {
                filtered.sort(Comparator.comparing(Spool::getId));
            }
            int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            pageIndex = Math.min(pageIndex, pages - 1);
            int from = pageIndex * PAGE_SIZE;
            int to = Math.min(from + PAGE_SIZE, filtered.size());
            spoolsTable.setItems(FXCollections.observableArrayList(filtered.subList(from, to)));
            int visibleRows = to == from ? 4 : to - from;
            double height = HEADER_HEIGHT + ROW_HEIGHT * visibleRows + BORDER_HEIGHT;
            spoolsTable.setMinHeight(height);
            spoolsTable.setPrefHeight(height);
            spoolsTable.setMaxHeight(height);
            spoolsTable.setPlaceholder(new Label(loadFailed
                    ? "No se pudieron consultar las bobinas."
                    : loadedSpools.isEmpty() ? "No hay bobinas registradas."
                    : "Ninguna bobina coincide con los filtros."));
            pageLabel.setText("Página " + (pageIndex + 1) + " de " + pages);
            previousPageButton.setDisable(loading || pageIndex == 0);
            nextPageButton.setDisable(loading || pageIndex >= pages - 1);
            updateActionAvailability();
            if (loadFailed) {
                statusLabel.setText("No se pudo consultar MySQL. Revisá la conexión.");
            } else {
                String summary = "Coincidencias: " + filtered.size() + " de " + loadedSpools.size()
                        + " bobinas · Filas " + (filtered.isEmpty() ? "0" : (from + 1) + "–" + to)
                        + ".";
                statusLabel.setText(successMessage == null ? summary : successMessage + " " + summary);
                successMessage = null;
            }
        } finally {
            rendering = false;
        }
    }

    private static boolean includes(String s, String q) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(q);
    }

    @FXML private void previousPage() {
        if (!loading && pageIndex > 0) { pageIndex--; renderPage(false); }
    }
    @FXML private void nextPage() {
        if (!loading && !nextPageButton.isDisabled()) { pageIndex++; renderPage(false); }
    }

    @FXML private void createSpool() { openForm(null); }

    @FXML private void editSpool() {
        Spool selected = spoolsTable.getSelectionModel().getSelectedItem();
        if (!loading && selected != null && selected.isActive()) {
            openForm(selected);
        }
    }

    @FXML
    private void changeSpoolStatus() {
        Spool selected = spoolsTable.getSelectionModel().getSelectedItem();
        if (loading || selected == null) return;

        boolean activate = !selected.isActive();
        String action = activate ? "Activar" : "Desactivar";
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.initOwner(spoolsTable.getScene().getWindow());
        confirmation.setTitle(action + " bobina");
        confirmation.setHeaderText("¿" + action + " la bobina #" + selected.getId() + "?");
        confirmation.setContentText("Color: " + selected.getColor() + "\n"
                + "Se conservarán el peso actual, el peso inicial y los consumos.");
        confirmation.getDialogPane().getStylesheets()
                .addAll(spoolsTable.getScene().getStylesheets());
        if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty()) return;

        setLoading(true);
        statusLabel.setText((activate ? "Activando" : "Desactivando")
                + " bobina #" + selected.getId() + "...");
        Task<Boolean> task = new Task<>() {
            @Override protected Boolean call() {
                return activate ? catalogService.activateSpool(selected.getId())
                        : catalogService.deactivateSpool(selected.getId());
            }
        };
        task.setOnSucceeded(event -> {
            setLoading(false);
            if (Boolean.TRUE.equals(task.getValue())) {
                successMessage = "Bobina #" + selected.getId()
                        + (activate ? " activada." : " desactivada.");
                reloadSpools();
            } else {
                statusLabel.setText("El estado de la bobina ya cambió o fue eliminada. "
                        + "Presioná Actualizar.");
            }
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            LOGGER.log(Level.WARNING, "Error al cambiar estado de bobina (tipo: {0}).",
                    error == null ? "desconocido" : error.getClass().getSimpleName());
            setLoading(false);
            statusLabel.setText("No se pudo cambiar el estado de la bobina. Revisá MySQL.");
        });
        Thread worker = new Thread(task, "costeo3d-estado-bobina");
        worker.setDaemon(true);
        worker.start();
    }

    private void openForm(Spool editingSpool) {
        if (loading) return;
        try {
            URL resource = Objects.requireNonNull(getClass().getResource(FORM_RESOURCE),
                    "No se encontró spool-form.fxml.");
            SpoolFormController controller = new SpoolFormController(catalogService, editingSpool);
            FXMLLoader loader = new FXMLLoader(resource);
            loader.setControllerFactory(type -> {
                if (type == SpoolFormController.class) return controller;
                throw new IllegalArgumentException("Controlador no registrado: " + type.getName());
            });
            Parent root = loader.load();
            Stage dialog = new Stage();
            dialog.initOwner(spoolsTable.getScene().getWindow());
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.setResizable(false);
            dialog.setTitle(editingSpool == null ? "Nueva bobina" : "Editar bobina");
            Scene scene = new Scene(root);
            scene.getStylesheets().addAll(spoolsTable.getScene().getStylesheets());
            dialog.setScene(scene);
            controller.setDialogStage(dialog);
            dialog.showAndWait();
            if (controller.wasSaved()) {
                // Mostrar siempre el registro recién creado, aunque hubiera filtros activos.
                searchField.clear();
                statusFilterCombo.setValue(ALL_STATES);
                pageIndex = 0;
                successMessage = editingSpool == null
                        ? "Bobina registrada correctamente."
                        : "Color actualizado sin modificar el stock.";
                reloadSpools();
            }
        } catch (IOException | IllegalStateException ex) {
            LOGGER.log(Level.WARNING, "Error al abrir formulario de bobina ({0}).",
                    ex.getClass().getSimpleName());
            statusLabel.setText("No fue posible abrir el formulario de bobinas.");
        }
    }

    private void setLoading(boolean value) {
        loading = value;
        refreshButton.setDisable(value);
        newSpoolButton.setDisable(value);
        updateActionAvailability();
        searchField.setDisable(value);
        statusFilterCombo.setDisable(value);
        spoolsTable.setDisable(value);
        loadingIndicator.setVisible(value);
        loadingIndicator.setManaged(value);
        previousPageButton.setDisable(value || pageIndex == 0);
        nextPageButton.setDisable(value || pageIndex >=
                Math.max(1, (loadedSpools.size() + PAGE_SIZE - 1) / PAGE_SIZE) - 1);
    }

    private void updateActionAvailability() {
        Spool selected = spoolsTable.getSelectionModel().getSelectedItem();
        boolean editable = !loading && selected != null && selected.isActive();
        editSpoolButton.setDisable(!editable);
        spoolStatusButton.setDisable(loading || selected == null);
        boolean activate = selected != null && !selected.isActive();
        spoolStatusButton.setText(activate ? "Activar seleccionada" : "Desactivar seleccionada");
        spoolStatusButton.getStyleClass().removeAll("danger-action", "secondary-action");
        spoolStatusButton.getStyleClass().add(activate ? "secondary-action" : "danger-action");
    }

    private static ReadOnlyStringWrapper text(String value) {
        return new ReadOnlyStringWrapper(value == null ? "—" : value);
    }
}
