package io.github.ardoco.model;

import java.util.Set;

/**
 * An artifact that holds a pre-computed set of biterms. This is used for enriched artifacts
 * where the biterms are derived from a frequency map, not from parsing a text body.
 */
public class PrecomputedBitermArtifact extends Artifact {

    private final ArtifactType originalType;

    public PrecomputedBitermArtifact(String identifier, Set<Biterm> biterms, ArtifactType originalType) {
        super(identifier, ""); // Text body is irrelevant here
        this.biterms = biterms;
        this.originalType = originalType;
    }

    public PrecomputedBitermArtifact(PrecomputedBitermArtifact other) {
        super(other);
        this.originalType = other.originalType;
    }

    @Override
    public Artifact deepCopy() {
        return new PrecomputedBitermArtifact(this);
    }

    @Override
    public ArtifactType getType() {
        return this.originalType;
    }

    @Override
    protected void preProcessing() {
        // No-op, biterms are pre-computed
    }

    @Override
    public Set<Biterm> getBiterms() {
        // Return the pre-computed set directly
        return this.biterms;
    }
}