package com.costeo3d.presentation.javafx.controller;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.application.service.CatalogService;
import com.costeo3d.domain.model.Model3D;
import com.costeo3d.infrastructure.storage.ModelImageStorage;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/** Alta y edición de modelos con imágenes locales opcionales (E10.4.4). */
public final class ModelFormController {
    private static final Logger LOGGER = Logger.getLogger(ModelFormController.class.getName());
    private static final BigDecimal MAX_GRAMS = new BigDecimal("99999999.99");

    private final CatalogService catalogService;
    private final Model3D original;
    private Stage dialogStage;
    private Path selectedImage;
    private boolean removeExistingImage;
    private boolean saving;
    private boolean saved;

    @FXML private Label formTitleLabel;
    @FXML private Label formDescriptionLabel;
    @FXML private TextField nameField;
    @FXML private TextField platformField;
    @FXML private TextField gramsField;
    @FXML private TextField minutesField;
    @FXML private Label selectedImageLabel;
    @FXML private Label previewPlaceholder;
    @FXML private ImageView previewImage;
    @FXML private Label feedbackLabel;
    @FXML private Button chooseImageButton;
    @FXML private Button removeImageButton;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    public ModelFormController(CatalogService catalogService) {
        this(catalogService, null);
    }

    public ModelFormController(CatalogService catalogService, Model3D original) {
        this.catalogService = Objects.requireNonNull(catalogService, "Se requiere CatalogService.");
        this.original = original;
    }

    @FXML
    private void initialize() {
        if (original == null) return;
        formTitleLabel.setText("Editar modelo 3D");
        formDescriptionLabel.setText("Modificá los datos del modelo #" + original.getId()
                + ". El estado se cambia desde la galería.");
        saveButton.setText("Guardar cambios");
        nameField.setText(original.getName());
        platformField.setText(original.getSourcePlatform() == null ? "" : original.getSourcePlatform());
        if (original.getEstimatedGrams() != null) {
            gramsField.setText(original.getEstimatedGrams().toPlainString().replace('.', ','));
        }
        minutesField.setText(Integer.toString(original.getEstimatedPrintTimeMinutes()));
        restoreExistingPreview();
    }

    public void setDialogStage(Stage stage) {
        dialogStage = Objects.requireNonNull(stage, "Se requiere la ventana del formulario.");
        dialogStage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, event -> {
            if (saving) event.consume();
        });
    }

    public boolean wasSaved() { return saved; }

    private boolean hasExistingImage() {
        return original != null && original.getReferenceImage() != null
                && !original.getReferenceImage().isBlank();
    }

    private void restoreExistingPreview() {
        selectedImage = null;
        removeExistingImage = false;
        Path previous = hasExistingImage()
                ? ModelImageStorage.resolveExistingImage(original.getReferenceImage()) : null;
        if (previous != null) {
            selectedImageLabel.setText("Imagen actual: " + previous.getFileName());
            selectedImageLabel.setTooltip(new Tooltip(previous.toString()));
            showPreview(previous);
        } else {
            clearPreview(hasExistingImage() ? "Imagen anterior no disponible" : "Sin imagen seleccionada");
            selectedImageLabel.setText(hasExistingImage()
                    ? "Imagen guardada sin archivo local. Podés reemplazarla o quitarla."
                    : "Ninguna imagen seleccionada (opcional)");
            selectedImageLabel.setTooltip(null);
        }
        removeImageButton.setDisable(!hasExistingImage());
    }

    @FXML
    private void chooseImage() {
        if (saving) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar imagen del modelo 3D");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Imágenes PNG y JPEG (*.png, *.jpg, *.jpeg)", "*.png", "*.jpg", "*.jpeg"));
        if (selectedImage != null) {
            File folder = selectedImage.toFile().getParentFile();
            if (folder != null && folder.isDirectory()) chooser.setInitialDirectory(folder);
        }
        File file = chooser.showOpenDialog(dialogStage);
        if (file == null) return;
        Path candidate = file.toPath();
        try {
            ModelImageStorage.validateSelectedImage(candidate);
            selectedImage = candidate;
            removeExistingImage = false;
            selectedImageLabel.setText("Nueva imagen: " + candidate.getFileName());
            selectedImageLabel.setTooltip(new Tooltip(candidate.toString()));
            removeImageButton.setDisable(false);
            feedbackLabel.setText("");
            showPreview(candidate);
        } catch (IOException | SecurityException ex) {
            feedbackLabel.setText(ex.getMessage() == null
                    ? "No se pudo leer la imagen seleccionada." : ex.getMessage());
        }
    }

    @FXML
    private void removeImage() {
        if (saving) return;
        selectedImage = null;
        removeExistingImage = true;
        clearPreview("Sin imagen seleccionada");
        selectedImageLabel.setText(original == null
                ? "Ninguna imagen seleccionada (opcional)"
                : "Se quitará la imagen al guardar los cambios.");
        selectedImageLabel.setTooltip(null);
        removeImageButton.setDisable(true);
        feedbackLabel.setText("");
    }

    private void clearPreview(String placeholder) {
        previewImage.setImage(null);
        previewImage.setVisible(false);
        previewPlaceholder.setText(placeholder);
        previewPlaceholder.setVisible(true);
    }

    private void showPreview(Path path) {
        Image image = new Image(path.toUri().toString(), 340, 142, true, true, true);
        previewImage.setImage(image);
        updatePreviewVisibility(image);
        image.progressProperty().addListener((obs, before, after) -> updatePreviewVisibility(image));
        image.errorProperty().addListener((obs, before, after) -> updatePreviewVisibility(image));
    }

    private void updatePreviewVisibility(Image image) {
        if (previewImage.getImage() != image) return;
        boolean ready = !image.isError() && image.getProgress() >= 1;
        previewImage.setVisible(ready);
        previewPlaceholder.setVisible(!ready);
        if (image.isError()) previewPlaceholder.setText("No se pudo mostrar la vista previa");
        else previewPlaceholder.setText("Preparando vista previa...");
    }

    @FXML
    private void saveModel() {
        if (saving) return;
        String name = nameField.getText().trim();
        String platform = optional(platformField.getText());
        String gramsText = gramsField.getText().trim();
        String minutesText = minutesField.getText().trim();
        Path newImage = selectedImage;
        boolean discardImage = removeExistingImage;

        if (name.isEmpty() || gramsText.isEmpty() || minutesText.isEmpty()) {
            feedbackLabel.setText("Completá nombre, gramos y tiempo estimado.");
            return;
        }
        if (name.length() > 150 || (platform != null && platform.length() > 150)) {
            feedbackLabel.setText("El nombre y la plataforma admiten hasta 150 caracteres.");
            return;
        }
        BigDecimal grams;
        int minutes;
        try {
            if (gramsText.contains(",") && gramsText.contains(".")) {
                throw new NumberFormatException("Separadores ambiguos.");
            }
            grams = new BigDecimal(gramsText.replace(',', '.'));
            minutes = Integer.parseInt(minutesText);
        } catch (NumberFormatException ex) {
            feedbackLabel.setText("Ingresá gramos válidos (ej.: 75,50) y minutos enteros (ej.: 120).");
            return;
        }
        if (grams.signum() <= 0 || grams.compareTo(MAX_GRAMS) > 0
                || grams.stripTrailingZeros().scale() > 2 || minutes <= 0) {
            feedbackLabel.setText("Gramos mayores que 0 (máximo 2 decimales) y tiempo mayor que 0.");
            return;
        }

        final BigDecimal finalGrams = grams;
        final int finalMinutes = minutes;
        setSaving(true);
        feedbackLabel.setText("Guardando modelo 3D...");
        Task<Model3D> task = new Task<>() {
            @Override protected Model3D call() throws Exception {
                String newReference = null;
                boolean persisted = false;
                try {
                    if (newImage != null) {
                        newReference = ModelImageStorage.copySelectedImage(newImage);
                    }
                    if (original == null) {
                        Model3D candidate = new Model3D(name, newReference, platform,
                                finalGrams, finalMinutes);
                        Model3D created = catalogService.createModel3D(candidate);
                        persisted = true;
                        return created;
                    }

                    // Lee el estado vigente para no sobrescribir una activación/desactivación.
                    Model3D current = catalogService.findModel3DById(original.getId())
                            .orElseThrow(() -> new IllegalStateException(
                                    "El modelo ya no existe en la base de datos."));
                    String previousReference = current.getReferenceImage();
                    String finalReference = newReference != null ? newReference
                            : discardImage ? null : previousReference;
                    Model3D updated = new Model3D(current.getId(), name, finalReference,
                            platform, finalGrams, finalMinutes, current.isActive());
                    if (!catalogService.updateModel3D(updated)) {
                        throw new IllegalStateException("El modelo ya no existe en la base de datos.");
                    }
                    persisted = true;
                    // Solo se elimina una copia gestionada y después de confirmar MySQL.
                    if (!Objects.equals(previousReference, finalReference)) {
                        try {
                            ModelImageStorage.deleteManagedCopy(previousReference);
                        } catch (IOException cleanupError) {
                            LOGGER.log(Level.WARNING,
                                    "No se pudo limpiar la imagen anterior del modelo.", cleanupError);
                        }
                    }
                    return updated;
                } finally {
                    if (!persisted && newReference != null) {
                        try {
                            ModelImageStorage.deleteManagedCopy(newReference);
                        } catch (IOException cleanupError) {
                            LOGGER.log(Level.WARNING,
                                    "No se pudo limpiar la imagen de una operación fallida.",
                                    cleanupError);
                        }
                    }
                }
            }
        };
        task.setOnSucceeded(event -> {
            saved = true;
            dialogStage.close();
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            LOGGER.log(Level.WARNING, "No se pudo guardar el modelo 3D (tipo: {0}).",
                    error == null ? "desconocido" : error.getClass().getSimpleName());
            if (error instanceof IOException) {
                feedbackLabel.setText("No se pudo guardar la imagen: " + error.getMessage());
            } else if (error instanceof IllegalStateException) {
                feedbackLabel.setText(error.getMessage());
            } else {
                feedbackLabel.setText("No se pudo guardar el modelo. Revisá MySQL y los datos.");
            }
            setSaving(false);
        });
        Thread worker = new Thread(task, "costeo3d-guardar-modelo");
        worker.setDaemon(true);
        worker.start();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @FXML private void cancel() {
        if (!saving && dialogStage != null) dialogStage.close();
    }

    private void setSaving(boolean value) {
        saving = value;
        saveButton.setDisable(value);
        cancelButton.setDisable(value);
        chooseImageButton.setDisable(value);
        removeImageButton.setDisable(value || (selectedImage == null
                && (removeExistingImage || !hasExistingImage())));
        nameField.setDisable(value);
        platformField.setDisable(value);
        gramsField.setDisable(value);
        minutesField.setDisable(value);
    }
}
