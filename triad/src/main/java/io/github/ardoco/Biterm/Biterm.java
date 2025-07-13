/* Licensed under MIT 2025. */
package io.github.ardoco.Biterm;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.trees.GrammaticalRelation;

import io.github.ardoco.StanfordLemmatizer;

public class Biterm {
    private IndexedWord firstTerm;
    private String firstTermString;
    private IndexedWord secondTerm;
    private String secondTermString;
    private GrammaticalRelation relation = null;
    private int weight = 1;

    public Biterm(IndexedWord first, IndexedWord second, GrammaticalRelation relation) {
        this.firstTerm = first;
        this.firstTermString = first.word();
        this.secondTerm = second;
        this.secondTermString = second.word();
        this.relation = relation;
    }

    public Biterm(String first, String second) {
        this.firstTermString = first;
        this.secondTermString = second;
        this.relation = null;
    }

    private boolean hasRelation() {
        return relation != null;
    }

    public IndexedWord getFirstWord() {
        return firstTerm;
    }

    public IndexedWord getSecondWord() {
        return secondTerm;
    }

    public String getFirstTerm() {
        return firstTermString;
    }

    public String getSecondTerm() {
        return secondTermString;
    }

    public GrammaticalRelation getRelation() {
        return relation;
    }

    public boolean isConsensual(Biterm other) {
        return StanfordLemmatizer.lemmatize(this.firstTermString)
                                .equals(StanfordLemmatizer.lemmatize(other.firstTermString))
                        && StanfordLemmatizer.lemmatize(this.secondTermString)
                                .equals(StanfordLemmatizer.lemmatize(other.secondTermString))
                || StanfordLemmatizer.lemmatize(this.firstTermString)
                                .equals(StanfordLemmatizer.lemmatize(other.secondTermString))
                        && StanfordLemmatizer.lemmatize(this.secondTermString)
                                .equals(StanfordLemmatizer.lemmatize(other.firstTermString));
    }

    @Override
    public String toString() {
        if (!hasRelation()) {
            return firstTermString.toLowerCase() + " " + secondTermString.toLowerCase();
        }
        return firstTermString.toLowerCase() + " " + secondTermString.toLowerCase() + ":" + relation.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Biterm) {
            Biterm other = (Biterm) obj;
            return this.firstTermString.equals(other.firstTermString)
                            && this.secondTermString.equals(other.secondTermString)
                    || this.firstTermString.equals(other.secondTermString)
                            && this.secondTermString.equals(other.firstTermString);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return firstTermString.hashCode() + secondTermString.hashCode();
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }
}
