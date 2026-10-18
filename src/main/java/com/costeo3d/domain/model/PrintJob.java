package com.costeo3d.domain.model;

import java.time.LocalDateTime;

import com.costeo3d.domain.enums.PrintJobStatus;
import com.costeo3d.domain.exception.InvalidStateTransitionException;
import com.costeo3d.domain.validation.ValidationUtils;

public class PrintJob {

    private Integer id;
    private Order order;
    private Model3D model;

    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    private PrintJobStatus status;
    private int estimatedPrintTimeMinutes;

    public PrintJob(Order order, Model3D model, LocalDateTime createdAt,
            int estimatedPrintTimeMinutes) {
        this(null, order, model, createdAt, null, null,
                PrintJobStatus.PENDING, estimatedPrintTimeMinutes);
    }

    public PrintJob(Integer id, Order order, Model3D model,
            LocalDateTime createdAt, LocalDateTime startedAt,
            LocalDateTime finishedAt, PrintJobStatus status,
            int estimatedPrintTimeMinutes) {

        this.id = id;
        this.order = order;
        this.model = model;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.status = status;
        this.estimatedPrintTimeMinutes = estimatedPrintTimeMinutes;
    }

    public Integer getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public Model3D getModel() {
        return model;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public PrintJobStatus getStatus() {
        return status;
    }

    public int getEstimatedPrintTimeMinutes() {
        return estimatedPrintTimeMinutes;
    }

    public void assignToOrder(Order order) {
        this.order = order;
    }

    public void setModel(Model3D model) {
        this.model = model;
    }

    public void setEstimatedPrintTimeMinutes(int estimatedPrintTimeMinutes) {
        this.estimatedPrintTimeMinutes = estimatedPrintTimeMinutes;
    }

    public void start(LocalDateTime startedAt)
            throws InvalidStateTransitionException {

        if (status != PrintJobStatus.PENDING) {
            throw new InvalidStateTransitionException(
                    "Only a pending print job can be started.");
        }

        this.startedAt = ValidationUtils.requireNotNull(
                startedAt, "Start date");

        status = PrintJobStatus.IN_PROGRESS;
    }

    public void complete(LocalDateTime finishedAt)
            throws InvalidStateTransitionException {

        if (status != PrintJobStatus.IN_PROGRESS) {
            throw new InvalidStateTransitionException(
                    "Only a print job in progress can be completed.");
        }

        this.finishedAt = ValidationUtils.requireNotNull(
                finishedAt, "Finish date");

        status = PrintJobStatus.COMPLETED;
    }

    public void fail(LocalDateTime finishedAt)
            throws InvalidStateTransitionException {

        if (status != PrintJobStatus.IN_PROGRESS) {
            throw new InvalidStateTransitionException(
                    "Only a print job in progress can fail.");
        }

        this.finishedAt = ValidationUtils.requireNotNull(
                finishedAt, "Finish date");

        status = PrintJobStatus.FAILED;
    }
}