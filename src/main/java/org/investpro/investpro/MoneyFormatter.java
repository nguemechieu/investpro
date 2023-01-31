package org.investpro.investpro;

/**
 * @author Michael Ennen
 */
public interface MoneyFormatter<T extends Money> {
    String format(T money);
}
