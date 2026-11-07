package com.costeo3d.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.costeo3d.application.port.ProductionFinalizationPort;
import com.costeo3d.domain.exception.BusinessRuleException;
import com.costeo3d.domain.exception.BusinessValidationException;
import com.costeo3d.domain.model.FilamentConsumption;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.infrastructure.persistence.dao.PrintJobDao;

/**
 * Integra las reglas existentes de produccion con la persistencia.
 * Nunca descuenta bobinas en memoria antes de confirmar una transaccion.
 */
public final class PersistentProductionService {

    private final ProductionService productionService;
    private final PrintJobDao printJobDao;
    private final ProductionFinalizationPort finalizationPort;

    public PersistentProductionService(ProductionService productionService,
            PrintJobDao printJobDao, ProductionFinalizationPort finalizationPort) {
        this.productionService = Objects.requireNonNull(productionService,
                "ProductionService is required.");
        this.printJobDao = Objects.requireNonNull(printJobDao,
                "PrintJobDao is required.");
        this.finalizationPort = Objects.requireNonNull(finalizationPort,
                "Production finalization port is required.");
    }

    public Optional<PrintJob> findById(int id) {
        return printJobDao.findById(id);
    }

    public List<PrintJob> findAll() {
        return printJobDao.findAll();
    }

    public List<PrintJob> findByOrderId(int orderId) {
        return printJobDao.findByOrderId(orderId);
    }

    /** Inicia un trabajo desde una copia recuperada de MySQL. */
    public PrintJob startJob(int jobId, LocalDateTime startedAt)
            throws BusinessRuleException {
        PrintJob job = requireJob(jobId);
        productionService.startJob(job, startedAt);
        if (!printJobDao.update(job)) {
            throw new IllegalStateException(
                    "The print job disappeared before its start could be saved.");
        }
        return requireJob(jobId);
    }

    /** Finaliza y descuenta stock solo dentro de la transaccion persistente. */
    public ProductionFinalizationPort.Completion completeJob(int jobId,
            List<FilamentConsumption> consumptions, LocalDateTime finishedAt)
            throws BusinessRuleException {
        return finalizationPort.completeJob(requireJob(jobId), consumptions, finishedAt);
    }

    /** Registra un trabajo fallido y su consumo real sin duplicar descuentos. */
    public ProductionFinalizationPort.Completion failJob(int jobId,
            List<FilamentConsumption> consumptions, LocalDateTime finishedAt)
            throws BusinessRuleException {
        return finalizationPort.failJob(requireJob(jobId), consumptions, finishedAt);
    }

    private PrintJob requireJob(int jobId) throws BusinessValidationException {
        return printJobDao.findById(jobId).orElseThrow(() ->
                new BusinessValidationException("The print job does not exist."));
    }
}
