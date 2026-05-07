package org.investpro.market;

import org.investpro.enums.TradingSessionStatus;
import org.investpro.exchange.oanda.OandaTradingSessionFactory;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstrumentTradingSessionTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private final InstrumentTradingSession session = OandaTradingSessionFactory.forInstrument("EUR_USD");

    @Test
    void sundayBeforeWeeklyOpenIsClosed() {
        assertEquals(TradingSessionStatus.CLOSED, status(2026, 5, 3, 17, 4));
    }

    @Test
    void sundayAtWeeklyOpenIsOpen() {
        assertEquals(TradingSessionStatus.OPEN, status(2026, 5, 3, 17, 5));
    }

    @Test
    void mondayAtDailyBreakStartIsBreak() {
        assertEquals(TradingSessionStatus.BREAK, status(2026, 5, 4, 16, 59));
    }

    @Test
    void mondayDuringDailyBreakIsBreak() {
        assertEquals(TradingSessionStatus.BREAK, status(2026, 5, 4, 17, 4));
    }

    @Test
    void mondayAtDailyBreakEndIsOpen() {
        assertEquals(TradingSessionStatus.OPEN, status(2026, 5, 4, 17, 5));
    }

    @Test
    void fridayAtWeeklyCloseBoundaryIsClosed() {
        assertEquals(TradingSessionStatus.CLOSED, status(2026, 5, 8, 16, 59));
    }

    @Test
    void saturdayNoonIsClosed() {
        assertEquals(TradingSessionStatus.CLOSED, status(2026, 5, 9, 12, 0));
    }

    private TradingSessionStatus status(int year, int month, int day, int hour, int minute) {
        return session.getStatus(ZonedDateTime.of(year, month, day, hour, minute, 0, 0, NEW_YORK));
    }
}
