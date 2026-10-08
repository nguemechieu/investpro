package org.investpro.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TelegramScreenshotTest {
    @Test
    void authorizedUserGetsChartInRequestingChatWithoutTradingCore() throws Exception {
        RecordingNotifier notifier = new RecordingNotifier();
        AtomicInteger captures = new AtomicInteger();
        notifier.setScreenshotCapture(chart -> {
            assertTrue(chart);
            captures.incrementAndGet();
            return new byte[]{1, 2, 3};
        });
        try {
            notifier.processUpdates(update(456, "/chart@InvestProBot"));
            assertTrue(notifier.completed.await(5, TimeUnit.SECONDS));
            assertEquals("456", notifier.photoChat);
            assertEquals(1, captures.get());
            assertArrayEquals(new byte[]{1, 2, 3}, notifier.photoBytes);
            assertFalse(Files.exists(notifier.photoFile));
            assertNull(notifier.getCommandHandler());
        } finally { notifier.close(); }
    }

    @Test
    void unauthorizedScreenshotDoesNotCaptureOrSend() throws Exception {
        RecordingNotifier notifier = new RecordingNotifier();
        AtomicInteger captures = new AtomicInteger();
        notifier.setScreenshotCapture(chart -> { captures.incrementAndGet(); return new byte[]{1}; });
        try {
            notifier.processUpdates(update(999, "/screenshot app"));
            assertEquals(0, captures.get());
            assertNull(notifier.photoChat);
        } finally { notifier.close(); }
    }

    private com.fasterxml.jackson.databind.JsonNode update(int user, String command) throws Exception {
        return new ObjectMapper().readTree("""
                {"ok":true,"result":[{"update_id":1,"message":{"from":{"id":%d},
                "chat":{"id":%d,"type":"private"},"text":"%s","date":1}}]}
                """.formatted(user, user, command));
    }

    private static class RecordingNotifier extends TelegramNotifier {
        final CountDownLatch completed = new CountDownLatch(1);
        volatile String photoChat;
        volatile byte[] photoBytes;
        volatile Path photoFile;
        RecordingNotifier() {
            super("");
            Properties settings = new Properties();
            settings.setProperty("TELEGRAM_ALLOWED_USER_IDS", "123,456");
            settings.setProperty("TELEGRAM_CHAT_ID", "123");
            configureRemoteAccess(settings);
        }
        @Override protected boolean sendPhotoToChat(String chat, Path file, String caption) {
            photoChat = chat; photoFile = file;
            try { photoBytes = Files.readAllBytes(file); } catch (Exception error) { throw new AssertionError(error); }
            return true;
        }
        @Override protected void sendMessageToChat(String chat, String text) { completed.countDown(); }
    }
}
