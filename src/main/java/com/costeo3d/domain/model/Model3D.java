package com.costeo3d.domain.model;

import java.math.BigDecimal;
import com.costeo3d.domain.contracts.Quotable;

public class Model3D implements Quotable {

    private Integer id;
    private String name;
    private String referenceImage;
    private String sourcePlatform;
    private BigDecimal estimatedGrams;
    private int estimatedPrintTimeMinutes;
    private boolean active;

    public Model3D(String name, String referenceImage, String sourcePlatform,
            BigDecimal estimatedGrams, int estimatedPrintTimeMinutes) {
        this(null, name, referenceImage, sourcePlatform,
                estimatedGrams, estimatedPrintTimeMinutes, true);
    }

    public Model3D(Integer id, String name, String referenceImage,
            String sourcePlatform, BigDecimal estimatedGrams,
            int estimatedPrintTimeMinutes, boolean active) {
        this.id = id;
        setName(name);
        this.referenceImage = referenceImage;
        this.sourcePlatform = sourcePlatform;
        setEstimatedGrams(estimatedGrams);
        setEstimatedPrintTimeMinutes(estimatedPrintTimeMinutes);
        this.active = active;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getReferenceImage() {
        return referenceImage;
    }

    public String getSourcePlatform() {
        return sourcePlatform;
    }

    @Override
    public BigDecimal getEstimatedGrams() {
        return estimatedGrams;
    }

    @Override
    public int getEstimatedPrintTimeMinutes() {
        return estimatedPrintTimeMinutes;
    }

    public boolean isActive() {
        return active;
    }

    public void setName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Model name cannot be null.");
        }
        this.name = name;
    }

    public void setReferenceImage(String referenceImage) {
        this.referenceImage = referenceImage;
    }

    public void setSourcePlatform(String sourcePlatform) {
        this.sourcePlatform = sourcePlatform;
    }

    public void setEstimatedGrams(BigDecimal estimatedGrams) {
        if (estimatedGrams == null || estimatedGrams.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Estimated grams must be greater than zero.");
        }
        this.estimatedGrams = estimatedGrams;
    }

    public void setEstimatedPrintTimeMinutes(int estimatedPrintTimeMinutes) {
        if (estimatedPrintTimeMinutes <= 0) {
            throw new IllegalArgumentException(
                    "Estimated print time must be greater than zero.");
        }
        this.estimatedPrintTimeMinutes = estimatedPrintTimeMinutes;
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }
}
