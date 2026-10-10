package org.investpro.exchange.ibkr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.MARKET_TYPES;
import org.investpro.utils.ORDER_TYPES;
import java.nio.file.Path;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class IbkrInstrumentSupportTest {
    @TempDir Path directory;
    private IbkrResolvedContract contract(long id, String type) {
        return new IbkrResolvedContract(id,"AAPL","AAPL "+id,type,"USD","SMART","","","100","20261218",200.0,"C",null,null,"","","","","TEST",Instant.now(),Instant.now(),"");
    }
    @Test void securityCodesRemainDistinct() {
        for (String code : new String[]{"STK","CASH","FUT","OPT","FOP","CONTFUT","BOND","FUND","WAR","CMDTY","CRYPTO","BAG","IND","CFD"}) {
            assertEquals(code, IbkrSecurityType.fromIbkrCode(code).ibkrCode());
        }
    }
    @Test void orderTypeAndSymbolLengthCannotInventOptionsOrForex() throws Exception {
        var mapper = new IbkrContractMapper();
        assertEquals("STK", mapper.toContract(new TradePair("IBM","USD"), ORDER_TYPES.STOP_LIMIT).secType());
        assertEquals("STK", mapper.toContract(new TradePair("LONGSYMBOL","USD")).secType());
        assertThrows(IllegalArgumentException.class, () -> mapper.toContract(new TradePair("ES","USD"), MARKET_TYPES.FUTURES));
    }
    @Test void ambiguousUnderlyingCannotSelectArbitraryOption() throws Exception {
        var repository = new IbkrContractRepository(directory.resolve("contracts.json"));
        repository.save(contract(123,"OPT")); repository.save(contract(456,"OPT"));
        var pair = new TradePair("AAPL","USD"); assertTrue(repository.findByTradePair(pair).isEmpty());
        assertTrue(repository.findByDisplaySymbol("AAPL").isEmpty());
        pair.setNativeSymbol("456"); assertEquals(456, repository.findByTradePair(pair).orElseThrow().conId());
    }
    @Test void numericSearchPreservesExactContractIdentity() {
        var connection = org.mockito.Mockito.mock(IbkrConnectionManager.class);
        org.mockito.Mockito.when(connection.isConnected()).thenReturn(true);
        org.mockito.Mockito.when(connection.getTwsSession()).thenReturn(new StubIbkrTwsSession());
        var search = new IbkrTwsContractSearchService(connection);
        var candidate = search.search("123456", java.time.Duration.ofSeconds(5)).join().getFirst();
        assertEquals(123456L, candidate.conId());
        assertEquals("", candidate.secType());
        assertEquals("", candidate.currency());
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> search.search("0", java.time.Duration.ofSeconds(5)).join());
    }}
