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

    public Order startProduction(int id) throws BusinessRuleException {
        return changeStatus(id, orderService::startProduction);
    }

    public Order markAsReady(int id) throws BusinessRuleException {
        return changeStatus(id, orderService::markAsReady);
    }

    public Order deliver(int id) throws BusinessRuleException {
        return changeStatus(id, orderService::deliver);
    }

    private Order changeStatus(int id, StatusChange operation)
            throws BusinessRuleException {
        // The DAO reconstructs a fresh order: failed writes do not mutate
        // an Order instance held by the caller.
        Order order = orderDao.findById(id).orElseThrow(() ->
                new BusinessValidationException("The order does not exist."));
        operation.apply(order);
        if (!orderDao.updateStatus(order)) {
            throw new IllegalStateException(
                    "The order disappeared before its status could be saved.");
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
