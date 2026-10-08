package org.investpro.ai;

import com.sun.net.httpserver.HttpServer;
import org.investpro.service.RssNewsService;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class AssistantNewsFeedTest {
    @Test void stockQueryAndActualFeedDateReachTheAssistant() throws Exception {
        var query = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rss", request -> {
            query.set(URLDecoder.decode(request.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
            byte[] xml = """
                    <rss><channel><item><title>Schwab reports earnings</title>
                    <description>Quarterly results published.</description>
                    <link>https://example.com/schw</link><source>Test Publisher</source>
                    <pubDate>Thu, 08 Oct 2026 12:00:00 GMT</pubDate></item></channel></rss>
                    """.getBytes(StandardCharsets.UTF_8);
            request.sendResponseHeaders(200, xml.length);
            try (var output = request.getResponseBody()) { output.write(xml); }
        });
        server.start();
        try {
            var rss = new RssNewsService(true, "http://127.0.0.1:" + server.getAddress().getPort()
                    + "/rss?q={query}", 2, 300, "InvestProTest");
            String result = new AssistantNewsData(rss).execute("/news SCHW stock 10");
            assertTrue(query.get().contains("Charles Schwab"));
            assertFalse(query.get().contains("crypto"));
            assertTrue(result.contains("Thu, 08 Oct 2026 12:00:00 GMT"));
            assertTrue(result.contains("https://example.com/schw"));
            assertTrue(result.contains("Test Publisher"));
        } finally { server.stop(0); }
    }
}
