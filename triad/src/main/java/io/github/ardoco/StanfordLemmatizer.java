package io.github.ardoco;

import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;

public class StanfordLemmatizer {

    private static StanfordCoreNLP pipeline;

    static {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,pos,lemma");
        pipeline = new StanfordCoreNLP(props);
    }

    public static String lemmatize(String text) {
        Annotation document = new Annotation(text);
        pipeline.annotate(document);
        List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);
        return tokens.stream()
                .map(token -> token.get(CoreAnnotations.LemmaAnnotation.class))
                .collect(Collectors.joining(" "));
    }
} 