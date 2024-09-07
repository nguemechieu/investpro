package org.investpro;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Container to ease passing around a tuple of two objects. This object provides a sensible
 * implementation of equals(), returning true if equals() is true on each of the contained
 * objects.
 */
public record SymmetricPair<F, S>(F first, S second) {
    /**
     * Constructor for a Pair.
     *
     * @param first  the first object in the Pair
     * @param second the second object in the pair
     */
    public SymmetricPair {
    }

    /**
     * Convenience method for creating an appropriately typed pair.
     *
     * @param a the first object in the Pair
     * @param b the second object in the pair
     * @return a Pair that is templates with the types of a and b
     */
    @Contract(value = "_, _ -> new", pure = true)
    public static <A, B> @NotNull SymmetricPair<A, B> of(A a, B b) {
        return new SymmetricPair<>(a, b);
    }

    /**
     * Checks the two objects for equality by delegating to their respective
     * {@link Object#equals(Object)} methods.
     *
     * @param object the {@link SymmetricPair} to which this one is to be checked for equality
     * @return true if the underlying objects of the Pair are both considered
     * equal
     */
    @Override
    public boolean equals(Object object) {
        if (!(object instanceof SymmetricPair<?, ?> sp)) {
            return false;
        }

        return (Objects.equals(this.first, sp.first) && Objects.equals(this.second, sp.second)) ||
                (Objects.equals(this.second, sp.first) && Objects.equals(this.first, sp.second));

    }

    /**
     * Compute a hash code using the hash codes of the underlying objects
     *
     * @return a hashcode of the Pair
     */
    @Override
    public int hashCode() {
        return (first == null ? 0 : first.hashCode()) ^ (second == null ? 0 : second.hashCode());
    }

    @Override
    public String toString() {
        return STR."SymmetricPair [\{first}, \{second}]";
    }

}
