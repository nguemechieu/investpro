package org.investpro;


public interface MoneyFormatter<T extends Money> {
    String format(T money);




}
