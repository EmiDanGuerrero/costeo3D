package com.costeo3d.infrastructure.persistence.dao;

import java.util.List;
import java.util.Optional;

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
}
