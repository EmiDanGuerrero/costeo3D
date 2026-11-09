package com.costeo3d.presentation.javafx.controller;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.application.service.HomeDashboardService;
import com.costeo3d.application.service.HomeDashboardSnapshot;
import com.costeo3d.application.service.HomeDashboardSnapshot.SpoolAlert;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Controla el Inicio sin efectuar consultas JDBC en el hilo de JavaFX. */
public final class HomeController {

    private static final Logger LOGGER = Logger.getLogger(HomeController.class.getName());
    private static final Locale ARGENTINA = Locale.forLanguageTag("es-AR");

    private final HomeDashboardService dashboard;
    private final Runnable openOrdersAction;
    private final Runnable openProductionAction;
    private final Runnable openSpoolsAction;

    @FXML private Label activeOrdersValue;
    @FXML private Label inProgressValue;
    @FXML private Label lowStockValue;
    @FXML private Label wasteGramsValue;
    @FXML private Label wasteDetailLabel;
    @FXML private Label dashboardStatus;
    @FXML private Label lowStockDescription;
    @FXML private VBox spoolAlertsBox;
    @FXML private Button refreshButton;

    public HomeController(HomeDashboardService dashboard, Runnable openOrdersAction,
            Runnable openProductionAction, Runnable openSpoolsAction) {
        this.dashboard = Objects.requireNonNull(dashboard, "Se requiere el servicio de Inicio.");
        this.openOrdersAction = Objects.requireNonNull(openOrdersAction);
        this.openProductionAction = Objects.requireNonNull(openProductionAction);
        this.openSpoolsAction = Objects.requireNonNull(openSpoolsAction);
    }

    @FXML
    private void initialize() {
        refresh();
    }

    @FXML
    private void refresh() {
        refreshButton.setDisable(true);
        dashboardStatus.setText("Actualizando indicadores...");
        Task<HomeDashboardSnapshot> task = new Task<>() {
            @Override
            protected HomeDashboardSnapshot call() {
                return dashboard.snapshot();
            }
        };
        task.setOnSucceeded(event -> {
            showSnapshot(task.getValue());
            dashboardStatus.setText("Indicadores actualizados. Podés volver a consultar cuando quieras.");
            refreshButton.setDisable(false);
        });
        task.setOnFailed(event -> {
            LOGGER.log(Level.WARNING, "No se pudo actualizar el panel de Inicio.", task.getException());
            clearIndicators();
            dashboardStatus.setText("No fue posible consultar MySQL. Revisá la conexión y pulsá Actualizar.");
            refreshButton.setDisable(false);
        });
        Thread worker = new Thread(task, "costeo3d-home-dashboard");
        worker.setDaemon(true);
        worker.start();
    }

    private void showSnapshot(HomeDashboardSnapshot data) {
        activeOrdersValue.setText(Integer.toString(data.activeOrders()));
        inProgressValue.setText(Integer.toString(data.jobsInProgress()));
        lowStockValue.setText(Integer.toString(data.lowStockSpools()));
        wasteGramsValue.setText(formatGrams(data.wastedGrams()));
        wasteDetailLabel.setText(data.failedJobs() == 1
                ? "1 impresión fallida con merma registrada"
                : data.failedJobs() + " impresiones fallidas con merma registrada");
        lowStockDescription.setText("Bobinas activas con "
                + formatGrams(data.lowStockThreshold()) + " o menos disponibles.");
        spoolAlertsBox.getChildren().clear();
        if (data.spoolAlerts().isEmpty()) {
            Label message = new Label("No hay bobinas activas por debajo del umbral.");
            message.getStyleClass().add("dashboard-muted");
            spoolAlertsBox.getChildren().add(message);
        } else {
            for (SpoolAlert alert : data.spoolAlerts()) {
                spoolAlertsBox.getChildren().add(alertRow(alert));
            }
            if (data.lowStockSpools() > data.spoolAlerts().size()) {
                Label more = new Label("Y " + (data.lowStockSpools() - data.spoolAlerts().size())
                        + " bobina(s) más. Consultá el catálogo de bobinas.");
                more.getStyleClass().add("dashboard-muted");
                spoolAlertsBox.getChildren().add(more);
            }
        }
    }

    private static HBox alertRow(SpoolAlert alert) {
        Label name = new Label("#" + alert.id() + " · " + alert.material() + " · " + alert.color());
        name.setWrapText(true);
        name.getStyleClass().add("dashboard-alert-name");
        Label grams = new Label(formatGrams(alert.availableGrams()));
        grams.getStyleClass().add("dashboard-alert-grams");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12, name, spacer, grams);
        row.getStyleClass().add("dashboard-alert-row");
        return row;
    }

    private void clearIndicators() {
        activeOrdersValue.setText("—");
        inProgressValue.setText("—");
        lowStockValue.setText("—");
        wasteGramsValue.setText("—");
        wasteDetailLabel.setText("Sin datos disponibles.");
        spoolAlertsBox.getChildren().clear();
        Label message = new Label("No se pudieron cargar las alertas de stock.");
        message.getStyleClass().add("dashboard-muted");
        spoolAlertsBox.getChildren().add(message);
    }

    private static String formatGrams(BigDecimal grams) {
        NumberFormat format = NumberFormat.getNumberInstance(ARGENTINA);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(grams) + " g";
    }

    @FXML private void openOrders() { openOrdersAction.run(); }
    @FXML private void openProduction() { openProductionAction.run(); }
    @FXML private void openSpools() { openSpoolsAction.run(); }
}
