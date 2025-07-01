package io.github.ardoco.artifact;

import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.semgraph.*;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.*;
import edu.stanford.nlp.util.*;
import io.github.ardoco.Biterm.Biterm;
import io.github.ardoco.Biterm.ConsensualBiterm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public abstract class Artifact {

    private static final Logger logger = LoggerFactory.getLogger(Artifact.class);
    protected String identifier;
    protected String textBody;
    protected Set<Biterm> biterms;
    protected Set<ConsensualBiterm> consensualBiterms = new HashSet<>();
    protected static StanfordCoreNLP pipeline;
    protected Set<ConsensualBiterm> extendedBiterms;

    static { //TODO: static weg, stattdessen statische Methode loadPipeline() 
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,depparse");
        props.setProperty("coref.algorithm", "neural");
        pipeline = new StanfordCoreNLP(props);
    }

    protected Artifact(String identifier, String textBody) {
        this.identifier = identifier;
        this.textBody = textBody;
    }

    abstract void preProcessing();

    /**
     * Looks for biterms in its own body using Stanford dependency parsing.
     * A biterm is a pair of words that have a direct dependency relationship.
     * This assumes the textBody is written in natural language.
     * @return A set of biterms, where each biterm is represented as the two words, separated by a space.
     */
    public Set<Biterm> getBiterms() {
        if (this.biterms != null) {
            return this.biterms;
        }

        this.biterms = getBitermsFromText(textBody);
        return this.biterms;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void extendBiterms(Set<String> consensualBiterms) {
        logger.info("Artifact.extendBiterms() called for: {} with consensual biterms: {}", this.getClass().getSimpleName(), consensualBiterms);
        // TODO: expand via added weight to IR method?
    }

    public String getTextBody() {
        return textBody;
    }

    protected void setTextBody(String textBody) {
        this.textBody = textBody;
    }


    protected boolean isValidWord(IndexedWord word) {
        //String pos = word.tag();
        // Check if the POS tag indicates a verb, noun, or adjective
        return isVerb(word.tag()) || isNoun(word.tag()) || isAdjective(word.tag());

    }

    protected Set<Biterm> getBitermsFromText(String text) {
        if (this.biterms != null) {
            return this.biterms;
        }

        Annotation document = new Annotation(text);
        pipeline.annotate(document);

        Set<Biterm> biterms = new LinkedHashSet<>(); //TODO: Überall LinkedHashSet verwenden, für reihenfolge

        for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
            SemanticGraph dependencies = sentence.get(BasicDependenciesAnnotation.class);
            
            for (SemanticGraphEdge edge : dependencies.edgeListSorted()) {
                IndexedWord governor = edge.getGovernor();
                IndexedWord dependent = edge.getDependent();
                
                if (isValidWord(governor) && isValidWord(dependent)) {
                    biterms.add(new Biterm(governor, dependent, edge.getRelation()));
                }
            }
        }
        return biterms;
    }

    private boolean isVerb(String pos) {
        return pos.startsWith("VB");
    }

    private boolean isNoun(String pos) {
        return pos.startsWith("NN");
    }

    private boolean isAdjective(String pos) {
        return pos.startsWith("JJ");
    }

    public void addConsensualBiterm(ConsensualBiterm biterm) {
        consensualBiterms.add(biterm);
    }

    protected String getEnrichedBody() {
        if (this.extendedBiterms != null) {
            StringBuilder enrichedBody = new StringBuilder(this.textBody);
            enrichedBody.append(this.textBody);
            enrichedBody.append('\n');
            for (ConsensualBiterm consensualBiterm : this.extendedBiterms) {
                enrichedBody.append(consensualBiterm.toString());
                enrichedBody.append('\n');
            }
            return enrichedBody.toString();
        }
        return this.textBody;
    }


}