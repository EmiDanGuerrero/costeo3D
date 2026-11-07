package com.costeo3d.infrastructure.persistence.dao;

import java.util.List;
import java.util.Optional;

import com.costeo3d.domain.model.FilamentConsumption;

/**
 * Persists the consumption records associated with print jobs.
 * Stock changes are not performed by this contract; they must be
 * coordinated in the same transaction as consumption and job updates.
 */
public interface FilamentConsumptionDao {

    FilamentConsumption insert(FilamentConsumption consumption);

    Optional<FilamentConsumption> findById(int id);

    List<FilamentConsumption> findByPrintJobId(int printJobId);
}
