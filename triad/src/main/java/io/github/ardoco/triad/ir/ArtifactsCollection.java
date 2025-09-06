package io.github.ardoco.triad.ir;

import io.github.ardoco.triad.model.Artifact;

import java.util.HashMap;
import java.util.Set;

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