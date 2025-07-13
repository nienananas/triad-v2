/* Licensed under MIT 2025. */
package io.github.ardoco.artifact;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DesignDocumentArtifact extends Artifact {

    private static final Logger logger = LoggerFactory.getLogger(DesignDocumentArtifact.class);

    private String content;

    public DesignDocumentArtifact(String identifier, String textBody) {
        super(identifier, textBody);
        logger.info("Preprocessing Design Document Artifact");
    }

    @Override
    protected void preProcessing() {
        System.out.println("Preprocessing Design Document Artifact");
        // nothing to do for design documents
    }
}
