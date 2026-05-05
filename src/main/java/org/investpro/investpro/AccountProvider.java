package org.investpro.investpro;

import org.investpro.investpro.models.Account;
import org.investpro.investpro.models.Fee;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public interface AccountProvider {
    List<org.investpro.investpro.models.Account> getAccounts() throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException;

    List<Account> getAccountSummary();

    List<Fee> getTradingFee() throws IOException, InterruptedException;
}
