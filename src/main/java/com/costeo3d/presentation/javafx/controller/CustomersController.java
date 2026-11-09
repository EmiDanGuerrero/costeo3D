package com.costeo3d.presentation.javafx.controller;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.application.service.CatalogService;
import com.costeo3d.domain.model.Customer;

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

/** E10.5: listado, alta, edición y estado reversible de clientes. */
public final class CustomersController {

    private static final Logger LOGGER = Logger.getLogger(CustomersController.class.getName());
    private static final String FORM_RESOURCE =
            "/com/costeo3d/presentation/javafx/view/customer-form.fxml";
    private static final String ALL_STATES = "Todos los estados";
    private static final int PAGE_SIZE = 10;
    private static final double HEADER_HEIGHT = 38;
    private static final double ROW_HEIGHT = 24;
    private static final double BORDER_HEIGHT = 2;

    private final CatalogService catalogService;
    private List<Customer> loadedCustomers = List.of();
    private int pageIndex;
    private boolean loading;
    private boolean loadFailed;
    private boolean rendering;
    private String successMessage;

    @FXML private TableView<Customer> customersTable;
    @FXML private TableColumn<Customer, Integer> idColumn;
    @FXML private TableColumn<Customer, String> nameColumn;
    @FXML private TableColumn<Customer, String> phoneColumn;
    @FXML private TableColumn<Customer, String> emailColumn;
    @FXML private TableColumn<Customer, String> statusColumn;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilterCombo;
    @FXML private Label statusLabel;
    @FXML private Label pageLabel;
    @FXML private Button previousPageButton;
    @FXML private Button nextPageButton;
    @FXML private Button refreshButton;
    @FXML private Button newCustomerButton;
    @FXML private Button editCustomerButton;
    @FXML private Button toggleStateButton;
    @FXML private ProgressIndicator loadingIndicator;

    public CustomersController(CatalogService catalogService) {
        this.catalogService = Objects.requireNonNull(catalogService, "Se requiere CatalogService.");
    }

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().getId()));
        nameColumn.setCellValueFactory(cell -> text(cell.getValue().getName()));
        phoneColumn.setCellValueFactory(cell -> text(cell.getValue().getPhone()));
        emailColumn.setCellValueFactory(cell -> text(cell.getValue().getEmail()));
        statusColumn.setCellValueFactory(cell ->
                text(cell.getValue().isActive() ? "Activo" : "Inactivo"));
        for (TableColumn<Customer, String> column :
                List.of(nameColumn, phoneColumn, emailColumn, statusColumn)) {
            column.setComparator(String.CASE_INSENSITIVE_ORDER);
        }
        customersTable.setSortPolicy(table -> {
            renderPage(false);
            return true;
        });
        customersTable.setFixedCellSize(ROW_HEIGHT);
        customersTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldCustomer, newCustomer) -> updateSelectionActions());
        customersTable.setPlaceholder(new Label("No hay clientes registrados."));
        statusFilterCombo.getItems().setAll(ALL_STATES, "Activos", "Inactivos");
        statusFilterCombo.setValue(ALL_STATES);
        searchField.textProperty().addListener((obs, oldValue, newValue) -> renderPage(true));
        statusFilterCombo.valueProperty().addListener(
                (obs, oldValue, newValue) -> renderPage(true));
        // Los más nuevos primero: un cliente recién creado queda visible al regresar.
        idColumn.setSortType(TableColumn.SortType.DESCENDING);
        customersTable.getSortOrder().setAll(idColumn);
        reloadCustomers();
    }

    @FXML
    private void reloadCustomers() {
        if (loading) return;
        setLoading(true);
        statusLabel.setText("Consultando clientes en MySQL...");
        Task<List<Customer>> task = new Task<>() {
            @Override protected List<Customer> call() {
                return catalogService.listCustomers();
            }
        };
        task.setOnSucceeded(event -> {
            loadedCustomers = List.copyOf(task.getValue());
            loadFailed = false;
            setLoading(false);
            renderPage(false);
        });
        task.setOnFailed(event -> {
            loadedCustomers = List.of();
            loadFailed = true;
            successMessage = null;
            Throwable error = task.getException();
            LOGGER.log(Level.WARNING, "Error al consultar clientes (tipo: {0}).",
                    error == null ? "desconocido" : error.getClass().getSimpleName());
            setLoading(false);
            renderPage(true);
        });
        Thread worker = new Thread(task, "costeo3d-consulta-clientes");
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
        if (rendering) return;
        rendering = true;
        try {
            if (resetPage) pageIndex = 0;
            String query = searchField.getText() == null ? ""
                    : searchField.getText().trim().toLowerCase(Locale.ROOT);
            String selectedState = statusFilterCombo.getValue();
            List<Customer> filtered = new ArrayList<>();
            for (Customer customer : loadedCustomers) {
                String id = customer.getId() == null ? "" : customer.getId().toString();
                boolean matches = query.isEmpty() || id.contains(query)
                        || includes(customer.getName(), query)
                        || includes(customer.getPhone(), query)
                        || includes(customer.getEmail(), query);
                boolean stateMatches = selectedState == null || ALL_STATES.equals(selectedState)
                        || ("Activos".equals(selectedState) && customer.isActive())
                        || ("Inactivos".equals(selectedState) && !customer.isActive());
                if (matches && stateMatches) filtered.add(customer);
            }
            Comparator<Customer> byId = Comparator.comparing(
                    Customer::getId, Comparator.nullsLast(Integer::compareTo));
            Comparator<Customer> comparator = customersTable.getComparator();
            if (comparator != null && !customersTable.getSortOrder().isEmpty()) {
                filtered.sort(comparator.thenComparing(byId));
            } else {
                filtered.sort(byId.reversed());
            }

            int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            pageIndex = Math.min(pageIndex, pages - 1);
            int from = pageIndex * PAGE_SIZE;
            int to = Math.min(from + PAGE_SIZE, filtered.size());
            Integer selectedId = selectedCustomerId();
            List<Customer> visible = filtered.subList(from, to);
            customersTable.setItems(FXCollections.observableArrayList(visible));
            if (selectedId != null) {
                visible.stream().filter(c -> Objects.equals(c.getId(), selectedId))
                        .findFirst().ifPresent(customersTable.getSelectionModel()::select);
            }
            updateSelectionActions();

            // Evita filas vacías: misma corrección visual de Materiales/Bobinas.
            int visibleRows = to == from ? 4 : to - from;
            double height = HEADER_HEIGHT + ROW_HEIGHT * visibleRows + BORDER_HEIGHT;
            customersTable.setMinHeight(height);
            customersTable.setPrefHeight(height);
            customersTable.setMaxHeight(height);
            customersTable.setPlaceholder(new Label(loadFailed
                    ? "No se pudieron consultar los clientes."
                    : loadedCustomers.isEmpty() ? "Todavía no hay clientes registrados."
                    : "Ningún cliente coincide con los filtros."));

            pageLabel.setText("Página " + (pageIndex + 1) + " de " + pages);
            previousPageButton.setDisable(loading || pageIndex == 0);
            nextPageButton.setDisable(loading || pageIndex >= pages - 1);
            if (loadFailed) {
                statusLabel.setText("No se pudo consultar MySQL. Revisá la conexión.");
            } else {
                String summary = "Coincidencias: " + filtered.size() + " de "
                        + loadedCustomers.size() + " clientes · Filas "
                        + (filtered.isEmpty() ? "0" : (from + 1) + "–" + to) + ".";
                statusLabel.setText(successMessage == null
                        ? summary : successMessage + " " + summary);
                successMessage = null;
            }
        } finally {
            rendering = false;
        }
    }

    private static boolean includes(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    @FXML private void previousPage() {
        if (!loading && pageIndex > 0) { pageIndex--; renderPage(false); }
    }

    @FXML private void nextPage() {
        if (!loading && !nextPageButton.isDisabled()) { pageIndex++; renderPage(false); }
    }

    private Integer selectedCustomerId() {
        Customer selected = customersTable.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.getId();
    }

    private void updateSelectionActions() {
        Customer selected = customersTable.getSelectionModel().getSelectedItem();
        boolean enabled = !loading && selected != null && selected.getId() != null;
        editCustomerButton.setDisable(!enabled);
        toggleStateButton.setDisable(!enabled);
        toggleStateButton.setText(selected == null ? "Activar / Desactivar"
                : selected.isActive() ? "Desactivar seleccionado" : "Activar seleccionado");
        toggleStateButton.getStyleClass().removeAll("secondary-action", "danger-action");
        toggleStateButton.getStyleClass().add(selected != null && selected.isActive()
                ? "danger-action" : "secondary-action");
    }

    @FXML
    private void createCustomer() {
        if (!loading) showCustomerForm(null);
    }

    @FXML
    private void editSelectedCustomer() {
        if (loading) return;
        Customer selected = customersTable.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getId() != null) showCustomerForm(selected);
    }

    private void showCustomerForm(Customer customer) {
        try {
            URL resource = Objects.requireNonNull(getClass().getResource(FORM_RESOURCE),
                    "No se encontró customer-form.fxml.");
            CustomerFormController form = customer == null
                    ? new CustomerFormController(catalogService)
                    : new CustomerFormController(catalogService, customer);
            FXMLLoader loader = new FXMLLoader(resource);
            loader.setControllerFactory(type -> {
                if (type == CustomerFormController.class) return form;
                throw new IllegalArgumentException("Controlador no registrado: " + type.getName());
            });
            Parent root = loader.load();
            Stage dialog = new Stage();
            dialog.initOwner(customersTable.getScene().getWindow());
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.setResizable(false);
            dialog.setTitle(customer == null ? "Nuevo cliente" : "Editar cliente");
            Scene scene = new Scene(root);
            scene.getStylesheets().addAll(customersTable.getScene().getStylesheets());
            dialog.setScene(scene);
            form.setDialogStage(dialog);
            dialog.showAndWait();
            if (form.wasSaved()) {
                if (customer == null) {
                    // El cliente nuevo debe verse aunque hubiera una búsqueda activa.
                    searchField.clear();
                    statusFilterCombo.setValue(ALL_STATES);
                    idColumn.setSortType(TableColumn.SortType.DESCENDING);
                    customersTable.getSortOrder().setAll(idColumn);
                    pageIndex = 0;
                    successMessage = "Cliente registrado correctamente.";
                } else {
                    successMessage = "Cliente actualizado correctamente.";
                }
                reloadCustomers();
            }
        } catch (IOException | IllegalStateException | IllegalArgumentException exception) {
            LOGGER.log(Level.WARNING, "No se pudo abrir el formulario de cliente ({0}).",
                    exception.getClass().getSimpleName());
            statusLabel.setText("No fue posible abrir el formulario de clientes.");
        }
    }

    @FXML
    private void toggleSelectedCustomer() {
        if (loading) return;
        Customer selected = customersTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getId() == null) return;
        boolean targetActive = !selected.isActive();
        String action = targetActive ? "activar" : "desactivar";
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.initOwner(customersTable.getScene().getWindow());
        confirmation.setTitle(targetActive ? "Activar cliente" : "Desactivar cliente");
        confirmation.setHeaderText("¿Querés " + action + " «" + selected.getName() + "»?");
        confirmation.setContentText("Cliente #" + selected.getId()
                + ". Se conservarán los datos y los presupuestos asociados.");
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        int customerId = selected.getId();
        setLoading(true);
        statusLabel.setText("Actualizando el estado del cliente...");
        Task<Boolean> task = new Task<>() {
            @Override protected Boolean call() {
                // Refrescamos los datos antes de cambiar el estado para no usar
                // los campos del listado, que podrían estar desactualizados.
                Customer current = catalogService.findCustomerById(customerId).orElse(null);
                if (current == null) return false;
                if (current.isActive() == targetActive) return true;
                if (targetActive) current.activate();
                else current.deactivate();
                return catalogService.updateCustomer(current);
            }
        };
        task.setOnSucceeded(event -> {
            setLoading(false);
            if (Boolean.TRUE.equals(task.getValue())) {
                successMessage = targetActive ? "Cliente activado correctamente."
                        : "Cliente desactivado correctamente.";
                reloadCustomers();
            } else {
                customersTable.getSelectionModel().clearSelection();
                statusLabel.setText("No se encontró el cliente. Actualizá el listado.");
            }
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            LOGGER.log(Level.WARNING, "No se pudo cambiar el estado del cliente ({0}).",
                    error == null ? "desconocido" : error.getClass().getSimpleName());
            setLoading(false);
            statusLabel.setText("No se pudo cambiar el estado. Revisá MySQL.");
        });
        Thread worker = new Thread(task, "costeo3d-estado-cliente");
        worker.setDaemon(true);
        worker.start();
    }

    private void setLoading(boolean value) {
        loading = value;
        refreshButton.setDisable(value);
        newCustomerButton.setDisable(value);
        updateSelectionActions();
        searchField.setDisable(value);
        statusFilterCombo.setDisable(value);
        customersTable.setDisable(value);
        loadingIndicator.setVisible(value);
        loadingIndicator.setManaged(value);
        previousPageButton.setDisable(value || pageIndex == 0);
        nextPageButton.setDisable(value);
    }

    private static ReadOnlyStringWrapper text(String value) {
        return new ReadOnlyStringWrapper(value == null || value.isBlank() ? "—" : value);
    }
}
