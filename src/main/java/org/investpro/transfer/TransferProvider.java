package org.investpro.transfer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface TransferProvider {

    Map<String, BigDecimal> getBalances();

    List<String> getSupportedCurrencies();

    BigDecimal estimateFee(TransferRequest request);

    TransferValidator.ValidationOutcome validateTransfer(TransferRequest request);

    TransferResult executeTransfer(TransferRequest request);

    TransferStatus getTransferStatus(String transactionId);
}
