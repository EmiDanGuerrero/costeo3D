package com.costeo3d.presentation.console;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import com.costeo3d.application.service.OrderService;
import com.costeo3d.application.service.ProductionService;
import com.costeo3d.bootstrap.DemoData;
import com.costeo3d.domain.enums.OrderStatus;
import com.costeo3d.domain.enums.PrintJobStatus;
import com.costeo3d.domain.exception.BusinessRuleException;
import com.costeo3d.domain.model.FilamentConsumption;
import com.costeo3d.domain.model.Order;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.domain.model.Spool;
import com.costeo3d.application.service.WasteEstimator;
import com.costeo3d.domain.model.EstimatedWaste;

public class ProductionConsole {

    private final DemoData data;
    private final ConsoleInput input;
    private final ProductionService productionService;
    private final OrderService orderService;
    private final WasteEstimator wasteEstimator;

    public ProductionConsole(DemoData data, Scanner scanner) {
        this.data = data;
        this.input = new ConsoleInput(scanner);
        this.productionService = new ProductionService();
        this.orderService = new OrderService();
        this.wasteEstimator = new WasteEstimator();
    }

    public void show() {
        boolean running = true;

        while (running) {
            printMenu();

            int option = input.readIntBetween(
                    "Seleccione una opción: ",
                    0,
                    5);

            switch (option) {
            case 1 -> listJobs();
            case 2 -> startJob();
            case 3 -> completeJob();
            case 4 -> failJob();
            case 5 -> estimateWaste();
            case 0 -> running = false;
            default -> throw new IllegalStateException(
                    "Unexpected production option: " + option);
            }
        }
    }

    private void printMenu() {
        System.out.println();
        System.out.println("--- PRODUCCIÓN ---");
        System.out.println("1. Listar trabajos");
        System.out.println("2. Iniciar trabajo");
        System.out.println("3. Finalizar trabajo");
        System.out.println("4. Registrar fallo");
        System.out.println("5. Estimar merma de trabajo fallido");
        System.out.println("0. Volver");
    }

    private void listJobs() {
        List<PrintJob> jobs = getAllJobs();

        if (jobs.isEmpty()) {
            System.out.println();
            System.out.println("No hay trabajos registrados.");
            return;
        }

        System.out.println();
        System.out.println("--- TRABAJOS DE IMPRESIÓN ---");

        for (int i = 0; i < jobs.size(); i++) {
            PrintJob job = jobs.get(i);

            String modelName = job.getModel() != null
                    ? job.getModel().getName()
                    : "Sin modelo";

            String customerName = job.getOrder() != null
                    ? job.getOrder()
                            .getQuote()
                            .getCustomer()
                            .getName()
                    : "Sin pedido";

            System.out.printf(
                    "%d. %s | Cliente: %s | Estado: %s | %d min%n",
                    i + 1,
                    modelName,
                    customerName,
                    job.getStatus(),
                    job.getEstimatedPrintTimeMinutes());
        }
    }

    private void startJob() {
        List<PrintJob> jobs = getAllJobs();

        if (jobs.isEmpty()) {
            System.out.println();
            System.out.println("No hay trabajos registrados.");
            return;
        }

        listJobs();

        int option = input.readIntBetween(
                "Seleccione el trabajo a iniciar: ",
                1,
                jobs.size());

        PrintJob job = jobs.get(option - 1);

        try {
            startOrderIfNecessary(job);

            productionService.startJob(
                    job,
                    LocalDateTime.now());

            System.out.println();
            System.out.println(
                    "Trabajo iniciado correctamente.");

        } catch (BusinessRuleException e) {
            System.out.println();
            System.out.println(
                    "No se pudo iniciar el trabajo: "
                    + e.getMessage());
        }
    }

    private void startOrderIfNecessary(PrintJob job)
            throws BusinessRuleException {

        Order order = job.getOrder();

        if (order != null
                && order.getStatus() == OrderStatus.PENDING) {

            orderService.startProduction(order);
        }
    }

    private List<PrintJob> getAllJobs() {
        List<PrintJob> jobs = new ArrayList<>();

        for (Order order : data.getOrders()) {
            jobs.addAll(order.getPrintJobs());
        }

        return jobs;
    }
    
    private PrintJob selectJob(String message) {
        List<PrintJob> jobs = getAllJobs();

        if (jobs.isEmpty()) {
            System.out.println();
            System.out.println("No hay trabajos registrados.");
            return null;
        }

        listJobs();

        int option = input.readIntBetween(
                message,
                1,
                jobs.size());

        return jobs.get(option - 1);
    }
    
    private void showAvailableSpools() {
        System.out.println("--- BOBINAS DISPONIBLES ---");

        for (int i = 0; i < data.getSpools().size(); i++) {
            Spool spool = data.getSpools().get(i);

            System.out.printf(
                    "%d. %s | %s | %s | %s g disponibles%n",
                    i + 1,
                    spool.getMaterial().getBrand(),
                    spool.getMaterial().getType(),
                    spool.getColor(),
                    spool.getCurrentWeight());
        }
    }
    
    private Spool selectSpool() {
        int option = input.readIntBetween(
                "Seleccione una bobina: ",
                1,
                data.getSpools().size());

        return data.getSpools().get(option - 1);
    }
    
    private List<FilamentConsumption> readConsumptions(
            PrintJob job) {

        List<FilamentConsumption> consumptions =
                new ArrayList<>();

        System.out.println();
        showAvailableSpools();

        int quantity = input.readPositiveInt(
                "Cantidad de bobinas utilizadas: ");

        for (int i = 0; i < quantity; i++) {
            System.out.printf(
                    "%nBobina %d de %d%n",
                    i + 1,
                    quantity);

            Spool spool = selectSpool();

            BigDecimal plannedGrams =
                    input.readPositiveBigDecimal(
                            "Gramos previstos: ");

            BigDecimal consumedGrams =
                    input.readNonNegativeBigDecimal(
                            "Gramos realmente consumidos: ");

            consumptions.add(
                    new FilamentConsumption(
                            job,
                            spool,
                            plannedGrams,
                            consumedGrams));
        }

        return consumptions;
    }
    
    private void updateOrderIfCompleted(PrintJob job) {
        Order order = job.getOrder();

        if (order == null
                || order.getStatus() != OrderStatus.IN_PRODUCTION) {
            return;
        }

        boolean allCompleted = order.getPrintJobs()
                .stream()
                .allMatch(printJob ->
                        printJob.getStatus()
                                == PrintJobStatus.COMPLETED);

        if (!allCompleted) {
            return;
        }

        try {
            orderService.markAsReady(order);

            System.out.println(
                    "Todos los trabajos finalizaron. "
                    + "El pedido quedó LISTO.");

        } catch (BusinessRuleException e) {
            System.out.println(
                    "El trabajo finalizó, pero no se pudo "
                    + "actualizar el pedido: "
                    + e.getMessage());
        }
    }
    
    private void completeJob() {
        PrintJob job = selectJob(
                "Seleccione el trabajo a finalizar: ");

        if (job == null) {
            return;
        }

        List<FilamentConsumption> consumptions =
                readConsumptions(job);

        try {
            productionService.completeJob(
                    job,
                    consumptions,
                    LocalDateTime.now());

            data.getConsumptions().addAll(consumptions);

            System.out.println();
            System.out.println(
                    "Trabajo finalizado correctamente.");

            updateOrderIfCompleted(job);

        } catch (BusinessRuleException e) {
            System.out.println();
            System.out.println(
                    "No se pudo finalizar el trabajo: "
                    + e.getMessage());
        }
    }
    
    private void failJob() {
        PrintJob job = selectJob(
                "Seleccione el trabajo que falló: ");

        if (job == null) {
            return;
        }

        List<FilamentConsumption> consumptions =
                readConsumptions(job);

        try {
            productionService.failJob(
                    job,
                    consumptions,
                    LocalDateTime.now());

            data.getConsumptions().addAll(consumptions);

            System.out.println();
            System.out.println(
                    "Fallo registrado correctamente.");

        } catch (BusinessRuleException e) {
            System.out.println();
            System.out.println(
                    "No se pudo registrar el fallo: "
                    + e.getMessage());
        }
    }
    
    private List<PrintJob> getFailedJobs() {
        return getAllJobs().stream()
                .filter(job ->
                        job.getStatus() == PrintJobStatus.FAILED)
                .toList();
    }
    
    private void estimateWaste() {
        List<PrintJob> failedJobs = getFailedJobs();

        if (failedJobs.isEmpty()) {
            System.out.println();
            System.out.println(
                    "No hay trabajos fallidos para estimar merma.");
            return;
        }

        System.out.println();
        System.out.println("--- TRABAJOS FALLIDOS ---");

        for (int i = 0; i < failedJobs.size(); i++) {
            PrintJob job = failedJobs.get(i);

            String modelName = job.getModel() != null
                    ? job.getModel().getName()
                    : "Sin modelo";

            System.out.printf(
                    "%d. %s | Estado: %s%n",
                    i + 1,
                    modelName,
                    job.getStatus());
        }

        int option = input.readIntBetween(
                "Seleccione un trabajo: ",
                1,
                failedJobs.size());

        PrintJob job = failedJobs.get(option - 1);

        int totalLayers = input.readPositiveInt(
                "Cantidad total de capas: ");

        int failureLayer = input.readPositiveInt(
                "Capa en la que ocurrió el fallo: ");

        try {
            EstimatedWaste estimate =
                    wasteEstimator.createEstimate(
                            job,
                            totalLayers,
                            failureLayer,
                            data.getWasteEstimates());

            data.getWasteEstimates().add(estimate);

            System.out.println();
            System.out.println(
                    "Merma estimada registrada correctamente.");

            System.out.printf(
                    "Avance aproximado al momento del fallo: %s%%%n",
                    estimate.getProgressPercentage());

        } catch (BusinessRuleException | IllegalArgumentException e) {
            System.out.println();
            System.out.println(
                    "No se pudo registrar la merma: "
                    + e.getMessage());
        }
    }
}