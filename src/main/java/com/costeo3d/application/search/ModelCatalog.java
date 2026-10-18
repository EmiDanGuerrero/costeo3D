package com.costeo3d.application.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.costeo3d.domain.model.Model3D;
import com.costeo3d.domain.validation.ValidationUtils;

public class ModelCatalog implements Searchable<Model3D> {

    private final List<Model3D> models;

    public ModelCatalog() {
        this.models = new ArrayList<>();
    }

    public ModelCatalog(List<Model3D> models) {
        this.models = new ArrayList<>(models);
    }

    public void add(Model3D model) {
        ValidationUtils.requireNotNull(model, "Model");
        models.add(model);
    }

    public List<Model3D> getAll() {
        return List.copyOf(models);
    }

    @Override
    public List<Model3D> search(String query) {
        String normalizedQuery = normalize(query);

        if (normalizedQuery.isBlank()) {
            return getAll();
        }

        return models.stream()
                .filter(model ->
                        containsIgnoreCase(
                                model.getName(),
                                normalizedQuery)
                        || containsIgnoreCase(
                                model.getSourcePlatform(),
                                normalizedQuery))
                .toList();
    }

    public List<Model3D> filterByActive(boolean active) {
        return models.stream()
                .filter(model ->
                        model.isActive() == active)
                .toList();
    }

    public List<Model3D> sort(
            List<Model3D> source,
            Comparator<Model3D> comparator) {

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