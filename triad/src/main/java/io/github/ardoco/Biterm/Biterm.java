package io.github.ardoco.Biterm;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.trees.GrammaticalRelation;

public class Biterm {
    private IndexedWord firstTerm;
    private String firstTermString;
    private IndexedWord secondTerm;
    private String secondTermString;
    private GrammaticalRelation relation = null;

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

    public String getFirstTerm() {
        return firstTermString;
    }

    public String getSecondTerm() {
        return secondTermString;
    }

    public GrammaticalRelation getRelation() {
        return relation;
    }

    @Override
    public String toString() {
        if (!hasRelation()) {
            return firstTermString.toLowerCase() + " " + secondTermString.toLowerCase();
        }
        return firstTermString.toLowerCase() + " " + secondTermString.toLowerCase() + ":" + relation.toString();
    }
}
