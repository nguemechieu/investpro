package org.investpro.data;

import org.investpro.exchange.ibkr.IbkrMarketDataException;
import org.investpro.ui.charts.CandleStickChart;
import org.investpro.utils.CandleDataSupplier;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CandleDataPagerSubscriptionTest {
    @Test void subscriptionRejectionReachesTheChartInsteadOfBeingSilentlySwallowed() {
        var chart = mock(CandleStickChart.class);
        var supplier = mock(CandleDataSupplier.class);
        var pager = new CandleDataPager(chart, supplier);
        var reason = new IbkrMarketDataException(162, "No market data permissions for NASDAQ STK");
        var request = CompletableFuture.<List<CandleData>>failedFuture(reason);
        var failure = assertThrows(CompletionException.class,
                () -> pager.getCandleDataPreProcessor().accept(request));
        assertSame(reason, failure.getCause());
        verifyNoInteractions(chart);
    }
}
