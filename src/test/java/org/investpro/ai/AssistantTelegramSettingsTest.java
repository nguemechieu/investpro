package org.investpro.ai;

import org.junit.jupiter.api.Test;
import java.util.Properties;
import java.util.prefs.Preferences;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistantTelegramSettingsTest {
    private static Preferences saved(String token, String chat, boolean enabled) {
        var prefs = mock(Preferences.class);
        when(prefs.get("telegramBotToken", "")).thenReturn(token);
        when(prefs.get("telegramChatId", "")).thenReturn(chat);
        when(prefs.getBoolean("telegramEnabled", true)).thenReturn(enabled);
        return prefs;
    }

    @Test void savedSettingsConfigureTheSharedAssistantAndOverrideFallbacks() {
        var settings = new Properties();
        settings.setProperty("TELEGRAM_CHAT_ID", "99");
        settings.setProperty("TELEGRAM_ALLOWED_USER_IDS", "123");
        var token = AssistantTelegramSettings.apply(settings, "old-token", saved(" saved-token ", " 123 ", true));
        try (var runtime = new AssistantRuntime(settings, token, "")) {
            assertEquals("saved-token", runtime.notifier().getBotToken());
            assertEquals("123", runtime.notifier().getPreferredChatId());
            assertTrue(runtime.notifier().isEnabled());
            assertEquals(java.util.Set.of("123"), runtime.notifier().getAllowedUsers());
        }
    }

    @Test void missingSavedCredentialsPreserveEnvironmentAndOnboardingConfiguration() {
        var settings = new Properties(); settings.setProperty("TELEGRAM_CHAT_ID", "99");
        assertEquals("environment-token", AssistantTelegramSettings.apply(settings, "environment-token", saved("", "", true)));
        assertEquals("99", settings.getProperty("TELEGRAM_CHAT_ID"));
        assertNull(settings.getProperty("telegram.chat_id"));
    }

    @Test void disabledSettingDoesNotStartTelegramEvenWithEnvironmentCredentials() {
        assertEquals("", AssistantTelegramSettings.apply(new Properties(), "environment-token", saved("saved-token", "123", false)));
    }
}
