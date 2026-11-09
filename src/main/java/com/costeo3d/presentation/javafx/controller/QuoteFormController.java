package com.costeo3d.presentation.javafx.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.application.service.CatalogService;
import com.costeo3d.application.service.PersistentQuoteService;
import com.costeo3d.domain.calculation.CalculationResult;
import com.costeo3d.domain.calculation.CostCalculator;
import com.costeo3d.domain.calculation.ManualCalculationData;
import com.costeo3d.domain.calculation.PricingParameters;
import com.costeo3d.domain.calculation.PrintProfile;
import com.costeo3d.infrastructure.config.PricingParametersStore;
import com.costeo3d.domain.exception.BusinessRuleException;
import com.costeo3d.domain.model.Customer;
import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.Model3D;
import com.costeo3d.domain.model.Quote;
import com.costeo3d.domain.model.QuoteItem;
import com.costeo3d.domain.model.QuoteMaterialItem;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.StringConverter;

/**
 * E11.2.6: aplica perfiles comerciales configurables sin modificar CostCalculator.
 * Conserva un modo manual explícito para compatibilidad con presupuestos anteriores.
 */
public final class QuoteFormController {

    private static final Logger LOGGER = Logger.getLogger(QuoteFormController.class.getName());

    private final CatalogService catalogs;
    private final PersistentQuoteService quotes;
    private final CostCalculator calculator = new CostCalculator();
    private final PricingParametersStore pricingStore = new PricingParametersStore();
    private PricingParameters pricingParameters;
    private CalculationInput lastCalculatedInput;
    private CalculationResult lastCalculation;
    private BigDecimal lastAppliedUnitPrice;
    private Stage dialogStage;
    private Integer createdQuoteId;
    private boolean busy;
    private boolean initializedCatalogs;

    @FXML private ComboBox<Customer> customerCombo;
    @FXML private ComboBox<Model3D> modelCombo;
    @FXML private ComboBox<Material> materialCombo;
    @FXML private TextField descriptionField;
    @FXML private ComboBox<PrintProfile> printProfileCombo;
    @FXML private Label printProfileHintLabel;
    @FXML private TextField quantityField;
    @FXML private TextField gramsField;
    @FXML private TableView<MaterialLine> materialsTable;
    @FXML private TableColumn<MaterialLine, String> materialNameColumn;
    @FXML private TableColumn<MaterialLine, BigDecimal> materialGramsColumn;
    @FXML private TableColumn<MaterialLine, BigDecimal> materialCostColumn;
    @FXML private Label materialsHintLabel;
    @FXML private Button addMaterialButton;
    @FXML private Button removeMaterialButton;
    private final ObservableList<MaterialLine> materialLines =
            FXCollections.observableArrayList(List.of());

    private record MaterialLine(Material material, BigDecimal grams) {
        String displayName() {
            return material.getBrand() + " · " + material.getType() + " · "
                    + material.getFinish() + " (#" + material.getId() + ")";
        }
    }

    private record MaterialInput(int materialId, BigDecimal grams) { }

    @FXML private TextField minutesField;
    @FXML private TextField electricityField;
    @FXML private TextField amortizationField;
    @FXML private TextField marginField;
    @FXML private TextField unitPriceField;
    @FXML private Button calculateButton;
    @FXML private CheckBox manualPriceCheckBox;
    @FXML private CheckBox automaticOperatingCostsCheckBox;
    @FXML private Label automaticCostsHintLabel;
    @FXML private Label calculationDetailsLabel;
    @FXML private Label calculationSummaryLabel;
    @FXML private Label costPerGramLabel;
    @FXML private Label feedbackLabel;
    @FXML private Label catalogHintLabel;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    @FXML private Button clearModelButton;

    public QuoteFormController(CatalogService catalogs, PersistentQuoteService quotes) {
        this.catalogs = Objects.requireNonNull(catalogs, "Se requiere CatalogService.");
        this.quotes = Objects.requireNonNull(quotes, "Se requiere PersistentQuoteService.");
    }

    @FXML private void initialize() {
        customerCombo.setConverter(display(Customer::getName));
        modelCombo.setConverter(display(Model3D::getName));
        materialCombo.setConverter(display(material -> material.getBrand() + " · "
                + material.getType() + " · " + material.getFinish()
                + " (#" + material.getId() + ")"));
        materialNameColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().displayName()));
        materialGramsColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().grams()));
        materialCostColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().material().getCostPerGram()));
        materialGramsColumn.setCellFactory(column -> decimalCell());
        materialCostColumn.setCellFactory(column -> decimalCell());
        materialsTable.setItems(materialLines);
        // Es un Callback, no una cadena: no asignarlo como atributo de FXML.
        materialsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        materialsTable.setFixedCellSize(28);
        materialsTable.setPlaceholder(new Label("Agregá un material para comenzar."));
        materialsTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldLine, newLine) -> updateRemoveButton());
        updateMaterialsHint();
        materialCombo.valueProperty().addListener((obs, previous, current) -> {
            costPerGramLabel.setText(current == null ? "Elegí un material activo."
                    : "Costo vigente: $" + current.getCostPerGram().toPlainString() + "/g"
                      + " (se registra el costo aplicado al emitir)");
        });
        modelCombo.valueProperty().addListener((obs, previous, model) -> {
            if (model != null) {
                if (descriptionField.getText().isBlank()) descriptionField.setText(model.getName());
                if (materialLines.isEmpty()) {
                    gramsField.setText(model.getEstimatedGrams().toPlainString());
                }
                minutesField.setText(Integer.toString(model.getEstimatedPrintTimeMinutes()));
            }
        });
        printProfileCombo.getItems().setAll(PrintProfile.values());
        printProfileCombo.valueProperty().addListener((obs, previous, current) -> {
            refreshProfileMargin();
            invalidateCalculation();
        });
        printProfileCombo.setValue(PrintProfile.NORMAL);
        // Cualquier edición posterior invalida el cálculo anterior.
        for (TextField field : List.of(descriptionField, quantityField, minutesField,
                electricityField, amortizationField, marginField)) {
            field.textProperty().addListener((obs, previous, current) -> invalidateCalculation());
        }
        // El costo por tiempo se actualiza al cambiar la duración del trabajo.
        minutesField.textProperty().addListener((obs, previous, current) ->
                refreshAutomaticOperatingCosts());
        unitPriceField.setEditable(false);
        manualPriceCheckBox.setSelected(false);
        loadPricingSettings();
        invalidateCalculation();
        updateBusy(false);
        loadCatalogs();
    }

    /** Lee la última configuración guardada al abrir el formulario. */
    private void loadPricingSettings() {
        try {
            pricingParameters = pricingStore.load();
            if (!configuredOperatingHours()) {
                automaticOperatingCostsCheckBox.setSelected(false);
                automaticCostsHintLabel.setText("Completá vida útil y horas anuales en "
                        + "Configuración de costos. Mientras tanto, ingresá los importes manualmente.");
            } else {
                automaticOperatingCostsCheckBox.setSelected(true);
                automaticCostsHintLabel.setText("Costos por unidad calculados con la configuración local.");
            }
            refreshProfileMargin();
        } catch (IOException | IllegalArgumentException failure) {
            LOGGER.log(Level.WARNING, "No se pudo leer la configuración de costos.", failure);
            pricingParameters = null;
            automaticOperatingCostsCheckBox.setSelected(false);
            automaticCostsHintLabel.setText("No se pudo cargar la configuración. "
                    + "Revisala desde Configuración de costos; podés usar importes manuales.");
        }
        refreshProfileMargin();
        updateOperatingCostsMode();
    }

    /** Seleccionar otro perfil recarga el margen, pero nunca impide ajustarlo a mano. */
    private void refreshProfileMargin() {
        PrintProfile profile = printProfileCombo.getValue();
        if (profile == null) return;
        if (pricingParameters == null) {
            printProfileHintLabel.setText("Sin configuración de perfiles: ingresá el margen manualmente.");
            return;
        }
        var selectedMargin = pricingParameters.marginFor(profile);
        if (selectedMargin.isPresent()) {
            marginField.setText(selectedMargin.get().toPlainString());
        } else {
            marginField.clear();
        }
        String origin = switch (profile) {
            case LLAVERO -> pricingParameters.keychainMarginPercentage() != null
                    ? "margen propio de Llavero" : "margen general heredado";
            case NORMAL -> "margen general";
            case GRANDE -> pricingParameters.largeMarginPercentage() != null
                    ? "margen propio de Grande" : "margen general heredado";
        };
        String hint = selectedMargin.isPresent()
                ? "Perfil " + profile.label() + ": " + origin + " (editable para este presupuesto)."
                : "Perfil " + profile.label() + ": configurá un margen o ingresalo acá.";
        if (profile == PrintProfile.LLAVERO) {
            hint += pricingParameters.keychainMinimumUnitPrice() == null
                    ? " Sin precio mínimo configurado."
                    : " Mínimo configurado: $" + pricingParameters.keychainMinimumUnitPrice()
                      + " por unidad (solo cálculo automático).";
        }
        printProfileHintLabel.setText(hint);
    }

    private boolean configuredOperatingHours() {
        return pricingParameters != null
                && pricingParameters.printerUsefulLifeHours() != null
                && pricingParameters.annualPrintHours() != null;
    }

    @FXML private void toggleAutomaticCosts() {
        if (busy) return;
        updateOperatingCostsMode();
        invalidateCalculation();
    }

    private void updateOperatingCostsMode() {
        boolean automatic = automaticOperatingCostsCheckBox.isSelected();
        electricityField.setEditable(!automatic);
        amortizationField.setEditable(!automatic);
        if (automatic) {
            refreshAutomaticOperatingCosts();
        } else if (configuredOperatingHours()) {
            automaticCostsHintLabel.setText("Modo manual: podés ajustar ambos importes por unidad.");
        }
    }

    /** Aplica tarifas por tiempo; el campo de amortización incluye mantenimiento. */
    private void refreshAutomaticOperatingCosts() {
        if (!automaticOperatingCostsCheckBox.isSelected() || !configuredOperatingHours()) return;
        final int minutes;
        try {
            minutes = positiveInteger(minutesField.getText(), "Tiempo (minutos)");
        } catch (IllegalArgumentException incomplete) {
            electricityField.clear();
            amortizationField.clear();
            automaticCostsHintLabel.setText("Ingresá un tiempo positivo para calcular los costos.");
            return;
        }
        BigDecimal electricity = pricingParameters.electricityCostForMinutes(minutes);
        BigDecimal equipment = pricingParameters.equipmentCostForMinutes(minutes).orElseThrow();
        electricityField.setText(electricity.toPlainString());
        amortizationField.setText(equipment.toPlainString());
        automaticCostsHintLabel.setText(minutes + " min → Electricidad $" + electricity.toPlainString()
                + " · Equipo + mantenimiento $" + equipment.toPlainString()
                + " (por unidad, sin filamento ni margen).");
    }

    private static <T> StringConverter<T> display(java.util.function.Function<T, String> text) {
        return new StringConverter<>() {
            @Override public String toString(T value) {
                return value == null ? "" : text.apply(value);
            }
            @Override public T fromString(String value) { return null; }
        };
    }

    public void setDialogStage(Stage stage) {
        dialogStage = Objects.requireNonNull(stage, "Se requiere la ventana de emisión.");
        dialogStage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, event -> {
            if (busy) event.consume();
        });
    }

    public Integer getCreatedQuoteId() { return createdQuoteId; }

    private record CatalogOptions(List<Customer> customers, List<Model3D> models,
                                  List<Material> materials) { }

    private void loadCatalogs() {
        updateBusy(true);
        feedbackLabel.setText("Cargando catálogos desde MySQL...");
        Task<CatalogOptions> task = new Task<>() {
            @Override protected CatalogOptions call() {
                return new CatalogOptions(
                        catalogs.listCustomers().stream().filter(Customer::isActive)
                                .sorted(Comparator.comparing(Customer::getName,
                                        String.CASE_INSENSITIVE_ORDER)).toList(),
                        catalogs.listModels3D().stream().filter(Model3D::isActive)
                                .sorted(Comparator.comparing(Model3D::getName,
                                        String.CASE_INSENSITIVE_ORDER)).toList(),
                        catalogs.listMaterials().stream().filter(Material::isActive)
                                .sorted(Comparator.comparing(Material::getBrand,
                                        String.CASE_INSENSITIVE_ORDER)).toList());
            }
        };
        task.setOnSucceeded(event -> {
            CatalogOptions options = task.getValue();
            customerCombo.getItems().setAll(options.customers());
            modelCombo.getItems().setAll(options.models());
            materialCombo.getItems().setAll(options.materials());
            initializedCatalogs = !options.customers().isEmpty() && !options.materials().isEmpty();
            catalogHintLabel.setText(initializedCatalogs
                    ? "Cliente obligatorio. Agregá uno o varios materiales. Modelo 3D opcional."
                    : "Necesitás al menos un cliente y un material activos para emitir.");
            feedbackLabel.setText(initializedCatalogs ? "" :
                    "Creá o activá un cliente y un material desde Catálogos.");
            updateBusy(false);
        });
        task.setOnFailed(event -> {
            logFailure("No se pudieron cargar los catálogos", task.getException());
            initializedCatalogs = false;
            feedbackLabel.setText("No se pudo consultar MySQL. Revisá la conexión.");
            updateBusy(false);
        });
        start(task, "costeo3d-cargar-form-presupuesto");
    }

    @FXML private void clearModel() {
        if (!busy) modelCombo.getSelectionModel().clearSelection();
    }

    /** Agrega un material a este concepto. Los gramos son por unidad de producto. */
    @FXML private void addMaterial() {
        if (busy || !initializedCatalogs) return;
        try {
            Material material = materialCombo.getValue();
            if (material == null || !material.isActive()) {
                throw new IllegalArgumentException("Seleccioná un material activo.");
            }
            if (materialLines.stream().anyMatch(line ->
                    Objects.equals(line.material().getId(), material.getId()))) {
                throw new IllegalArgumentException(
                        "Ese material ya está agregado. Quitalo para modificar sus gramos.");
            }
            BigDecimal grams = decimal(gramsField.getText(),
                    "Gramos del material", 2, 8, true);
            materialLines.add(new MaterialLine(material, grams));
            materialsTable.getSelectionModel().clearSelection();
            gramsField.clear();
            materialCombo.getSelectionModel().clearSelection();
            feedbackLabel.setText("");
            updateMaterialsHint();
            invalidateCalculation();
        } catch (IllegalArgumentException invalid) {
            feedbackLabel.setText(invalid.getMessage());
        }
    }

    @FXML private void removeMaterial() {
        if (busy) return;
        MaterialLine selected = materialsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            materialLines.remove(selected);
            materialsTable.getSelectionModel().clearSelection();
            updateMaterialsHint();
            invalidateCalculation();
        }
    }

    private static TableCell<MaterialLine, BigDecimal> decimalCell() {
        return new TableCell<>() {
            @Override protected void updateItem(BigDecimal value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : value.toPlainString());
            }
        };
    }

    private void updateMaterialsHint() {
        BigDecimal totalGrams = materialLines.stream()
                .map(MaterialLine::grams).reduce(BigDecimal.ZERO, BigDecimal::add);
        materialsHintLabel.setText(materialLines.size() + " materiales · "
                + totalGrams.toPlainString() + " g por unidad. "
                + "El costo histórico se fija al emitir el presupuesto.");
        double height = 40 + Math.max(1, materialLines.size()) * 28;
        materialsTable.setMinHeight(height);
        materialsTable.setPrefHeight(height);
        materialsTable.setMaxHeight(height);
        updateRemoveButton();
    }

    private void updateRemoveButton() {
        removeMaterialButton.setDisable(busy ||
                materialsTable.getSelectionModel().getSelectedItem() == null);
    }


    private record CalculationMaterial(int materialId, BigDecimal grams,
                                       BigDecimal costPerGram) { }

    private record CalculationInput(String description, int quantity, int minutes,
            List<CalculationMaterial> materials, BigDecimal electricity,
            BigDecimal amortization, BigDecimal margin, PrintProfile profile) { }

    private record QuoteInput(int customerId, Integer modelId,
            String description, int quantity, List<MaterialInput> materials, int minutes,
            BigDecimal electricity, BigDecimal amortization,
            BigDecimal margin, BigDecimal unitPrice,
            boolean calculated, CalculationInput calculationInput) { }

    /** Se usa ManualCalculationData porque los gramos del concepto pueden combinar materiales. */
    private static ManualCalculationData quotable(CalculationInput input) {
        BigDecimal grams = input.materials().stream()
                .map(CalculationMaterial::grams)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ManualCalculationData(input.description(), grams, input.minutes());
    }

    private CalculationInput readCalculationInput() {
        String description = descriptionField.getText() == null ? ""
                : descriptionField.getText().trim();
        PrintProfile profile = printProfileCombo.getValue();
        if (profile == null) {
            throw new IllegalArgumentException("Seleccioná un tipo de impresión.");
        }
        if (description.isEmpty() || profile.descriptionPrefix().length() + description.length() > 255) {
            throw new IllegalArgumentException("La descripción debe ser obligatoria y caber en "
                    + "255 caracteres junto con el nombre del perfil.");
        }
        if (materialLines.isEmpty()) {
            throw new IllegalArgumentException("Agregá al menos un material al presupuesto.");
        }
        return new CalculationInput(description,
                positiveInteger(quantityField.getText(), "Cantidad"),
                positiveInteger(minutesField.getText(), "Tiempo (minutos)"),
                materialLines.stream().map(line -> new CalculationMaterial(
                        line.material().getId(), line.grams(),
                        line.material().getCostPerGram())).toList(),
                decimal(electricityField.getText(), "Electricidad", 2, 10, false),
                decimal(amortizationField.getText(), "Amortización", 2, 10, true),
                decimal(marginField.getText(), "Margen (%)", 2, 4, false),
                profile);
    }

    /** Solo cálculo local: sin consultar MySQL ni persistir nada. */
    @FXML private void calculatePrice() {
        if (busy || manualPriceCheckBox.isSelected()) return;
        try {
            CalculationInput input = readCalculationInput();
            Map<Material, BigDecimal> usage = new LinkedHashMap<>();
            materialLines.forEach(line -> usage.put(line.material(), line.grams()));
            CalculationResult result = calculator.calculate(quotable(input), usage,
                    input.electricity(), input.amortization(), input.margin());
            BigDecimal unitPrice = profilePrice(input.profile(), result.getFinalPrice());
            if (unitPrice.precision() - unitPrice.scale() > 10) {
                throw new IllegalArgumentException("El precio final supera el máximo admitido por MySQL.");
            }
            BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(input.quantity()));
            if (total.precision() - total.scale() > 10) {
                throw new IllegalArgumentException("El total del presupuesto supera el máximo admitido por MySQL.");
            }
            lastCalculatedInput = input;
            lastCalculation = result;
            lastAppliedUnitPrice = unitPrice;
            unitPriceField.setText(unitPrice.toPlainString());
            calculationDetailsLabel.setText("Filamento: $" + result.getFilamentCost().toPlainString()
                    + "  |  Electricidad: $" + result.getElectricityCost().toPlainString()
                    + "  |  Equipo + mantenimiento: $" + result.getAmortizationCost().toPlainString()
                    + "  |  Costo base: $" + result.getTotalCost().toPlainString()
                    + "  |  Margen: $" + result.getMarginAmount().toPlainString()
                    + (unitPrice.compareTo(result.getFinalPrice()) > 0
                        ? "  |  Ajuste por mínimo comercial: $" + unitPrice.subtract(result.getFinalPrice())
                        : ""));
            calculationSummaryLabel.setText("Precio unitario: $" + unitPrice.toPlainString()
                    + "  ·  Total por " + input.quantity() + " unidad(es): $" + total.toPlainString());
            feedbackLabel.setText("Cálculo realizado con los costos de materiales cargados. "+
                    "Al emitir se verificará que sigan vigentes.");
        } catch (IllegalArgumentException invalid) {
            invalidateCalculation();
            feedbackLabel.setText(invalid.getMessage());
        }
    }

    private BigDecimal profilePrice(PrintProfile profile, BigDecimal calculatedPrice) {
        return pricingParameters == null ? calculatedPrice
                : pricingParameters.applyMinimum(profile, calculatedPrice);
    }

    @FXML private void togglePriceMode() {
        if (busy) return;
        boolean manual = manualPriceCheckBox.isSelected();
        unitPriceField.setEditable(manual);
        invalidateCalculation();
        unitPriceField.clear();
        calculationDetailsLabel.setText(manual
                ? "Modo manual: el precio lo decidís vos, sin usar CostCalculator."
                : "Agregá los materiales y presioná Calcular precio.");
        feedbackLabel.setText("");
        updateBusy(false);
    }

    private void invalidateCalculation() {
        lastCalculatedInput = null;
        lastCalculation = null;
        lastAppliedUnitPrice = null;
        if (manualPriceCheckBox != null && !manualPriceCheckBox.isSelected()) {
            if (unitPriceField != null) unitPriceField.clear();
            if (calculationDetailsLabel != null) calculationDetailsLabel.setText(
                    "Sin calcular. Los importes son por unidad de producto.");
        }
        if (calculationSummaryLabel != null) calculationSummaryLabel.setText(
                "Calculá nuevamente si cambiás datos del concepto.");
    }

    @FXML private void saveQuote() {
        if (busy || !initializedCatalogs) return;
        final QuoteInput input;
        try {
            Customer customer = customerCombo.getValue();
            if (customer == null) throw new IllegalArgumentException("Seleccioná un cliente activo.");
            if (materialLines.isEmpty()) {
                throw new IllegalArgumentException("Agregá al menos un material al presupuesto.");
            }
            CalculationInput calculationInput = readCalculationInput();
            Integer modelId = modelCombo.getValue() == null ? null : modelCombo.getValue().getId();
            boolean calculated = !manualPriceCheckBox.isSelected();
            BigDecimal unitPrice;
            if (calculated) {
                if (lastCalculation == null || !calculationInput.equals(lastCalculatedInput)) {
                    throw new IllegalArgumentException(
                            "Calculá el precio antes de emitir. Si cambiaste un dato, volvé a calcular.");
                }
                unitPrice = lastAppliedUnitPrice;
                if (unitPrice == null) throw new IllegalArgumentException("Volvé a calcular el precio.");
            } else {
                unitPrice = decimal(unitPriceField.getText(),
                        "Precio unitario manual", 2, 10, false);
            }
            BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(calculationInput.quantity()));
            if (total.precision() - total.scale() > 10) {
                throw new IllegalArgumentException(
                        "Cantidad × precio unitario supera el total admitido por MySQL.");
            }
            List<MaterialInput> snapshot = materialLines.stream()
                    .map(line -> new MaterialInput(line.material().getId(), line.grams()))
                    .toList();
            input = new QuoteInput(customer.getId(), modelId,
                    calculationInput.profile().descriptionPrefix() + calculationInput.description(),
                    calculationInput.quantity(), List.copyOf(snapshot),
                    calculationInput.minutes(), calculationInput.electricity(),
                    calculationInput.amortization(), calculationInput.margin(),
                    unitPrice, calculated, calculationInput);
        } catch (IllegalArgumentException invalid) {
            feedbackLabel.setText(invalid.getMessage());
            return;
        }
        updateBusy(true);
        feedbackLabel.setText("Emitiendo presupuesto y guardando detalles en MySQL...");
        Task<Quote> task = new Task<>() {
            @Override protected Quote call() throws BusinessRuleException {
                // Consultar entidades vigentes evita presupuestar con un catálogo desactualizado.
                Customer customer = catalogs.findCustomerById(input.customerId())
                        .orElseThrow(() -> new IllegalArgumentException("El cliente ya no existe."));
                if (!customer.isActive()) {
                    throw new IllegalArgumentException(
                            "El cliente está inactivo. Actualizá los catálogos.");
                }
                List<QuoteMaterialItem> persistedMaterials = new ArrayList<>();
                Map<Material, BigDecimal> currentUsage = new LinkedHashMap<>();
                for (MaterialInput line : input.materials()) {
                    Material current = catalogs.findMaterialById(line.materialId())
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Uno de los materiales ya no existe."));
                    if (!current.isActive()) {
                        throw new IllegalArgumentException(
                                "Uno de los materiales está inactivo. Actualizá los catálogos.");
                    }
                    persistedMaterials.add(new QuoteMaterialItem(
                            current, line.grams(), current.getCostPerGram()));
                    currentUsage.put(current, line.grams());
                    if (input.calculated()) {
                        CalculationMaterial expected = input.calculationInput().materials().stream()
                                .filter(m -> m.materialId() == line.materialId()).findFirst()
                                .orElseThrow(() -> new IllegalArgumentException("Material faltante en el cálculo."));
                        if (expected.costPerGram().compareTo(current.getCostPerGram()) != 0) {
                            throw new IllegalArgumentException(
                                    "Cambió el costo de un material. Cerrá y abrí el formulario para recalcular.");
                        }
                    }
                }
                if (input.calculated()) {
                    CalculationResult verified = calculator.calculate(
                            quotable(input.calculationInput()), currentUsage,
                            input.electricity(), input.amortization(), input.margin());
                    BigDecimal verifiedPrice = profilePrice(input.calculationInput().profile(),
                            verified.getFinalPrice());
                    if (verifiedPrice.compareTo(input.unitPrice()) != 0) {
                        throw new IllegalArgumentException(
                                "El precio cambió al verificar los costos. Volvé a calcular.");
                    }
                }
                Model3D model = null;
                if (input.modelId() != null) {
                    model = catalogs.findModel3DById(input.modelId())
                            .orElseThrow(() -> new IllegalArgumentException("El modelo ya no existe."));
                    if (!model.isActive()) {
                        throw new IllegalArgumentException("El modelo está inactivo.");
                    }
                }
                QuoteItem item = new QuoteItem(model, input.description(), input.quantity(),
                        input.minutes(), input.electricity(), input.amortization(),
                        input.margin(), input.unitPrice());
                persistedMaterials.forEach(item::addMaterial);
                return quotes.createQuote(customer, List.of(item), LocalDateTime.now().withNano(0));
            }
        };
        task.setOnSucceeded(event -> {
            createdQuoteId = task.getValue().getId();
            dialogStage.close();
        });
        task.setOnFailed(event -> {
            Throwable failure = task.getException();
            logFailure("No se pudo emitir el presupuesto", failure);
            feedbackLabel.setText(failure instanceof BusinessRuleException
                    || failure instanceof IllegalArgumentException
                    ? failure.getMessage() : "No se pudo emitir. Revisá MySQL y los datos.");
            updateBusy(false);
        });
        start(task, "costeo3d-emitir-presupuesto");
    }

    private static int positiveInteger(String text, String name) {
        try {
            int value = Integer.parseInt(text == null ? "" : text.trim());
            if (value > 0) return value;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(name + ": ingresá un entero positivo.");
    }

    /** Valida la escala y precisión real de DECIMAL(p,s), evitando redondeos silenciosos. */
    private static BigDecimal decimal(String text, String name, int scale,
                                      int integerDigits, boolean positive) {
        final BigDecimal value;
        try {
            value = new BigDecimal(text == null ? "" : text.trim().replace(',', '.'));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(name + ": ingresá un importe numérico.");
        }
        if (value.scale() > scale || value.scale() < -integerDigits
                || value.precision() - value.scale() > integerDigits) {
            throw new IllegalArgumentException(name + ": máximo " + integerDigits
                    + " cifras enteras y " + scale + " decimales.");
        }
        if (positive && value.signum() <= 0 || !positive && value.signum() < 0) {
            throw new IllegalArgumentException(name + (positive
                    ? ": debe ser mayor que cero." : ": no puede ser negativo."));
        }
        return value;
    }

    @FXML private void cancel() {
        if (!busy && dialogStage != null) dialogStage.close();
    }

    private void updateBusy(boolean value) {
        busy = value;
        saveButton.setDisable(value || !initializedCatalogs);
        calculateButton.setDisable(value || !initializedCatalogs || manualPriceCheckBox.isSelected());
        manualPriceCheckBox.setDisable(value);
        printProfileCombo.setDisable(value);
        automaticOperatingCostsCheckBox.setDisable(value || !configuredOperatingHours());
        cancelButton.setDisable(value);
        clearModelButton.setDisable(value);
        addMaterialButton.setDisable(value || !initializedCatalogs);
        updateRemoveButton();
        progressIndicator.setVisible(value);
        progressIndicator.setManaged(value);
        for (javafx.scene.control.Control control : List.of(customerCombo, modelCombo,
                materialCombo, descriptionField, quantityField, gramsField, minutesField,
                electricityField, amortizationField, marginField, unitPriceField)) {
            control.setDisable(value);
        }
    }

    private static void logFailure(String context, Throwable failure) {
        LOGGER.log(Level.WARNING, "{0} ({1}).", new Object[]{context,
                failure == null ? "desconocido" : failure.getClass().getSimpleName()});
    }

    private static void start(Task<?> task, String name) {
        Thread worker = new Thread(task, name);
        worker.setDaemon(true);
        worker.start();
    }
}
