package org.investpro;

/**
 * @author NOEL NGUEMECHIEU
 */
public interface MoneyFormatter<T extends Money> {
    String format(T money);


}
