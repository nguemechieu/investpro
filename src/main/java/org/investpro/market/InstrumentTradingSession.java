package org.investpro.market;

import lombok.Builder;
import lombok.Getter;
import org.investpro.enums.TradingSessionStatus;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.Set;

@Getter
@Builder
public class InstrumentTradingSession {

    private final String instrument;
    private final ZoneId zoneId;

    private final LocalTime weeklyOpenTime;
    private final DayOfWeek weeklyOpenDay;

    private final LocalTime weeklyCloseTime;
    private final DayOfWeek weeklyCloseDay;

    private final LocalTime dailyBreakStart;
    private final LocalTime dailyBreakEnd;

    private final Set<DayOfWeek> tradingDays;

    private final String notes;

    public TradingSessionStatus getStatus(ZonedDateTime now) {
        if (now == null) {
            return TradingSessionStatus.UNKNOWN;
        }

        ZonedDateTime localNow = now.withZoneSameInstant(zoneId);
        DayOfWeek day = localNow.getDayOfWeek();
        LocalTime time = localNow.toLocalTime();

        if (!tradingDays.contains(day)) {
            return TradingSessionStatus.CLOSED;
        }

        if (isBeforeWeeklyOpen(day, time) || isAfterWeeklyClose(day, time)) {
            return TradingSessionStatus.CLOSED;
        }

        if (isInDailyBreak(time)) {
            return TradingSessionStatus.BREAK;
        }

        return TradingSessionStatus.OPEN;
    }

    public boolean isTradableNow(ZonedDateTime now) {
        return getStatus(now).isTradable();
    }

    private boolean isInDailyBreak(LocalTime time) {
        if (dailyBreakStart == null || dailyBreakEnd == null) {
            return false;
        }

        return !time.isBefore(dailyBreakStart) && time.isBefore(dailyBreakEnd);
    }

    private boolean isBeforeWeeklyOpen(DayOfWeek day, LocalTime time) {
        return day == weeklyOpenDay && time.isBefore(weeklyOpenTime);
    }

    private boolean isAfterWeeklyClose(DayOfWeek day, LocalTime time) {
        return day == weeklyCloseDay && !time.isBefore(weeklyCloseTime);
    }
}