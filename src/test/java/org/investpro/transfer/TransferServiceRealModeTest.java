package org.investpro.transfer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferServiceRealModeTest {

    @Test
    void defaultServiceDoesNotRegisterSimulatedProviders() {
        TransferService service = new TransferService();

        assertTrue(service.providerNames().isEmpty());
    }
}
