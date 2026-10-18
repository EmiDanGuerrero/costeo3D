package com.costeo3d.application.service;

import java.time.LocalDateTime;
import java.util.List;

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

        boolean allCompleted = printJobs.stream()
                .allMatch(job ->
                        job.getStatus() == PrintJobStatus.COMPLETED);

        if (!allCompleted) {
            throw new BusinessValidationException(
                    "All print jobs must be completed before "
                    + "the order can be marked as ready.");
        }

        order.markAsReady();
    }

    public void deliver(Order order)
            throws BusinessRuleException {

        ValidationUtils.requireNotNull(order, "Order");

        order.deliver();
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