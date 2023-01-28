package org.investpro.investpro;

public enum TimeFrame {
    S5	(5),// second candlesticks, minute alignment
    S10	(10),// second candlesticks, minute alignment
    S15	(15),// second candlesticks, minute alignment
    S30	(30),// second candlesticks, minute alignment
    M1	(1),// minute candlesticks, minute alignment
    M2	(2),// minute candlesticks, hour alignment
    M4(	4),// minute candlesticks, hour alignment
    M5(	5),// minute candlesticks, hour alignment
    M10	(10),// minute candlesticks, hour alignment
    M15	(15),// minute candlesticks, hour alignment
    M30	(30),// minute candlesticks, hour alignment
    H1	(1),// hour candlesticks, hour alignment
    H2	(2),// hour candlesticks, day alignment
    H3	(3),// hour candlesticks, day alignment
    H4	(4),// hour candlesticks, day alignment
    H6	(6),// hour candlesticks, day alignment
    H8	(8),// hour candlesticks, day alignment
    H12	(12),// hour candlesticks, day alignment
    D	(1),// day candlesticks, day alignment
    W	(1),// week candlesticks, aligned to start of week
    M	(1), PERIOD_CURRENT(0);// month candlesticks, aligned to first day of the month

    TimeFrame(int i) {
    }

}
