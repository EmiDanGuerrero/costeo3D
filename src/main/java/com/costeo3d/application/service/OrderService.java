package com.costeo3d.application.service;

import java.time.LocalDateTime;
import java.util.List;

import com.costeo3d.domain.enums.OrderStatus;
import com.costeo3d.domain.enums.PrintJobStatus;
import com.costeo3d.domain.enums.QuoteStatus;
import com.costeo3d.domain.exception.BusinessRuleException;
import com.costeo3d.domain.exception.BusinessValidationException;
import com.costeo3d.domain.model.Order;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.domain.model.Quote;
import com.costeo3d.domain.validation.ValidationUtils;

public class OrderService {

	public Order createOrder(
	        Quote quote,
	        LocalDateTime createdAt,
	        List<PrintJob> printJobs,
	        List<Order> existingOrders)
	        throws BusinessValidationException {

	    ValidationUtils.requireNotNull(quote, "Quote");
	    ValidationUtils.requireNotNull(createdAt, "Creation date");
	    ValidationUtils.requireNotNull(printJobs, "Print jobs");
	    ValidationUtils.requireNotNull(
	            existingOrders, "Existing orders");

	    if (quote.getStatus() != QuoteStatus.ACCEPTED) {
	        throw new BusinessValidationException(
	                "An order can only be created from an accepted quote.");
	    }

	    if (printJobs.isEmpty()) {
	        throw new BusinessValidationException(
	                "An order must contain at least one print job.");
	    }

	    if (hasOrderForQuote(quote, existingOrders)) {
	        throw new BusinessValidationException(
	                "The quote already has an associated order.");
	    }

	    Order order = new Order(quote, createdAt);

	    for (PrintJob printJob : printJobs) {
	        ValidationUtils.requireNotNull(
	                printJob, "Print job");

	        order.addPrintJob(printJob);
	    }

	    return order;
	}

    public void startProduction(Order order)
            throws BusinessRuleException {

        ValidationUtils.requireNotNull(order, "Order");

        if (order.getPrintJobs().isEmpty()) {
            throw new BusinessValidationException(
                    "An order must contain at least one print job.");
        }

        order.startProduction();
    }

    public void markAsReady(Order order)
            throws BusinessRuleException {

        ValidationUtils.requireNotNull(order, "Order");

        List<PrintJob> printJobs = order.getPrintJobs();

        if (printJobs.isEmpty()) {
            throw new BusinessValidationException(
                    "An order must contain at least one print job.");
        }

        if (!canMarkAsReady(printJobs)) {
            throw new BusinessValidationException(
                    "Para marcar el pedido como Listo se necesita al menos un "
                    + "trabajo finalizado, sin trabajos pendientes o en proceso, "
                    + "y cada intento fallido debe contar con un reintento posterior.");
        }

        order.markAsReady();
    }

    /** Fallas are historical attempts; they do not prevent a completed retry. */
    public static boolean canMarkAsReady(List<PrintJob> jobs) {
        if (jobs == null || jobs.isEmpty()
                || jobs.stream().noneMatch(job -> job.getStatus() == PrintJobStatus.COMPLETED)
                || jobs.stream().anyMatch(job -> job.getStatus() == PrintJobStatus.PENDING
                        || job.getStatus() == PrintJobStatus.IN_PROGRESS)) {
            return false;
        }
        // Every failed attempt must have its own recorded successor. Together
        // with the lack of open jobs, every retry chain ends in COMPLETED.
        return jobs.stream().filter(job -> job.getStatus() == PrintJobStatus.FAILED)
                .allMatch(failed -> failed.getId() != null
                        && jobs.stream().anyMatch(retry ->
                                java.util.Objects.equals(retry.getRetryOfJobId(), failed.getId())
                                && retry.getId() != null && retry.getId() > failed.getId()));
    }

    public void deliver(Order order)
            throws BusinessRuleException {

        ValidationUtils.requireNotNull(order, "Order");

        order.deliver();
    }


    /** Cancels only a pending order whose print jobs have not started. */
    public void cancelPending(Order order) throws BusinessRuleException {
        ValidationUtils.requireNotNull(order, "Order");
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessValidationException(
                    "Solo se pueden cancelar pedidos pendientes desde Pedidos.");
        }
        if (order.getPrintJobs().stream()
                .anyMatch(job -> job.getStatus() != PrintJobStatus.PENDING)) {
            throw new BusinessValidationException(
                    "No se puede cancelar: alguno de sus trabajos ya comenzó.");
        }
        order.cancel();
    }

    private boolean hasOrderForQuote(
            Quote quote,
            List<Order> existingOrders) {

        return existingOrders.stream()
                .anyMatch(order -> sameQuote(
                        quote, order.getQuote()));
    }

    private boolean sameQuote(Quote first, Quote second) {
        if (first == second) {
            return true;
        }

        if (first.getId() == null || second.getId() == null) {
            return false;
        }

        return first.getId().equals(second.getId());
    }
}