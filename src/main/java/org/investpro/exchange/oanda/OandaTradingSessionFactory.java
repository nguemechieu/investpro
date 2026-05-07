package org.investpro.exchange.oanda;

import org.investpro.market.InstrumentTradingSession;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;

public final class OandaTradingSessionFactory {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private OandaTradingSessionFactory() {
    }

    public static InstrumentTradingSession forInstrument(String instrument) {
        if (instrument == null || instrument.isBlank()) {
            return unknown("UNKNOWN");
        }

        String normalized = instrument.trim().toUpperCase();

        // Default OANDA FX/CFD-style session.
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
                        DayOfWeek.FRIDAY
                ))
                .notes("OANDA default FX session: Sun 17:05 to Fri 16:59 New York time, with daily 16:59-17:05 break.")
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
                .notes("Unknown OANDA trading session.")
                .build();
    }
}