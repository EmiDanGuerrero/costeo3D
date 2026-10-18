package com.costeo3d.presentation.console;

import java.math.BigDecimal;
import java.util.Scanner;

public class ConsoleInput {

    private final Scanner scanner;

    public ConsoleInput(Scanner scanner) {
        this.scanner = scanner;
    }

    public int readIntBetween(String message, int min, int max) {
        while (true) {
            System.out.print(message);
            String input = scanner.nextLine().trim();

            try {
                int value = Integer.parseInt(input);

                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException e) {
                // Invalid input is handled below.
            }

            System.out.printf(
                    "Ingrese un número entre %d y %d.%n",
                    min,
                    max);
        }
    }

    public int readPositiveInt(String message) {
        while (true) {
            System.out.print(message);
            String input = scanner.nextLine().trim();

            try {
                int value = Integer.parseInt(input);

                if (value > 0) {
                    return value;
                }
            } catch (NumberFormatException e) {
                // Invalid input is handled below.
            }

            System.out.println(
                    "Ingrese un número entero mayor que cero.");
        }
    }

    public BigDecimal readNonNegativeBigDecimal(String message) {
        while (true) {
            System.out.print(message);
            String input = scanner.nextLine()
                    .trim()
                    .replace(',', '.');

            try {
                BigDecimal value = new BigDecimal(input);

                if (value.compareTo(BigDecimal.ZERO) >= 0) {
                    return value;
                }
            } catch (NumberFormatException e) {
                // Invalid input is handled below.
            }

            System.out.println(
                    "Ingrese un número mayor o igual que cero.");
        }
    }

    public BigDecimal readPositiveBigDecimal(String message) {
        while (true) {
            System.out.print(message);
            String input = scanner.nextLine()
                    .trim()
                    .replace(',', '.');

            try {
                BigDecimal value = new BigDecimal(input);

                if (value.compareTo(BigDecimal.ZERO) > 0) {
                    return value;
                }
            } catch (NumberFormatException e) {
                // Invalid input is handled below.
            }

            System.out.println(
                    "Ingrese un número mayor que cero.");
        }
    }

    public String readRequiredText(String message) {
        while (true) {
            System.out.print(message);
            String value = scanner.nextLine().trim();

            if (!value.isBlank()) {
                return value;
            }

            System.out.println(
                    "El valor no puede estar vacío.");
        }
    }
}