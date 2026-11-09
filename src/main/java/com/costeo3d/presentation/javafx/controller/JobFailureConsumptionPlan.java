package com.costeo3d.presentation.javafx.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.domain.service.LayerFailureEstimator;

/**
 * Adaptador de E11.4.4: se apoya en el plan de gramos aprobados de E11.4.3.
 * No accede a la base ni actualiza el estado del trabajo.
 */
final class JobFailureConsumptionPlan {
    record Line(Material material, BigDecimal approvedGrams, BigDecimal estimatedWasteGrams) {
        Line {
            Objects.requireNonNull(material);
            Objects.requireNonNull(approvedGrams);
            Objects.requireNonNull(estimatedWasteGrams);
        }
    }

    record Plan(List<Line> lines, int totalLayers, int failedAtLayer,
            String sourceDescription) {
        Plan {
            lines = List.copyOf(lines);
        }
        BigDecimal totalEstimatedWaste() {
            return lines.stream().map(Line::estimatedWasteGrams)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    private JobFailureConsumptionPlan() { }

    static Plan fromJob(PrintJob job, int totalLayers, int failedAtLayer) {
        Objects.requireNonNull(job);
        JobConsumptionPlan.Plan approved = JobConsumptionPlan.fromJob(job);
        List<Line> lines = approved.lines().stream()
                .map(line -> new Line(line.material(), line.grams(),
                        LayerFailureEstimator.estimate(line.grams(), totalLayers, failedAtLayer)))
                .toList();
        return new Plan(lines, totalLayers, failedAtLayer,
                approved.sourceDescription() + " Merma estimada por proporción de capas.");
    }
}
