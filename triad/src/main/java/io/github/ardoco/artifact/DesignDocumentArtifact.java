package io.github.ardoco.artifact;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DesignDocumentArtifact extends Artifact {

    private static final Logger logger = LoggerFactory.getLogger(DesignDocumentArtifact.class);

    private String content;

    public DesignDocumentArtifact(String content) {
        super(null, content);
        logger.info("Preprocessing Design Document Artifact");
    }

    @Override
    protected void preProcessing() {
        System.out.println("Preprocessing Design Document Artifact");
        // nothing to do for design documents
    }
} 