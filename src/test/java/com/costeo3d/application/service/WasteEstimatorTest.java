package com.costeo3d.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.costeo3d.domain.exception.BusinessRuleException;
import com.costeo3d.domain.exception.BusinessValidationException;
import com.costeo3d.domain.model.EstimatedWaste;
import com.costeo3d.domain.model.PrintJob;

class WasteEstimatorTest {

    private final WasteEstimator wasteEstimator =
            new WasteEstimator();

    @Test
    void shouldCalculateWasteProgressForFailedJob()
            throws BusinessRuleException {

        PrintJob job = createFailedJob();

        EstimatedWaste estimate =
                wasteEstimator.createEstimate(
                        job,
                        500,
                        125,
                        new ArrayList<>());

        assertEquals(
                new BigDecimal("25.00"),
                estimate.getProgressPercentage());
    }

    @Test
    void shouldRejectSecondWasteEstimateForSameJob()
            throws BusinessRuleException {

        PrintJob job = createFailedJob();

        List<EstimatedWaste> estimates =
                new ArrayList<>();

        EstimatedWaste firstEstimate =
                wasteEstimator.createEstimate(
                        job,
                        500,
                        125,
                        estimates);

        estimates.add(firstEstimate);

        assertThrows(
                BusinessValidationException.class,
                () -> wasteEstimator.createEstimate(
                        job,
                        500,
                        200,
                        estimates));
    }

    @Test
    void shouldRejectFailureLayerGreaterThanTotalLayers()
            throws BusinessRuleException {

        PrintJob job = createFailedJob();

        assertThrows(
                IllegalArgumentException.class,
                () -> wasteEstimator.createEstimate(
                        job,
                        500,
                        600,
                        new ArrayList<>()));
    }

    private PrintJob createFailedJob()
            throws BusinessRuleException {

        PrintJob job = new PrintJob(
                null,
                null,
                LocalDateTime.now(),
                300);

        job.start(LocalDateTime.now());
        job.fail(LocalDateTime.now());

        return job;
    }
}