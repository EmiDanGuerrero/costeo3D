package com.costeo3d.domain.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.costeo3d.domain.exception.InvalidStateTransitionException;
import com.costeo3d.domain.enums.OrderStatus;

public class Order {

    private Integer id;
    private Quote quote;
    private LocalDateTime createdAt;
    private OrderStatus status;

    private final List<PrintJob> printJobs;

    public Order(Quote quote, LocalDateTime createdAt) {
        this(null, quote, createdAt, OrderStatus.PENDING);
    }

    public Order(Integer id, Quote quote, LocalDateTime createdAt,
            OrderStatus status) {
        this.id = id;
        this.quote = quote;
        this.createdAt = createdAt;
        this.status = status;
        this.printJobs = new ArrayList<>();
    }

    public Integer getId() {
        return id;
    }

    public Quote getQuote() {
        return quote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public List<PrintJob> getPrintJobs() {
        return List.copyOf(printJobs);
    }

    public void addPrintJob(PrintJob printJob) {
        printJobs.add(printJob);
        printJob.assignToOrder(this);
    }

    public void startProduction() throws InvalidStateTransitionException {
        if (status != OrderStatus.PENDING) {
            throw new InvalidStateTransitionException(
                    "Only a pending order can start production.");
        }

        status = OrderStatus.IN_PRODUCTION;
    }

    public void markAsReady() throws InvalidStateTransitionException {
        if (status != OrderStatus.IN_PRODUCTION) {
            throw new InvalidStateTransitionException(
                    "Only an order in production can be marked as ready.");
        }

        status = OrderStatus.READY;
    }

    public void deliver() throws InvalidStateTransitionException {
        if (status != OrderStatus.READY) {
            throw new InvalidStateTransitionException(
                    "Only a ready order can be delivered.");
        }

        status = OrderStatus.DELIVERED;
    }

    public void cancel() throws InvalidStateTransitionException {
        if (status == OrderStatus.DELIVERED
                || status == OrderStatus.CANCELLED) {

            throw new InvalidStateTransitionException(
                    "The order cannot be cancelled from its current state.");
        }

        status = OrderStatus.CANCELLED;
    }
}