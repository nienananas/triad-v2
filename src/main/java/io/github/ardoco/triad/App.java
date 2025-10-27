/* Licensed under MIT 2025. */
package io.github.ardoco.triad;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import io.github.ardoco.triad.ir.JSD;
import io.github.ardoco.triad.ir.LSI;
import io.github.ardoco.triad.ir.SingleLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ardoco.triad.config.Config;
import io.github.ardoco.triad.config.ProjectConfig;
import io.github.ardoco.triad.evaluation.Evaluation;
import io.github.ardoco.triad.evaluation.GoldStandard;
import io.github.ardoco.triad.ir.IRModel;
import io.github.ardoco.triad.ir.SimilarityMatrix;
import io.github.ardoco.triad.ir.VSM;
import io.github.ardoco.triad.model.PreprocessedProject;
import io.github.ardoco.triad.model.Project;
import io.github.ardoco.triad.pipeline.TriadPipeline;
import io.github.ardoco.triad.util.OutputLog;

public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);
    private static final String OUTPUT_DIR = "experiments/output";

    /**
     * Entry point of the TRIAD application.
     * <p>
     * Reads the {@code config.json}, iterates over configured projects and IR models,
     * runs the IR-ONLY baseline and the full TRIAD pipeline, and writes evaluation
     * artifacts (summary and precision-recall curves) to the {@code output/} folder.
     *
     * @param args command-line arguments (unused)
     * @throws IOException if reading configuration or writing outputs fails
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            logger.error("Invalid number of arguments! There should be only one argument, namely the path to the config file.");
            return;
        }
        ObjectMapper mapper = new ObjectMapper();
        Config config = mapper.readValue(new File(args[0]), Config.class);

        for (ProjectConfig projectConfig : config.getProjects()) {
            Project project;
            if ("Dronology-Original-Preproc".equals(projectConfig.getName())) {
                project = new PreprocessedProject(projectConfig);
                logger.warn("Using special PreprocessedProject loader for '{}'", projectConfig.getName());
            } else {
                project = new Project(projectConfig);
            }
            logger.info("================================================================================");
            logger.info("STARTING ANALYSIS FOR PROJECT: {}", project.getName());
            logger.info("================================================================================");

            GoldStandard goldStandard = new GoldStandard(project.getGoldStandardPath());

            Path projectOutputDir = Paths.get(OUTPUT_DIR, project.getName());
            Files.createDirectories(projectOutputDir);

            IRModel irModel;
            //Check if the given model is implemented and if so initialize the irModel
            switch (config.getIrMethod()) {
                case "VSM":
                    irModel = new VSM();
                    break;
                case "LSI":
                    irModel = new LSI();
                    break;
                case "JSD":
                    irModel = new JSD();
                    break;
                default:
                    logger.error("Unrecognized IR MODEL: {}", config.getIrMethod());
                    return;
            }
            TriadPipeline pipeline = new TriadPipeline(project, irModel);
            SimilarityMatrix results;
            String approachName;

            //Run either the IR method on their own or run Triad using the IR method
            if (config.getRunTriad()) {
                logger.info("--- RUNNING TRIAD WITH METHOD: {} ---", irModel.getModelName());
                results = pipeline.run();
                approachName = "Triad-" + irModel.getModelName();
            } else {
                logger.info("--- RUNNING IR METHOD: {} ---", irModel.getModelName());
                results = pipeline.runIrOnly();
                approachName = irModel.getModelName();
            }


            OutputLog.writeSimilarityMatrixToFile(OUTPUT_DIR + "/similarities/" + project.getName() + "_" + approachName + ".csv", results);
            if (config.getDoEvaluate()) {
                evaluateAndLog(approachName, results, goldStandard, project);
                logger.info("Testing multiple thresholds for positive links");
                for (double threshold = 0.1; threshold < 1.0; threshold += 0.1) {
                    evaluateAndLogPositivesWithThreshold(results, goldStandard, threshold, approachName, project.getName());
                }

                //TODO: Fix negative samples
                /*
                logger.info("Testing multiple thresholds for negative examples");
                for (double threshold = 0.0; threshold < 0.5; threshold += 0.05) {
                    evaluateAndLogNegativesWithThreshold(results, goldStandard, threshold, approachName, project.getName());
                }
                */

            }
        }
    }

    /**
     * @author ninananas
     */
    private static void evaluateAndLogPositivesWithThreshold(SimilarityMatrix results, GoldStandard goldStandard, double threshold, String approachName, String projectName) throws IOException {
        List<SingleLink> links = results.getLinksAboveThreshold(threshold);
        Evaluation.PRF prf = Evaluation.calculatePRF(links, goldStandard);
        logger.info("Results for threshold {}:", threshold);
        logger.info("Precision: {}", String.format("%.4f", prf.precision()));
        logger.info("Recall:    {}", String.format("%.4f", prf.recall()));
        logger.info("F1-Score:  {}", String.format("%.4f", prf.f1()));
        logger.info("Amount: {}", links.size());

        Path summaryPath = Paths.get(OUTPUT_DIR, approachName + "_evaluation_with_threshold.csv");
        if (!Files.exists(summaryPath)) {
            Files.writeString(summaryPath, "Approach,Project,Threshold,Precision,Recall,F1,Amount\n", StandardOpenOption.CREATE);
        }
        String summaryLog = String.format(
                "%s,%s,%.4f,%.4f,%.4f,%.4f,%d%n",
                approachName, projectName, threshold, prf.precision(), prf.recall(), prf.f1(), links.size());
        Files.writeString(summaryPath, summaryLog, StandardOpenOption.APPEND);
    }

    /**
     * @author ninananas
     */
    private static void evaluateAndLogNegativesWithThreshold(SimilarityMatrix results, GoldStandard goldStandard, double threshold, String approachName, String projectName) throws IOException {
        List<SingleLink> links = results.getLinksBelowThreshold(threshold);
        Evaluation.PRF prf = Evaluation.calculatePRF(links, goldStandard);
        logger.info("Results for negative threshold {}:", threshold);
        logger.info("Precision: {}", String.format("%.4f", prf.precision()));
        logger.info("Recall:    {}", String.format("%.4f", prf.recall()));
        logger.info("F1-Score:  {}", String.format("%.4f", prf.f1()));
        logger.info("Amount: {}", links.size());

        Path summaryPath = Paths.get(OUTPUT_DIR, approachName + "_evaluation_with_threshold.csv");
        if (!Files.exists(summaryPath)) {
            Files.writeString(summaryPath, "Approach,Project,Threshold,Precision,Recall,F1,Amount\n", StandardOpenOption.CREATE);
        }
        String summaryLog = String.format(
                "%s,%s,%.4f,%.4f,%.4f,%.4f,%d%n",
                approachName, projectName, threshold, prf.precision(), prf.recall(), prf.f1(), links.size());
        Files.writeString(summaryPath, summaryLog, StandardOpenOption.APPEND);
    }

    /**
      * @author ninananas
     */
    private static void evaluateAndLogTopKRetrieval(SimilarityMatrix results, GoldStandard goldStandard, int k, String approachName, String projectName) throws IOException {
        List<SingleLink> links = results.getTopKLinks(k);
        Evaluation.PRF prf = Evaluation.calculatePRF(links, goldStandard);
        logger.info("Results for Top {}:", k);
        logger.info("Precision: {}", String.format("%.4f", prf.precision()));
        logger.info("Recall:    {}", String.format("%.4f", prf.recall()));
        logger.info("F1-Score:  {}", String.format("%.4f", prf.f1()));
        logger.info("Amount: {}", links.size());

        Path summaryPath = Paths.get(OUTPUT_DIR, approachName + "_evaluation_top_k.csv");
        if (!Files.exists(summaryPath)) {
            Files.writeString(summaryPath, "Approach,Project,Threshold,Precision,Recall,F1,Amount\n", StandardOpenOption.CREATE);
        }
        String summaryLog = String.format(
                "%s,%s,%d,%.4f,%.4f,%.4f,%d%n",
                approachName, projectName, k, prf.precision(), prf.recall(), prf.f1(), links.size());
        Files.writeString(summaryPath, summaryLog, StandardOpenOption.APPEND);
    }

    private static void evaluateAndLog(
            String approachName, SimilarityMatrix results, GoldStandard goldStandard, Project project)
            throws IOException {
        Evaluation.PRF prf = Evaluation.calculatePRF(results.getAllLinks(), goldStandard);
        double map = Evaluation.calculateMAP(results, goldStandard);

        logger.info("Results for: {}", approachName);
        logger.info("Precision: {}", String.format("%.4f", prf.precision()));
        logger.info("Recall:    {}", String.format("%.4f", prf.recall()));
        logger.info("F1-Score:  {}", String.format("%.4f", prf.f1()));
        logger.info("MAP:       {}", String.format("%.4f", map));

        Path summaryPath = Paths.get(OUTPUT_DIR, "evaluation_summary.csv");
        if (!Files.exists(summaryPath)) {
            Files.writeString(summaryPath, "Project,Approach,Precision,Recall,F1,MAP\n", StandardOpenOption.CREATE);
        }
        String summaryLog = String.format(
                "%s,%s,%.4f,%.4f,%.4f,%.4f%n",
                project.getName(), approachName, prf.precision(), prf.recall(), prf.f1(), map);
        Files.writeString(summaryPath, summaryLog, StandardOpenOption.APPEND);

        // Calculate interpolated precision at 20 recall cutoffs (0.05, 0.10, ..., 1.00)
        List<Double> interpolatedPrecisions = Evaluation.getPrecisionAtRecallLevels(results, goldStandard, 20);

        Path prCurvePath = Paths.get(OUTPUT_DIR, project.getName(), "precision_recall_curves.csv");

        OutputLog.writePrecisionRecallCurveToFile(prCurvePath.toString(), approachName, interpolatedPrecisions);
        logger.info("Precision-Recall curve data for '{}' written to {}", approachName, prCurvePath);
    }

    private static void compareApproaches(
            SimilarityMatrix base, SimilarityMatrix improved, GoldStandard gold, String modelName) {
        // Use F-measures at 11 recall levels for Wilcoxon test
        List<Double> baseFMeasures = Evaluation.getFMeasuresAt11RecallLevels(base, gold);
        List<Double> improvedFMeasures = Evaluation.getFMeasuresAt11RecallLevels(improved, gold);

        if (baseFMeasures.stream().allMatch(d -> d == 0.0)
                && improvedFMeasures.stream().allMatch(d -> d == 0.0)) {
            logger.warn("Both samples for Wilcoxon test are all zeros. Skipping comparison for {}.", modelName);
            return;
        }

        double pValue = Evaluation.calculatePValue(improvedFMeasures, baseFMeasures);
        double cliffsDelta = Evaluation.calculateCliffsDelta(improvedFMeasures, baseFMeasures);

        logger.info("STATISTICAL COMPARISON (TRIAD vs. IR-ONLY) for {}:", modelName);
        logger.info("Wilcoxon p-value: {}", String.format("%.4f", pValue));
        logger.info("Cliff's Delta: {}", String.format("%.4f", cliffsDelta));
    }
}
