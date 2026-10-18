package com.costeo3d.application.service;

import java.util.List;

import com.costeo3d.domain.enums.PrintJobStatus;
import com.costeo3d.domain.exception.BusinessValidationException;
import com.costeo3d.domain.model.EstimatedWaste;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.domain.validation.ValidationUtils;

public class WasteEstimator {

    public EstimatedWaste createEstimate(
            PrintJob printJob,
            int totalLayers,
            int failureLayer,
            List<EstimatedWaste> existingEstimates)
            throws BusinessValidationException {

        ValidationUtils.requireNotNull(printJob, "Print job");
        ValidationUtils.requireNotNull(
                existingEstimates, "Existing waste estimates");

        if (printJob.getStatus() != PrintJobStatus.FAILED) {
            throw new BusinessValidationException(
                    "Waste can only be estimated for a failed print job.");
        }

        if (hasEstimateForJob(printJob, existingEstimates)) {
            throw new BusinessValidationException(
                    "The print job already has a waste estimate.");
        }

        return new EstimatedWaste(
                printJob,
                totalLayers,
                failureLayer);
    }

    private boolean hasEstimateForJob(
            PrintJob printJob,
            List<EstimatedWaste> existingEstimates) {

        return existingEstimates.stream()
                .anyMatch(estimate ->
                        sameJob(printJob, estimate.getPrintJob()));
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