/* Licensed under MIT 2025. */
package io.github.ardoco.triad.ir;

public interface IRModel {
    SimilarityMatrix Compute(ArtifactsCollection source, ArtifactsCollection target);

    String getModelName();

    TermDocumentMatrix getTermDocumentMatrixOfQueries();

    TermDocumentMatrix getTermDocumentMatrixOfDocuments();
}
