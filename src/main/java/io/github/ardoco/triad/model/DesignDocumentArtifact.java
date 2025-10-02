/* Licensed under MIT 2025. */
package io.github.ardoco.triad.model;

public class DesignDocumentArtifact extends Artifact {

    public DesignDocumentArtifact(String identifier, String textBody) {
        super(identifier, textBody);
    }

    public DesignDocumentArtifact(DesignDocumentArtifact other) {
        super(other);
    }

    @Override
    public Artifact deepCopy() {
        return new DesignDocumentArtifact(this);
    }

    @Override
    protected void preProcessing() {
        // No special preprocessing for design docs
    }

    @Override
    public ArtifactType getType() {
        return ArtifactType.TEXTUAL;
    }
}
