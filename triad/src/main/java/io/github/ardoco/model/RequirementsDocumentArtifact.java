package io.github.ardoco.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequirementsDocumentArtifact extends Artifact {
    private static final Logger logger = LoggerFactory.getLogger(RequirementsDocumentArtifact.class);

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
    public ArtifactType getType() {
        return ArtifactType.TEXTUAL;
    }
}