package com.costeo3d.infrastructure.persistence.dao;

import java.util.Optional;

import com.costeo3d.domain.model.EstimatedWaste;

/**
 * Persists the one-per-failed-job waste estimate.
 * An estimate does not represent actual filament consumption.
 */
public interface EstimatedWasteDao {

    EstimatedWaste insert(EstimatedWaste waste);

    Optional<EstimatedWaste> findById(int id);

    Optional<EstimatedWaste> findByPrintJobId(int printJobId);
}
