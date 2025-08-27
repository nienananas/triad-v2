package io.github.ardoco.ir;

import java.util.HashSet;
import java.util.Set;

/**
 * Computes a union of IR models by taking the element-wise average of their similarity scores.
 * This class combines VSM, LSI, and JSD models.
 */
public final class IRUnion {
    private IRUnion() {}

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