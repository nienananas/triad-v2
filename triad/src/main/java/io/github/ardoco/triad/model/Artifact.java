package io.github.ardoco.triad.model;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;
import io.github.ardoco.triad.text.TextProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class Artifact {

    private static final Logger logger = LoggerFactory.getLogger(Artifact.class);

    protected String identifier;
    protected String textBody;
    protected Set<Biterm> biterms;
    protected Map<ConsensualBiterm, Integer> consensualBiterms = new HashMap<>();

    protected static final StanfordCoreNLP pipeline;
    static {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,depparse");
        pipeline = new StanfordCoreNLP(props);
        logger.info("StanfordCoreNLP pipeline initialized for Artifact.");
    }

    protected Artifact(String identifier, String textBody) {
        this.identifier = identifier;
        this.textBody = textBody;
    }

    protected Artifact(Artifact other) {
        this.identifier = other.identifier;
        this.textBody = other.textBody;
        if (other.biterms != null) {
            this.biterms = new HashSet<>(other.biterms);
        }
        if (other.consensualBiterms != null) {
            this.consensualBiterms = new HashMap<>(other.consensualBiterms);
        }
    }

    public abstract Artifact deepCopy();
    public abstract ArtifactType getType();

    protected abstract void preProcessing();

    public void enrichBodyWithConsensualBiterms() {
        if (consensualBiterms.isEmpty()) return;

        StringBuilder enrichment = new StringBuilder();
        for (Map.Entry<ConsensualBiterm, Integer> entry : consensualBiterms.entrySet()) {
            String bitermText = entry.getKey().getFirstTerm() + " " + entry.getKey().getSecondTerm() + " ";
            int frequency = entry.getValue();
            for (int i = 0; i < frequency; i++) {
                enrichment.append(bitermText);
            }
        }
        this.textBody = (this.textBody == null ? "" : this.textBody) + " " + enrichment.toString().trim();
        this.biterms = null; // Invalidate cache
    }

    public Set<Biterm> getBiterms() {
        if (this.biterms == null) {
            this.biterms = getBitermsFromText(this.textBody);
        }
        return this.biterms;
    }

    public String getIdentifier() { return identifier; }
    public String getTextBody() { return textBody; }

    protected Set<Biterm> getBitermsFromText(String text) {
        Set<Biterm> extracted = new LinkedHashSet<>();
        if (text == null || text.isBlank()) return extracted;

        Annotation doc = new Annotation(text);
        pipeline.annotate(doc);

        List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
        if (sentences == null) return extracted;

        for (CoreMap sentence : sentences) {
            SemanticGraph deps = sentence.get(EnhancedPlusPlusDependenciesAnnotation.class);
            if (deps == null) continue;

            for (SemanticGraphEdge edge : deps.edgeListSorted()) {
                String govWord = edge.getGovernor().originalText();
                String depWord = edge.getDependent().originalText();
                String govPos = edge.getGovernor().tag();
                String depPos = edge.getDependent().tag();

                if (!isContentPOS(govPos) || !isContentPOS(depPos)) continue;
                
                String processedGov = TextProcessor.processTermOriginal(govWord);
                String processedDep = TextProcessor.processTermOriginal(depWord);
                
                if (processedGov.isEmpty() || processedDep.isEmpty() || processedGov.equals(processedDep)) {
                    continue;
                }

                extracted.add(new Biterm(processedGov, processedDep));
                extracted.add(new Biterm(processedDep, processedGov));
            }
        }
        return extracted;
    }

    private static boolean isContentPOS(String pos) {
        return pos != null && (pos.startsWith("NN") || pos.startsWith("VB") || pos.startsWith("JJ"));
    }

    public void addConsensualBiterm(ConsensualBiterm biterm) {
        this.consensualBiterms.merge(biterm, 1, Integer::sum);
    }

    public void filterBiterms(Set<String> allowedBiterms) {
        if (this.biterms == null) getBiterms();
        this.biterms = this.biterms.stream()
                .filter(b -> allowedBiterms.contains(b.toString()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}