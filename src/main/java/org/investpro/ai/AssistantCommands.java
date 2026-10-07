package org.investpro.ai;

import java.util.Locale;
import java.util.Set;

/** Model requests share the existing command layer; confirmation remains a user action. */
public final class AssistantCommands {
    private AssistantCommands() { }
    public static final String SYNTAX = "/status /balance /portfolio /positions /orders /history /quote SYMBOL "
            + "/watch SYMBOL /unwatch SYMBOL /watchlist /buy SYMBOL QUANTITY /sell SYMBOL QUANTITY "
            + "/limit buy|sell SYMBOL QUANTITY PRICE /stop buy|sell SYMBOL QUANTITY STOP_PRICE "
            + "/trailing buy|sell SYMBOL QUANTITY DISTANCE amount|percent /bracket buy|sell SYMBOL QUANTITY ENTRY STOP_LOSS TAKE_PROFIT "
            + "/cancel ORDER_ID /cancelall /pause /resume /botstart /botstop /mode /exchange /risk /strategy /health /screenshot /abort /help";
    private static final Set<String> ALLOWED = Set.of("status", "balance", "portfolio", "positions", "orders",
            "history", "quote", "market", "watch", "unwatch", "watchlist", "buy", "sell", "limit", "stop",
            "cancel", "cancelall", "trailing", "bracket", "botstart", "botstop", "pause", "resume", "mode", "exchange", "risk", "strategy", "health", "screenshot", "abort", "help", "size");
    public static boolean isModelCommand(String command) {
        if (command == null || !command.startsWith("/") || command.contains("\n") || command.contains("\r")) return false;
        return ALLOWED.contains(command.substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT));
    }
}
