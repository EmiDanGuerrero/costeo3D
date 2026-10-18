package com.costeo3d.domain.exception;

public abstract class BusinessRuleException extends Exception {

    private static final long serialVersionUID = 1L;

    protected BusinessRuleException(String message) {
        super(message);
    }

    protected BusinessRuleException(String message, Throwable cause) {
        super(message, cause);
    }
}