package com.costeo3d.domain.validation;

import java.math.BigDecimal;

public final class ValidationUtils {

    private ValidationUtils() {
    }

    public static <T> T requireNotNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null.");
        }

        return value;
    }

    public static void requirePositive(BigDecimal value, String fieldName) {
        requireNotNull(value, fieldName);

        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be greater than zero.");
        }
    }

    public static void requireNonNegative(BigDecimal value, String fieldName) {
        requireNotNull(value, fieldName);

        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be negative.");
        }
    }

    public static void requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be greater than zero.");
        }
    }
}