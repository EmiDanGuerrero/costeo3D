package com.costeo3d.application.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.costeo3d.domain.exception.BusinessRuleException;
import com.costeo3d.domain.exception.BusinessValidationException;
import com.costeo3d.domain.model.Order;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.domain.model.Quote;
import com.costeo3d.domain.enums.OrderStatus;
import com.costeo3d.domain.enums.PrintJobStatus;
import com.costeo3d.infrastructure.persistence.dao.OrderDao;
import com.costeo3d.infrastructure.persistence.dao.QuoteDao;

/**
 * Connects the existing order rules to persistent quotes and orders.
 * No SQL or JDBC types belong to this application service.
 */
public final class PersistentOrderService {

    private final OrderService orderService;
    private final QuoteDao quoteDao;
    private final OrderDao orderDao;

    public PersistentOrderService(OrderService orderService,
            QuoteDao quoteDao, OrderDao orderDao) {
        this.orderService = Objects.requireNonNull(orderService, "OrderService is required.");
        this.quoteDao = Objects.requireNonNull(quoteDao, "QuoteDao is required.");
        this.orderDao = Objects.requireNonNull(orderDao, "OrderDao is required.");
    }

    /** Creates the complete order and its jobs through OrderDao's transaction. */
    public Order createOrder(int quoteId, LocalDateTime createdAt,
            List<PrintJob> printJobs) throws BusinessValidationException {
        Quote quote = quoteDao.findById(quoteId).orElseThrow(() ->
                new BusinessValidationException("The quote does not exist."));

        // OrderService assigns jobs to an order. Copies avoid changing the
        // caller's objects if persistence rejects the new order.
        List<PrintJob> drafts = copyPrintJobs(printJobs);
        Order draft = orderService.createOrder(
                quote, createdAt, drafts, orderDao.findAll());
        return orderDao.insert(draft);
    }

    public Optional<Order> findById(int id) {
        return orderDao.findById(id);
    }

    public List<Order> findAll() {
        return orderDao.findAll();
    }

    /**
     * Edits planning data only. Starting/finishing a job remains a Production
     * responsibility and must never happen via this method.
     */
    public Order updatePendingJobPlan(int orderId, int jobId,
            Integer modelId, int estimatedMinutes) throws BusinessRuleException {
        if (orderId <= 0 || jobId <= 0 || estimatedMinutes <= 0) {
            throw new BusinessValidationException(
                    "El pedido, el trabajo y el tiempo estimado deben ser válidos.");
        }
        Order order = orderDao.findById(orderId).orElseThrow(() ->
                new BusinessValidationException("No se encontró el pedido."));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessValidationException(
                    "Solo se pueden modificar trabajos de pedidos pendientes.");
        }
        PrintJob job = order.getPrintJobs().stream()
                .filter(item -> Objects.equals(item.getId(), jobId))
                .findFirst().orElseThrow(() -> new BusinessValidationException(
                        "El trabajo no pertenece al pedido seleccionado."));
        if (job.getStatus() != PrintJobStatus.PENDING) {
            throw new BusinessValidationException(
                    "No se puede editar un trabajo que ya comenzó.");
        }
        // The DAO rechecks both states atomically and allows switching only to
        // active models (or keeping the already associated inactive model).
        if (!orderDao.updatePendingJobPlan(orderId, jobId, modelId, estimatedMinutes)) {
            throw new BusinessValidationException(
                    "El trabajo cambió de estado o el modelo seleccionado no está disponible. Actualizá la vista.");
        }
        return orderDao.findById(orderId).orElseThrow(() ->
                new IllegalStateException("No se pudo recuperar el pedido actualizado."));
    }

    public Order startProduction(int id) throws BusinessRuleException {
        return changeStatus(id, orderService::startProduction);
    }

    public Order markAsReady(int id) throws BusinessRuleException {
        return changeStatus(id, orderService::markAsReady);
    }

    public Order deliver(int id) throws BusinessRuleException {
        return changeStatus(id, orderService::deliver);
    }

    /** Cancel only while the order and all its jobs remain pending. */
    public Order cancelPending(int id) throws BusinessRuleException {
        return changeStatus(id, orderService::cancelPending);
    }

    private Order changeStatus(int id, StatusChange operation)
            throws BusinessRuleException {
        // The DAO reconstructs a fresh order: failed writes do not mutate
        // an Order instance held by the caller.
        Order order = orderDao.findById(id).orElseThrow(() ->
                new BusinessValidationException("The order does not exist."));
        OrderStatus previousStatus = order.getStatus();
        operation.apply(order);
        // Optimistic status check plus job-state conditions are enforced by
        // one atomic SQL statement. Never overwrite a concurrent transition.
        if (!orderDao.changeStatusIfCurrent(
                id, previousStatus, order.getStatus())) {
            throw new BusinessValidationException(
                    "El pedido o alguno de sus trabajos cambió de estado. "
                    + "Actualizá la lista antes de reintentar.");
        }
        return orderDao.findById(id).orElseThrow(() ->
                new IllegalStateException("The saved order could not be retrieved."));
    }

    private static List<PrintJob> copyPrintJobs(List<PrintJob> printJobs) {
        if (printJobs == null) {
            return null; // The original OrderService validates this argument.
        }
        List<PrintJob> drafts = new ArrayList<>(printJobs.size());
        for (PrintJob job : printJobs) {
            if (job == null) {
                drafts.add(null); // The original OrderService rejects null jobs.
            } else {
                drafts.add(new PrintJob(job.getId(), null, job.getModel(),
                        job.getCreatedAt(), job.getStartedAt(),
                        job.getFinishedAt(), job.getStatus(),
                        job.getEstimatedPrintTimeMinutes()));
            }
        }
        return drafts;
    }

    @FunctionalInterface
    private interface StatusChange {
        void apply(Order order) throws BusinessRuleException;
    }
}
