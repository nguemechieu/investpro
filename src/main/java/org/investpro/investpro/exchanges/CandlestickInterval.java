package org.investpro.investpro.exchanges;

import lombok.Getter;

@Getter
public enum CandlestickInterval {
    ONE_MINUTE(1),
    FIVE_MINUTES(5),
    FIFTEEN_MINUTES(15),
    THIRTY_MINUTES(30),
    ONE_HOUR(60),
    TWO_HOURS(120),
    FOUR_HOURS(240),
    SIX_HOURS(360),
    EIGHT_HOURS(480),
    TWELVE_HOURS(720),
    DAY(86400),
    WEEK(604800),
    MONTH(2592000);
    private final int seconds;

    CandlestickInterval(int i) {
        this.seconds = i;
    }
}
