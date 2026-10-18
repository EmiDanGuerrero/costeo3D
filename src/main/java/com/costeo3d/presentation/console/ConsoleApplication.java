package com.costeo3d.presentation.console;

import java.util.Scanner;

import com.costeo3d.bootstrap.DemoData;
import com.costeo3d.domain.model.Customer;
import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.Model3D;
import com.costeo3d.domain.model.Spool;
import com.costeo3d.presentation.console.menu.MainMenu;

public class ConsoleApplication {

    private final DemoData data;

    public ConsoleApplication(DemoData data) {
        this.data = data;
    }

    public void run() {
        try (Scanner scanner = new Scanner(System.in)) {
            MainMenu mainMenu = new MainMenu(scanner);
            
            CostCalculatorConsole costCalculatorConsole =
                    new CostCalculatorConsole(data, scanner);
            
            QuoteConsole quoteConsole =
                    new QuoteConsole(data, scanner);

            OrderConsole orderConsole =
                    new OrderConsole(data, scanner);
            
            ProductionConsole productionConsole =
                    new ProductionConsole(data, scanner);
            
            boolean running = true;

            while (running) {
                int option = mainMenu.readOption();

                switch (option) {
                    case 1 -> showMaterials();
                    case 2 -> showSpools();
                    case 3 -> showModels();
                    case 4 -> showCustomers();
                    case 5 -> costCalculatorConsole.show();
                    case 6 -> quoteConsole.show();
                    case 7 -> orderConsole.show();
                    case 8 -> productionConsole.show();
                    case 0 -> running = false;
                    default -> throw new IllegalStateException(
                            "Unexpected menu option: " + option);
                }
            }
        }

        System.out.println();
        System.out.println("Aplicación finalizada.");
    }

    private void showMaterials() {
        System.out.println();
        System.out.println("--- MATERIALES ---");

        int position = 1;

        for (Material material : data.getMaterials()) {
            System.out.printf(
                    "%d. %s | %s | %s | $%s/g | %s%n",
                    position++,
                    material.getBrand(),
                    material.getType(),
                    material.getFinish(),
                    material.getCostPerGram(),
                    material.isActive() ? "Activo" : "Inactivo");
        }
    }

    private void showSpools() {
        System.out.println();
        System.out.println("--- BOBINAS ---");

        int position = 1;

        for (Spool spool : data.getSpools()) {
            System.out.printf(
                    "%d. %s | %s | %s | %s g disponibles | %s%n",
                    position++,
                    spool.getMaterial().getBrand(),
                    spool.getMaterial().getType(),
                    spool.getColor(),
                    spool.getCurrentWeight(),
                    spool.isActive() ? "Activa" : "Inactiva");
        }
    }

    private void showModels() {
        System.out.println();
        System.out.println("--- MODELOS 3D ---");

        int position = 1;

        for (Model3D model : data.getModels()) {
            System.out.printf(
                    "%d. %s | %s | %s g | %d min | %s%n",
                    position++,
                    model.getName(),
                    model.getSourcePlatform(),
                    model.getEstimatedGrams(),
                    model.getEstimatedPrintTimeMinutes(),
                    model.isActive() ? "Activo" : "Inactivo");
        }
    }

    private void showCustomers() {
        System.out.println();
        System.out.println("--- CLIENTES ---");

        int position = 1;

        for (Customer customer : data.getCustomers()) {
            System.out.printf(
                    "%d. %s | %s | %s | %s%n",
                    position++,
                    customer.getName(),
                    customer.getPhone(),
                    customer.getEmail(),
                    customer.isActive() ? "Activo" : "Inactivo");
        }
    }
}