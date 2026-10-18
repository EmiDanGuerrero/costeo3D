package com.costeo3d.presentation.console;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import com.costeo3d.application.service.OrderService;
import com.costeo3d.bootstrap.DemoData;
import com.costeo3d.domain.exception.BusinessRuleException;
import com.costeo3d.domain.model.Order;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.domain.model.Quote;
import com.costeo3d.domain.model.QuoteItem;

public class OrderConsole {

    private final DemoData data;
    private final ConsoleInput input;
    private final OrderService orderService;

    public OrderConsole(DemoData data, Scanner scanner) {
        this.data = data;
        this.input = new ConsoleInput(scanner);
        this.orderService = new OrderService();
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
            case 1 -> createOrder();
            case 2 -> listOrders();
            case 3 -> deliverOrder();
            case 0 -> running = false;
            default -> throw new IllegalStateException(
                    "Unexpected order option: " + option);
            }
        }
    }

    private void printMenu() {
        System.out.println();
        System.out.println("--- PEDIDOS ---");
        System.out.println("1. Crear pedido desde presupuesto");
        System.out.println("2. Listar pedidos");
        System.out.println("3. Entregar pedido");
        System.out.println("0. Volver");
    }

    private void createOrder() {
        if (data.getQuotes().isEmpty()) {
            System.out.println();
            System.out.println(
                    "No hay presupuestos registrados.");
            return;
        }

        listQuotes();

        int option = input.readIntBetween(
                "Seleccione un presupuesto: ",
                1,
                data.getQuotes().size());

        Quote quote = data.getQuotes().get(option - 1);

        List<PrintJob> printJobs =
                createPrintJobsFromQuote(quote);

        try {
            Order order = orderService.createOrder(
                    quote,
                    LocalDateTime.now(),
                    printJobs,
                    data.getOrders());

            data.getOrders().add(order);

            System.out.println();
            System.out.println(
                    "Pedido creado correctamente.");

            showOrder(order);

        } catch (BusinessRuleException e) {
            System.out.println();
            System.out.println(
                    "No se pudo crear el pedido: "
                    + e.getMessage());
        }
    }

    private List<PrintJob> createPrintJobsFromQuote(
            Quote quote) {

        List<PrintJob> printJobs = new ArrayList<>();
        LocalDateTime createdAt = LocalDateTime.now();

        for (QuoteItem item : quote.getItems()) {
            for (int i = 0; i < item.getQuantity(); i++) {
                PrintJob printJob = new PrintJob(
                        null,
                        item.getModel(),
                        createdAt,
                        item.getEstimatedPrintTimeMinutes());

                printJobs.add(printJob);
            }
        }

        return printJobs;
    }

    private void listQuotes() {
        System.out.println();
        System.out.println("--- PRESUPUESTOS ---");

        for (int i = 0; i < data.getQuotes().size(); i++) {
            Quote quote = data.getQuotes().get(i);

            System.out.printf(
                    "%d. %s | %s | $%s%n",
                    i + 1,
                    quote.getCustomer().getName(),
                    quote.getStatus(),
                    quote.getTotal());
        }
    }

    private void listOrders() {
        if (data.getOrders().isEmpty()) {
            System.out.println();
            System.out.println(
                    "No hay pedidos registrados.");
            return;
        }

        System.out.println();
        System.out.println("--- PEDIDOS REGISTRADOS ---");

        for (int i = 0; i < data.getOrders().size(); i++) {
            System.out.printf("%d. ", i + 1);
            showOrder(data.getOrders().get(i));
        }
    }

    private void showOrder(Order order) {
        System.out.printf(
                "Cliente: %s | Estado: %s | Trabajos: %d%n",
                order.getQuote()
                        .getCustomer()
                        .getName(),
                order.getStatus(),
                order.getPrintJobs().size());
    }
    
    private void deliverOrder() {
        if (data.getOrders().isEmpty()) {
            System.out.println();
            System.out.println(
                    "No hay pedidos registrados.");
            return;
        }

        listOrders();

        int option = input.readIntBetween(
                "Seleccione el pedido a entregar: ",
                1,
                data.getOrders().size());

        Order order = data.getOrders().get(option - 1);

        try {
            orderService.deliver(order);

            System.out.println();
            System.out.println(
                    "Pedido entregado correctamente.");

        } catch (BusinessRuleException e) {
            System.out.println();
            System.out.println(
                    "No se pudo entregar el pedido: "
                    + e.getMessage());
        }
    }
}