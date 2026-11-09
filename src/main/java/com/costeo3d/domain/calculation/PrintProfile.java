package com.costeo3d.domain.calculation;

/** Perfil comercial seleccionado por el usuario; no clasifica por gramos. */
public enum PrintProfile {
    LLAVERO("Llavero"),
    NORMAL("Normal"),
    GRANDE("Grande");

    private final String label;

    PrintProfile(String label) { this.label = label; }

    public String label() { return label; }

    /** Identifica el perfil en el texto histórico sin modificar MySQL. */
    public String descriptionPrefix() { return "[" + label + "] "; }

    @Override public String toString() { return label; }
}
