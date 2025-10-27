/* Licensed under MIT 2025. */
package io.github.ardoco.triad.evaluation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.apache.commons.math3.stat.inference.WilcoxonSignedRankTest;

import edu.kit.kastel.mcse.ardoco.metrics.ClassificationMetricsCalculator;
import edu.kit.kastel.mcse.ardoco.metrics.result.SingleClassificationResult;
import edu.stanford.nlp.util.IdentityHashSet;

import io.github.ardoco.triad.ir.LinksList;
import io.github.ardoco.triad.ir.SimilarityMatrix;
import io.github.ardoco.triad.ir.SingleLink;

public class Evaluation {

    /**
     * A record to hold Precision, Recall, and F1-score.
     */
    public record PRF(double precision, double recall, double f1) {}

    /**
     * Calculates precision, recall, and F1-score for all links present in the similarity matrix.
     * This method treats any link with a score as a "retrieved" link.
     *
     * @param results the similarity matrix containing retrieved links.
     * @param goldStandard the gold standard for comparison.
     * @return a PRF record containing the calculated metrics.
     */
    public static PRF calculatePRF(List<SingleLink> results, GoldStandard goldStandard) {
        Set<SingleLink> retrievedLinks = new IdentityHashSet<>(results);
        Set<SingleLink> groundTruthLinks = new IdentityHashSet<>(goldStandard.getLinks());

        // Use the non-generic getInstance() method as required by the library version
        ClassificationMetricsCalculator calculator = ClassificationMetricsCalculator.getInstance();

        // The calculator needs a unique string identifier for each link, ignoring the score.
        SingleClassificationResult<String> classificationResult = calculator.calculateMetrics(
                retrievedLinks,
                groundTruthLinks,
                link -> link.getSourceArtifactId() + " -> " + link.getTargetArtifactId(),
                null);

        return new PRF(
                classificationResult.getPrecision(), classificationResult.getRecall(), classificationResult.getF1());
    }

    public static double calculatePrecision(List<SingleLink> retrieved, GoldStandard gold) {
        if (retrieved.isEmpty()) {
            return 0.0;
        }
        long correct = retrieved.stream()
                .filter(link -> gold.isLink(link.getSourceArtifactId(), link.getTargetArtifactId()))
                .count();
        return (double) correct / retrieved.size();
    }

    public static double calculateRecall(List<SingleLink> retrieved, GoldStandard gold) {
        if (retrieved.isEmpty()) {
            return 0.0;
        }
        long correct = retrieved.stream()
                .filter(link -> gold.isLink(link.getSourceArtifactId(), link.getTargetArtifactId()))
                .count();
        int totalRelevant = gold.getTotalRelevantLinks();
        return totalRelevant == 0 ? 0.0 : (double) correct / totalRelevant;
    }

    public static double calculateFMeasure(double precision, double recall) {
        if (precision + recall == 0) {
            return 0.0;
        }
        return 2 * (precision * recall) / (precision + recall);
    }

    public static double calculateAP(List<SingleLink> rankedRetrieved, Set<String> relevantLinks) {
        if (rankedRetrieved.isEmpty() || relevantLinks.isEmpty()) {
            return 0.0;
        }
        double ap = 0.0;
        int relevantCount = 0;
        for (int i = 0; i < rankedRetrieved.size(); i++) {
            SingleLink link = rankedRetrieved.get(i);
            if (relevantLinks.contains(link.getTargetArtifactId())) {
                relevantCount++;
                ap += (double) relevantCount / (i + 1);
            }
        }
        return ap / relevantLinks.size();
    }

    public static double calculateMAP(SimilarityMatrix similarityMatrix, GoldStandard gold) {
        double map = 0.0;
        Set<String> sources = similarityMatrix.getSourceArtifacts();
        if (sources.isEmpty()) {
            return 0.0;
        }
        for (String source : sources) {
            LinksList rankedLinks = similarityMatrix.getLinks(source);
            if (rankedLinks == null) continue;
            rankedLinks.sort(Comparator.comparingDouble(SingleLink::getScore).reversed());
            Set<String> relevant = gold.getRelevantLinks(source);
            map += calculateAP(rankedLinks, relevant);
        }
        return map / sources.size();
    }

    public static List<Double> getFMeasuresAt11RecallLevels(SimilarityMatrix similarityMatrix, GoldStandard gold) {
        List<Double> interpolatedPrecisions = getPrecisionAtRecallLevels(similarityMatrix, gold, 11);
        List<Double> fMeasures = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            double recallLevel = i / 10.0;
            fMeasures.add(calculateFMeasure(interpolatedPrecisions.get(i), recallLevel));
        }
        return fMeasures;
    }

    /**
     * Calculates interpolated precision at N standard recall levels.
     * @param similarityMatrix The matrix of ranked links.
     * @param gold The gold standard.
     * @param levels The number of recall levels (e.g., 11 for 0.0, 0.1, ..., 1.0 or 20 for 0.05, 0.10, ..., 1.00).
     * @return A list of interpolated precision values.
     */
    public static List<Double> getPrecisionAtRecallLevels(
            SimilarityMatrix similarityMatrix, GoldStandard gold, int levels) {
        List<SingleLink> allLinks = similarityMatrix.getAllLinks();
        allLinks.sort(Comparator.comparingDouble(SingleLink::getScore).reversed());

        int totalRelevant = gold.getTotalRelevantLinks();
        if (totalRelevant == 0) {
            return Collections.nCopies(levels, 0.0);
        }

        ArrayList<Double> recalls = new ArrayList<>();
        ArrayList<Double> precisions = new ArrayList<>();
        int correctRetrieved = 0;

        for (int i = 0; i < allLinks.size(); i++) {
            SingleLink link = allLinks.get(i);
            if (gold.isLink(link.getSourceArtifactId(), link.getTargetArtifactId())) {
                correctRetrieved++;
            }
            recalls.add((double) correctRetrieved / totalRelevant);
            precisions.add((double) correctRetrieved / (i + 1));
        }

        List<Double> interpolatedPrecisions = new ArrayList<>();
        for (int i = 0; i < levels; i++) {
            // Adjust recall level calculation based on whether levels is 11 (0-10) or 20 (1-20)
            double recallLevel = (levels == 11) ? i / 10.0 : (i + 1.0) / levels;
            double maxPrecision = 0.0;
            for (int j = 0; j < recalls.size(); j++) {
                if (recalls.get(j) >= recallLevel) {
                    if (precisions.get(j) > maxPrecision) {
                        maxPrecision = precisions.get(j);
                    }
                }
            }
            interpolatedPrecisions.add(maxPrecision);
        }
        return interpolatedPrecisions;
    }

    public static double calculatePValue(List<Double> sample1, List<Double> sample2) {
        if (sample1.size() != sample2.size() || sample1.isEmpty()) {
            throw new IllegalArgumentException("Samples must have the same, non-zero size.");
        }
        double[] sample1Array = sample1.stream().mapToDouble(d -> d).toArray();
        double[] sample2Array = sample2.stream().mapToDouble(d -> d).toArray();

        WilcoxonSignedRankTest wilcoxon = new WilcoxonSignedRankTest();
        return wilcoxon.wilcoxonSignedRankTest(sample1Array, sample2Array, true);
    }

    public static double calculateCliffsDelta(List<Double> sample1, List<Double> sample2) {
        int more = 0;
        int less = 0;
        for (double x1 : sample1) {
            for (double x2 : sample2) {
                if (x1 > x2) more++;
                if (x1 < x2) less++;
            }
        }
        if (sample1.isEmpty() || sample2.isEmpty()) return 0;
        return (double) (more - less) / (sample1.size() * sample2.size());
    }
}
