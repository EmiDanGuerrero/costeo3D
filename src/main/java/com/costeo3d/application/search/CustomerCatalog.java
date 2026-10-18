package com.costeo3d.application.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.costeo3d.domain.model.Customer;
import com.costeo3d.domain.validation.ValidationUtils;

public class CustomerCatalog implements Searchable<Customer> {

    private final List<Customer> customers;

    public CustomerCatalog() {
        this.customers = new ArrayList<>();
    }

    public CustomerCatalog(List<Customer> customers) {
        this.customers = new ArrayList<>(customers);
    }

    public void add(Customer customer) {
        ValidationUtils.requireNotNull(customer, "Customer");
        customers.add(customer);
    }

    public List<Customer> getAll() {
        return List.copyOf(customers);
    }

    @Override
    public List<Customer> search(String query) {
        String normalizedQuery = normalize(query);

        if (normalizedQuery.isBlank()) {
            return getAll();
        }

        return customers.stream()
                .filter(customer ->
                        containsIgnoreCase(
                                customer.getName(),
                                normalizedQuery)
                        || containsIgnoreCase(
                                customer.getPhone(),
                                normalizedQuery)
                        || containsIgnoreCase(
                                customer.getEmail(),
                                normalizedQuery))
                .toList();
    }

    public List<Customer> filterByActive(boolean active) {
        return customers.stream()
                .filter(customer ->
                        customer.isActive() == active)
                .toList();
    }

    public List<Customer> sort(
            List<Customer> source,
            Comparator<Customer> comparator) {

        ValidationUtils.requireNotNull(source, "Source");
        ValidationUtils.requireNotNull(comparator, "Comparator");

        return source.stream()
                .sorted(comparator)
                .toList();
    }

    private boolean containsIgnoreCase(
            String value,
            String normalizedQuery) {

        return value != null
                && normalize(value).contains(normalizedQuery);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.trim()
                .toLowerCase(Locale.ROOT);
    }
}