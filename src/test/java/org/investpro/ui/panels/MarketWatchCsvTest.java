package org.investpro.ui.panels;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketWatchCsvTest {
    @Test
    void exportPreservesCommasQuotesAndMultilineIssues() {
        assertEquals("\"Rate limit, retry \"\"later\"\"\nPending\"",
                MarketWatchPanel.csvField("Rate limit, retry \"later\"\nPending"));
        assertEquals("\"\"", MarketWatchPanel.csvField(null));
    }
}
