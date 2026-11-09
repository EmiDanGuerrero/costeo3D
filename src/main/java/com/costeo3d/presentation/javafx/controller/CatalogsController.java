package com.costeo3d.presentation.javafx.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.application.service.CatalogService;
import com.costeo3d.domain.model.Material;

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

/** Catálogo de materiales: CRUD, filtros locales, orden global y paginación. */
public final class CatalogsController {

    private static final Logger LOGGER = Logger.getLogger(CatalogsController.class.getName());
    private static final String FORM_RESOURCE =
            "/com/costeo3d/presentation/javafx/view/material-form.fxml";
    private static final String ALL_TYPES = "Todos los tipos";
    private static final String ALL_STATES = "Todos los estados";
    private static final int PAGE_SIZE = 10;
    // Alturas acordes al CSS actual: cabecera de 38 px y filas de 24 px.
    private static final double TABLE_HEADER_HEIGHT = 38.0;
    private static final double TABLE_ROW_HEIGHT = 24.0;
    private static final double TABLE_BORDER_HEIGHT = 2.0;
    private static final int EMPTY_TABLE_VISIBLE_ROWS = 4;

    private final CatalogService catalogService;
    private final Runnable showSpoolsNavigation;
    private final Runnable showModelsNavigation;

    @FXML private TableView<Material> materialsTable;
    @FXML private TableColumn<Material, Integer> idColumn;
    @FXML private TableColumn<Material, String> brandColumn;
    @FXML private TableColumn<Material, String> typeColumn;
    @FXML private TableColumn<Material, String> finishColumn;
    @FXML private TableColumn<Material, BigDecimal> costColumn;
    @FXML private TableColumn<Material, String> activeColumn;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> typeFilterCombo;
    @FXML private ComboBox<String> statusFilterCombo;
    @FXML private Button clearFiltersButton;
    @FXML private Button previousPageButton;
    @FXML private Button nextPageButton;
    @FXML private Label pageLabel;
    @FXML private Button refreshButton;
    @FXML private Button newMaterialButton;
    @FXML private Button editMaterialButton;
    @FXML private Button materialStatusButton;
    @FXML private Label statusLabel;
    @FXML private ProgressIndicator loadingIndicator;

    private List<Material> loadedMaterials = List.of();
    private int pageIndex;
    private int pageCount = 1;
    private boolean loading;
    private boolean loadFailed;
    private boolean updatingFilters;
    private boolean renderingPage;
    private String pendingSuccessMessage;

    public CatalogsController(CatalogService catalogService, Runnable showSpoolsNavigation,
                              Runnable showModelsNavigation) {
        this.catalogService = Objects.requireNonNull(catalogService, "Se requiere CatalogService.");
        this.showSpoolsNavigation = Objects.requireNonNull(
                showSpoolsNavigation, "Se requiere navegación a bobinas.");
        this.showModelsNavigation = Objects.requireNonNull(
                showModelsNavigation, "Se requiere navegación a modelos 3D.");
    }

    @FXML
    private void showSpools() {
        showSpoolsNavigation.run();
    }

    @FXML
    private void showModels() {
        showModelsNavigation.run();
    }

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().getId()));
        brandColumn.setCellValueFactory(cell -> text(cell.getValue().getBrand()));
        typeColumn.setCellValueFactory(cell -> text(cell.getValue().getType()));
        finishColumn.setCellValueFactory(cell -> text(cell.getValue().getFinish()));
        costColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().getCostPerGram()));
        activeColumn.setCellValueFactory(cell ->
                text(cell.getValue().isActive() ? "Activo" : "Inactivo"));

        brandColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
        typeColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
        finishColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
        activeColumn.setComparator(String.CASE_INSENSITIVE_ORDER);

        // La ordenación de las cabeceras se aplica sobre TODOS los registros,
        // nunca solamente sobre las diez filas visibles de la página.
        materialsTable.setSortPolicy(table -> {
            renderPage(false);
            return true;
        });
        materialsTable.setFixedCellSize(TABLE_ROW_HEIGHT);
        materialsTable.setPlaceholder(new Label("No hay materiales registrados."));
        materialsTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> updateActionAvailability());

        typeFilterCombo.getItems().setAll(ALL_TYPES);
        typeFilterCombo.setValue(ALL_TYPES);
        statusFilterCombo.getItems().setAll(ALL_STATES, "Activos", "Inactivos");
        statusFilterCombo.setValue(ALL_STATES);
        searchField.textProperty().addListener((obs, oldValue, newValue) -> filtersChanged());
        typeFilterCombo.valueProperty().addListener((obs, oldValue, newValue) -> filtersChanged());
        statusFilterCombo.valueProperty().addListener((obs, oldValue, newValue) -> filtersChanged());

        updateActionAvailability();
        reloadMaterials();
    }

    @FXML
    private void reloadMaterials() {
        if (loading) {
            return;
        }
        setLoading(true);
        statusLabel.setText("Consultando materiales en MySQL...");
        materialsTable.setPlaceholder(new Label("Cargando materiales..."));

        Task<List<Material>> task = new Task<>() {
            @Override
            protected List<Material> call() {
                return catalogService.listMaterials();
            }
        };
        task.setOnSucceeded(event -> {
            loadedMaterials = List.copyOf(task.getValue());
            loadFailed = false;
            refreshTypeOptions();
            setLoading(false);
            renderPage(false);
        });
        task.setOnFailed(event -> {
            loadedMaterials = List.of();
            loadFailed = true;
            pendingSuccessMessage = null;
            Throwable error = task.getException();
            // Evitar registrar textos SQL o datos de la configuración local.
            LOGGER.log(Level.WARNING, "Falló la consulta de materiales (tipo: {0}).",
                    error == null ? "desconocido" : error.getClass().getSimpleName());
            setLoading(false);
            renderPage(true);
        });
        Thread worker = new Thread(task, "costeo3d-consulta-materiales");
        worker.setDaemon(true);
        worker.start();
    }

    private void refreshTypeOptions() {
        String previous = typeFilterCombo.getValue();
        Set<String> types = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Material material : loadedMaterials) {
            if (material.getType() != null && !material.getType().isBlank()) {
                types.add(material.getType().trim());
            }
        }
        updatingFilters = true;
        try {
            typeFilterCombo.getItems().setAll(ALL_TYPES);
            typeFilterCombo.getItems().addAll(types);
            typeFilterCombo.setValue(typeFilterCombo.getItems().contains(previous)
                    ? previous : ALL_TYPES);
        } finally {
            updatingFilters = false;
        }
    }

    private void filtersChanged() {
        if (!updatingFilters) {
            renderPage(true);
        }
    }

    @FXML
    private void clearFilters() {
        updatingFilters = true;
        try {
            searchField.clear();
            typeFilterCombo.setValue(ALL_TYPES);
            statusFilterCombo.setValue(ALL_STATES);
        } finally {
            updatingFilters = false;
        }
        renderPage(true);
    }

    /** Los filtros y el ordenamiento actúan sobre la lista ya traída de MySQL. */
    private void renderPage(boolean resetPage) {
        if (renderingPage) {
            return;
        }
        renderingPage = true;
        try {
            if (resetPage) {
                pageIndex = 0;
            }
            String query = searchField.getText() == null ? ""
                    : searchField.getText().trim().toLowerCase(Locale.ROOT);
            String selectedType = typeFilterCombo.getValue();
            String selectedState = statusFilterCombo.getValue();
            List<Material> filtered = new ArrayList<>();
            for (Material material : loadedMaterials) {
                boolean matchesText = query.isEmpty()
                        || Integer.toString(material.getId()).contains(query)
                        || includes(material.getBrand(), query)
                        || includes(material.getType(), query)
                        || includes(material.getFinish(), query);
                boolean matchesType = selectedType == null || ALL_TYPES.equals(selectedType)
                        || selectedType.equalsIgnoreCase(material.getType());
                boolean matchesState = selectedState == null || ALL_STATES.equals(selectedState)
                        || ("Activos".equals(selectedState) && material.isActive())
                        || ("Inactivos".equals(selectedState) && !material.isActive());
                if (matchesText && matchesType && matchesState) {
                    filtered.add(material);
                }
            }

            Comparator<Material> comparator = materialsTable.getComparator();
            if (comparator != null && !materialsTable.getSortOrder().isEmpty()) {
                filtered.sort(comparator.thenComparing(Material::getId));
            } else {
                filtered.sort(Comparator.comparing(Material::getId));
            }

            int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            pageCount = pages;
            pageIndex = Math.min(pageIndex, pages - 1);
            int from = pageIndex * PAGE_SIZE;
            int to = Math.min(from + PAGE_SIZE, filtered.size());
            materialsTable.setItems(FXCollections.observableArrayList(filtered.subList(from, to)));
            adjustTableHeight(to - from);
            materialsTable.setPlaceholder(new Label(loadFailed
                    ? "No fue posible recuperar los materiales."
                    : loadedMaterials.isEmpty() ? "No hay materiales registrados."
                    : "Ningún material coincide con los filtros."));
            pageLabel.setText("Página " + (pageIndex + 1) + " de " + pages);
            previousPageButton.setDisable(loading || pageIndex == 0);
            nextPageButton.setDisable(loading || pageIndex >= pages - 1);
            if (loadFailed) {
                statusLabel.setText("No se pudo consultar MySQL. Revisá la conexión e intentá nuevamente.");
            } else {
                String summary = "Coincidencias: " + filtered.size() + " de " + loadedMaterials.size()
                        + " materiales · Filas " + (filtered.isEmpty() ? "0" : (from + 1) + "–" + to)
                        + ".";
                statusLabel.setText(pendingSuccessMessage == null
                        ? summary : pendingSuccessMessage + " " + summary);
                pendingSuccessMessage = null;
            }
            updateActionAvailability();
        } finally {
            renderingPage = false;
        }
    }

    /** Evita que TableView dibuje filas vacías para rellenar la ventana. */
    private void adjustTableHeight(int pageRows) {
        // Si no hay resultados, dejar lugar suficiente para el mensaje vacío.
        int visibleRows = pageRows == 0 ? EMPTY_TABLE_VISIBLE_ROWS : pageRows;
        double height = TABLE_HEADER_HEIGHT + visibleRows * TABLE_ROW_HEIGHT
                + TABLE_BORDER_HEIGHT;
        materialsTable.setMinHeight(height);
        materialsTable.setPrefHeight(height);
        materialsTable.setMaxHeight(height);
    }

    private static boolean includes(String text, String query) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(query);
    }

    @FXML
    private void previousPage() {
        if (!loading && pageIndex > 0) {
            pageIndex--;
            renderPage(false);
        }
    }

    @FXML
    private void nextPage() {
        if (!loading && !nextPageButton.isDisabled()) {
            pageIndex++;
            renderPage(false);
        }
    }

    @FXML
    private void createMaterial() {
        openForm(null);
    }

    @FXML
    private void editMaterial() {
        Material selected = materialsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            openForm(selected);
        }
    }

    @FXML
    private void changeMaterialStatus() {
        Material selected = materialsTable.getSelectionModel().getSelectedItem();
        if (loading || selected == null) return;

        boolean activate = !selected.isActive();
        String action = activate ? "Activar" : "Desactivar";
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.initOwner(materialsTable.getScene().getWindow());
        confirmation.setTitle(action + " material");
        confirmation.setHeaderText("¿" + action + " el material #" + selected.getId() + "?");
        confirmation.setContentText("Marca: " + selected.getBrand() + "\n"
                + "Se conservarán el identificador, los costos y el historial.");
        confirmation.getDialogPane().getStylesheets()
                .addAll(materialsTable.getScene().getStylesheets());
        if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty()) return;

        setLoading(true);
        statusLabel.setText((activate ? "Activando" : "Desactivando")
                + " material #" + selected.getId() + "...");
        Task<Boolean> task = new Task<>() {
            @Override protected Boolean call() {
                // Leer el estado vigente y aplicar la transición sobre esa instancia.
                Material current = catalogService.findMaterialById(selected.getId()).orElse(null);
                if (current == null || current.isActive() == activate) return false;
                if (activate) current.activate(); else current.deactivate();
                return catalogService.updateMaterial(current);
            }
        };
        task.setOnSucceeded(event -> {
            setLoading(false);
            if (Boolean.TRUE.equals(task.getValue())) {
                pendingSuccessMessage = "Material #" + selected.getId()
                        + (activate ? " activado." : " desactivado.");
                reloadMaterials();
            } else {
                statusLabel.setText("El estado del material ya cambió o fue eliminado. "
                        + "Presioná Actualizar.");
            }
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            LOGGER.log(Level.WARNING, "No se pudo cambiar el estado del material (tipo: {0}).",
                    error == null ? "desconocido" : error.getClass().getSimpleName());
            statusLabel.setText("No se pudo cambiar el estado del material. Revisá MySQL.");
            setLoading(false);
        });
        Thread worker = new Thread(task, "costeo3d-estado-material");
        worker.setDaemon(true);
        worker.start();
    }

    private void openForm(Material materialToEdit) {
        try {
            URL url = Objects.requireNonNull(getClass().getResource(FORM_RESOURCE),
                    "No se encontró material-form.fxml en los recursos.");
            MaterialFormController formController =
                    new MaterialFormController(catalogService, materialToEdit);
            FXMLLoader loader = new FXMLLoader(url);
            loader.setControllerFactory(type -> {
                if (type == MaterialFormController.class) {
                    return formController;
                }
                throw new IllegalArgumentException("Controlador no registrado: " + type.getName());
            });
            Parent root = loader.load();
            Stage dialog = new Stage();
            dialog.initOwner(materialsTable.getScene().getWindow());
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.setTitle(materialToEdit == null ? "Nuevo material" : "Editar material");
            dialog.setResizable(false);
            Scene scene = new Scene(root);
            scene.getStylesheets().addAll(materialsTable.getScene().getStylesheets());
            dialog.setScene(scene);
            formController.setDialogStage(dialog);
            dialog.showAndWait();
            if (formController.wasSaved()) {
                // Al crear, mostrar todos los materiales para que el nuevo sea visible.
                if (materialToEdit == null) {
                    clearFilters();
                }
                reloadMaterials();
            }
        } catch (IOException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING, "No se pudo abrir el formulario (tipo: {0}).",
                    exception.getClass().getSimpleName());
            statusLabel.setText("No se pudo abrir el formulario de materiales.");
        }
    }

    private void setLoading(boolean isLoading) {
        loading = isLoading;
        refreshButton.setDisable(loading);
        newMaterialButton.setDisable(loading);
        searchField.setDisable(loading);
        typeFilterCombo.setDisable(loading);
        statusFilterCombo.setDisable(loading);
        clearFiltersButton.setDisable(loading);
        materialsTable.setDisable(loading);
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
        updateActionAvailability();
        previousPageButton.setDisable(loading || pageIndex == 0);
        nextPageButton.setDisable(loading || pageIndex >= pageCount - 1);
    }

    private void updateActionAvailability() {
        Material selected = materialsTable.getSelectionModel().getSelectedItem();
        editMaterialButton.setDisable(loading || selected == null);
        materialStatusButton.setDisable(loading || selected == null);
        boolean activate = selected != null && !selected.isActive();
        materialStatusButton.setText(activate ? "Activar seleccionado" : "Desactivar seleccionado");
        materialStatusButton.getStyleClass().removeAll("danger-action", "secondary-action");
        materialStatusButton.getStyleClass().add(activate ? "secondary-action" : "danger-action");
    }

    private static ReadOnlyStringWrapper text(String value) {
        return new ReadOnlyStringWrapper(value == null ? "—" : value);
    }
}
