package com.costeo3d.presentation.javafx.controller;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import com.costeo3d.bootstrap.AppServices;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Mantiene el marco principal y carga vistas FXML independientes en el centro. */
public final class MainController {

    private static final String VIEW_DIRECTORY = "/com/costeo3d/presentation/javafx/view/";

    private final AppServices services;
    private Parent activeView;

    @FXML private Button homeButton;
    @FXML private Button materialsButton;
    @FXML private Button spoolsButton;
    @FXML private Button modelsButton;
    @FXML private Button customersButton;
    @FXML private Button quotesButton;
    @FXML private Button ordersButton;
    @FXML private Button productionButton;
    @FXML private Button pricingSettingsButton;
    @FXML private StackPane contentArea;
    @FXML private VBox placeholderPage;
    @FXML private Label sectionTitle;
    @FXML private Label sectionDescription;
    @FXML private Label sectionStatus;

    public MainController(AppServices services) {
        this.services = Objects.requireNonNull(services, "Se requieren los servicios de aplicación.");
    }

    @FXML
    private void initialize() {
        showHome();
    }

    @FXML
    private void showHome() {
        Parent view = loadView("home.fxml", controllerType -> {
            if (controllerType == HomeController.class) {
                return new HomeController(services.dashboard(), this::showOrders,
                        this::showProduction, this::showSpools);
            }
            throw new IllegalArgumentException(
                    "Controlador JavaFX no registrado: " + controllerType.getName());
        });
        switchContent(view);
        selectNavigation(homeButton);
    }

    @FXML
    private void showCatalogs() {
        Parent view = loadView("catalogs.fxml", controllerType -> {
            if (controllerType == CatalogsController.class) {
                return new CatalogsController(services.catalogs(), this::showSpools, this::showModels);
            }
            throw new IllegalArgumentException(
                    "Controlador JavaFX no registrado: " + controllerType.getName());
        });
        switchContent(view);
        selectNavigation(materialsButton);
    }

    /** Vista de bobinas dentro de la sección Catálogos. */
    @FXML
    private void showSpools() {
        Parent view = loadView("spools.fxml", controllerType -> {
            if (controllerType == SpoolsController.class) {
                return new SpoolsController(services.catalogs(), this::showCatalogs);
            }
            throw new IllegalArgumentException(
                    "Controlador JavaFX no registrado: " + controllerType.getName());
        });
        switchContent(view);
        selectNavigation(spoolsButton);
    }

    /** Vista de modelos 3D dentro de Catálogos. */
    @FXML
    private void showModels() {
        Parent view = loadView("models.fxml", controllerType -> {
            if (controllerType == ModelsController.class) {
                return new ModelsController(services.catalogs(), this::showCatalogs);
            }
            throw new IllegalArgumentException(
                    "Controlador JavaFX no registrado: " + controllerType.getName());
        });
        switchContent(view);
        selectNavigation(modelsButton);
    }

    /** Catálogo de clientes, accesible directamente desde el menú lateral. */
    @FXML
    private void showCustomers() {
        Parent view = loadView("customers.fxml", controllerType -> {
            if (controllerType == CustomersController.class) {
                return new CustomersController(services.catalogs());
            }
            throw new IllegalArgumentException(
                    "Controlador JavaFX no registrado: " + controllerType.getName());
        });
        switchContent(view);
        selectNavigation(customersButton);
    }

    /** E11.1: presupuestos existentes, detalles y cambios de estado. */
    @FXML
    private void showQuotes() {
        Parent view = loadView("quotes.fxml", controllerType -> {
            if (controllerType == QuotesController.class) {
                return new QuotesController(services.quotes(), services.catalogs());
            }
            throw new IllegalArgumentException(
                    "Controlador JavaFX no registrado: " + controllerType.getName());
        });
        switchContent(view);
        selectNavigation(quotesButton);
    }

    /** E11.3: consulta y creación de pedidos persistentes. */
    @FXML
    private void showOrders() {
        Parent view = loadView("orders.fxml", controllerType -> {
            if (controllerType == OrdersController.class) {
                return new OrdersController(services.orders(), services.quotes(), services.catalogs());
            }
            throw new IllegalArgumentException(
                    "Controlador JavaFX no registrado: " + controllerType.getName());
        });
        switchContent(view);
        selectNavigation(ordersButton);
    }

    /** E11.4: seguimiento de trabajos persistentes en MySQL. */
    @FXML
    private void showProduction() {
        Parent view = loadView("production.fxml", controllerType -> {
            if (controllerType == ProductionController.class) {
                return new ProductionController(services.production(), services.catalogs());
            }
            throw new IllegalArgumentException(
                    "Controlador JavaFX no registrado: " + controllerType.getName());
        });
        switchContent(view);
        selectNavigation(productionButton);
    }

    @FXML
    private void showPricingSettings() {
        Parent view = loadView("pricing-settings.fxml", controllerType -> {
            if (controllerType == PricingSettingsController.class) {
                return new PricingSettingsController();
            }
            throw new IllegalArgumentException(
                    "Controlador JavaFX no registrado: " + controllerType.getName());
        });
        switchContent(view);
        selectNavigation(pricingSettingsButton);
    }

    private Parent loadView(String file, Function<Class<?>, Object> controllerFactory) {
        URL location = Objects.requireNonNull(
                getClass().getResource(VIEW_DIRECTORY + file),
                "No se encontró " + file + " en los recursos.");
        FXMLLoader loader = new FXMLLoader(location);
        loader.setControllerFactory(controllerFactory::apply);
        try {
            return loader.load();
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo cargar la vista " + file + ".", exception);
        }
    }

    private void showPlaceholder(Button selected, String title,
                                 String description, String status) {
        sectionTitle.setText(title);
        sectionDescription.setText(description);
        sectionStatus.setText(status);
        switchContent(null);
        selectNavigation(selected);
    }

    private void switchContent(Parent nextView) {
        if (activeView != null) {
            contentArea.getChildren().remove(activeView);
        }
        activeView = nextView;
        boolean showPlaceholder = nextView == null;
        placeholderPage.setVisible(showPlaceholder);
        placeholderPage.setManaged(showPlaceholder);
        if (nextView != null) {
            contentArea.getChildren().add(nextView);
        }
    }

    private void selectNavigation(Button selected) {
        for (Button button : List.of(homeButton, materialsButton, spoolsButton,
                                     modelsButton, customersButton, quotesButton,
                                     ordersButton, productionButton, pricingSettingsButton)) {
            button.getStyleClass().remove("nav-button-active");
        }
        selected.getStyleClass().add("nav-button-active");
    }
}
