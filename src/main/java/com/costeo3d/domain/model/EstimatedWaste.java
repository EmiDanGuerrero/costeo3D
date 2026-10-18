package com.costeo3d.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.costeo3d.domain.validation.ValidationUtils;

public class EstimatedWaste {

    private Integer id;
    private PrintJob printJob;
    private int totalLayers;
    private int failureLayer;

    public EstimatedWaste(PrintJob printJob, int totalLayers, int failureLayer) {
        this(null, printJob, totalLayers, failureLayer);
    }

    public EstimatedWaste(Integer id, PrintJob printJob,
            int totalLayers, int failureLayer) {

        validateLayers(totalLayers, failureLayer);

        this.id = id;
        this.printJob = ValidationUtils.requireNotNull(
                printJob, "Print job");
        this.totalLayers = totalLayers;
        this.failureLayer = failureLayer;
    }

    public Integer getId() {
        return id;
    }

    public PrintJob getPrintJob() {
        return printJob;
    }

    public int getTotalLayers() {
        return totalLayers;
    }

    public int getFailureLayer() {
        return failureLayer;
    }

    public void setTotalLayers(int totalLayers) {
        validateLayers(totalLayers, failureLayer);
        this.totalLayers = totalLayers;
    }

    public void setFailureLayer(int failureLayer) {
        validateLayers(totalLayers, failureLayer);
        this.failureLayer = failureLayer;
    }

    public BigDecimal getProgressPercentage() {
        return BigDecimal.valueOf(failureLayer)
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        BigDecimal.valueOf(totalLayers),
                        2,
                        RoundingMode.HALF_UP);
    }

    private void validateLayers(int totalLayers, int failureLayer) {
        ValidationUtils.requirePositive(totalLayers, "Total layers");
        ValidationUtils.requirePositive(failureLayer, "Failure layer");

        if (failureLayer > totalLayers) {
            throw new IllegalArgumentException(
                    "Failure layer cannot exceed total layers.");
        }
    }
}