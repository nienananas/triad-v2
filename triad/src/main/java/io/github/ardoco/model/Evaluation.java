package io.github.ardoco.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.stat.inference.WilcoxonSignedRankTest;

public class Evaluation {

    public static double calculatePrecision(List<SingleLink> retrieved, GoldStandard gold) {
        if (retrieved.isEmpty()) {
            return 0.0;
        }
        long correct = retrieved.stream().filter(link -> gold.isLink(link.getSourceArtifactId(), link.getTargetArtifactId())).count();
        return (double) correct / retrieved.size();
    }

    public static double calculateRecall(List<SingleLink> retrieved, GoldStandard gold) {
        if (retrieved.isEmpty()) {
            return 0.0;
        }
        long correct = retrieved.stream().filter(link -> gold.isLink(link.getSourceArtifactId(), link.getTargetArtifactId())).count();
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
            List<SingleLink> rankedLinks = similarityMatrix.getLinks(source);
            rankedLinks.sort(Comparator.comparingDouble(SingleLink::getScore).reversed());
            Set<String> relevant = gold.getRelevantLinks(source);
            map += calculateAP(rankedLinks, relevant);
        }
        return map / sources.size();
    }

    public static List<Double> getFMeasuresAt11RecallLevels(SimilarityMatrix similarityMatrix, GoldStandard gold) {
        List<SingleLink> allLinks = similarityMatrix.getAllLinks();
        allLinks.sort(Comparator.comparingDouble(SingleLink::getScore).reversed());

        int totalRelevant = gold.getTotalRelevantLinks();
        if (totalRelevant == 0) {
            return Collections.nCopies(11, 0.0);
        }

        List<Double> fMeasures = new ArrayList<>();
        ArrayList<Double> recalls = new ArrayList<>();
        ArrayList<Double> precisions = new ArrayList<>();

        int correctRetrieved = 0;
        for (int i = 0; i < allLinks.size(); i++) {
            SingleLink link = allLinks.get(i);
            if (gold.isLink(link.getSourceArtifactId(), link.getTargetArtifactId())) {
                correctRetrieved++;
            }
            double recall = (double) correctRetrieved / totalRelevant;
            double precision = (double) correctRetrieved / (i + 1);
            recalls.add(recall);
            precisions.add(precision);
        }

        for (double recallLevel = 0.0; recallLevel <= 1.0; recallLevel += 0.1) {
            double maxPrecision = 0.0;
            for (int i = 0; i < recalls.size(); i++) {
                if (recalls.get(i) >= recallLevel) {
                    if (precisions.get(i) > maxPrecision) {
                        maxPrecision = precisions.get(i);
                    }
                }
            }
            fMeasures.add(calculateFMeasure(maxPrecision, recallLevel));
        }
        return fMeasures;
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