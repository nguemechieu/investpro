package org.investpro.models.currency;

import javax.swing.*;

/**
 * @author NOEL NGUEMECHIEU
 */
public interface MoneyFormatter<T extends Money> {
    String format(T money);



}
