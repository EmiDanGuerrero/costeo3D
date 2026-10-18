package com.costeo3d.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.costeo3d.domain.enums.OrderStatus;
import com.costeo3d.domain.enums.PrintJobStatus;
import com.costeo3d.domain.exception.BusinessRuleException;
import com.costeo3d.domain.exception.BusinessValidationException;
import com.costeo3d.domain.model.Customer;
import com.costeo3d.domain.model.Order;
import com.costeo3d.domain.model.PrintJob;
import com.costeo3d.domain.model.Quote;

class OrderServiceTest {

    private final OrderService orderService =
            new OrderService();

    @Test
    void shouldRejectOrderFromQuoteNotAccepted() {
        Customer customer = new Customer(
                "Juan Perez",
                "3874000001",
                "juan@example.com");

        Quote quote = new Quote(
                customer,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(3));

        PrintJob job = new PrintJob(
                null,
                null,
                LocalDateTime.now(),
                120);

        assertThrows(
                BusinessValidationException.class,
                () -> orderService.createOrder(
                        quote,
                        LocalDateTime.now(),
                        List.of(job),
                        new ArrayList<>()));
    }

    @Test
    void shouldRejectSecondOrderForSameQuote()
            throws BusinessRuleException {

        Quote quote = createAcceptedQuote();

        PrintJob firstJob = new PrintJob(
                null,
                null,
                LocalDateTime.now(),
                120);

        List<Order> existingOrders =
                new ArrayList<>();

        Order firstOrder = orderService.createOrder(
                quote,
                LocalDateTime.now(),
                List.of(firstJob),
                existingOrders);

        existingOrders.add(firstOrder);

        PrintJob secondJob = new PrintJob(
                null,
                null,
                LocalDateTime.now(),
                120);

        assertThrows(
                BusinessValidationException.class,
                () -> orderService.createOrder(
                        quote,
                        LocalDateTime.now(),
                        List.of(secondJob),
                        existingOrders));
    }

    @Test
    void shouldMarkOrderAsReadyWhenAllJobsAreCompleted()
            throws BusinessRuleException {

        Quote quote = createAcceptedQuote();

        PrintJob firstJob = new PrintJob(
                null,
                null,
                LocalDateTime.now(),
                120);

        PrintJob secondJob = new PrintJob(
                null,
                null,
                LocalDateTime.now(),
                180);

        Order order = orderService.createOrder(
                quote,
                LocalDateTime.now(),
                List.of(firstJob, secondJob),
                new ArrayList<>());

        orderService.startProduction(order);

        firstJob.start(LocalDateTime.now());
        firstJob.complete(LocalDateTime.now());

        secondJob.start(LocalDateTime.now());
        secondJob.complete(LocalDateTime.now());

        orderService.markAsReady(order);

        assertEquals(
                OrderStatus.READY,
                order.getStatus());

        assertEquals(
                PrintJobStatus.COMPLETED,
                firstJob.getStatus());

        assertEquals(
                PrintJobStatus.COMPLETED,
                secondJob.getStatus());
    }

    private Quote createAcceptedQuote()
            throws BusinessRuleException {

        Customer customer = new Customer(
                "Juan Perez",
                "3874000001",
                "juan@example.com");

        Quote quote = new Quote(
                customer,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(3));

        quote.updateTotal(
                new BigDecimal("1000.00"));

        quote.accept();

        return quote;
    }
}