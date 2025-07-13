/* Licensed under MIT 2025. */
package io.github.ardoco.model;

import io.github.ardoco.document.ArtifactsCollection;
import io.github.ardoco.document.TermDocumentMatrix;

public interface IRModel {
    SimilarityMatrix Compute(ArtifactsCollection source, ArtifactsCollection target);

    String getModelName();

    TermDocumentMatrix getTermDocumentMatrixOfQueries();

    TermDocumentMatrix getTermDocumentMatrixOfDocuments();
}
