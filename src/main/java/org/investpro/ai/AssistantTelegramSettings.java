package org.investpro.ai;

import org.jspecify.annotations.NonNull;

import java.util.Properties;
import java.util.prefs.Preferences;

/** Reads the same saved Telegram settings as SettingsPanel without exposing credentials. */
final class AssistantTelegramSettings {
    private AssistantTelegramSettings() { }

    static String apply(Properties settings, String fallbackToken, @NonNull Preferences saved) {
        String token = saved.get("TELEGRAM_TOKEN", "").trim();
        if (token.isBlank()) token = fallbackToken;
        String chat = saved.get("TELEGRAM_ALLOWED_CHAT_IDS", "").trim();
        if (!chat.isBlank()) settings.setProperty("TELEGRAM_ALLOWED_CHAT_IDS", chat);
        // An absent preference means legacy environment/onboarding configuration still applies.
        if (!saved.getBoolean("telegramEnabled", true)) token = "";
        return token;
    }
}
