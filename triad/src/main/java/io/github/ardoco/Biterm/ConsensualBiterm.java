/* Licensed under MIT 2025. */
package io.github.ardoco.Biterm;
import java.util.Objects;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.trees.GrammaticalRelation;

public class ConsensualBiterm extends Biterm {
    private String source;
    private String destination;

    public ConsensualBiterm(
            IndexedWord first, IndexedWord second, GrammaticalRelation relation, String source, String destination) {
        super(first, second, relation);
        this.source = source;
        this.destination = destination;
    }

    public ConsensualBiterm(String first, String second, String source, String destination) {
        super(first, second);
        this.source = source;
        this.destination = destination;
    }

    public ConsensualBiterm(Biterm biterm, String source, String destination) {
        super(biterm.getFirstWord(), biterm.getSecondWord(), biterm.getRelation());
        this.source = source;
        this.destination = destination;
    }

    public String getSource() {
        return source;
    }

    public String getDestination() {
        return destination;
    }

    @Override
    public String toString() {
        return super.toString() + " [" + source + " - " + destination + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ConsensualBiterm) {
            ConsensualBiterm other = (ConsensualBiterm) obj;
            return super.equals(other)
                    && this.source.equals(other.source)
                    && this.destination.equals(other.destination);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), source, destination);
    }
}
