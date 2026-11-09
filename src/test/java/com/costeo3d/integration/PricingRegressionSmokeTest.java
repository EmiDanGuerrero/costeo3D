package com.costeo3d.integration;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.costeo3d.domain.calculation.CalculationResult;
import com.costeo3d.domain.calculation.CostCalculator;
import com.costeo3d.domain.calculation.ManualCalculationData;
import com.costeo3d.domain.calculation.PricingParameters;
import com.costeo3d.domain.calculation.PrintProfile;
import com.costeo3d.domain.model.Material;
import com.costeo3d.infrastructure.config.PricingParametersStore;

/**
 * E11.2.7 - Pruebas de regresión de costeo, sin JavaFX, MySQL ni JUnit.
 * Ejecutar como Java Application desde src/test/java en Eclipse.
 * No modifica config/pricing.local.properties; usa una carpeta temporal.
 */
public final class PricingRegressionSmokeTest {
    private static int passed;

    private PricingRegressionSmokeTest() { }

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("costeo3d-e1127-");
        try {
            PricingParameters params = configured();

            eq("tarifa $/kWh", params.electricityRatePerKwh(), bd("309"));
            eq("potencia W", params.averagePrinterWatts(), bd("100"));
            eq("vida útil horas", params.printerUsefulLifeHours(), 8000);
            eq("uso anual horas", params.annualPrintHours(), 4380);
            eq("electricidad por hora", params.electricityCostPerHour(), bd("30.90"));
            eq("electricidad 120 minutos", params.electricityCostForMinutes(120), bd("61.80"));
            eq("depreciación por hora", params.depreciationCostPerHour().orElseThrow(), bd("150.00"));
            eq("mantenimiento por hora", params.maintenanceCostPerHour().orElseThrow(), bd("68.49"));
            eq("equipo y mantenimiento 120 minutos", params.equipmentCostForMinutes(120).orElseThrow(), bd("436.99"));
            eq("operativo horario (suma componentes mostrados)", params.combinedHourlyCost().orElseThrow(), bd("249.39"));

            eq("margen normal", params.marginFor(PrintProfile.NORMAL).orElseThrow(), bd("80"));
            eq("margen llavero", params.marginFor(PrintProfile.LLAVERO).orElseThrow(), bd("150"));
            eq("margen grande", params.marginFor(PrintProfile.GRANDE).orElseThrow(), bd("35"));
            eq("mínimo eleva llavero", params.applyMinimum(PrintProfile.LLAVERO, bd("686.75")), bd("1500.00"));
            eq("mínimo no reduce precio", params.applyMinimum(PrintProfile.LLAVERO, bd("1600.00")), bd("1600.00"));
            eq("mínimo no cambia normal", params.applyMinimum(PrintProfile.NORMAL, bd("686.75")), bd("686.75"));
            eq("mínimo no cambia grande", params.applyMinimum(PrintProfile.GRANDE, bd("686.75")), bd("686.75"));
            eq("prefijo histórico grande", PrintProfile.GRANDE.descriptionPrefix(), "[Grande] ");

            PricingParameters inherited = new PricingParameters(bd("309"), bd("100"),
                    bd("1200000"), 8000, bd("300000"), 4380, bd("80"), null, null, null);
            eq("llavero hereda margen general si está vacío",
                    inherited.marginFor(PrintProfile.LLAVERO).orElseThrow(), bd("80"));
            eq("grande hereda margen general si está vacío",
                    inherited.marginFor(PrintProfile.GRANDE).orElseThrow(), bd("80"));
            eq("mínimo desactivado si está vacío",
                    inherited.applyMinimum(PrintProfile.LLAVERO, bd("500.00")), bd("500.00"));

            Material m1 = new Material(101, "Material prueba A", "PLA", "Mate", bd("0.1234"), true);
            Material m2 = new Material(102, "Material prueba B", "PETG", "Mate", bd("0.1943"), true);
            Map<Material, BigDecimal> used = new LinkedHashMap<>();
            used.put(m1, bd("50.50"));
            used.put(m2, bd("25.00"));
            ManualCalculationData item = new ManualCalculationData("Prueba E11.2.7", bd("75.50"), 120);
            CostCalculator calculator = new CostCalculator();
            CalculationResult normal = calculator.calculate(item, used,
                    params.electricityCostForMinutes(120), params.equipmentCostForMinutes(120).orElseThrow(),
                    params.marginFor(PrintProfile.NORMAL).orElseThrow());
            eq("filamento multi-material", normal.getFilamentCost(), bd("11.09"));
            eq("costo base redondeado", normal.getTotalCost(), bd("509.88"));
            eq("precio unitario normal", normal.getFinalPrice(), bd("917.78"));
            eq("total dos unidades normal", normal.getFinalPrice().multiply(bd("2")), bd("1835.56"));
            CalculationResult keychain = calculator.calculate(item, used,
                    params.electricityCostForMinutes(120), params.equipmentCostForMinutes(120).orElseThrow(),
                    params.marginFor(PrintProfile.LLAVERO).orElseThrow());
            eq("llavero antes de mínimo", keychain.getFinalPrice(), bd("1274.70"));
            eq("llavero después de mínimo", params.applyMinimum(PrintProfile.LLAVERO,
                    keychain.getFinalPrice()), bd("1500.00"));
            CalculationResult large = calculator.calculate(item, used,
                    params.electricityCostForMinutes(120), params.equipmentCostForMinutes(120).orElseThrow(),
                    params.marginFor(PrintProfile.GRANDE).orElseThrow());
            eq("precio unitario grande", large.getFinalPrice(), bd("688.34"));

            Path file = temp.resolve("config").resolve("pricing.local.properties");
            PricingParametersStore store = new PricingParametersStore(file);
            store.save(params);
            PricingParameters restored = store.load();
            eq("margen normal persiste", restored.defaultMarginPercentage(), bd("80"));
            eq("margen llavero persiste", restored.keychainMarginPercentage(), bd("150"));
            eq("margen grande persiste", restored.largeMarginPercentage(), bd("35"));
            eq("mínimo llavero persiste", restored.keychainMinimumUnitPrice(), bd("1500"));
            eq("configuración eléctrica persiste", restored.electricityRatePerKwh(), bd("309"));
            eq("horas anuales persisten", restored.annualPrintHours(), 4380);

            Path legacy = temp.resolve("legacy.properties");
            Files.writeString(legacy, "electricity.rate.per.kwh=309\nprinter.average.watts=100\n"
                    + "printer.replacement.value=1200000\nprinter.useful.life.hours=8000\n"
                    + "maintenance.annual.cost=300000\nprinting.hours.per.year=4380\n"
                    + "margin.default.percentage=80\n", StandardCharsets.UTF_8);
            PricingParameters old = new PricingParametersStore(legacy).load();
            eq("archivo anterior conserva margen general", old.defaultMarginPercentage(), bd("80"));
            eq("archivo anterior mantiene perfiles por herencia",
                    old.marginFor(PrintProfile.GRANDE).orElseThrow(), bd("80"));
            expectFailure("tiempo cero rechazado", () -> params.electricityCostForMinutes(0));
            expectFailure("mínimo negativo rechazado", () -> new PricingParameters(
                    bd("309"), bd("100"), bd("1200000"), 8000,
                    bd("300000"), 4380, bd("80"), bd("150"), bd("35"), bd("-1")));

            System.out.println("\nRESULTADO: " + passed + " pruebas OK. "
                    + "Sin modificaciones a MySQL ni a la configuración real.");
        } finally {
            try (var paths = Files.walk(temp)) {
                for (Path p : paths.sorted((a, b) -> b.compareTo(a)).toList()) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }

    private static PricingParameters configured() {
        return new PricingParameters(bd("309"), bd("100"), bd("1200000"), 8000,
                bd("300000"), 4380, bd("80"), bd("150"), bd("35"), bd("1500"));
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }

    private static void eq(String label, Object actual, Object expected) {
        boolean equal = actual instanceof BigDecimal a && expected instanceof BigDecimal e
                ? a.compareTo(e) == 0 : expected.equals(actual);
        if (!equal) throw new AssertionError(label + " - esperado " + expected + ", obtenido " + actual);
        passed++;
        System.out.println("OK " + passed + " - " + label);
    }

    private static void expectFailure(String label, Runnable operation) {
        try {
            operation.run();
            throw new AssertionError(label + " - se esperaba rechazo");
        } catch (IllegalArgumentException expected) {
            passed++;
            System.out.println("OK " + passed + " - " + label);
        }
    }
}
