package com.costeo3d.infrastructure.persistence;

/** Representa un error tecnico durante una operacion de persistencia. */
public class PersistenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
