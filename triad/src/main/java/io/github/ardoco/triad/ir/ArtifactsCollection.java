package io.github.ardoco.triad.ir;

import io.github.ardoco.triad.model.Artifact;

import java.util.HashMap;
import java.util.Set;

public class ArtifactsCollection extends HashMap<String, Artifact> {
    /**
     * Construct a collection from a set of artifacts keyed by their identifier.
     *
     * @param artifacts artifacts to include
     */
    public ArtifactsCollection(Set<? extends Artifact> artifacts) {
        for (Artifact artifact : artifacts) {
            this.put(artifact.getIdentifier(), artifact);
        }
    }

    public ArtifactsCollection() {
        super();
    }
}