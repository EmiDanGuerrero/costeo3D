package com.costeo3d.presentation.console;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;

import com.costeo3d.application.service.QuoteService;
import com.costeo3d.bootstrap.DemoData;
import com.costeo3d.domain.calculation.CalculationResult;
import com.costeo3d.domain.calculation.CostCalculator;
import com.costeo3d.domain.exception.BusinessRuleException;
import com.costeo3d.domain.model.Customer;
import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.Model3D;
import com.costeo3d.domain.model.Quote;
import com.costeo3d.domain.model.QuoteItem;
import com.costeo3d.domain.model.QuoteMaterialItem;

public class QuoteConsole {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final DemoData data;
    private final ConsoleInput input;
    private final CostCalculator calculator;
    private final QuoteService quoteService;

    public QuoteConsole(DemoData data, Scanner scanner) {
        this.data = data;
        this.input = new ConsoleInput(scanner);
        this.calculator = new CostCalculator();
        this.quoteService = new QuoteService();
    }

    public void show() {
        boolean running = true;

        while (running) {
            printMenu();

            int option = input.readIntBetween(
                    "Seleccione una opción: ",
                    0,
                    3);

            switch (option) {
                case 1 -> createQuote();
                case 2 -> listQuotes();
                case 3 -> acceptQuote();
                case 0 -> running = false;
                default -> throw new IllegalStateException(
                        "Unexpected quote option: " + option);
            }
        }
    }

    private void printMenu() {
        System.out.println();
        System.out.println("--- PRESUPUESTOS ---");
        System.out.println("1. Crear presupuesto");
        System.out.println("2. Listar presupuestos");
        System.out.println("3. Aceptar presupuesto");
        System.out.println("0. Volver");
    }

    private void createQuote() {
        Customer customer = selectCustomer();
        Model3D model = selectModel();
        Material material = selectMaterial();

        int quantity = input.readPositiveInt(
                "Cantidad: ");

        BigDecimal electricityCost =
                input.readNonNegativeBigDecimal(
                        "Costo de electricidad por unidad: $");

        BigDecimal amortizationCost =
                input.readPositiveBigDecimal(
                        "Costo de amortización por unidad: $");

        BigDecimal marginPercentage =
                input.readNonNegativeBigDecimal(
                        "Margen (%): ");

        CalculationResult result = calculator.calculate(
                model,
                material,
                electricityCost,
                amortizationCost,
                marginPercentage);

        QuoteMaterialItem materialItem =
                new QuoteMaterialItem(
                        material,
                        model.getEstimatedGrams(),
                        material.getCostPerGram());

        QuoteItem item = new QuoteItem(
                model,
                model.getName(),
                quantity,
                model.getEstimatedPrintTimeMinutes(),
                electricityCost,
                amortizationCost,
                marginPercentage,
                result.getFinalPrice());

        item.addMaterial(materialItem);

        try {
            Quote quote = quoteService.createQuote(
                    customer,
                    List.of(item),
                    LocalDateTime.now());

            data.getQuotes().add(quote);

            System.out.println();
            System.out.println("Presupuesto creado correctamente.");
            showQuote(quote);

        } catch (BusinessRuleException e) {
            System.out.println();
            System.out.println(
                    "No se pudo crear el presupuesto: "
                    + e.getMessage());
        }
    }

    private void listQuotes() {
        refreshExpirations();

        if (data.getQuotes().isEmpty()) {
            System.out.println();
            System.out.println("No hay presupuestos registrados.");
            return;
        }

        System.out.println();
        System.out.println("--- PRESUPUESTOS REGISTRADOS ---");

        for (int i = 0; i < data.getQuotes().size(); i++) {
            System.out.printf("%d. ", i + 1);
            showQuote(data.getQuotes().get(i));
        }
    }

    private void acceptQuote() {
        refreshExpirations();

        if (data.getQuotes().isEmpty()) {
            System.out.println();
            System.out.println("No hay presupuestos registrados.");
            return;
        }

        listQuotes();

        int option = input.readIntBetween(
                "Seleccione el presupuesto a aceptar: ",
                1,
                data.getQuotes().size());

        Quote quote = data.getQuotes().get(option - 1);

        try {
            quoteService.acceptQuote(
                    quote,
                    LocalDateTime.now());

            System.out.println(
                    "Presupuesto aceptado correctamente.");

        } catch (BusinessRuleException e) {
            System.out.println(
                    "No se pudo aceptar el presupuesto: "
                    + e.getMessage());
        }
    }

    private Customer selectCustomer() {
        List<Customer> customers = data.getCustomers();

        System.out.println();
        System.out.println("Clientes:");

        for (int i = 0; i < customers.size(); i++) {
            System.out.printf(
                    "%d. %s%n",
                    i + 1,
                    customers.get(i).getName());
        }

        int option = input.readIntBetween(
                "Seleccione un cliente: ",
                1,
                customers.size());

        return customers.get(option - 1);
    }

    private Model3D selectModel() {
        List<Model3D> models = data.getModels();

        System.out.println();
        System.out.println("Modelos:");

        for (int i = 0; i < models.size(); i++) {
            Model3D model = models.get(i);

            System.out.printf(
                    "%d. %s | %s g%n",
                    i + 1,
                    model.getName(),
                    model.getEstimatedGrams());
        }

        int option = input.readIntBetween(
                "Seleccione un modelo: ",
                1,
                models.size());

        return models.get(option - 1);
    }

    private Material selectMaterial() {
        List<Material> materials = data.getMaterials();

        System.out.println();
        System.out.println("Materiales:");

        for (int i = 0; i < materials.size(); i++) {
            Material material = materials.get(i);

            System.out.printf(
                    "%d. %s | %s | %s | $%s/g%n",
                    i + 1,
                    material.getBrand(),
                    material.getType(),
                    material.getFinish(),
                    material.getCostPerGram());
        }

        int option = input.readIntBetween(
                "Seleccione un material: ",
                1,
                materials.size());

        return materials.get(option - 1);
    }

    private void showQuote(Quote quote) {
        System.out.printf(
                "Cliente: %s | Estado: %s | Total: $%s"
                + " | Emisión: %s | Vencimiento: %s%n",
                quote.getCustomer().getName(),
                quote.getStatus(),
                quote.getTotal(),
                quote.getIssuedAt().format(DATE_FORMAT),
                quote.getExpiresAt().format(DATE_FORMAT));
    }

    private void refreshExpirations() {
        for (Quote quote : data.getQuotes()) {
            try {
                quoteService.expireIfNecessary(
                        quote,
                        LocalDateTime.now());
            } catch (BusinessRuleException e) {
                System.out.println(
                        "No se pudo actualizar un presupuesto: "
                        + e.getMessage());
            }
        }
    }
}