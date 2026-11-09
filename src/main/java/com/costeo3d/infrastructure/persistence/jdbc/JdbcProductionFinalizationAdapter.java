package com.costeo3d.infrastructure.persistence.jdbc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.costeo3d.application.port.ProductionFinalizationPort;
import com.costeo3d.domain.exception.BusinessRuleException;
import com.costeo3d.domain.model.FilamentConsumption;
import com.costeo3d.domain.model.PrintJob;

/** Adapta la transaccion JDBC existente al contrato de aplicacion. */
public final class JdbcProductionFinalizationAdapter
        implements ProductionFinalizationPort {

    private final JdbcProductionTransaction transaction;

    public JdbcProductionFinalizationAdapter(JdbcProductionTransaction transaction) {
        this.transaction = Objects.requireNonNull(transaction,
                "Production transaction is required.");
    }

    @Override
    public Completion completeJob(PrintJob job, List<FilamentConsumption> consumptions,
            LocalDateTime finishedAt) throws BusinessRuleException {
        return toCompletion(transaction.completeJob(job, consumptions, finishedAt));
    }

    @Override
    public Completion failJob(PrintJob job, List<FilamentConsumption> consumptions,
            LocalDateTime finishedAt) throws BusinessRuleException {
        return toCompletion(transaction.failJob(job, consumptions, finishedAt));
    }

    @Override
    public Completion failJobWithEstimate(PrintJob job,
            List<FilamentConsumption> consumptions, LocalDateTime finishedAt,
            int totalLayers, int failureLayer) throws BusinessRuleException {
        return toCompletion(transaction.failJobWithEstimate(
                job, consumptions, finishedAt, totalLayers, failureLayer));
    }

    private static Completion toCompletion(JdbcProductionTransaction.Result result) {
        return new Completion(result.finishedJob(), result.consumptionIds(),
                result.remainingGramsBySpool(), result.retryJobId());
    }
}
