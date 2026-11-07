package com.costeo3d.infrastructure.logging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/** Configura los registros tecnicos locales de la aplicacion. */
public final class AppLogging {

    private static final Logger APP_LOGGER = Logger.getLogger("com.costeo3d");
    private static boolean configured;

    private AppLogging() {
    }

    public static synchronized void configure() {
        if (configured) {
            return;
        }

        APP_LOGGER.setLevel(Level.INFO);
        APP_LOGGER.setUseParentHandlers(false);

        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.WARNING);
        consoleHandler.setFormatter(new SimpleFormatter());
        APP_LOGGER.addHandler(consoleHandler);

        try {
            Files.createDirectories(Path.of("logs"));
            FileHandler fileHandler = new FileHandler(
                    "logs/costeo3d-%g.log", 1_048_576, 3, true);
            fileHandler.setLevel(Level.INFO);
            fileHandler.setFormatter(new SimpleFormatter());
            APP_LOGGER.addHandler(fileHandler);
        } catch (IOException | SecurityException e) {
            APP_LOGGER.warning(
                    "Cannot initialize file logging; console logging remains available.");
        }

        configured = true;
    }
}
