package com.costeo3d.domain.exception;

public class BusinessValidationException extends BusinessRuleException {

    private static final long serialVersionUID = 1L;

    public BusinessValidationException(String message) {
        super(message);
    }
}