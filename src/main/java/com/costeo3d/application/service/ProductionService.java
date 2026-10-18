package com.costeo3d.application.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.costeo3d.domain.enums.PrintJobStatus;
import com.costeo3d.domain.exception.BusinessRuleException;
import com.costeo3d.domain.exception.BusinessValidationException;
import com.costeo3d.domain.exception.InsufficientStockException;
import com.costeo3d.domain.exception.InvalidStateTransitionException;
import com.costeo3d.domain.model.FilamentConsumption;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.domain.model.Spool;
import com.costeo3d.domain.validation.ValidationUtils;

public class ProductionService {

    public void startJob(PrintJob printJob, LocalDateTime startedAt)
            throws BusinessRuleException {

        ValidationUtils.requireNotNull(printJob, "Print job");
        ValidationUtils.requireNotNull(startedAt, "Start date");

        printJob.start(startedAt);
    }

    public void completeJob(
            PrintJob printJob,
            List<FilamentConsumption> consumptions,
            LocalDateTime finishedAt)
            throws BusinessRuleException {

        validateJobInProgress(printJob);
        ValidationUtils.requireNotNull(finishedAt, "Finish date");

        Map<Spool, BigDecimal> consumptionBySpool =
                prepareConsumptions(printJob, consumptions);

        validateStock(consumptionBySpool);
        applyConsumptions(consumptionBySpool);

        printJob.complete(finishedAt);
    }

    public void failJob(
            PrintJob printJob,
            List<FilamentConsumption> consumptions,
            LocalDateTime finishedAt)
            throws BusinessRuleException {

        validateJobInProgress(printJob);
        ValidationUtils.requireNotNull(finishedAt, "Finish date");

        Map<Spool, BigDecimal> consumptionBySpool =
                prepareConsumptions(printJob, consumptions);

        validateStock(consumptionBySpool);
        applyConsumptions(consumptionBySpool);

        printJob.fail(finishedAt);
    }

    private void validateJobInProgress(PrintJob printJob)
            throws InvalidStateTransitionException {

        ValidationUtils.requireNotNull(printJob, "Print job");

        if (printJob.getStatus() != PrintJobStatus.IN_PROGRESS) {
            throw new InvalidStateTransitionException(
                    "Only a print job in progress can be finished.");
        }
    }

    private Map<Spool, BigDecimal> prepareConsumptions(
            PrintJob printJob,
            List<FilamentConsumption> consumptions)
            throws BusinessValidationException {

        ValidationUtils.requireNotNull(
                consumptions, "Filament consumptions");

        if (consumptions.isEmpty()) {
            throw new BusinessValidationException(
                    "At least one filament consumption is required.");
        }

        Map<Spool, BigDecimal> consumptionBySpool =
                new LinkedHashMap<>();

        for (FilamentConsumption consumption : consumptions) {
            ValidationUtils.requireNotNull(
                    consumption, "Filament consumption");

            if (!sameJob(printJob, consumption.getPrintJob())) {
                throw new BusinessValidationException(
                        "All consumptions must belong to the print job.");
            }

            Spool spool = ValidationUtils.requireNotNull(
                    consumption.getSpool(), "Spool");

            ValidationUtils.requireNonNegative(
                    consumption.getConsumedGrams(),
                    "Consumed grams");

            consumptionBySpool.merge(
                    spool,
                    consumption.getConsumedGrams(),
                    BigDecimal::add);
        }

        return consumptionBySpool;
    }

    private void validateStock(
            Map<Spool, BigDecimal> consumptionBySpool)
            throws InsufficientStockException {

        for (Map.Entry<Spool, BigDecimal> entry
                : consumptionBySpool.entrySet()) {

            Spool spool = entry.getKey();
            BigDecimal requiredGrams = entry.getValue();

            if (spool.getCurrentWeight()
                    .compareTo(requiredGrams) < 0) {

                throw new InsufficientStockException(
                        "The spool does not have enough filament.");
            }
        }
    }

    private void applyConsumptions(
            Map<Spool, BigDecimal> consumptionBySpool) {

        for (Map.Entry<Spool, BigDecimal> entry
                : consumptionBySpool.entrySet()) {

            Spool spool = entry.getKey();
            BigDecimal consumedGrams = entry.getValue();

            spool.updateCurrentWeight(
                    spool.getCurrentWeight()
                            .subtract(consumedGrams));
        }
    }

    private boolean sameJob(PrintJob first, PrintJob second) {
        if (first == second) {
            return true;
        }

        if (first == null || second == null
                || first.getId() == null
                || second.getId() == null) {
            return false;
        }

        return first.getId().equals(second.getId());
    }
}