package com.costeo3d.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.costeo3d.domain.enums.PrintJobStatus;
import com.costeo3d.domain.exception.BusinessRuleException;
import com.costeo3d.domain.exception.InsufficientStockException;
import com.costeo3d.domain.model.FilamentConsumption;
import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.domain.model.Spool;

class ProductionServiceTest {

    private final ProductionService productionService =
            new ProductionService();

    @Test
    void shouldCompleteJobAndUpdateStock()
            throws BusinessRuleException {

        Material material = new Material(
                "Elegoo",
                "PLA",
                "Matte",
                new BigDecimal("18.50"));

        Spool spool = new Spool(
                material,
                "Black",
                new BigDecimal("1000"));

        PrintJob job = new PrintJob(
                null,
                null,
                LocalDateTime.now(),
                300);

        productionService.startJob(
                job,
                LocalDateTime.now());

        FilamentConsumption consumption =
                new FilamentConsumption(
                        job,
                        spool,
                        new BigDecimal("125"),
                        new BigDecimal("120"));

        productionService.completeJob(
                job,
                List.of(consumption),
                LocalDateTime.now());

        assertEquals(
                new BigDecimal("880"),
                spool.getCurrentWeight());

        assertEquals(
                PrintJobStatus.COMPLETED,
                job.getStatus());
    }

    @Test
    void shouldNotModifyStockWhenStockIsInsufficient()
            throws BusinessRuleException {

        Material material = new Material(
                "Elegoo",
                "PLA",
                "Matte",
                new BigDecimal("18.50"));

        Spool spool = new Spool(
                material,
                "White",
                new BigDecimal("110"));

        PrintJob job = new PrintJob(
                null,
                null,
                LocalDateTime.now(),
                300);

        productionService.startJob(
                job,
                LocalDateTime.now());

        FilamentConsumption consumption =
                new FilamentConsumption(
                        job,
                        spool,
                        new BigDecimal("100"),
                        new BigDecimal("150"));

        assertThrows(
                InsufficientStockException.class,
                () -> productionService.completeJob(
                        job,
                        List.of(consumption),
                        LocalDateTime.now()));

        assertEquals(
                new BigDecimal("110"),
                spool.getCurrentWeight());

        assertEquals(
                PrintJobStatus.IN_PROGRESS,
                job.getStatus());
    }
}