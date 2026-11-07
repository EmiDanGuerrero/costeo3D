
package com.costeo3d.bootstrap;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class JavaFxSmokeTest extends Application {

    @Override
    public void start(Stage stage) {
        Label label = new Label("JavaFX 25 funcionando");

        Scene scene = new Scene(label, 350, 150);

        stage.setTitle("Prueba JavaFX - Costeo 3D");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
