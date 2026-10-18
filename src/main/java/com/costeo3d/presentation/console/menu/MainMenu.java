package com.costeo3d.presentation.console.menu;

import java.util.Scanner;

public class MainMenu {

    private static final int MIN_OPTION = 0;
    private static final int MAX_OPTION = 8;

    private final Scanner scanner;

    public MainMenu(Scanner scanner) {
        this.scanner = scanner;
    }

    public int readOption() {
        while (true) {
            printMenu();

            String input = scanner.nextLine().trim();

            try {
                int option = Integer.parseInt(input);

                if (option >= MIN_OPTION && option <= MAX_OPTION) {
                    return option;
                }
            } catch (NumberFormatException e) {
                // Invalid input is handled below.
            }

            System.out.println("Opción inválida. Intente nuevamente.");
            System.out.println();
        }
    }

    private void printMenu() {
        System.out.println();
        System.out.println("==================================");
        System.out.println("     COSTEO 3D - MENÚ PRINCIPAL");
        System.out.println("==================================");
        System.out.println("1. Materiales");
        System.out.println("2. Bobinas");
        System.out.println("3. Modelos 3D");
        System.out.println("4. Clientes");
        System.out.println("5. Calculadora de costos");
        System.out.println("6. Presupuestos");
        System.out.println("7. Pedidos");
        System.out.println("8. Producción");
        System.out.println("0. Salir");
        System.out.println("==================================");
        System.out.print("Seleccione una opción: ");
    }
}