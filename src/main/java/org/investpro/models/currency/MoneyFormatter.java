package org.investpro.models.currency;

/**
 * @author NOEL NGUEMECHIEU
 */
public interface MoneyFormatter<T extends Money> {
    String format(T money);



}
