package com.costeo3d.domain.model;

import java.math.BigDecimal;

import com.costeo3d.domain.validation.ValidationUtils;

public class FilamentConsumption {

    private Integer id;
    private PrintJob printJob;
    private Spool spool;
    private BigDecimal plannedGrams;
    private BigDecimal consumedGrams;

    public FilamentConsumption(PrintJob printJob, Spool spool,
            BigDecimal plannedGrams, BigDecimal consumedGrams) {
        this(null, printJob, spool, plannedGrams, consumedGrams);
    }

    public FilamentConsumption(Integer id, PrintJob printJob, Spool spool,
            BigDecimal plannedGrams, BigDecimal consumedGrams) {
        this.id = id;
        this.printJob = ValidationUtils.requireNotNull(printJob, "Print job");
        setSpool(spool);
        setPlannedGrams(plannedGrams);
        updateConsumedGrams(consumedGrams);
    }

    public Integer getId() {
        return id;
    }

    public PrintJob getPrintJob() {
        return printJob;
    }

    public Spool getSpool() {
        return spool;
    }

    public BigDecimal getPlannedGrams() {
        return plannedGrams;
    }

    public BigDecimal getConsumedGrams() {
        return consumedGrams;
    }

    public void setSpool(Spool spool) {
        this.spool = ValidationUtils.requireNotNull(spool, "Spool");
    }

    public void setPlannedGrams(BigDecimal plannedGrams) {
        if (plannedGrams != null) {
            ValidationUtils.requireNonNegative(plannedGrams, "Planned grams");
        }
        this.plannedGrams = plannedGrams;
    }

    public void updateConsumedGrams(BigDecimal consumedGrams) {
        ValidationUtils.requireNonNegative(consumedGrams, "Consumed grams");
        this.consumedGrams = consumedGrams;
    }
}
