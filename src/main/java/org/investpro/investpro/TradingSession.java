package org.investpro.investpro;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class TradingSession {
    private static final AtomicReference<TradingMode> MODE = new AtomicReference<>(TradingMode.PAPER);

    private TradingSession() {
    }

    public static TradingMode getMode() {
        return MODE.get();
    }

    public static void setMode(TradingMode mode) {
        MODE.set(Objects.requireNonNullElse(mode, TradingMode.PAPER));
    }

    public static boolean isPaperTrading() {
        return getMode().isPaper();
    }

    public static boolean isLiveTrading() {
        return getMode().isLive();
    }
}
