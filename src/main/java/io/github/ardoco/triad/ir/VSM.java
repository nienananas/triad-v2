/* Licensed under MIT 2025. */
package io.github.ardoco.triad.ir;

import java.util.Collections;
import java.util.List;

public class VSM implements IRModel {
    private TermDocumentMatrix queries;
    private TermDocumentMatrix documents;

    @Override
    /**
     * Compute VSM similarities between source and target artifact collections.
     */
    public SimilarityMatrix Compute(ArtifactsCollection source, ArtifactsCollection target) {
        ArtifactsCollection bothSourceAndTarget = new ArtifactsCollection();
        bothSourceAndTarget.putAll(source);
        bothSourceAndTarget.putAll(target);
        return Compute(
                new TermDocumentMatrix(source),
                new TermDocumentMatrix(target),
                new TermDocumentMatrix(bothSourceAndTarget));
    }

    /**
     * Compute VSM similarities from pre-built term-document matrices.
     */
    public SimilarityMatrix Compute(TermDocumentMatrix source, TermDocumentMatrix target, TermDocumentMatrix both) {
        TermDocumentMatrix TF = ComputeTF(both);
        double[] IDF = ComputeIDF(ComputeDF(both), both.numDocs());
        TermDocumentMatrix TFIDF = ComputeTFIDF(TF, IDF);
        TermDocumentMatrix sourceIDs = ComputeIdentities(source);
        TermDocumentMatrix targetIDs = ComputeIdentities(target);
        TermDocumentMatrix sourceWithTFIDF = ReplaceIDWithTFIDF(sourceIDs, TFIDF);
        TermDocumentMatrix targetWithTFIDF = ReplaceIDWithTFIDF(targetIDs, TFIDF);
        return ComputeSimilarities(sourceWithTFIDF, targetWithTFIDF);
    }

    private TermDocumentMatrix ReplaceIDWithTFIDF(TermDocumentMatrix ids, TermDocumentMatrix tfidf) {
        for (int i = 0; i < ids.numDocs(); i++) {
            for (int j = 0; j < ids.numTerms(); j++) {
                double value = tfidf.getValue(ids.getDocumentName(i), ids.getTermName(j));
                ids.setValue(i, j, value);
            }
        }
        return ids;
    }

    private TermDocumentMatrix ComputeTFIDF(TermDocumentMatrix tf, double[] idf) {
        for (int i = 0; i < tf.numDocs(); i++) {
            for (int j = 0; j < tf.numTerms(); j++) {
                tf.setValue(i, j, tf.getValue(i, j) * idf[j]);
            }
        }
        return tf;
    }

    private double[] ComputeIDF(double[] df, int numDocs) {
        double[] idf = new double[df.length];
        for (int i = 0; i < df.length; i++) {
            if (df[i] <= 0.0) {
                idf[i] = 0.0;
            } else {
                idf[i] = Math.log(numDocs / df[i]);
            }
        }
        return idf;
    }

    private double[] ComputeDF(TermDocumentMatrix matrix) {
        double[] df = new double[matrix.numTerms()];
        for (int j = 0; j < matrix.numTerms(); j++) {
            df[j] = 0.0;
            for (int i = 0; i < matrix.numDocs(); i++) {
                df[j] += (matrix.getValue(i, j) > 0.0) ? 1.0 : 0.0;
            }
        }
        return df;
    }

    private TermDocumentMatrix ComputeTF(TermDocumentMatrix matrix) {
        for (int i = 0; i < matrix.numDocs(); i++) {
            double max = 0.0;
            for (int k = 0; k < matrix.numTerms(); k++) {
                max += matrix.getValue(i, k);
            }
            if (max == 0.0) {
                continue;
            }
            for (int j = 0; j < matrix.numTerms(); j++) {
                matrix.setValue(i, j, (matrix.getValue(i, j) / max));
            }
        }
        return matrix;
    }

    private TermDocumentMatrix ComputeIdentities(TermDocumentMatrix matrix) {
        for (int i = 0; i < matrix.numDocs(); i++) {
            for (int j = 0; j < matrix.numTerms(); j++) {
                matrix.setValue(i, j, ((matrix.getValue(i, j) > 0.0) ? 1.0 : 0.0));
            }
        }
        return matrix;
    }

    private SimilarityMatrix ComputeSimilarities(TermDocumentMatrix ids, TermDocumentMatrix tfidf) {
        SimilarityMatrix sims = new SimilarityMatrix();
        List<TermDocumentMatrix> matrices = TermDocumentMatrix.equalize(ids, tfidf);

        queries = matrices.get(0);
        documents = matrices.get(1);

        for (int i = 0; i < ids.numDocs(); i++) {
            LinksList links = new LinksList();
            for (int j = 0; j < tfidf.numDocs(); j++) {
                double product = 0.0;
                double asquared = 0.0;
                double bsquared = 0.0;
                for (int k = 0; k < matrices.get(0).numTerms(); k++) {
                    double a = matrices.get(0).getValue(i, k);
                    double b = matrices.get(1).getValue(j, k);
                    product += (a * b);
                    asquared += Math.pow(a, 2);
                    bsquared += Math.pow(b, 2);
                }
                double cross = Math.sqrt(asquared) * Math.sqrt(bsquared);
                if (cross == 0.0) {
                    links.add(new SingleLink(
                            ids.getDocumentName(i).trim(),
                            tfidf.getDocumentName(j).trim(),
                            0.0));
                } else {
                    links.add(new SingleLink(ids.getDocumentName(i), tfidf.getDocumentName(j), product / cross));
                }
            }

            Collections.sort(links, Collections.reverseOrder());

            for (SingleLink link : links) {
                sims.addLink(link.getSourceArtifactId(), link.getTargetArtifactId(), link.getScore());
            }
        }
        return sims;
    }

    @Override
    public String getModelName() {
        return "VSM";
    }

    @Override
    public TermDocumentMatrix getTermDocumentMatrixOfQueries() {
        return queries;
    }

    @Override
    public TermDocumentMatrix getTermDocumentMatrixOfDocuments() {
        return documents;
    }
}
