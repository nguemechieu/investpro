package org.investpro.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.investpro.service.RssNewsService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistantNewsDataTest {
    @Test void newsIsReadOnlyAndProjectsSourcesWithoutInventingPublicationDates() throws Exception {
        var feed = mock(RssNewsService.class);
        when(feed.fetchSymbolNews("SCHW", "stock", 20)).thenReturn(List.of(Map.of(
                "title", "Schwab earnings", "source", "Publisher", "url", "https://example.com/news",
                "timestamp", "2026-10-08T12:00:00Z")));
        var result = new ObjectMapper().readTree(new AssistantNewsData(feed).execute("/news SCHW stock 100"));
        assertTrue(AssistantCommands.isReadOnly("/news SCHW stock 10"));
        assertTrue(result.path("available").asBoolean());
        assertEquals("https://example.com/news", result.path("articles").get(0).path("url").asText());
        assertEquals("", result.path("articles").get(0).path("publicationDate").asText());
        verify(feed).fetchSymbolNews("SCHW", "stock", 20);
    }

    @Test void invalidCommandsNeverFetchAndEmptyResultsAreExplicit() throws Exception {
        var feed = mock(RssNewsService.class);
        var news = new AssistantNewsData(feed);
        assertTrue(news.execute("/news SCHW invalid").contains("Usage"));
        verifyNoInteractions(feed);
        assertFalse(new ObjectMapper().readTree(news.execute("/news SCHW stock")).path("available").asBoolean());
        assertTrue(news.execute("/news SCHW stock").contains("Do not invent"));
    }
}
