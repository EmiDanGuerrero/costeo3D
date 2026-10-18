package com.costeo3d.domain.contracts;

import java.math.BigDecimal;

public interface Quotable {

    BigDecimal getEstimatedGrams();

    int getEstimatedPrintTimeMinutes();
}