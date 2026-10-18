package com.costeo3d.application.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.costeo3d.domain.model.Material;

public class MaterialCatalog implements Searchable<Material> {

    private final List<Material> materials;

    public MaterialCatalog() {
        this.materials = new ArrayList<>();
    }

    public MaterialCatalog(List<Material> materials) {
        this.materials = new ArrayList<>(materials);
    }

    public void add(Material material) {
        if (material == null) {
            throw new IllegalArgumentException(
                    "Material cannot be null.");
        }

        materials.add(material);
    }

    public List<Material> getAll() {
        return List.copyOf(materials);
    }

    @Override
    public List<Material> search(String query) {
        String normalizedQuery = normalize(query);

        if (normalizedQuery.isBlank()) {
            return getAll();
        }

        return materials.stream()
                .filter(material ->
                        containsIgnoreCase(
                                material.getBrand(),
                                normalizedQuery)
                        || containsIgnoreCase(
                                material.getType(),
                                normalizedQuery)
                        || containsIgnoreCase(
                                material.getFinish(),
                                normalizedQuery))
                .toList();
    }

    public List<Material> filterByActive(boolean active) {
        return materials.stream()
                .filter(material ->
                        material.isActive() == active)
                .toList();
    }

    public List<Material> sort(
            List<Material> source,
            Comparator<Material> comparator) {

        if (source == null) {
            throw new IllegalArgumentException(
                    "Source cannot be null.");
        }

        if (comparator == null) {
            throw new IllegalArgumentException(
                    "Comparator cannot be null.");
        }

        return source.stream()
                .sorted(comparator)
                .toList();
    }

    private boolean containsIgnoreCase(
            String value,
            String normalizedQuery) {

        return value != null
                && normalize(value)
                        .contains(normalizedQuery);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.trim()
                .toLowerCase(Locale.ROOT);
    }
}