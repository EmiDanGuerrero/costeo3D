package com.costeo3d.presentation.javafx.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.application.service.CatalogService;
import com.costeo3d.domain.model.Model3D;
import com.costeo3d.infrastructure.storage.ModelImageStorage;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;

/** E10.4.4: galería con edición y estado reversible. */
public final class ModelsController {
    private static final Logger LOGGER = Logger.getLogger(ModelsController.class.getName());
    private static final String FORM_RESOURCE =
            "/com/costeo3d/presentation/javafx/view/model-form.fxml";
    private static final String ALL_STATES = "Todos los estados";
    private static final String SORT_RECENT = "Más recientes (ID ↓)";
    private static final String SORT_OLD = "Más antiguos (ID ↑)";
    private static final String SORT_NAME_ASC = "Nombre (A–Z)";
    private static final String SORT_NAME_DESC = "Nombre (Z–A)";
    private static final String SORT_GRAMS_ASC = "Gramos (menor a mayor)";
    private static final String SORT_GRAMS_DESC = "Gramos (mayor a menor)";
    private static final String SORT_TIME_ASC = "Tiempo (menor a mayor)";
    private static final String SORT_TIME_DESC = "Tiempo (mayor a menor)";
    private static final int PAGE_SIZE = 9;
    private static final double CARD_WIDTH = 220;
    private static final double IMAGE_HEIGHT = 146;

    private final CatalogService catalogService;
    // Se conserva la firma usada por MainController (E10.4.1).
    private List<Model3D> loadedModels = List.of();
    private int pageIndex;
    private Integer selectedModelId;
    private boolean loading;
    private boolean loadFailed;
    private String pendingMessage;

    @FXML private ScrollPane modelsScroll;
    @FXML private StackPane galleryArea;
    @FXML private FlowPane galleryFlow;
    @FXML private VBox emptyStateBox;
    @FXML private Label emptyStateTitle;
    @FXML private Label emptyStateDescription;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilterCombo;
    @FXML private ComboBox<String> sortCombo;
    @FXML private Label statusLabel;
    @FXML private Label pageLabel;
    @FXML private Button previousPageButton;
    @FXML private Button nextPageButton;
    @FXML private Button refreshButton;
    @FXML private Button newModelButton;
    @FXML private Button editModelButton;
    @FXML private Button toggleStateButton;
    @FXML private ProgressIndicator loadingIndicator;

    public ModelsController(CatalogService catalogService, Runnable returnToMaterials) {
        this.catalogService = Objects.requireNonNull(catalogService, "Se requiere CatalogService.");
        Objects.requireNonNull(returnToMaterials, "Se requiere navegación.");
    }

    @FXML
    private void initialize() {
        statusFilterCombo.getItems().setAll(ALL_STATES, "Activos", "Inactivos");
        statusFilterCombo.setValue(ALL_STATES);
        sortCombo.getItems().setAll(SORT_RECENT, SORT_OLD, SORT_NAME_ASC, SORT_NAME_DESC,
                SORT_GRAMS_ASC, SORT_GRAMS_DESC, SORT_TIME_ASC, SORT_TIME_DESC);
        sortCombo.setValue(SORT_RECENT);
        // El FlowPane se ajusta automáticamente al ancho disponible al redimensionar.
        galleryFlow.setPrefWrapLength(CARD_WIDTH * 3 + 28);
        searchField.textProperty().addListener((obs, before, after) -> renderPage(true));
        statusFilterCombo.valueProperty().addListener((obs, before, after) -> renderPage(true));
        sortCombo.valueProperty().addListener((obs, before, after) -> renderPage(true));
        reloadModels();
    }

    @FXML
    private void reloadModels() {
        if (loading) return;
        setLoading(true);
        statusLabel.setText("Consultando modelos 3D en MySQL...");
        Task<List<Model3D>> task = new Task<>() {
            @Override protected List<Model3D> call() {
                return catalogService.listModels3D();
            }
        };
        task.setOnSucceeded(event -> {
            loadedModels = List.copyOf(task.getValue());
            loadFailed = false;
            setLoading(false);
            renderPage(false);
        });
        task.setOnFailed(event -> {
            loadedModels = List.of();
            loadFailed = true;
            pendingMessage = null;
            Throwable error = task.getException();
            LOGGER.log(Level.WARNING, "Error al consultar modelos 3D (tipo: {0}).",
                    error == null ? "desconocido" : error.getClass().getSimpleName());
            setLoading(false);
            renderPage(true);
        });
        Thread worker = new Thread(task, "costeo3d-consulta-modelos");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void clearFilters() {
        searchField.clear();
        statusFilterCombo.setValue(ALL_STATES);
        sortCombo.setValue(SORT_RECENT);
        renderPage(true);
    }

    private void renderPage(boolean resetPage) {
        if (resetPage) {
            pageIndex = 0;
            selectedModelId = null;
            modelsScroll.setVvalue(0);
        }
        String query = searchField.getText() == null ? ""
                : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String state = statusFilterCombo.getValue();
        List<Model3D> filtered = new ArrayList<>();
        for (Model3D model : loadedModels) {
            String id = model.getId() == null ? "" : model.getId().toString();
            boolean textMatch = query.isEmpty() || id.contains(query)
                    || includes(model.getName(), query)
                    || includes(model.getSourcePlatform(), query);
            boolean stateMatch = state == null || ALL_STATES.equals(state)
                    || ("Activos".equals(state) && model.isActive())
                    || ("Inactivos".equals(state) && !model.isActive());
            if (textMatch && stateMatch) filtered.add(model);
        }
        filtered.sort(comparatorFor(sortCombo.getValue()));

        int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        pageIndex = Math.min(pageIndex, pages - 1);
        int from = pageIndex * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, filtered.size());
        ToggleGroup selection = new ToggleGroup();
        galleryFlow.getChildren().clear();
        for (Model3D model : filtered.subList(from, to)) {
            galleryFlow.getChildren().add(createCard(model, selection));
        }
        boolean empty = filtered.isEmpty();
        galleryFlow.setVisible(!empty);
        galleryFlow.setManaged(!empty);
        emptyStateBox.setVisible(empty);
        emptyStateBox.setManaged(empty);
        if (empty) {
            if (loadFailed) {
                emptyStateTitle.setText("No se pudieron cargar los modelos");
                emptyStateDescription.setText("Revisá la conexión a MySQL y presioná Actualizar.");
            } else if (loadedModels.isEmpty()) {
                emptyStateTitle.setText("Todavía no hay modelos 3D");
                emptyStateDescription.setText("Usá «Nuevo modelo 3D» para crear el primero.");
            } else {
                emptyStateTitle.setText("No se encontraron modelos");
                emptyStateDescription.setText("Probá otra búsqueda o limpiá los filtros.");
            }
        }
        pageLabel.setText("Página " + (pageIndex + 1) + " de " + pages);
        previousPageButton.setDisable(loading || pageIndex == 0);
        nextPageButton.setDisable(loading || pageIndex >= pages - 1);
        if (selectedModelId != null && filtered.subList(from, to).stream()
                .noneMatch(m -> Objects.equals(m.getId(), selectedModelId))) {
            selectedModelId = null;
        }
        updateSelectionActions();
        if (loadFailed) {
            statusLabel.setText("No se pudo consultar MySQL. Revisá la conexión.");
        } else {
            String summary = "Coincidencias: " + filtered.size() + " de " + loadedModels.size()
                    + " modelos · Mostrando " + (empty ? "0" : (from + 1) + "–" + to) + ".";
            statusLabel.setText(pendingMessage == null ? summary : pendingMessage + " " + summary);
            pendingMessage = null;
        }
    }

    private static Comparator<Model3D> comparatorFor(String option) {
        Comparator<Model3D> byId = Comparator.comparing(
                Model3D::getId, Comparator.nullsFirst(Integer::compareTo));
        Comparator<Model3D> byName = Comparator.comparing(
                m -> safe(m.getName()), String.CASE_INSENSITIVE_ORDER);
        Comparator<Model3D> byGrams = Comparator.comparing(
                Model3D::getEstimatedGrams, Comparator.nullsFirst(BigDecimal::compareTo));
        Comparator<Model3D> byTime = Comparator.comparingInt(Model3D::getEstimatedPrintTimeMinutes);
        Comparator<Model3D> result;
        if (SORT_OLD.equals(option)) result = byId;
        else if (SORT_NAME_ASC.equals(option)) result = byName;
        else if (SORT_NAME_DESC.equals(option)) result = byName.reversed();
        else if (SORT_GRAMS_ASC.equals(option)) result = byGrams;
        else if (SORT_GRAMS_DESC.equals(option)) result = byGrams.reversed();
        else if (SORT_TIME_ASC.equals(option)) result = byTime;
        else if (SORT_TIME_DESC.equals(option)) result = byTime.reversed();
        else result = byId.reversed();
        return result.thenComparing(byId);
    }

    private ToggleButton createCard(Model3D model, ToggleGroup selection) {
        VBox content = new VBox(0);
        content.getStyleClass().add("model-card-content");

        StackPane cover = new StackPane();
        cover.getStyleClass().add("model-cover");
        cover.setMinHeight(IMAGE_HEIGHT);
        cover.setPrefHeight(IMAGE_HEIGHT);
        cover.setMaxHeight(IMAGE_HEIGHT);

        VBox fallback = new VBox(8);
        fallback.setAlignment(Pos.CENTER);
        fallback.getStyleClass().add("model-image-placeholder");
        SVGPath pictogram = new SVGPath();
        pictogram.setContent("M3 4 H21 V20 H3 Z M6 16 L10 12 L13 15 L16 11 L19 15 M8 9 H8.5");
        pictogram.getStyleClass().add("model-image-icon");
        Label noImage = new Label("Sin imagen");
        noImage.getStyleClass().add("model-no-image-label");
        fallback.getChildren().addAll(pictogram, noImage);
        cover.getChildren().add(fallback);
        addLocalImagePreview(cover, fallback, model.getReferenceImage());

        Label state = new Label(model.isActive() ? "Activo" : "Inactivo");
        state.getStyleClass().addAll("model-state-badge",
                model.isActive() ? "model-state-active" : "model-state-inactive");
        StackPane.setAlignment(state, Pos.TOP_RIGHT);
        StackPane.setMargin(state, new Insets(10));
        cover.getChildren().add(state);
        content.getChildren().add(cover);

        VBox details = new VBox(5);
        details.getStyleClass().add("model-card-details");
        Label title = new Label(safe(model.getName()));
        title.getStyleClass().add("model-card-title");
        title.setWrapText(true);
        title.setMinHeight(38);
        title.setMaxWidth(CARD_WIDTH - 28);
        Label platform = new Label(model.getSourcePlatform() == null
                || model.getSourcePlatform().isBlank() ? "Sin plataforma" : model.getSourcePlatform());
        platform.getStyleClass().add("model-card-platform");
        platform.setMaxWidth(CARD_WIDTH - 28);
        platform.setEllipsisString("…");
        HBox metrics = new HBox(8);
        metrics.setAlignment(Pos.CENTER_LEFT);
        metrics.getStyleClass().add("model-card-metrics");
        Label grams = new Label(formatGrams(model.getEstimatedGrams()));
        Label separator = new Label("·");
        Label time = new Label(model.getEstimatedPrintTimeMinutes() + " min");
        metrics.getChildren().addAll(grams, separator, time);
        Label id = new Label("Modelo #" + (model.getId() == null ? "—" : model.getId()));
        id.getStyleClass().add("model-card-id");
        details.getChildren().addAll(title, platform, metrics, id);
        VBox.setVgrow(details, Priority.NEVER);
        content.getChildren().add(details);

        ToggleButton card = new ToggleButton();
        card.getStyleClass().add("model-card");
        card.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        card.setGraphic(content);
        card.setToggleGroup(selection);
        card.setPrefWidth(CARD_WIDTH);
        card.setMinWidth(CARD_WIDTH);
        card.setMaxWidth(CARD_WIDTH);
        card.setAccessibleText("Modelo " + safe(model.getName()) + ", "
                + (model.isActive() ? "activo" : "inactivo"));
        card.setSelected(Objects.equals(selectedModelId, model.getId()));
        card.setOnAction(event -> {
            selectedModelId = card.isSelected() ? model.getId() : null;
            updateSelectionActions();
        });
        return card;
    }

    /** Muestra imágenes del almacenamiento de Costeo 3D y admite rutas locales antiguas. */
    private static void addLocalImagePreview(StackPane cover, VBox fallback, String reference) {
        Path path = ModelImageStorage.resolveExistingImage(reference);
        if (path == null) return;
        Image image = new Image(path.toUri().toString(), CARD_WIDTH, IMAGE_HEIGHT,
                true, true, true);
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(CARD_WIDTH);
        imageView.setFitHeight(IMAGE_HEIGHT);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setVisible(false);
        cover.getChildren().add(imageView);
        Runnable refresh = () -> {
            boolean ready = !image.isError() && image.getProgress() >= 1;
            imageView.setVisible(ready);
            fallback.setVisible(!ready);
        };
        image.progressProperty().addListener((obs, oldValue, newValue) -> refresh.run());
        image.errorProperty().addListener((obs, oldValue, newValue) -> refresh.run());
        refresh.run();
    }

    private static String formatGrams(BigDecimal grams) {
        return grams == null ? "— g" : grams.setScale(2, RoundingMode.HALF_UP)
                .toPlainString().replace('.', ',') + " g";
    }

    private static boolean includes(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    @FXML private void previousPage() {
        if (!loading && pageIndex > 0) {
            pageIndex--;
            selectedModelId = null;
            modelsScroll.setVvalue(0);
            renderPage(false);
        }
    }

    @FXML private void nextPage() {
        if (!loading && !nextPageButton.isDisabled()) {
            pageIndex++;
            selectedModelId = null;
            modelsScroll.setVvalue(0);
            renderPage(false);
        }
    }

    private Model3D selectedModel() {
        if (selectedModelId == null) return null;
        for (Model3D model : loadedModels) {
            if (Objects.equals(model.getId(), selectedModelId)) return model;
        }
        return null;
    }

    private void updateSelectionActions() {
        Model3D selected = selectedModel();
        boolean enabled = selected != null && !loading;
        editModelButton.setDisable(!enabled);
        toggleStateButton.setDisable(!enabled);
        toggleStateButton.setText(selected == null ? "Activar / Desactivar"
                : selected.isActive() ? "Desactivar seleccionado" : "Activar seleccionado");
        toggleStateButton.getStyleClass().remove("secondary-action");
        toggleStateButton.getStyleClass().remove("danger-action");
        toggleStateButton.getStyleClass().add(selected != null && selected.isActive()
                ? "danger-action" : "secondary-action");
    }

    @FXML
    private void createModel() {
        if (loading) return;
        showModelForm(null);
    }

    @FXML
    private void editSelectedModel() {
        if (loading) return;
        Model3D model = selectedModel();
        if (model != null) showModelForm(model);
    }

    private void showModelForm(Model3D model) {
        try {
            URL url = Objects.requireNonNull(getClass().getResource(FORM_RESOURCE),
                    "No se encontró model-form.fxml.");
            ModelFormController form = model == null
                    ? new ModelFormController(catalogService)
                    : new ModelFormController(catalogService, model);
            FXMLLoader loader = new FXMLLoader(url);
            loader.setControllerFactory(type -> {
                if (type == ModelFormController.class) return form;
                throw new IllegalArgumentException("Controlador no registrado: " + type.getName());
            });
            Parent root = loader.load();
            Stage dialog = new Stage();
            dialog.initOwner(modelsScroll.getScene().getWindow());
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.setResizable(false);
            dialog.setTitle(model == null ? "Nuevo modelo 3D" : "Editar modelo 3D");
            Scene scene = new Scene(root);
            scene.getStylesheets().addAll(modelsScroll.getScene().getStylesheets());
            dialog.setScene(scene);
            form.setDialogStage(dialog);
            dialog.showAndWait();
            if (form.wasSaved()) {
                if (model == null) {
                    searchField.clear();
                    statusFilterCombo.setValue(ALL_STATES);
                    sortCombo.setValue(SORT_RECENT);
                    pageIndex = 0;
                    selectedModelId = null;
                    pendingMessage = "Modelo 3D registrado correctamente.";
                } else {
                    pendingMessage = "Modelo 3D actualizado correctamente.";
                }
                reloadModels();
            }
        } catch (IOException | IllegalStateException | IllegalArgumentException ex) {
            LOGGER.log(Level.WARNING, "No se pudo abrir el formulario de modelo (tipo: {0}).",
                    ex.getClass().getSimpleName());
            statusLabel.setText("No fue posible abrir el formulario de modelos 3D.");
        }
    }

    @FXML
    private void toggleSelectedModel() {
        if (loading) return;
        Model3D model = selectedModel();
        if (model == null || model.getId() == null) return;
        boolean targetActive = !model.isActive();
        String action = targetActive ? "activar" : "desactivar";
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.initOwner(modelsScroll.getScene().getWindow());
        confirmation.setTitle(targetActive ? "Activar modelo 3D" : "Desactivar modelo 3D");
        confirmation.setHeaderText("¿Querés " + action + " «" + model.getName() + "»?");
        confirmation.setContentText("Modelo #" + model.getId()
                + ". Se conservarán su imagen, datos e historial.");
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        int modelId = model.getId();
        setLoading(true);
        statusLabel.setText("Actualizando el estado del modelo 3D...");
        Task<Boolean> task = new Task<>() {
            @Override protected Boolean call() {
                Model3D current = catalogService.findModel3DById(modelId).orElse(null);
                if (current == null) return false;
                if (current.isActive() == targetActive) return true;
                if (targetActive) current.activate();
                else current.deactivate();
                return catalogService.updateModel3D(current);
            }
        };
        task.setOnSucceeded(event -> {
            setLoading(false);
            if (Boolean.TRUE.equals(task.getValue())) {
                pendingMessage = targetActive ? "Modelo 3D activado correctamente."
                        : "Modelo 3D desactivado correctamente.";
                reloadModels();
            } else {
                statusLabel.setText("No se encontró el modelo seleccionado. Actualizá la galería.");
                selectedModelId = null;
                updateSelectionActions();
            }
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            LOGGER.log(Level.WARNING, "No se pudo actualizar estado de modelo (tipo: {0}).",
                    error == null ? "desconocido" : error.getClass().getSimpleName());
            setLoading(false);
            statusLabel.setText("No se pudo cambiar el estado. Revisá MySQL.");
        });
        Thread worker = new Thread(task, "costeo3d-cambiar-estado-modelo");
        worker.setDaemon(true);
        worker.start();
    }

    private void setLoading(boolean value) {
        loading = value;
        refreshButton.setDisable(value);
        newModelButton.setDisable(value);
        updateSelectionActions();
        searchField.setDisable(value);
        statusFilterCombo.setDisable(value);
        sortCombo.setDisable(value);
        galleryFlow.setDisable(value);
        loadingIndicator.setVisible(value);
        loadingIndicator.setManaged(value);
        previousPageButton.setDisable(value || pageIndex == 0);
        nextPageButton.setDisable(value);
    }
}
