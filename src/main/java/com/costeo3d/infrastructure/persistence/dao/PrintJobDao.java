package com.costeo3d.infrastructure.persistence.dao;

import java.util.List;
import java.util.Optional;

import com.costeo3d.domain.model.PrintJob;

/** Persistence contract for print jobs and their association with orders. */
public interface PrintJobDao {

    // Inserts a print job and returns an instance with its database-generated ID.
    PrintJob insert(PrintJob printJob);

    // Retrieves a print job, including its associated order and model when present.
    Optional<PrintJob> findById(int id);

    // Lists all print jobs, regardless of status or order association.
    List<PrintJob> findAll();

    // Lists the print jobs belonging to a particular persisted order.
    List<PrintJob> findByOrderId(int orderId);

    // Persists changes to the print job, including status and production dates.
    // Returns false when the record does not exist.
    boolean update(PrintJob printJob);
}
