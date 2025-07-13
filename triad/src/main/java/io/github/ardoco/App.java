/* Licensed under MIT 2025. */
package io.github.ardoco;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.ardoco.Biterm.Biterm;
import io.github.ardoco.Biterm.ConsensualBiterm;
import io.github.ardoco.artifact.DesignDocumentArtifact;
import io.github.ardoco.artifact.RequirementsDocumentArtifact;
import io.github.ardoco.artifact.SourceCodeArtifact;
import io.github.ardoco.document.ArtifactsCollection;
import io.github.ardoco.model.Evaluation;
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

            // calculate similarity pair wise
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
            evaluateAndLog("IR-ONLY - " + irModel.getModelName(), irOnlyResults, goldStandard);

            // TRIAD full (b+o+i)
            SimilarityMatrix triadResults = adjustScoresByTransitivity(
                    reqCodeSimilarity, reqReqSimilarity, reqDesignSimilarity, designDesignSimilarity, designCodeSimilarity);
            evaluateAndLog("TRIAD (b+o+i) - " + irModel.getModelName(), triadResults, goldStandard);
            
            logger.info("--- Statistical Comparison for {} ---", irModel.getModelName());
            List<Double> triadFMeasures = Evaluation.getFMeasuresAt11RecallLevels(triadResults, goldStandard);
            List<Double> irOnlyFMeasures = Evaluation.getFMeasuresAt11RecallLevels(irOnlyResults, goldStandard);
            
            double pValue = Evaluation.calculatePValue(triadFMeasures, irOnlyFMeasures);
            double cliffsDelta = Evaluation.calculateCliffsDelta(triadFMeasures, irOnlyFMeasures);

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

    private static void evaluateAndLog(String approachName, SimilarityMatrix results, GoldStandard goldStandard) throws IOException {
        List<SingleLink> allLinks = results.getAllLinks();

        // Calculate Precision, Recall, F-measure for the whole set
        double precision = Evaluation.calculatePrecision(allLinks, goldStandard);
        double recall = Evaluation.calculateRecall(allLinks, goldStandard);
        double fMeasure = Evaluation.calculateFMeasure(precision, recall);
        
        // Calculate MAP
        double map = Evaluation.calculateMAP(results, goldStandard);

        logger.info("Results for: {}", approachName);
        logger.info("Precision: {}", String.format("%.4f", precision));
        logger.info("Recall: {}", String.format("%.4f", recall));
        logger.info("F-Measure: {}", String.format("%.4f", fMeasure));
        logger.info("MAP: {}", String.format("%.4f", map));
        
        // Log to file
        String logMessage = String.format("Approach: %s, Precision: %.4f, Recall: %.4f, F-Measure: %.4f, MAP: %.4f%n", 
                                          approachName, precision, recall, fMeasure, map);
        Files.writeString(Paths.get("output/evaluation_results.txt"), logMessage, java.nio.file.StandardOpenOption.APPEND, java.nio.file.StandardOpenOption.CREATE);
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
}