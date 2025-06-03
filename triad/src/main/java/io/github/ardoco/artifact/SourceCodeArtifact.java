package io.github.ardoco.artifact;

import static io.github.ardoco.artifact.Artifact.pipeline;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.semgraph.*;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.BasicDependenciesAnnotation;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;
import io.github.ardoco.Biterm.Biterm;

public class SourceCodeArtifact extends Artifact {

    public SourceCodeArtifact(String textBody) {
        super(textBody);
    }

    @Override
    protected void preProcessing() {
        System.out.println("Preprocessing Code Artifact");

    }

    @Override
    public Set<Biterm> getBiterms() {
        System.out.println("Trying to extract identifiers from code artifact");
        
        // match camel case identifiers using regex
        Set<Biterm> identifiers = new HashSet<>();
        Pattern pattern = Pattern.compile("(([A-Z]*[a-z]+)([A-Z]+[a-z]*)+)");
        Matcher matcher = pattern.matcher(textBody);
        // for each group 1 match, take all G2 and G3 matches as terms
        while (matcher.find()) {
            String identifier = matcher.group(1);
            System.out.println("Found identifier: " + identifier);
            int lastWordIndex = 0;
            List<String> terms = new LinkedList<>();
            for (int i = 1; i < identifier.length() - 1; i++) {
                if (Character.isUpperCase(identifier.charAt(i)) && Character.isLowerCase(identifier.charAt(i - 1))) {
                    System.out.println("Found word: " + identifier.substring(lastWordIndex, i));
                    String term = identifier.substring(lastWordIndex, i);
                    terms.add(term);
                    lastWordIndex = i;
                }
            }
            if (lastWordIndex < identifier.length() - 1) {
                String term = identifier.substring(lastWordIndex, identifier.length());
                terms.add(term);
            }

            // debug show all terms
            System.out.println("Terms: " + terms);

            for (int i = 0; i < terms.size() - 1; i++) {
                for (int j = i + 1; j < terms.size(); j++) {
                    identifiers.add(new Biterm(terms.get(i), terms.get(j)));
                }
            }
        }
        return identifiers;
    }
}