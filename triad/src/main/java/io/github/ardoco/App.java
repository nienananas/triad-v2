package io.github.ardoco;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.stat.inference.WilcoxonSignedRankTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.kastel.mcse.ardoco.metrics.ClassificationMetricsCalculator;
import edu.kit.kastel.mcse.ardoco.metrics.result.SingleClassificationResult;
import edu.stanford.nlp.util.IdentityHashSet;
import io.github.ardoco.Biterm.Biterm;
import io.github.ardoco.Biterm.ConsensualBiterm;
import io.github.ardoco.artifact.DesignDocumentArtifact;
import io.github.ardoco.artifact.RequirementsDocumentArtifact;
import io.github.ardoco.artifact.SourceCodeArtifact;
import io.github.ardoco.document.ArtifactsCollection;
import io.github.ardoco.model.GoldStandard;
import io.github.ardoco.model.IRModel;
import io.github.ardoco.model.JSD;
import io.github.ardoco.model.LSI;
import io.github.ardoco.model.LinksList;
import io.github.ardoco.model.SimilarityMatrix;
import io.github.ardoco.model.SingleLink;
import io.github.ardoco.model.VSM;
import io.github.ardoco.util.OutputLog;


public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    // --- CONFIGURABLE IR MODELS ---
    private static final List<IRModel> IR_MODELS_TO_RUN = List.of(
        new VSM(), 
        new LSI(), 
        new JSD()
    );

    public static void main(String[] args) throws IOException {
        Project project = new Project("Dronology");

        // Load Gold Standard
        GoldStandard goldStandard = new GoldStandard(Paths.get("dataset/" + project.getName() + "/gold_standard"));

        Set<RequirementsDocumentArtifact> requirementsArtifacts = project.getRequirementsArtifacts();
        Set<DesignDocumentArtifact> designArtifacts = project.getDesignArtifacts();
        Set<SourceCodeArtifact> codeArtifacts = project.getCodeArtifacts();

        logger.info("Design artifacts: {}", designArtifacts.size());
        logger.info("Code artifacts: {}", codeArtifacts.size());
        logger.info("Requirements artifacts: {}", requirementsArtifacts.size());

        logger.info("Finding consensual biterms...");

        Set<ConsensualBiterm> designCodeConsensualBiterms = new HashSet<>();
        Set<ConsensualBiterm> designReqConsensualBiterms = new HashSet<>();

        // design as intermediate
        int i = 0;
        for (DesignDocumentArtifact designDocumentArtifact : designArtifacts) {
            logger.info("Processing design artifact: {}", ++i);
            Set<Biterm> designBiterms = designDocumentArtifact.getBiterms();
            for (Biterm designBiterm : designBiterms) {
                // code artifacts
                for (SourceCodeArtifact codeArtifact : codeArtifacts) {
                    Set<Biterm> codeBiterms = codeArtifact.getBiterms();
                    for (Biterm codeBiterm : codeBiterms) {
                        if (designBiterm.isConsensual(codeBiterm)) {
                            ConsensualBiterm consensualBiterm = new ConsensualBiterm(
                                    designBiterm, designDocumentArtifact.getIdentifier(), codeArtifact.getIdentifier());
                            designCodeConsensualBiterms.add(consensualBiterm);
                            designDocumentArtifact.addConsensualBiterm(consensualBiterm);
                            codeArtifact.addConsensualBiterm(consensualBiterm);
                        }
                    }
                }
                // requirements artifacts
                for (RequirementsDocumentArtifact requirementsArtifact : requirementsArtifacts) {
                    Set<Biterm> requirementBiterms = requirementsArtifact.getBiterms();
                    for (Biterm requirementBiterm : requirementBiterms) {
                        if (designBiterm.isConsensual(requirementBiterm)) {
                            ConsensualBiterm consensualBiterm = new ConsensualBiterm(
                                    designBiterm,
                                    designDocumentArtifact.getIdentifier(),
                                    requirementsArtifact.getIdentifier());
                            designReqConsensualBiterms.add(consensualBiterm);
                            designDocumentArtifact.addConsensualBiterm(consensualBiterm);
                            requirementsArtifact.addConsensualBiterm(consensualBiterm);
                        }
                    }
                }
            }
        }

        OutputLog.writeBitermsToFile(
                "output/" + project.getName() + "/Design-Code-ConsensualBiterms.txt", designCodeConsensualBiterms);
        OutputLog.writeBitermsToFile(
                "output/" + project.getName() + "/Design-Requirements-ConsensualBiterms.txt",
                designReqConsensualBiterms);

        // extend artifacts with consensual biterms
        ArtifactsCollection reqCollection = new ArtifactsCollection(requirementsArtifacts);
        ArtifactsCollection designCollection = new ArtifactsCollection(designArtifacts);
        ArtifactsCollection codeCollection = new ArtifactsCollection(codeArtifacts);


        for (IRModel irModel : IR_MODELS_TO_RUN) {
            logger.info("--- RUNNING ANALYSIS WITH IR MODEL: {} ---", irModel.getModelName());

            //  similarity pair wise
            var reqDesignSimilarity = irModel.Compute(reqCollection, designCollection);
            var designCodeSimilarity = irModel.Compute(designCollection, codeCollection);
            var reqCodeSimilarity = irModel.Compute(reqCollection, codeCollection);
            // inner transitive links
            var reqReqSimilarity = irModel.Compute(reqCollection, reqCollection);
            var designDesignSimilarity = irModel.Compute(designCollection, designCollection);

            // eval
            logger.info("Start eval for IR Model: {}", irModel.getModelName());

            // IR only
            SimilarityMatrix irOnlyResults = reqCodeSimilarity;
            evaluateAndLog("IR-ONLY - " + irModel.getModelName(), irOnlyResults, goldStandard, project.getName());

            // TRIAD full (b+o+i)
            SimilarityMatrix triadResults = adjustScoresByTransitivity(
                    reqCodeSimilarity, reqReqSimilarity, reqDesignSimilarity, designDesignSimilarity, designCodeSimilarity);
            evaluateAndLog("TRIAD (b+o+i) - " + irModel.getModelName(), triadResults, goldStandard, project.getName());
            
            logger.info("--- Statistical Comparison for {} ---", irModel.getModelName());
            List<Double> triadPrecisions = calculatePrecisionRecallCurve(triadResults, goldStandard);
            List<Double> irOnlyPrecisions = calculatePrecisionRecallCurve(irOnlyResults, goldStandard);
    
            List<Double> triadFMeasures = new ArrayList<>();
            List<Double> irOnlyFMeasures = new ArrayList<>();
        
            for (int j = 0; j < 20; j++) {
                double recallLevel = (j + 1) / 20.0; // From 0.05 to 1.00
                double triadPrecision = triadPrecisions.get(j);
                double irOnlyPrecision = irOnlyPrecisions.get(j);
                
                double triadF1 = (triadPrecision + recallLevel) == 0 ? 0.0 : 2 * (triadPrecision * recallLevel) / (triadPrecision + recallLevel);
                triadFMeasures.add(triadF1);
        
                double irOnlyF1 = (irOnlyPrecision + recallLevel) == 0 ? 0.0 : 2 * (irOnlyPrecision * recallLevel) / (irOnlyPrecision + recallLevel);
                irOnlyFMeasures.add(irOnlyF1);
            }

            double[] triadFMeasuresArray = triadFMeasures.stream().mapToDouble(d->d).toArray();
            double[] irOnlyFMeasuresArray = irOnlyFMeasures.stream().mapToDouble(d->d).toArray();
        
            WilcoxonSignedRankTest wilcoxon = new WilcoxonSignedRankTest();
            double pValue = wilcoxon.wilcoxonSignedRankTest(triadFMeasuresArray, irOnlyFMeasuresArray, true);
            double cliffsDelta = calculateCliffsDelta(triadFMeasures, irOnlyFMeasures);

            logger.info("TRIAD vs IR-ONLY on {}: p-value = {}, Cliff's Delta = {}", irModel.getModelName(), String.format("%.4f", pValue), String.format("%.4f", cliffsDelta));
        
            // Final link generation - MOVED INSIDE THE LOOP
            List<SingleLink> allLinks = triadResults.getAllLinks();
            List<SingleLink> nonZeroLinks =
                    allLinks.stream().filter(link -> link.getScore() > 0.0).collect(Collectors.toList());

            if (!nonZeroLinks.isEmpty()) {
                nonZeroLinks.sort(Comparator.comparingDouble(SingleLink::getScore));
                int cutoffIndex = (int) Math.floor(nonZeroLinks.size() * 0.25);
                double threshold = nonZeroLinks.get(cutoffIndex).getScore();

                List<SingleLink> topLinks = allLinks.stream()
                        .filter(link -> link.getScore() >= threshold)
                        .sorted(Comparator.comparingDouble(SingleLink::getScore).reversed())
                        .collect(Collectors.toList());

                logger.info("Top Traceability Links for {} (Threshold: {}):", irModel.getModelName(), String.format("%.4f", threshold));
                for (SingleLink link : topLinks) {
                    logger.info(
                            "  {} -> {} (Score: {})",
                            link.getSourceArtifactId(),
                            link.getTargetArtifactId(),
                            String.format("%.4f", link.getScore()));
                }
                try {
                    // Make the output filename unique for each IR model
                    OutputLog.writeTraceabilityLinksToFile(
                            "output/" + project.getName() + "/RE-Code-TraceabilityLinks-" + irModel.getModelName() + ".csv", topLinks);
                } catch (IOException e) {
                    logger.error("Failed to write traceability links to file", e);
                }
            } else {
                logger.info("No non-zero traceability links found for {}.", irModel.getModelName());
            }
        }

    }

    private static void evaluateAndLog(String approachName, SimilarityMatrix results, GoldStandard goldStandard, String projectName) throws IOException {
        Set<SingleLink> allLinks = new IdentityHashSet<>(results.getAllLinks()); 
        Set<SingleLink> groundTruthLinks = new IdentityHashSet<>(goldStandard.getLinks());

        ClassificationMetricsCalculator calculator = ClassificationMetricsCalculator.getInstance();
        SingleClassificationResult<String> result = calculator.calculateMetrics(allLinks, groundTruthLinks, l -> l.getSourceArtifactId() + " -> " + l.getTargetArtifactId(), null);
        double precision = result.getPrecision();
        double recall = result.getRecall();
        double fMeasure = result.getF1();
        
        // Calculate MAP
        double map = calculateMAP(results, goldStandard);

        logger.info("Results for: {}", approachName);
        logger.info("Precision: {}", String.format("%.4f", precision));
        logger.info("Recall: {}", String.format("%.4f", recall));
        logger.info("F-Measure: {}", String.format("%.4f", fMeasure));
        logger.info("MAP: {}", String.format("%.4f", map));
        
        // Log to file
        String logMessage = String.format("Approach: %s, Precision: %.4f, Recall: %.4f, F-Measure: %.4f, MAP: %.4f%n", 
                                          approachName, precision, recall, fMeasure, map);
        
        Path outputPath = Paths.get("output/evaluation_results.txt");
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, logMessage, java.nio.file.StandardOpenOption.APPEND, java.nio.file.StandardOpenOption.CREATE);

        // Precision-Recall Curve Calculation and Export
        List<Double> interpolatedPrecisions = calculatePrecisionRecallCurve(results, goldStandard);
        String prCurvePath = "output/" + projectName + "/precision_recall_curves.csv";
        Files.createDirectories(Paths.get(prCurvePath).getParent());
        OutputLog.writePrecisionRecallCurveToFile(prCurvePath, approachName, interpolatedPrecisions);
        logger.info("Precision-Recall curve data for '{}' written to {}", approachName, prCurvePath);
    }
    
    private static SimilarityMatrix adjustScoresByTransitivity(
            SimilarityMatrix reqCodeSim,
            SimilarityMatrix reqReqSim,
            SimilarityMatrix reqDesignSim,
            SimilarityMatrix designDesignSim,
            SimilarityMatrix designCodeSim) {

        SimilarityMatrix adjustedMatrix = reqCodeSim.deepCopy();
        Set<String> sources = reqCodeSim.getSourceArtifacts();
        Set<String> targets = reqCodeSim.getTargetArtifacts();

        for (String s : sources) {
            for (String t : targets) {
                double totalBonus = 0;

                // Path Type 1: S -> I -> T
                List<SingleLink> s_i_links = findValidTransitiveLinks(reqDesignSim, s, 3, 0.5);
                for (SingleLink siLink : s_i_links) {
                    String i = siLink.getTargetArtifactId();
                    List<SingleLink> i_t_links = findValidTransitiveLinks(designCodeSim, i, 2, 0.6);
                    for (SingleLink itLink : i_t_links) {
                        if (itLink.getTargetArtifactId().equals(t)) {
                            totalBonus += siLink.getScore() * itLink.getScore();
                        }
                    }
                }

                // Path Type 2: S -> S' -> I -> T
                List<SingleLink> s_sPrime_links = findValidTransitiveLinks(reqReqSim, s, 3, 0.5);
                for (SingleLink ssPrimeLink : s_sPrime_links) {
                    String sPrime = ssPrimeLink.getTargetArtifactId();
                    if (sPrime.equals(s)) continue;
                    List<SingleLink> sPrime_i_links = findValidTransitiveLinks(reqDesignSim, sPrime, 2, 0.6);
                    for (SingleLink sPrimeiLink : sPrime_i_links) {
                        String i = sPrimeiLink.getTargetArtifactId();
                        List<SingleLink> i_t_links = findValidTransitiveLinks(designCodeSim, i, 1, 0.7);
                        for (SingleLink itLink : i_t_links) {
                            if (itLink.getTargetArtifactId().equals(t)) {
                                totalBonus += ssPrimeLink.getScore() * sPrimeiLink.getScore() * itLink.getScore();
                            }
                        }
                    }
                }

                // Path Type 3: S -> I -> I' -> T
                s_i_links = findValidTransitiveLinks(reqDesignSim, s, 3, 0.5);
                for (SingleLink siLink : s_i_links) {
                    String i = siLink.getTargetArtifactId();
                    List<SingleLink> i_iPrime_links = findValidTransitiveLinks(designDesignSim, i, 2, 0.6);
                    for (SingleLink iiPrimeLink : i_iPrime_links) {
                        String iPrime = iiPrimeLink.getTargetArtifactId();
                        if (iPrime.equals(i)) continue;
                        List<SingleLink> iPrime_t_links = findValidTransitiveLinks(designCodeSim, iPrime, 1, 0.7);
                        for (SingleLink iPrimetLink : iPrime_t_links) {
                            if (iPrimetLink.getTargetArtifactId().equals(t)) {
                                totalBonus += siLink.getScore() * iiPrimeLink.getScore() * iPrimetLink.getScore();
                            }
                        }
                    }
                }

                double initialScore = reqCodeSim.getScore(s, t);
                double adjustedScore = initialScore * (1 + totalBonus);
                adjustedMatrix.setScore(s, t, adjustedScore);
            }
        }
        return adjustedMatrix;
    }

    private static List<SingleLink> findValidTransitiveLinks(
            SimilarityMatrix matrix, String sourceArtifact, int t, double m) {
        if (matrix == null || matrix.getLinks(sourceArtifact) == null) {
            return new ArrayList<>();
        }

        LinksList allLinks = matrix.getLinks(sourceArtifact);
        if (allLinks.isEmpty()) {
            return new ArrayList<>();
        }

        double maxSimilarity = 0.0;
        for (SingleLink link : allLinks) {
            if (link.getScore() > maxSimilarity) {
                maxSimilarity = link.getScore();
            }
        }

        if (maxSimilarity == 0) {
            return new ArrayList<>();
        }

        double m_threshold = m * maxSimilarity;
        List<SingleLink> filteredLinks = allLinks.stream()
                .filter(link -> link.getScore() >= m_threshold)
                .sorted(Comparator.comparingDouble(SingleLink::getScore).reversed())
                .collect(Collectors.toList());

        return filteredLinks.subList(0, Math.min(t, filteredLinks.size()));
    }
    
    private static List<Double> calculatePrecisionRecallCurve(SimilarityMatrix similarityMatrix, GoldStandard gold) {
        List<SingleLink> allLinks = new ArrayList<>(similarityMatrix.getAllLinks());
        allLinks.sort(Comparator.comparingDouble(SingleLink::getScore).reversed());

        int totalRelevant = gold.getTotalRelevantLinks();
        if (totalRelevant == 0) {
            return Collections.nCopies(20, 0.0);
        }

        List<Double> recalls = new ArrayList<>();
        List<Double> precisions = new ArrayList<>();
        int correctRetrieved = 0;

        for (int i = 0; i < allLinks.size(); i++) {
            SingleLink link = allLinks.get(i);
            if (gold.isLink(link.getSourceArtifactId(), link.getTargetArtifactId())) {
                correctRetrieved++;
            }
            double currentRecall = (double) correctRetrieved / totalRelevant;
            double currentPrecision = (double) correctRetrieved / (i + 1);
            recalls.add(currentRecall);
            precisions.add(currentPrecision);
        }

        List<Double> interpolatedPrecisions = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            double recallLevel = i / 20.0;
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

    private static double calculateAP(List<SingleLink> rankedRetrieved, Set<String> relevantLinks) {
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

    private static double calculateMAP(SimilarityMatrix similarityMatrix, GoldStandard gold) {
        double map = 0.0;
        Set<String> sources = similarityMatrix.getSourceArtifacts();
        if (sources.isEmpty()) {
            return 0.0;
        }
        int sourceCount = 0;
        for (String source : sources) {
            Set<String> relevant = gold.getRelevantLinks(source);
            if (relevant.isEmpty()){
                continue;
            }
            sourceCount++;
            List<SingleLink> rankedLinks = new ArrayList<>(similarityMatrix.getLinks(source));
            rankedLinks.sort(Comparator.comparingDouble(SingleLink::getScore).reversed());
            map += calculateAP(rankedLinks, relevant);
        }
        return sourceCount == 0 ? 0.0 : map / sourceCount;
    }
    
    private static double calculateCliffsDelta(List<Double> sample1, List<Double> sample2) {
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