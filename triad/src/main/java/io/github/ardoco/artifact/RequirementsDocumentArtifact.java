package io.github.ardoco.artifact;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequirementsDocumentArtifact extends Artifact {
    private static final Logger logger = LoggerFactory.getLogger(RequirementsDocumentArtifact.class);

    public RequirementsDocumentArtifact(String identifier, String textBody) {
        super(identifier, textBody);
        logger.info("Preprocessing Requirements Document Artifact");
    }

    @Override
    protected void preProcessing() {
        logger.info("Preprocessing Requirements Document Artifact");
    }
} 