package com.costeo3d.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.costeo3d.domain.exception.InvalidStateTransitionException;
import com.costeo3d.domain.enums.QuoteStatus;

public class Quote {

    private Integer id;
    private Customer customer;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private QuoteStatus status;
    private BigDecimal total;

    private final List<QuoteItem> items;

    public Quote(Customer customer, LocalDateTime issuedAt,
            LocalDateTime expiresAt) {

        this(null, customer, issuedAt, expiresAt,
                QuoteStatus.VALID, BigDecimal.ZERO);
    }

    public Quote(Integer id, Customer customer, LocalDateTime issuedAt,
            LocalDateTime expiresAt, QuoteStatus status, BigDecimal total) {

        this.id = id;
        this.customer = customer;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.status = status;
        this.total = total;
        this.items = new ArrayList<>();
    }

    public Integer getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public QuoteStatus getStatus() {
        return status;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public List<QuoteItem> getItems() {
        return List.copyOf(items);
    }

    public void addItem(QuoteItem item) {
        items.add(item);
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void updateTotal(BigDecimal total) {
        this.total = total;
    }

    public void accept() throws InvalidStateTransitionException {
        if (status != QuoteStatus.VALID) {
            throw new InvalidStateTransitionException(
                    "Only a valid quote can be accepted.");
        }

        status = QuoteStatus.ACCEPTED;
    }

    public void expire() throws InvalidStateTransitionException {
        if (status != QuoteStatus.VALID) {
            throw new InvalidStateTransitionException(
                    "Only a valid quote can expire.");
        }

        status = QuoteStatus.EXPIRED;
    }

    public void cancel() throws InvalidStateTransitionException {
        if (status == QuoteStatus.EXPIRED
                || status == QuoteStatus.CANCELLED) {

            throw new InvalidStateTransitionException(
                    "The quote cannot be cancelled from its current state.");
        }

        status = QuoteStatus.CANCELLED;
    }
}