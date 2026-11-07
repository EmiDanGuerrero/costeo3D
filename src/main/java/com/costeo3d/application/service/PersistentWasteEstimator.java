package com.costeo3d.application.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.costeo3d.domain.exception.BusinessValidationException;
import com.costeo3d.domain.model.EstimatedWaste;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.infrastructure.persistence.dao.EstimatedWasteDao;
import com.costeo3d.infrastructure.persistence.dao.PrintJobDao;

/**
 * Aplica las reglas existentes de estimacion de merma y las persiste.
 * Una estimacion no representa un consumo real ni modifica el stock.
 */
public final class PersistentWasteEstimator {

    private final WasteEstimator wasteEstimator;
    private final PrintJobDao printJobDao;
    private final EstimatedWasteDao estimatedWasteDao;

    public PersistentWasteEstimator(WasteEstimator wasteEstimator,
            PrintJobDao printJobDao, EstimatedWasteDao estimatedWasteDao) {
        this.wasteEstimator = Objects.requireNonNull(wasteEstimator,
                "WasteEstimator is required.");
        this.printJobDao = Objects.requireNonNull(printJobDao,
                "PrintJobDao is required.");
        this.estimatedWasteDao = Objects.requireNonNull(estimatedWasteDao,
                "EstimatedWasteDao is required.");
    }

    /**
     * Recupera el trabajo de MySQL, verifica la regla de negocio y persiste
     * una unica estimacion para ese trabajo.
     */
    public EstimatedWaste createEstimate(int printJobId,
            int totalLayers, int failureLayer) throws BusinessValidationException {
        PrintJob job = printJobDao.findById(printJobId).orElseThrow(() ->
                new BusinessValidationException("The print job does not exist."));

        List<EstimatedWaste> existing = estimatedWasteDao
                .findByPrintJobId(printJobId)
                .map(List::of)
                .orElseGet(List::of);

        EstimatedWaste estimate = wasteEstimator.createEstimate(
                job, totalLayers, failureLayer, existing);

        // El UNIQUE(id_trabajo) de MySQL impide duplicados concurrentes.
        return estimatedWasteDao.insert(estimate);
    }

    public Optional<EstimatedWaste> findById(int id) {
        return estimatedWasteDao.findById(id);
    }

    public Optional<EstimatedWaste> findByPrintJobId(int printJobId) {
        return estimatedWasteDao.findByPrintJobId(printJobId);
    }
}
