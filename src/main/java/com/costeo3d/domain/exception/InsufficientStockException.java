package com.costeo3d.domain.exception;

public class InsufficientStockException extends BusinessRuleException {

    private static final long serialVersionUID = 1L;

    public InsufficientStockException(String message) {
        super(message);
    }
}