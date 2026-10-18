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
        this.name = name;
        this.referenceImage = referenceImage;
        this.sourcePlatform = sourcePlatform;
        this.estimatedGrams = estimatedGrams;
        this.estimatedPrintTimeMinutes = estimatedPrintTimeMinutes;
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
        this.name = name;
    }

    public void setReferenceImage(String referenceImage) {
        this.referenceImage = referenceImage;
    }

    public void setSourcePlatform(String sourcePlatform) {
        this.sourcePlatform = sourcePlatform;
    }

    public void setEstimatedGrams(BigDecimal estimatedGrams) {
        this.estimatedGrams = estimatedGrams;
    }

    public void setEstimatedPrintTimeMinutes(int estimatedPrintTimeMinutes) {
        this.estimatedPrintTimeMinutes = estimatedPrintTimeMinutes;
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }
}