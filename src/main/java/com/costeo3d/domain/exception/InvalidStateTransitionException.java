package com.costeo3d.domain.exception;

public class InvalidStateTransitionException extends BusinessRuleException {

    private static final long serialVersionUID = 1L;

    public InvalidStateTransitionException(String message) {
        super(message);
    }
}