package com.costeo3d.presentation.javafx;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

import com.costeo3d.bootstrap.AppBootstrap;
import com.costeo3d.bootstrap.AppServices;
import com.costeo3d.presentation.javafx.controller.MainController;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/** Punto de entrada de la aplicación gráfica. La consola queda independiente. */
public final class DesktopApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        AppServices services = AppBootstrap.createServices();

        URL fxml = Objects.requireNonNull(
                getClass().getResource("/com/costeo3d/presentation/javafx/view/main.fxml"),
                "No se encontró main.fxml en los recursos.");
        URL css = Objects.requireNonNull(
                getClass().getResource("/com/costeo3d/presentation/javafx/css/app.css"),
                "No se encontró app.css en los recursos.");

        FXMLLoader loader = new FXMLLoader(fxml);
        loader.setControllerFactory(controllerType -> {
            if (controllerType == MainController.class) {
                return new MainController(services);
            }
            throw new IllegalArgumentException(
                    "Controlador JavaFX no registrado: " + controllerType.getName());
        });

        Parent root = loader.load();
        Scene scene = new Scene(root, 1080, 700);
        scene.getStylesheets().add(css.toExternalForm());

        URL iconLarge = Objects.requireNonNull(
                getClass().getResource("/com/costeo3d/presentation/javafx/icons/costeo3d.png"),
                "No se encontró el ícono de Costeo 3D.");
        URL iconSmall = Objects.requireNonNull(
                getClass().getResource("/com/costeo3d/presentation/javafx/icons/costeo3d_32.png"),
                "No se encontró el ícono pequeño de Costeo 3D.");
        stage.getIcons().addAll(new Image(iconSmall.toExternalForm()),
                                new Image(iconLarge.toExternalForm()));

        stage.setTitle("Costeo 3D");
        stage.setMinWidth(860);
        stage.setMinHeight(560);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
