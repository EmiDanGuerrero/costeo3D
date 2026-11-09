package com.costeo3d.infrastructure.persistence.dao;

import java.util.List;
import java.util.Optional;

import com.costeo3d.domain.enums.OrderStatus;
import com.costeo3d.domain.model.Order;

/** Persistence contract for orders associated with accepted quotes. */
public interface OrderDao {

    // Registers a new order and returns a new instance with the MySQL-generated ID.
    Order insert(Order order);

    // Recovers an order with the quote associated with it.
    Optional<Order> findById(int id);

    // Recovers all orders, regardless of their status.
    List<Order> findAll();

    // Persists only the order status, preserving the original quote association.
    // Returns false if the order does not exist.
    boolean updateStatus(Order order);


    /**
     * Atomically changes a status only if the current DB status matches the
     * expected value and the associated jobs allow this transition. Returns
     * false when it is no longer allowed. Does not alter jobs or stock.
     */
    boolean changeStatusIfCurrent(
            int orderId, OrderStatus expected, OrderStatus next);

    /**
     * Changes only the model and estimated minutes of a pending job belonging to
     * a pending order. A different model must exist and be active. Returns false
     * if the job is no longer eligible; never changes stock, dates or status.
     */
    boolean updatePendingJobPlan(int orderId, int jobId,
            Integer modelId, int estimatedMinutes);
}
