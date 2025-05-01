package org.investpro.investpro;


public interface MoneyFormatter<T extends Money> {
    String format(T money);


}
