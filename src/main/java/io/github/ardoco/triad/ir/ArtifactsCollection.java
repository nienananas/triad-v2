/* Licensed under MIT 2025. */
package io.github.ardoco.triad.ir;

import java.util.HashMap;
import java.util.Set;

import io.github.ardoco.triad.model.Artifact;

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
