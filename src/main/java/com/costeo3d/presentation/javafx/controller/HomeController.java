package com.costeo3d.presentation.javafx.controller;

import java.util.Objects;

import javafx.fxml.FXML;

/** Controla las acciones de la vista Inicio, sin conocer JDBC ni la ventana principal. */
public final class HomeController {

    private final Runnable openCatalogsAction;

    public HomeController(Runnable openCatalogsAction) {
        this.openCatalogsAction = Objects.requireNonNull(
                openCatalogsAction, "Se requiere la acción de navegación a catálogos.");
    }

    @FXML
    private void openCatalogs() {
        openCatalogsAction.run();
    }
}
