package org.investpro.investpro;

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


    @Override
    public String toString() {
        return "SymmetricPair{" +
                "first=" + first +
                ", second=" + second +
                '}';
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


}
