package org.investpro.exchange.ibkr;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IbkrProtocolRoutingTest {
    @Test void twsSearchNeverProbesClientPortalAuthentication() {
        IbkrConnectionManager manager = new IbkrConnectionManager(new StubIbkrTwsSession(), "");
        var tws = mock(IbkrContractSearchService.class);
        var portal = mock(IbkrContractSearchService.class);
        var client = mock(IbkrClientPortalClient.class);
        var result = CompletableFuture.<List<IbkrContractCandidate>>completedFuture(List.of());
        when(tws.search("AAPL", Duration.ofSeconds(5))).thenReturn(result);
        try {
            assertSame(result, new IbkrAdaptiveContractSearchService(manager, tws, portal, client)
                    .search("AAPL", Duration.ofSeconds(5)));
            verifyNoInteractions(portal, client);
        } finally { manager.shutdown(); }
    }
    @Test void twsContractDetailsNeverSwitchToClientPortal() {
        IbkrConnectionManager manager = new IbkrConnectionManager(new StubIbkrTwsSession(), "");
        var tws = mock(IbkrContractDetailsService.class);
        var portal = mock(IbkrContractDetailsService.class);
        var client = mock(IbkrClientPortalClient.class);
        var result = CompletableFuture.<IbkrResolvedContract>completedFuture(null);
        when(tws.requestDetails(null, Duration.ofSeconds(5))).thenReturn(result);
        try {
            assertSame(result, new IbkrAdaptiveContractDetailsService(manager, tws, portal, client)
                    .requestDetails(null, Duration.ofSeconds(5)));
            verifyNoInteractions(portal, client);
        } finally { manager.shutdown(); }
    }
}
