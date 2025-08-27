package io.github.ardoco.ir;

public interface IRModel {
    SimilarityMatrix Compute(ArtifactsCollection source, ArtifactsCollection target);

    String getModelName();

    TermDocumentMatrix getTermDocumentMatrixOfQueries();

    TermDocumentMatrix getTermDocumentMatrixOfDocuments();
}