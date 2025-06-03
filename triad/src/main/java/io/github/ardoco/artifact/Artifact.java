package io.github.ardoco.artifact;

import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.semgraph.*;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.*;
import edu.stanford.nlp.util.*;
import io.github.ardoco.Biterm.Biterm;

import java.util.*;

public abstract class Artifact {

    protected String textBody;
    protected static StanfordCoreNLP pipeline;

    static {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,depparse");
        props.setProperty("coref.algorithm", "neural");
        pipeline = new StanfordCoreNLP(props);
    }

    protected Artifact(String textBody) {
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
        Annotation document = new Annotation(textBody);
        pipeline.annotate(document);

        Set<Biterm> biterms = new HashSet<>();
        
        for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
            SemanticGraph dependencies = sentence.get(BasicDependenciesAnnotation.class);
            
            for (SemanticGraphEdge edge : dependencies.edgeListSorted()) {
                IndexedWord governor = edge.getGovernor();
                IndexedWord dependent = edge.getDependent();
                
                // Skip anything that is not a verb, noun, or adjective
                if (isValidWord(governor) && isValidWord(dependent)) {
                    Biterm biterm = new Biterm(governor, dependent, edge.getRelation());
                    biterms.add(biterm);
                }
            }
        }
        
        return biterms;
    }


    public void extendBiterms(Set<String> consensualBiterms) {
        System.out.println("Artifact.extendBiterms() called for: " + this.getClass().getSimpleName() + " with consensual biterms: " + consensualBiterms);
        // TODO: expand via added weight to IR method?
    }

    public String getTextBody() {
        return textBody;
    }

    protected void setTextBody(String textBody) {
        this.textBody = textBody;
    }


    private boolean isValidWord(IndexedWord word) {
        //String pos = word.tag();
        // Check if the POS tag indicates a verb, noun, or adjective
        return isVerb(word.tag()) || isNoun(word.tag()) || isAdjective(word.tag());

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
}