package com.costeo3d.presentation.javafx.controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.Order;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.domain.model.Quote;
import com.costeo3d.domain.model.QuoteItem;
import com.costeo3d.domain.model.QuoteMaterialItem;

/**
 * E11.4.3 — Gramos aprobados del presupuesto, sin recálculos ni edición.
 *
 * No es obligatorio que los ID de modelo de un QuoteItem y un PrintJob
 * coincidan CUANDO el pedido permite identificar el concepto sin ambigüedad:
 * un solo concepto y un trabajo, o un solo concepto y todos los trabajos
 * correspondientes a un mismo modelo de producción identificado.
 *
 * Cuando hay varios conceptos, solamente se asigna por un ID de modelo
 * explícito y único. Nunca se intenta asignar por posición o por nombre.
 */
public final class JobConsumptionPlan {

    record Line(Material material, BigDecimal grams) {
        Line {
            Objects.requireNonNull(material, "El material es obligatorio.");
            Objects.requireNonNull(grams, "Los gramos son obligatorios.");
            if (grams.signum() <= 0 || grams.stripTrailingZeros().scale() > 2) {
                throw new IllegalArgumentException(
                        "El presupuesto contiene gramos inválidos para registrar el consumo.");
            }
        }
    }

    record Plan(List<Line> lines, String sourceDescription) {
        Plan {
            lines = List.copyOf(lines);
            if (lines.isEmpty()) {
                throw new IllegalArgumentException(
                        "No hay gramos del presupuesto registrados para este trabajo.");
            }
        }
    }

    private JobConsumptionPlan() { }

    static Plan fromJob(PrintJob job) {
        Objects.requireNonNull(job, "El trabajo es obligatorio.");
        Order order = job.getOrder();
        Quote quote = order == null ? null : order.getQuote();
        if (quote == null || quote.getItems() == null || quote.getItems().isEmpty()) {
            throw new IllegalArgumentException(
                    "El pedido de este trabajo no incluye detalles del presupuesto aprobado. "
                    + "No se puede descontar stock sin sus gramos presupuestados.");
        }

        List<QuoteItem> items = quote.getItems();
        if (items.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("El presupuesto tiene conceptos incompletos.");
        }
        List<PrintJob> jobs = order.getPrintJobs();
        if (job.getId() == null || jobs == null || jobs.stream().noneMatch(other ->
                other != null && Objects.equals(other.getId(), job.getId()))) {
            throw new IllegalArgumentException(
                    "No se encontró el trabajo #" + job.getId()
                    + " entre los trabajos del pedido. Actualizá Producción.");
        }

        // Primero buscamos coincidencia real de modelo (ID no nulo en ambos).
        List<QuoteItem> candidates = items.stream()
                .filter(item -> sameModel(item, job)).toList();
        if (candidates.size() > 1) {
            throw new IllegalArgumentException(
                    "El modelo del trabajo #" + job.getId() + " aparece en "
                    + candidates.size() + " conceptos del presupuesto #" + quote.getId()
                    + ". Falta la relación trabajo ↔ concepto; no se puede adivinar.");
        }

        QuoteItem item;
        List<PrintJob> relevantJobs;
        String assignment;
        if (candidates.size() == 1) {
            // Múltiples conceptos: solamente se admite un ID coincidente único.
            item = candidates.get(0);
            relevantJobs = jobs.stream()
                    .filter(other -> other != null && sameModel(item, other)).toList();
            assignment = "Concepto identificado por ID de modelo del presupuesto.";
        } else if (items.size() == 1) {
            // Única alternativa presupuestada. No exigir un ID de modelo idéntico
            // si la relación con los trabajos del mismo pedido es inequívoca.
            item = items.get(0);
            if (jobs.size() == 1) {
                relevantJobs = List.of(job);
            } else if (allJobsSameIdentifiedModel(jobs, job)) {
                relevantJobs = List.copyOf(jobs);
            } else {
                throw new IllegalArgumentException(
                        "El presupuesto #" + quote.getId() + " tiene un único concepto, "
                        + "pero el pedido tiene " + jobs.size() + " trabajos con modelos "
                        + "distintos o sin identificar. No es seguro distribuir sus gramos.");
            }
            assignment = "Concepto único del presupuesto #" + quote.getId()
                    + ", asociado al trabajo por su pedido (sin exigir coincidencia de ID). "
                    + "Modelo presupuestado: " + quoteModelLabel(item)
                    + "; modelo del trabajo: " + modelLabel(job)
                    + ". Verificá que corresponda a la impresión antes de confirmar.";
        } else {
            String approvedModels = items.stream()
                    .limit(12)
                    .map(JobConsumptionPlan::quoteModelLabel)
                    .collect(Collectors.joining(", "));
            if (items.size() > 12) approvedModels += ", ...";
            throw new IllegalArgumentException(
                    "El trabajo #" + job.getId() + " tiene modelo " + modelLabel(job)
                    + ", pero ninguno de los " + items.size() + " conceptos del presupuesto #"
                    + quote.getId() + " coincide. Modelos de conceptos: [" + approvedModels
                    + "]. Para varios conceptos se necesita vincular cada trabajo con "
                    + "su concepto; no se descontará material de otro modelo.");
        }

        int quantity = item.getQuantity();
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "El concepto elegido tiene una cantidad presupuestada inválida: "
                    + quantity + ".");
        }

        int matchingJobs = relevantJobs.size();
        int factor;
        String distribution;
        if (matchingJobs == 1) {
            factor = quantity;
            distribution = "Una impresión para la tanda de " + quantity + " unidad(es).";
        } else if (matchingJobs == quantity) {
            factor = 1;
            distribution = "Consumo presupuestado por unidad.";
        } else if (matchingJobs > quantity
                && failedAttempts(relevantJobs) >= matchingJobs - quantity) {
            factor = 1;
            distribution = "Consumo por unidad; hay intentos fallidos registrados.";
        } else {
            throw new IllegalArgumentException(
                    "El concepto del presupuesto #" + quote.getId() + " contempla "
                    + quantity + " unidad(es), pero hay " + matchingJobs
                    + " trabajo(s) asociados a este concepto. No se puede saber si cada "
                    + "trabajo representa una unidad o parte de la tanda. "
                    + "No se usarán gramos supuestos.");
        }

        if (item.getMaterials() == null || item.getMaterials().isEmpty()) {
            throw new IllegalArgumentException(
                    "El concepto presupuestado no tiene materiales y gramos registrados.");
        }
        Map<Integer, Line> aggregate = new LinkedHashMap<>();
        for (QuoteMaterialItem materialItem : item.getMaterials()) {
            if (materialItem == null) {
                throw new IllegalArgumentException("El presupuesto tiene un material incompleto.");
            }
            Material material = materialItem.getMaterial();
            BigDecimal grams = materialItem.getEstimatedGrams();
            if (material == null || material.getId() == null || grams == null) {
                throw new IllegalArgumentException(
                        "El concepto seleccionado no tiene datos completos de materiales y gramos.");
            }
            if (grams.signum() <= 0 || grams.stripTrailingZeros().scale() > 2) {
                throw new IllegalArgumentException(
                        "Los gramos guardados en el presupuesto no son válidos.");
            }
            BigDecimal total = grams.multiply(BigDecimal.valueOf(factor));
            Line previous = aggregate.get(material.getId());
            aggregate.put(material.getId(), new Line(material,
                    previous == null ? total : previous.grams().add(total)));
        }
        return new Plan(new ArrayList<>(aggregate.values()),
                "Consumo aprobado del presupuesto #" + quote.getId() + ". "
                + distribution + " " + assignment);
    }

    private static boolean sameModel(QuoteItem item, PrintJob job) {
        Integer quoteId = item.getModel() == null ? null : item.getModel().getId();
        Integer jobId = job.getModel() == null ? null : job.getModel().getId();
        return quoteId != null && jobId != null && quoteId.equals(jobId);
    }

    private static boolean allJobsSameIdentifiedModel(List<PrintJob> jobs, PrintJob selected) {
        Integer modelId = selected.getModel() == null ? null : selected.getModel().getId();
        return modelId != null && jobs.stream().allMatch(other -> other != null
                && other.getModel() != null
                && modelId.equals(other.getModel().getId()));
    }

    private static long failedAttempts(List<PrintJob> jobs) {
        return jobs.stream().filter(value -> value.getStatus() != null
                && ("FAILED".equals(value.getStatus().name())
                    || "FAILURE".equals(value.getStatus().name())))
                .count();
    }

    private static String modelLabel(PrintJob job) {
        return job.getModel() == null || job.getModel().getId() == null
                ? "sin modelo de catálogo" : "ID " + job.getModel().getId();
    }

    private static String quoteModelLabel(QuoteItem item) {
        return item.getModel() == null || item.getModel().getId() == null
                ? "sin modelo de catálogo" : "ID " + item.getModel().getId();
    }
}
