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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a generic artifact in a software project, such as a requirement, a design document, or a source code file.
 * <p>
 * This abstract class serves as the base for all specific artifact types. It encapsulates common properties like an
 * identifier and a text body, and provides the core logic for extracting semantic {@link Biterm}s using
 * Natural Language Processing (NLP). Subclasses are responsible for implementing specialized parsing and pre-processing.
 */
public abstract class Artifact {

    private static final Logger logger = LoggerFactory.getLogger(Artifact.class);

    protected String identifier;
    protected String textBody;
    protected Set<Biterm> biterms;
    protected Map<ConsensualBiterm, Integer> consensualBiterms = new HashMap<>();

    /**
     * A shared, static instance of the StanfordCoreNLP pipeline, initialized for efficient dependency parsing.
     * This heavy object is created only once to avoid performance overhead on artifact creation.
     */
    protected static final StanfordCoreNLP pipeline;
    static {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,depparse");
        pipeline = new StanfordCoreNLP(props);
        logger.info("StanfordCoreNLP pipeline initialized for dependency parsing.");
    }

    /**
     * Constructs a new Artifact.
     *
     * @param identifier a unique identifier for the artifact (e.g., file name, requirement ID).
     * @param textBody   the raw textual content of the artifact.
     */
    protected Artifact(String identifier, String textBody) {
        this.identifier = identifier;
        this.textBody = textBody;
    }

    /**
     * Constructs a copy of another Artifact.
     *
     * @param other the Artifact to copy.
     */
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

    /**
     * Creates a deep copy of this artifact instance.
     *
     * @return a new artifact instance with the same state.
     */
    public abstract Artifact deepCopy();

    /**
     * Returns the specific type of the artifact.
     *
     * @return the {@link ArtifactType}.
     */
    public abstract ArtifactType getType();

    /**
     * A hook for subclasses to perform any necessary pre-processing on the artifact's content
     * before biterm extraction.
     */
    protected abstract void preProcessing();

    /**
     * Enriches the artifact's text body by appending consensual biterms.
     * This method modifies the internal {@code textBody} and invalidates the biterm cache,
     * forcing re-computation on the next call to {@link #getBiterms()}.
     */
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
        this.biterms = null; // Invalidate cache to force re-computation
    }

    /**
     * Gets the set of biterms extracted from the artifact's text body.
     * <p>
     * This method uses lazy initialization. The biterms are computed on the first call and cached for
     * subsequent requests. The cache is invalidated if the text body is modified (e.g., by enrichment).
     *
     * @return a {@link Set} of {@link Biterm} objects.
     */
    public Set<Biterm> getBiterms() {
        if (this.biterms == null) {
            this.biterms = getBitermsFromText(this.textBody);
        }
        return this.biterms;
    }

    /**
     * Gets the artifact's unique identifier.
     * @return the identifier string.
     */
    public String getIdentifier() { return identifier; }

    /**
     * Gets the artifact's processed text body.
     * @return the text body string.
     */
    public String getTextBody() { return textBody; }

    /**
     * Analyzes the artifact's text and returns a map of term pairs and the grammatical relation connecting them.
     * This method is intended for debugging and analysis purposes to inspect the dependency parsing results.
     *
     * @return A map where the key is a string like {@code "'term1' -> 'term2'"} and the value is the relation (e.g., "amod", "nsubj").
     */
    public Map<String, String> getBitermRelations() {
        Map<String, String> bitermRelations = new HashMap<>();
        if (this.textBody == null || this.textBody.isBlank()) return bitermRelations;

        String raw = this.textBody.replaceAll("(?i)\\[(SUMMARY|DESCRIPTION)\\]", " ").replace('-', ' ');
        Annotation doc = new Annotation(raw);
        pipeline.annotate(doc);

        List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
        if (sentences == null) return bitermRelations;

        for (CoreMap sentence : sentences) {
            SemanticGraph deps = sentence.get(EnhancedPlusPlusDependenciesAnnotation.class);
            if (deps == null) continue;

            for (SemanticGraphEdge edge : deps.edgeListSorted()) {
                String govWord = TextProcessor.processWord(edge.getGovernor().originalText());
                String depWord = TextProcessor.processWord(edge.getDependent().originalText());
                String relation = edge.getRelation().toString();

                if (!govWord.isEmpty() && !depWord.isEmpty()) {
                    String key = String.format("'%s' -> '%s'", govWord, depWord);
                    bitermRelations.put(key, relation);
                }
            }
        }
        return bitermRelations;
    }

    /**
     * Extracts weighted biterms from a given text using NLP dependency parsing.
     * This is the core logic for semantic analysis of textual artifacts. The process involves:
     * 1. Parsing sentences and generating a dependency graph.
     * 2. Identifying compound nouns and adjectival modifiers to link related terms.
     * 3. Filtering grammatical relations to keep only semantically significant ones (e.g., `obj`, `nsubj`).
     * 4. Applying special heuristics for certain words (e.g., bridging relationships around the term "code").
     *
     * @param text The input text to analyze.
     * @return A {@link Set} of weighted {@link Biterm}s.
     */
    protected Set<Biterm> getBitermsFromText(String text) {
        Map<Biterm, Integer> bitermFrequencies = new HashMap<>();
        if (text == null || text.isBlank()) return new HashSet<>();

        String raw = text
            .replaceAll("(?i)\\[(SUMMARY|DESCRIPTION)\\]", " ")
            .replace('-', ' ');
        Annotation doc = new Annotation(raw);
        pipeline.annotate(doc);

        List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
        if (sentences == null) return new HashSet<>();

        for (CoreMap sentence : sentences) {
            SemanticGraph deps = sentence.get(EnhancedPlusPlusDependenciesAnnotation.class);
            if (deps == null) continue;

            // Pass 1: Collect compound and adjectival modifiers for each head word.
            Map<String, Set<String>> headModifiers = new HashMap<>();
            for (SemanticGraphEdge edge : deps.edgeListSorted()) {
                String rel = edge.getRelation().toString();
                if (rel.equals("compound") || rel.equals("amod")) {
                    String head = TextProcessor.processWord(edge.getGovernor().originalText());
                    String mod  = TextProcessor.processWord(edge.getDependent().originalText());
                    if (!head.isEmpty() && !mod.isEmpty() && !head.equals(mod)) {
                        headModifiers.computeIfAbsent(head, k -> new LinkedHashSet<>()).add(mod);
                    }
                }
            }

            // Propagate modifiers one hop to handle chains (e.g., "state transition diagram" -> diagram:{state, transition}).
            for (Map.Entry<String, Set<String>> e : new HashMap<>(headModifiers).entrySet()) {
                String head = e.getKey();
                Set<String> modsCopy = new LinkedHashSet<>(e.getValue());
                for (String mid : modsCopy) {
                    for (String x : headModifiers.getOrDefault(mid, Collections.emptySet())) {
                        if (!x.equals(head)) {
                            headModifiers
                                .computeIfAbsent(head, k -> new LinkedHashSet<>())
                                .add(x);
                        }
                    }
                }
            }

            // Pass 2: Extract biterms from allowed grammatical relations.
            for (SemanticGraphEdge edge : deps.edgeListSorted()) {
                String rel = edge.getRelation().toString();

                String govWord = edge.getGovernor().originalText();
                String depWord = edge.getDependent().originalText();
                String govPos = edge.getGovernor().tag();
                String depPos = edge.getDependent().tag();

                if (!isContentPOS(govPos) || !isContentPOS(depPos)) continue;

                String processedGov = TextProcessor.processWord(govWord);
                String processedDep = TextProcessor.processWord(depWord);
                if (processedGov.isEmpty() || processedDep.isEmpty() || processedGov.equals(processedDep)) continue;

                // Heuristic: Special handling for "context" to reduce noise.
                if ("context".equals(processedGov) || "context".equals(processedDep)) {
                    String other = "context".equals(processedGov) ? processedDep : processedGov;
                    if (!other.equals("ssl") && !other.equals("use")) continue;
                }

                // Heuristic: Bridge relationships around the word "code" using its modifiers.
                if (!rel.equals("compound") && !rel.equals("amod")) {
                    boolean involvesCode = "code".equals(processedGov) || "code".equals(processedDep);
                    if (involvesCode) {
                        String other = "code".equals(processedGov) ? processedDep : processedGov;
                        for (String m : headModifiers.getOrDefault("code", Collections.emptySet())) {
                            Biterm b1 = new Biterm(other, m);
                            bitermFrequencies.merge(b1, 1, Integer::sum);
                            Biterm b2 = new Biterm(m, other);
                            bitermFrequencies.merge(b2, 1, Integer::sum);
                        }
                        continue; // Skip emitting the direct pair with "code".
                    }
                }

                // Allow-list of semantically significant grammatical relations.
                boolean allowed = rel.equals("compound") || rel.equals("amod") ||
                                  rel.equals("obj") || rel.equals("dobj") ||
                                  rel.equals("xcomp") || rel.equals("nmod:of") ||
                                  rel.startsWith("acl");
                
                // Heuristic: Allow specific prepositional relations.
                if (rel.startsWith("nmod:for") || rel.startsWith("obl:for")) {
                    String govLemma = TextProcessor.processWord(edge.getGovernor().originalText());
                    allowed = "use".equals(govLemma);
                }
                if (rel.startsWith("nmod:to") || rel.startsWith("obl:to")) {
                    String govLemma = TextProcessor.processWord(edge.getGovernor().originalText());
                    allowed = "set".equals(govLemma);
                }
                if (rel.equals("nsubj")) {
                    String govLemma = TextProcessor.processWord(edge.getGovernor().originalText());
                    allowed = "set".equals(govLemma);
                }
                
                if (!allowed) continue;

                // Create biterms in both orders to treat the relationship as symmetric.
                Biterm biterm1 = new Biterm(processedGov, processedDep);
                bitermFrequencies.merge(biterm1, 1, Integer::sum);

                Biterm biterm2 = new Biterm(processedDep, processedGov);
                bitermFrequencies.merge(biterm2, 1, Integer::sum);
            }
        }

        // Finalize biterms with their computed frequencies as weights.
        Set<Biterm> finalBiterms = new HashSet<>();
        for (Map.Entry<Biterm, Integer> entry : bitermFrequencies.entrySet()) {
            Biterm b = entry.getKey();
            b.setWeight(entry.getValue());
            finalBiterms.add(b);
        }
        return finalBiterms;
    }

    /**
     * Checks if the Part-of-Speech tag is for a content-bearing word (noun, verb, or adjective).
     * This helps filter out grammatical noise.
     * @param pos the POS tag to check.
     * @return {@code true} if the tag represents a content word, {@code false} otherwise.
     */
    private static boolean isContentPOS(String pos) {
        return pos != null && (pos.startsWith("NN") || pos.startsWith("VB") || pos.startsWith("JJ"));
    }

    /**
     * Adds a consensual biterm found during the enrichment phase.
     *
     * @param biterm the consensual biterm to add.
     */
    public void addConsensualBiterm(ConsensualBiterm biterm) {
        this.consensualBiterms.merge(biterm, 1, Integer::sum);
    }

    /**
     * Filters the artifact's biterms in place, keeping only those present in the provided set of allowed biterms.
     * This is used to create the final "consensual" biterm sets for each artifact.
     *
     * @param allowedBiterms a {@link Set} of biterm strings that are allowed to be kept.
     */
    public void filterBiterms(Set<String> allowedBiterms) {
        if (this.biterms == null) getBiterms(); // Ensure biterms are computed if they haven't been already.
        this.biterms = this.biterms.stream()
                .filter(b -> allowedBiterms.contains(b.toString()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}