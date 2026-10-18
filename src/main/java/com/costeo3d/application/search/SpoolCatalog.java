package com.costeo3d.application.search;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.costeo3d.domain.model.Spool;
import com.costeo3d.domain.validation.ValidationUtils;

public class SpoolCatalog implements Searchable<Spool> {

    private final List<Spool> spools;

    public SpoolCatalog() {
        this.spools = new ArrayList<>();
    }

    public SpoolCatalog(List<Spool> spools) {
        this.spools = new ArrayList<>(spools);
    }

    public void add(Spool spool) {
        ValidationUtils.requireNotNull(spool, "Spool");
        spools.add(spool);
    }

    public List<Spool> getAll() {
        return List.copyOf(spools);
    }

    @Override
    public List<Spool> search(String query) {
        String normalizedQuery = normalize(query);

        if (normalizedQuery.isBlank()) {
            return getAll();
        }

        return spools.stream()
                .filter(spool ->
                        containsIgnoreCase(
                                spool.getColor(),
                                normalizedQuery)
                        || containsIgnoreCase(
                                spool.getMaterial().getBrand(),
                                normalizedQuery)
                        || containsIgnoreCase(
                                spool.getMaterial().getType(),
                                normalizedQuery)
                        || containsIgnoreCase(
                                spool.getMaterial().getFinish(),
                                normalizedQuery))
                .toList();
    }

    public List<Spool> filterByActive(boolean active) {
        return spools.stream()
                .filter(spool ->
                        spool.isActive() == active)
                .toList();
    }

    public List<Spool> filterLowStock(BigDecimal threshold) {
        ValidationUtils.requireNonNegative(
                threshold, "Low stock threshold");

        return spools.stream()
                .filter(spool ->
                        spool.getCurrentWeight()
                                .compareTo(threshold) <= 0)
                .toList();
    }

    public List<Spool> sort(
            List<Spool> source,
            Comparator<Spool> comparator) {

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