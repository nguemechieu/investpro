package org.investpro.exchange.ibkr;

import org.investpro.market.InstrumentTradingSession;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;

/**
 * IBKR session metadata aligned with the broker session behavior used by OANDA.
 *
 * <p>
 * This keeps IBKR instruments from falling back to UNKNOWN session status in
 * the UI.
 * </p>
 */
public final class IbkrTradingSessionFactory {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private IbkrTradingSessionFactory() {
    }

    public static InstrumentTradingSession forInstrument(String instrument) {
        if (instrument == null || instrument.isBlank()) {
            return unknown("UNKNOWN");
        }

        String normalized = instrument.trim().toUpperCase();

        return InstrumentTradingSession.builder()
                .instrument(normalized)
                .zoneId(NEW_YORK)
                .weeklyOpenDay(DayOfWeek.SUNDAY)
                .weeklyOpenTime(LocalTime.of(17, 5))
                .weeklyCloseDay(DayOfWeek.FRIDAY)
                .weeklyCloseTime(LocalTime.of(16, 59))
                .dailyBreakStart(LocalTime.of(16, 59))
                .dailyBreakEnd(LocalTime.of(17, 5))
                .tradingDays(EnumSet.of(
                        DayOfWeek.SUNDAY,
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY))
                .notes("IBKR session aligned with OANDA-style New York trading hours for consistent UI session status.")
                .build();
    }

    private static InstrumentTradingSession unknown(String instrument) {
        return InstrumentTradingSession.builder()
                .instrument(instrument)
                .zoneId(NEW_YORK)
                .weeklyOpenDay(DayOfWeek.MONDAY)
                .weeklyOpenTime(LocalTime.MIDNIGHT)
                .weeklyCloseDay(DayOfWeek.FRIDAY)
                .weeklyCloseTime(LocalTime.MAX)
                .dailyBreakStart(null)
                .dailyBreakEnd(null)
                .tradingDays(EnumSet.noneOf(DayOfWeek.class))
                .notes("Unknown IBKR trading session.")
                .build();
    }
}