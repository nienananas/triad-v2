package io.github.ardoco.document;

import java.util.HashMap;
import java.util.Set;

import io.github.ardoco.artifact.Artifact;

public class ArtifactsCollection extends HashMap<String, Artifact> {
    public ArtifactsCollection(Set<? extends Artifact> artifacts) {
        for (Artifact artifact : artifacts) {
            this.put(artifact.getIdentifier(), artifact);
        }
    }

    public ArtifactsCollection() {
        super();
    }
} 