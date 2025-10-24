/* Licensed under MIT 2025. */
package io.github.ardoco.triad;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

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
    //private static final List<IRModel> IR_MODELS_TO_RUN = List.of(new VSM(), new LSI(), new JSD());
    private static final List<IRModel> IR_MODELS_TO_RUN = List.of(new VSM());
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
        ObjectMapper mapper = new ObjectMapper();
        Config config = mapper.readValue(new File("config.json"), Config.class);

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

            for (IRModel irModel : IR_MODELS_TO_RUN) {
                logger.info("--- RUNNING ANALYSIS WITH IR MODEL: {} ---", irModel.getModelName());

                TriadPipeline pipeline = new TriadPipeline(project, irModel);

                // Run IR-ONLY baseline
                SimilarityMatrix irOnlyResults = pipeline.runIrOnly();
                OutputLog.writeSimilarityMatrixToFile(OUTPUT_DIR + "/similarities/" + project.getName() + "_" + irModel.getModelName()+ ".csv", irOnlyResults);
                if (config.getDoEvaluate()) {
                    evaluateAndLog("IR-ONLY-" + irModel.getModelName(), irOnlyResults, goldStandard, project);
                }


                // Run full TRIAD pipeline
                SimilarityMatrix triadResults = pipeline.run();
                if (config.getDoEvaluate()) {
                    evaluateAndLog("TRIAD-" + irModel.getModelName(), triadResults, goldStandard, project);
                }


                // Perform statistical comparison
                //compareApproaches(irOnlyResults, triadResults, goldStandard, irModel.getModelName());
            }
        }
    }

    private static void evaluateAndLog(
            String approachName, SimilarityMatrix results, GoldStandard goldStandard, Project project)
            throws IOException {
        Evaluation.PRF prf = Evaluation.calculatePRF(results, goldStandard);
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
