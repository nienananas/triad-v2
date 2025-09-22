/* Licensed under MIT 2025. */
package io.github.ardoco.triad.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.ardoco.triad.text.TextProcessor;

public class RequirementsDocumentArtifact extends Artifact {
    private static final Logger logger = LoggerFactory.getLogger(RequirementsDocumentArtifact.class);
    private String cachedProcessedTextBody;

    public RequirementsDocumentArtifact(String identifier, String textBody) {
        super(identifier, textBody);
    }

    public RequirementsDocumentArtifact(RequirementsDocumentArtifact other) {
        super(other);
    }

    @Override
    public Artifact deepCopy() {
        return new RequirementsDocumentArtifact(this);
    }

    @Override
    protected void preProcessing() {
        logger.info("Preprocessing Requirements Document Artifact");
    }

    @Override
    public String getTextBody() {
        if (cachedProcessedTextBody == null) {
            cachedProcessedTextBody = TextProcessor.processText(super.getTextBody());
        }
        return cachedProcessedTextBody;
    }

    @Override
    public ArtifactType getType() {
        return ArtifactType.TEXTUAL;
    }
}
