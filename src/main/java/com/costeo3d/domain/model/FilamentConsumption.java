package com.costeo3d.domain.model;

import java.math.BigDecimal;

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
        this.printJob = printJob;
        this.spool = spool;
        this.plannedGrams = plannedGrams;
        this.consumedGrams = consumedGrams;
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
        this.spool = spool;
    }

    public void setPlannedGrams(BigDecimal plannedGrams) {
        this.plannedGrams = plannedGrams;
    }

    public void updateConsumedGrams(BigDecimal consumedGrams) {
        this.consumedGrams = consumedGrams;
    }
}