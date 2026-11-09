package com.costeo3d.infrastructure.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Properties;

import com.costeo3d.domain.calculation.PricingParameters;

/** Archivo local ignorado por Git. No depende de MySQL. */
public final class PricingParametersStore {

    private final Path file;

    public PricingParametersStore() {
        this(Path.of("config", "pricing.local.properties"));
    }

    public PricingParametersStore(Path file) {
        this.file = Objects.requireNonNull(file).toAbsolutePath().normalize();
    }

    public Path location() { return file; }

    public PricingParameters load() throws IOException {
        PricingParameters defaults = PricingParameters.initialValues();
        if (!Files.isRegularFile(file)) return defaults;
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        try {
            return new PricingParameters(
                    decimal(properties, "electricity.rate.per.kwh", defaults.electricityRatePerKwh()),
                    decimal(properties, "printer.average.watts", defaults.averagePrinterWatts()),
                    decimal(properties, "printer.replacement.value", defaults.printerReplacementValue()),
                    optionalInteger(properties, "printer.useful.life.hours"),
                    decimal(properties, "maintenance.annual.cost", defaults.annualMaintenanceCost()),
                    optionalInteger(properties, "printing.hours.per.year"),
                    optionalDecimal(properties, "margin.default.percentage"),
                    optionalDecimal(properties, "margin.llavero.percentage"),
                    optionalDecimal(properties, "margin.grande.percentage"),
                    optionalDecimal(properties, "price.llavero.minimum"));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Configuración de costeo inválida en " + file + ": "
                    + exception.getMessage(), exception);
        }
    }

    public void save(PricingParameters settings) throws IOException {
        Objects.requireNonNull(settings);
        Path directory = file.getParent();
        Files.createDirectories(directory);
        Properties properties = new Properties();
        properties.setProperty("electricity.rate.per.kwh", plain(settings.electricityRatePerKwh()));
        properties.setProperty("printer.average.watts", plain(settings.averagePrinterWatts()));
        properties.setProperty("printer.replacement.value", plain(settings.printerReplacementValue()));
        properties.setProperty("printer.useful.life.hours", nullable(settings.printerUsefulLifeHours()));
        properties.setProperty("maintenance.annual.cost", plain(settings.annualMaintenanceCost()));
        properties.setProperty("printing.hours.per.year", nullable(settings.annualPrintHours()));
        properties.setProperty("margin.default.percentage", settings.defaultMarginPercentage() == null
                ? "" : plain(settings.defaultMarginPercentage()));
        properties.setProperty("margin.llavero.percentage", settings.keychainMarginPercentage() == null
                ? "" : plain(settings.keychainMarginPercentage()));
        properties.setProperty("margin.grande.percentage", settings.largeMarginPercentage() == null
                ? "" : plain(settings.largeMarginPercentage()));
        properties.setProperty("price.llavero.minimum", settings.keychainMinimumUnitPrice() == null
                ? "" : plain(settings.keychainMinimumUnitPrice()));
        Path temporary = Files.createTempFile(directory, "pricing-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(writer, "Costeo 3D - parámetros de costeo locales");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String plain(BigDecimal number) { return number.stripTrailingZeros().toPlainString(); }
    private static String nullable(Integer number) { return number == null ? "" : number.toString(); }
    private static BigDecimal decimal(Properties values, String key, BigDecimal fallback) {
        String value = values.getProperty(key);
        return value == null ? fallback : new BigDecimal(value.trim());
    }
    private static BigDecimal optionalDecimal(Properties values, String key) {
        String value = values.getProperty(key, "").trim();
        return value.isEmpty() ? null : new BigDecimal(value);
    }
    private static Integer optionalInteger(Properties values, String key) {
        String value = values.getProperty(key, "").trim();
        return value.isEmpty() ? null : Integer.valueOf(value);
    }
}
