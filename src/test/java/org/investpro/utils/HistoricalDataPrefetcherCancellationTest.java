package org.investpro.utils;

import org.investpro.data.CandleData;
import org.investpro.models.trading.TradePair;
import org.investpro.persistence.repository.HistoricalDataRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HistoricalDataPrefetcherCancellationTest {
    @Test
    @SuppressWarnings("unchecked")
    void interruptedFetchCancelsPendingPageAndDoesNotFallBackToEmptyData() throws Exception {
        var repository = mock(HistoricalDataRepository.class);
        var supplier = mock(CandleDataSupplier.class);
        Future<List<CandleData>> page = mock(Future.class);
        when(supplier.get()).thenReturn(page);
        when(page.get()).thenThrow(new InterruptedException("cancelled"));
        var fetcher = new HistoricalDataPrefetcher(repository, (_, _) -> supplier);
        try {
            assertThrows(CancellationException.class, () -> fetcher.fetchAndCacheDataSync(
                    new TradePair("BTC", "USD"), LocalDateTime.now().minusDays(2),
                    LocalDateTime.now(), "1h", null));
            assertTrue(Thread.currentThread().isInterrupted());
            verify(page).cancel(true);
            verifyNoInteractions(repository);
        } finally {
            Thread.interrupted();
        }
    }
}
