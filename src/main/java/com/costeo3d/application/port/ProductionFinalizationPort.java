package com.costeo3d.application.port;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.costeo3d.domain.exception.BusinessRuleException;
import com.costeo3d.domain.model.FilamentConsumption;
import com.costeo3d.domain.model.PrintJob;

/**
 * Contrato para finalizar trabajos y registrar consumos de forma atomica.
 * La implementacion decide como persistir y proteger el stock.
 */
public interface ProductionFinalizationPort {

    Completion completeJob(PrintJob job, List<FilamentConsumption> consumptions,
            LocalDateTime finishedAt) throws BusinessRuleException;

    Completion failJob(PrintJob job, List<FilamentConsumption> consumptions,
            LocalDateTime finishedAt) throws BusinessRuleException;

    /** E11.4.4: registra la merma por capas junto al consumo y el estado FALLIDO. */
    Completion failJobWithEstimate(PrintJob job, List<FilamentConsumption> consumptions,
            LocalDateTime finishedAt, int totalLayers, int failureLayer)
            throws BusinessRuleException;

    /** Resultado confirmado, con IDs y pesos restantes obtenidos de la transaccion. */
    record Completion(PrintJob finishedJob, List<Integer> consumptionIds,
            Map<Integer, BigDecimal> remainingGramsBySpool) {
        public Completion {
            Objects.requireNonNull(finishedJob, "Finished job is required.");
            consumptionIds = List.copyOf(consumptionIds);
            remainingGramsBySpool = Map.copyOf(remainingGramsBySpool);
        }
    }
}
