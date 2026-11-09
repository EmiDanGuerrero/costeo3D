package com.costeo3d.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.costeo3d.application.service.HomeDashboardSnapshot.SpoolAlert;

class HomeDashboardServiceTest {

    @Test
    void delegatesThresholdAndLimitToReader() {
        HomeDashboardService service = new HomeDashboardService((threshold, limit) -> {
            assertEquals(new BigDecimal("200.00"), threshold);
            assertEquals(5, limit);
            return new HomeDashboardSnapshot(2, 1, 1, 1,
                    new BigDecimal("100.00"), threshold,
                    List.of(new SpoolAlert(3, "Elegoo · PLA", "Bordo", new BigDecimal("150.00"))));
        });
        HomeDashboardSnapshot data = service.snapshot();
        assertEquals(2, data.activeOrders());
        assertEquals(1, data.jobsInProgress());
        assertEquals(new BigDecimal("100.00"), data.wastedGrams());
    }

    @Test
    void snapshotMakesSpoolAlertsImmutable() {
        List<SpoolAlert> original = new ArrayList<>();
        original.add(new SpoolAlert(4, "PLA", "Blanco", BigDecimal.ZERO));
        HomeDashboardSnapshot data = new HomeDashboardSnapshot(1, 1, 1, 1,
                BigDecimal.ZERO, BigDecimal.TEN, original);
        original.clear();
        assertEquals(1, data.spoolAlerts().size());
        assertThrows(UnsupportedOperationException.class, () -> data.spoolAlerts().clear());
    }

    @Test
    void invalidSummaryIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new HomeDashboardSnapshot(
                -1, 0, 0, 0, BigDecimal.ZERO, BigDecimal.TEN, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new HomeDashboardSnapshot(
                0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.TEN,
                List.of(new SpoolAlert(1, "PLA", "Rojo", BigDecimal.ZERO))));
    }
}
