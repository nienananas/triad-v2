/* Licensed under MIT 2025. */
package io.github.ardoco.triad.ir;

import java.util.HashSet;
import java.util.Set;

/**
 * Computes a union of IR models by taking the element-wise average of their similarity scores.
 * This class combines VSM, LSI, and JSD models.
 */
public final class IRUnion {
    private IRUnion() {}

    /**
     * Compute the average union of VSM, LSI, and JSD similarities.
     *
     * @param source source artifacts
     * @param target target artifacts
     * @return element-wise average of per-model similarity scores
     */
    public static SimilarityMatrix computeUnion(ArtifactsCollection source, ArtifactsCollection target) {
        IRModel vsm = new VSM();
        IRModel lsi = new LSI();
        IRModel jsd = new JSD();

        SimilarityMatrix sV = vsm.Compute(source, target);
        SimilarityMatrix sL = lsi.Compute(source, target);
        SimilarityMatrix sJ = jsd.Compute(source, target);

        Set<String> sources = new HashSet<>();
        sources.addAll(sV.getSourceArtifacts());
        sources.addAll(sL.getSourceArtifacts());
        sources.addAll(sJ.getSourceArtifacts());

        Set<String> targets = new HashSet<>();
        targets.addAll(sV.getTargetArtifacts());
        targets.addAll(sL.getTargetArtifacts());
        targets.addAll(sJ.getTargetArtifacts());

        SimilarityMatrix out = new SimilarityMatrix();
        for (String s : sources) {
            for (String t : targets) {
                double v = sV.getScore(s, t);
                double l = sL.getScore(s, t);
                double j = sJ.getScore(s, t);
                double avg = (v + l + j) / 3.0;
                if (avg > 0.0) {
                    out.addLink(s, t, avg);
                }
            }
        }
        return out;
    }
}
