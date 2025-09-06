package io.github.ardoco.triad.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CCodeArtifact extends Artifact {
    private static final Logger logger = LoggerFactory.getLogger(CCodeArtifact.class);

    public CCodeArtifact(String identifier, String textBody) {
        super(identifier, textBody);
    }
    
    public CCodeArtifact(CCodeArtifact other) {
        super(other);
    }

    @Override
    public Artifact deepCopy() {
        return new CCodeArtifact(this);
    }

    @Override
    protected void preProcessing() {
        logger.info("Preprocessing C Code Artifact (as Textual)");
    }

    @Override
    public ArtifactType getType() {
        return ArtifactType.C_CODE;
    }
}