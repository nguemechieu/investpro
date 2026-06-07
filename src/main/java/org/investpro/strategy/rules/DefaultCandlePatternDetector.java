package org.investpro.strategy.rules;

import org.investpro.data.CandleData;

import java.util.ArrayList;
import java.util.List;

public class DefaultCandlePatternDetector implements CandlePatternDetector {

    @Override
    public List<CandlePatternSignal> detect(List<CandleData> candles, CandlePattern pattern) {
        if (candles == null || candles.isEmpty() || pattern == null) {
            return List.of();
        }

        return switch (pattern) {
            case HAMMER -> detectHammerLike(candles, pattern, true);
            case HANGING_MAN -> detectHammerLike(candles, pattern, false);
            case SHOOTING_STAR, INVERTED_HAMMER -> detectInvertedHammerLike(candles, pattern);
            case DRAGONFLY_DOJI, TAKURI_LINE -> detectDragonflyDoji(candles, pattern);
            case GRAVESTONE_DOJI -> detectGravestoneDoji(candles, pattern);
            case ENGULFING_BULLISH -> detectEngulfing(candles, pattern, true);
            case ENGULFING_BEARISH -> detectEngulfing(candles, pattern, false);
            case MORNING_STAR, MORNING_DOJI_STAR -> detectMorningStar(candles, pattern);
            case EVENING_STAR, EVENING_DOJI_STAR -> detectEveningStar(candles, pattern);
            case THREE_BLACK_CROWS, IDENTICAL_THREE_CROWS -> detectThreeBlackCrows(candles, pattern);
            case THREE_ADVANCING_WHITE_SOLDIERS -> detectThreeAdvancingWhiteSoldiers(candles, pattern);
            case MARUBOZU_BULLISH, MARUBOZU_BEARISH -> detectMarubozu(candles, pattern);
            case CLOSING_MARUBOZU_BULLISH, CLOSING_MARUBOZU_BEARISH -> detectClosingMarubozu(candles, pattern);
            case BELT_HOLD_BULLISH, BELT_HOLD_BEARISH -> detectBeltHold(candles, pattern);
            case LONG_LINE_BULLISH, LONG_LINE_BEARISH -> detectLongLine(candles, pattern);
            case SHORT_LINE_BULLISH, SHORT_LINE_BEARISH -> detectShortLine(candles, pattern);
            case SPINNING_TOP_BULLISH, SPINNING_TOP_BEARISH -> detectSpinningTop(candles, pattern);
            case HIGH_WAVE_BULLISH, HIGH_WAVE_BEARISH, RICKSHAW_MAN -> detectHighWave(candles, pattern);
            case HARAMI_BULLISH, HARAMI_BEARISH -> detectHarami(candles, pattern, false);
            case HARAMI_CROSS_BULLISH, HARAMI_CROSS_BEARISH -> detectHarami(candles, pattern, true);
            case DOJI_STAR_BULLISH, DOJI_STAR_BEARISH -> detectDojiStar(candles, pattern);
            case PIERCING -> detectPiercing(candles, pattern);
            case DARK_CLOUD_COVER -> detectDarkCloudCover(candles, pattern);
            case THREE_INSIDE_UP_DOWN_BULLISH, THREE_INSIDE_UP_DOWN_BEARISH -> detectThreeInside(candles, pattern);
            case THREE_OUTSIDE_UP_DOWN_BULLISH, THREE_OUTSIDE_UP_DOWN_BEARISH -> detectThreeOutside(candles, pattern);
            case COUNTERATTACK_BULLISH, COUNTERATTACK_BEARISH -> detectCounterattack(candles, pattern);
            case KICKING_BULLISH, KICKING_BEARISH, KICKING_BY_LENGTH_BULLISH, KICKING_BY_LENGTH_BEARISH -> detectKicking(candles, pattern);
            case SEPARATING_LINES_BULLISH, SEPARATING_LINES_BEARISH -> detectSeparatingLines(candles, pattern);
            case DOWNSIDE_TASUKI_GAP, UPSIDE_TASUKI_GAP -> detectTasukiGap(candles, pattern);
            case DOWNSIDE_GAP_THREE_METHODS_BULLISH, UPSIDE_GAP_THREE_METHODS_BEARISH -> detectGapThreeMethods(candles, pattern);
            case UPSIDE_GAP_TWO_CROWS, TWO_CROWS -> detectTwoCrows(candles, pattern);
            case THREE_LINE_STRIKE_BULLISH, THREE_LINE_STRIKE_BEARISH -> detectThreeLineStrike(candles, pattern);
            case RISING_THREE_METHODS, FALLING_THREE_METHODS -> detectRisingFallingThreeMethods(candles, pattern);
            case MAT_HOLD_BULLISH, MAT_HOLD_BEARISH -> detectMatHold(candles, pattern);
            case HIKKAKE_BULLISH, HIKKAKE_BEARISH, MODIFIED_HIKKAKE_BULLISH, MODIFIED_HIKKAKE_BEARISH -> detectHikkake(candles, pattern);
            case LADDER_BOTTOM, LADDER_TOP -> detectLadder(candles, pattern);
            case ADVANCE_BLOCK, STALLED_PATTERN_BEARISH, STALLED_PATTERN_BULLISH -> detectAdvanceBlockOrStall(candles, pattern);
            case ABANDONED_BABY_BULLISH, ABANDONED_BABY_BEARISH, BREAKAWAY_BULLISH, BREAKAWAY_BEARISH -> detectGapReversal(candles, pattern);
            case HOMING_PIGEON_BULLISH, HOMING_PIGEON_BEARISH -> detectHomingPigeon(candles, pattern);
            case MATCHING_LOW -> detectMatchingLow(candles, pattern);
            case CONCEALING_BABY_SWALLOW -> detectConcealingBabySwallow(candles, pattern);
            case IN_NECK, ON_NECK -> detectNeckLine(candles, pattern);
            case THREE_STARS_IN_THE_SOUTH, UNIQUE_3_RIVER -> detectBullishCompressionReversal(candles, pattern);
            case STICK_SANDWICH_BULLISH, STICK_SANDWICH_BEARISH -> detectStickSandwich(candles, pattern);
            case TRI_STAR_BULLISH, TRI_STAR_BEARISH -> detectTriStar(candles, pattern);
            case UP_DOWN_GAP_SIDE_BY_SIDE_WHITE_LINES_BULLISH, UP_DOWN_GAP_SIDE_BY_SIDE_WHITE_LINES_BEARISH -> detectSideBySideWhiteLines(candles, pattern);
            default -> detectDirectionalFallback(candles, pattern);
        };
    }

    private List<CandlePatternSignal> detectHammerLike(List<CandleData> candles, CandlePattern pattern, boolean preferDowntrend) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            CandleData c = candles.get(i);
            double r = range(c);
            double b = body(c);
            if (r > 0 && lowerShadow(c) >= Math.max(b * 2.0, r * 0.45) && upperShadow(c) <= r * 0.20
                    && (!preferDowntrend || downtrend(candles, i))) {
                signals.add(signal(pattern, i, 0.64, "Hammer-style candle with long lower shadow."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectInvertedHammerLike(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            CandleData c = candles.get(i);
            double r = range(c);
            double b = body(c);
            if (r > 0 && upperShadow(c) >= Math.max(b * 2.0, r * 0.45) && lowerShadow(c) <= r * 0.20) {
                signals.add(signal(pattern, i, 0.62, "Inverted hammer / shooting-star candle structure."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectEngulfing(List<CandleData> candles, CandlePattern pattern, boolean bullish) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            CandleData p = candles.get(i - 1);
            CandleData c = candles.get(i);
            boolean match = bullish
                    ? isBearish(p) && isBullish(c) && c.openPrice() <= p.closePrice() && c.closePrice() >= p.openPrice()
                    : isBullish(p) && isBearish(c) && c.openPrice() >= p.closePrice() && c.closePrice() <= p.openPrice();
            if (match) {
                signals.add(signal(pattern, i, 0.72, "Current real body engulfs previous opposite body."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectMorningStar(List<CandleData> candles, CandlePattern pattern) {
        return detectStar(candles, pattern, true);
    }

    private List<CandlePatternSignal> detectEveningStar(List<CandleData> candles, CandlePattern pattern) {
        return detectStar(candles, pattern, false);
    }

    private List<CandlePatternSignal> detectStar(List<CandleData> candles, CandlePattern pattern, boolean bullish) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 2; i < candles.size(); i++) {
            CandleData a = candles.get(i - 2);
            CandleData b = candles.get(i - 1);
            CandleData c = candles.get(i);
            boolean middleStar = isSmallBody(b) || isDoji(b);
            boolean match = bullish
                    ? isBearish(a) && middleStar && isBullish(c) && c.closePrice() > midpoint(a)
                    : isBullish(a) && middleStar && isBearish(c) && c.closePrice() < midpoint(a);
            if (match) {
                signals.add(signal(pattern, i, 0.68, "Three-candle star reversal."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectDragonflyDoji(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            CandleData c = candles.get(i);
            double r = range(c);
            if (r > 0 && body(c) / r <= 0.10 && upperShadow(c) / r <= 0.15 && lowerShadow(c) / r >= 0.60) {
                signals.add(signal(pattern, i, 0.61, "Doji near high with long lower shadow."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectGravestoneDoji(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            CandleData c = candles.get(i);
            double r = range(c);
            if (r > 0 && body(c) / r <= 0.10 && lowerShadow(c) / r <= 0.15 && upperShadow(c) / r >= 0.60) {
                signals.add(signal(pattern, i, 0.61, "Doji near low with long upper shadow."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectThreeBlackCrows(List<CandleData> candles, CandlePattern pattern) {
        return detectThreeSameDirection(candles, pattern, false);
    }

    private List<CandlePatternSignal> detectThreeAdvancingWhiteSoldiers(List<CandleData> candles, CandlePattern pattern) {
        return detectThreeSameDirection(candles, pattern, true);
    }

    private List<CandlePatternSignal> detectThreeSameDirection(List<CandleData> candles, CandlePattern pattern, boolean bullish) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 2; i < candles.size(); i++) {
            CandleData a = candles.get(i - 2);
            CandleData b = candles.get(i - 1);
            CandleData c = candles.get(i);
            boolean same = bullish ? isBullish(a) && isBullish(b) && isBullish(c) : isBearish(a) && isBearish(b) && isBearish(c);
            boolean progress = bullish ? b.closePrice() > a.closePrice() && c.closePrice() > b.closePrice()
                    : b.closePrice() < a.closePrice() && c.closePrice() < b.closePrice();
            if (same && progress) {
                signals.add(signal(pattern, i, 0.70, "Three consecutive candles closing progressively in pattern direction."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectMarubozu(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            CandleData c = candles.get(i);
            if (directionMatches(c, pattern) && body(c) >= range(c) * 0.85
                    && upperShadow(c) <= range(c) * 0.08 && lowerShadow(c) <= range(c) * 0.08) {
                signals.add(signal(pattern, i, 0.66, "Full-bodied marubozu candle with minimal shadows."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectClosingMarubozu(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            CandleData c = candles.get(i);
            if (directionMatches(c, pattern) && body(c) >= range(c) * 0.70
                    && closeNearExtreme(c, pattern.getDefaultSignal() == SignalType.BUY)) {
                signals.add(signal(pattern, i, 0.64, "Strong body closes near directional extreme."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectBeltHold(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            CandleData c = candles.get(i);
            if (directionMatches(c, pattern) && isLongBody(c, candles, i)
                    && (pattern.getDefaultSignal() == SignalType.BUY ? openNearLow(c) : openNearHigh(c))) {
                signals.add(signal(pattern, i, 0.63, "Belt-hold candle opens near one extreme and closes strongly."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectLongLine(List<CandleData> candles, CandlePattern pattern) {
        return detectBodyLength(candles, pattern, true);
    }

    private List<CandlePatternSignal> detectShortLine(List<CandleData> candles, CandlePattern pattern) {
        return detectBodyLength(candles, pattern, false);
    }

    private List<CandlePatternSignal> detectBodyLength(List<CandleData> candles, CandlePattern pattern, boolean longBody) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            CandleData c = candles.get(i);
            boolean lengthMatch = longBody ? isLongBody(c, candles, i) : isSmallBody(c);
            if (directionMatches(c, pattern) && lengthMatch) {
                signals.add(signal(pattern, i, longBody ? 0.60 : 0.55, longBody ? "Long directional real body." : "Short directional real body."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectSpinningTop(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            CandleData c = candles.get(i);
            if (directionMatches(c, pattern) && isSmallBody(c) && upperShadow(c) > body(c) && lowerShadow(c) > body(c)) {
                signals.add(signal(pattern, i, 0.54, "Small body with upper and lower shadows."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectHighWave(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            CandleData c = candles.get(i);
            double r = range(c);
            if (r > 0 && body(c) / r <= 0.25 && upperShadow(c) / r >= 0.30 && lowerShadow(c) / r >= 0.30) {
                signals.add(signal(pattern, i, 0.53, "High-wave indecision candle with long shadows."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectHarami(List<CandleData> candles, CandlePattern pattern, boolean cross) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            CandleData p = candles.get(i - 1);
            CandleData c = candles.get(i);
            boolean inside = bodyHigh(c) < bodyHigh(p) && bodyLow(c) > bodyLow(p);
            boolean reversal = pattern.getDefaultSignal() == SignalType.BUY ? isBearish(p) : isBullish(p);
            boolean second = cross ? isDoji(c) : isSmallBody(c);
            if (inside && reversal && second) {
                signals.add(signal(pattern, i, cross ? 0.62 : 0.59, "Small inside body after opposite directional candle."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectDojiStar(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            CandleData p = candles.get(i - 1);
            CandleData c = candles.get(i);
            boolean prior = pattern.getDefaultSignal() == SignalType.BUY ? isBearish(p) : isBullish(p);
            if (prior && isDoji(c) && Math.abs(c.openPrice() - p.closePrice()) > avgBody(candles, i, 10) * 0.25) {
                signals.add(signal(pattern, i, 0.60, "Doji star after directional candle."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectPiercing(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            CandleData p = candles.get(i - 1);
            CandleData c = candles.get(i);
            if (isBearish(p) && isBullish(c) && c.openPrice() < p.closePrice()
                    && c.closePrice() > midpoint(p) && c.closePrice() < p.openPrice()) {
                signals.add(signal(pattern, i, 0.66, "Bullish candle pierces above prior bearish midpoint."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectDarkCloudCover(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            CandleData p = candles.get(i - 1);
            CandleData c = candles.get(i);
            if (isBullish(p) && isBearish(c) && c.openPrice() > p.closePrice()
                    && c.closePrice() < midpoint(p) && c.closePrice() > p.openPrice()) {
                signals.add(signal(pattern, i, 0.66, "Bearish candle closes below prior bullish midpoint."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectThreeInside(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 2; i < candles.size(); i++) {
            CandleData a = candles.get(i - 2);
            CandleData b = candles.get(i - 1);
            CandleData c = candles.get(i);
            boolean harami = bodyHigh(b) < bodyHigh(a) && bodyLow(b) > bodyLow(a);
            boolean match = pattern.getDefaultSignal() == SignalType.BUY
                    ? isBearish(a) && harami && isBullish(c) && c.closePrice() > a.openPrice()
                    : isBullish(a) && harami && isBearish(c) && c.closePrice() < a.openPrice();
            if (match) {
                signals.add(signal(pattern, i, 0.65, "Harami followed by directional confirmation candle."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectThreeOutside(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        boolean bullish = pattern.getDefaultSignal() == SignalType.BUY;
        for (int i = 2; i < candles.size(); i++) {
            CandleData a = candles.get(i - 2);
            CandleData b = candles.get(i - 1);
            CandleData c = candles.get(i);
            boolean engulf = bullish
                    ? isBearish(a) && isBullish(b) && b.closePrice() >= a.openPrice()
                    : isBullish(a) && isBearish(b) && b.closePrice() <= a.openPrice();
            boolean confirm = bullish ? isBullish(c) && c.closePrice() > b.closePrice() : isBearish(c) && c.closePrice() < b.closePrice();
            if (engulf && confirm) {
                signals.add(signal(pattern, i, 0.66, "Engulfing pattern followed by directional confirmation."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectCounterattack(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            CandleData p = candles.get(i - 1);
            CandleData c = candles.get(i);
            boolean opposite = pattern.getDefaultSignal() == SignalType.BUY ? isBearish(p) && isBullish(c) : isBullish(p) && isBearish(c);
            if (opposite && nearlyEqual(c.closePrice(), p.closePrice(), avgBody(candles, i, 10) * 0.25)) {
                signals.add(signal(pattern, i, 0.58, "Opposite candle counterattacks to close near prior close."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectKicking(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            CandleData p = candles.get(i - 1);
            CandleData c = candles.get(i);
            boolean match = pattern.getDefaultSignal() == SignalType.BUY
                    ? isBearishMarubozu(p) && isBullishMarubozu(c) && gapUp(p, c)
                    : isBullishMarubozu(p) && isBearishMarubozu(c) && gapDown(p, c);
            if (match) {
                signals.add(signal(pattern, i, 0.72, "Opposite marubozu candles separated by a gap."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectSeparatingLines(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            CandleData p = candles.get(i - 1);
            CandleData c = candles.get(i);
            boolean match = pattern.getDefaultSignal() == SignalType.BUY
                    ? isBearish(p) && isBullish(c)
                    : isBullish(p) && isBearish(c);
            if (match && nearlyEqual(c.openPrice(), p.openPrice(), avgBody(candles, i, 10) * 0.25)) {
                signals.add(signal(pattern, i, 0.58, "Opposite candles open near the same price and separate directionally."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectTasukiGap(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        boolean bullish = pattern.getDefaultSignal() == SignalType.BUY;
        for (int i = 2; i < candles.size(); i++) {
            CandleData a = candles.get(i - 2);
            CandleData b = candles.get(i - 1);
            CandleData c = candles.get(i);
            boolean gap = bullish ? gapUp(a, b) : gapDown(a, b);
            boolean retrace = bullish ? isBearish(c) && c.closePrice() > a.highPrice() : isBullish(c) && c.closePrice() < a.lowPrice();
            if (gap && retrace) {
                signals.add(signal(pattern, i, 0.60, "Gap continuation followed by partial retracement."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectGapThreeMethods(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        boolean bullish = pattern.getDefaultSignal() == SignalType.BUY;
        for (int i = 2; i < candles.size(); i++) {
            CandleData a = candles.get(i - 2);
            CandleData b = candles.get(i - 1);
            CandleData c = candles.get(i);
            boolean gap = bullish ? gapDown(a, b) : gapUp(a, b);
            boolean fillAttempt = bullish ? isBullish(c) && c.closePrice() > b.openPrice() : isBearish(c) && c.closePrice() < b.openPrice();
            if (gap && fillAttempt) {
                signals.add(signal(pattern, i, 0.59, "Gap sequence followed by directional fill attempt."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectTwoCrows(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 2; i < candles.size(); i++) {
            CandleData a = candles.get(i - 2);
            CandleData b = candles.get(i - 1);
            CandleData c = candles.get(i);
            if (isBullish(a) && isBearish(b) && isBearish(c) && b.openPrice() > a.closePrice()
                    && c.closePrice() < b.closePrice()) {
                signals.add(signal(pattern, i, 0.64, "Two bearish crows after bullish candle."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectThreeLineStrike(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        boolean bullish = pattern.getDefaultSignal() == SignalType.BUY;
        for (int i = 3; i < candles.size(); i++) {
            CandleData a = candles.get(i - 3);
            CandleData b = candles.get(i - 2);
            CandleData c = candles.get(i - 1);
            CandleData d = candles.get(i);
            boolean three = bullish
                    ? isBearish(a) && isBearish(b) && isBearish(c) && b.closePrice() < a.closePrice() && c.closePrice() < b.closePrice()
                    : isBullish(a) && isBullish(b) && isBullish(c) && b.closePrice() > a.closePrice() && c.closePrice() > b.closePrice();
            boolean strike = bullish ? isBullish(d) && d.closePrice() > a.openPrice() : isBearish(d) && d.closePrice() < a.openPrice();
            if (three && strike) {
                signals.add(signal(pattern, i, 0.65, "Three-line sequence reversed by strong strike candle."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectRisingFallingThreeMethods(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        boolean bullish = pattern.getDefaultSignal() == SignalType.BUY;
        for (int i = 4; i < candles.size(); i++) {
            CandleData first = candles.get(i - 4);
            CandleData last = candles.get(i);
            boolean bookends = bullish ? isBullish(first) && isBullish(last) && last.closePrice() > first.closePrice()
                    : isBearish(first) && isBearish(last) && last.closePrice() < first.closePrice();
            boolean inside = true;
            for (int j = i - 3; j <= i - 1; j++) {
                CandleData m = candles.get(j);
                inside &= m.highPrice() <= first.highPrice() && m.lowPrice() >= first.lowPrice();
            }
            if (bookends && inside) {
                signals.add(signal(pattern, i, 0.63, "Continuation with small contained pause candles."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectMatHold(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> base = detectRisingFallingThreeMethods(candles, pattern);
        return base.stream()
                .map(s -> signal(pattern, s.candleIndex(), Math.min(0.68, s.confidence() + 0.03), "Mat-hold continuation structure."))
                .toList();
    }

    private List<CandlePatternSignal> detectHikkake(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        boolean bullish = pattern.getDefaultSignal() == SignalType.BUY;
        for (int i = 3; i < candles.size(); i++) {
            CandleData inside = candles.get(i - 2);
            CandleData breakout = candles.get(i - 1);
            CandleData confirm = candles.get(i);
            CandleData mother = candles.get(i - 3);
            boolean insideBar = inside.highPrice() < mother.highPrice() && inside.lowPrice() > mother.lowPrice();
            boolean falseBreak = bullish ? breakout.lowPrice() < inside.lowPrice() : breakout.highPrice() > inside.highPrice();
            boolean reversal = bullish ? confirm.closePrice() > inside.highPrice() : confirm.closePrice() < inside.lowPrice();
            if (insideBar && falseBreak && reversal) {
                signals.add(signal(pattern, i, 0.62, "Inside bar false breakout followed by reversal confirmation."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectLadder(List<CandleData> candles, CandlePattern pattern) {
        return detectDirectionalFallback(candles, pattern).stream()
                .filter(s -> pattern.getDefaultSignal() == SignalType.BUY ? downtrend(candles, s.candleIndex()) : uptrend(candles, s.candleIndex()))
                .toList();
    }

    private List<CandlePatternSignal> detectAdvanceBlockOrStall(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 2; i < candles.size(); i++) {
            CandleData a = candles.get(i - 2);
            CandleData b = candles.get(i - 1);
            CandleData c = candles.get(i);
            if (isBullish(a) && isBullish(b) && isBullish(c)
                    && body(c) < body(b) && body(b) < body(a)
                    && upperShadow(c) > body(c)) {
                signals.add(signal(pattern, i, 0.60, "Bullish advance is stalling with shrinking bodies."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectGapReversal(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        boolean bullish = pattern.getDefaultSignal() == SignalType.BUY;
        for (int i = 2; i < candles.size(); i++) {
            CandleData a = candles.get(i - 2);
            CandleData b = candles.get(i - 1);
            CandleData c = candles.get(i);
            boolean exhaustionGap = bullish ? gapDown(a, b) : gapUp(a, b);
            boolean reverse = bullish ? isBullish(c) && c.closePrice() > midpoint(a) : isBearish(c) && c.closePrice() < midpoint(a);
            if (exhaustionGap && (isDoji(b) || isSmallBody(b)) && reverse) {
                signals.add(signal(pattern, i, 0.66, "Gap exhaustion followed by reversal candle."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectHomingPigeon(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            CandleData a = candles.get(i - 1);
            CandleData b = candles.get(i);
            if (isBearish(a) && isBearish(b) && bodyHigh(b) < bodyHigh(a) && bodyLow(b) > bodyLow(a)) {
                signals.add(signal(pattern, i, 0.58, "Bearish body contained within previous bearish body."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectMatchingLow(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            CandleData a = candles.get(i - 1);
            CandleData b = candles.get(i);
            if (isBearish(a) && isBearish(b) && nearlyEqual(a.closePrice(), b.closePrice(), avgBody(candles, i, 10) * 0.20)) {
                signals.add(signal(pattern, i, 0.58, "Two bearish candles close at matching low."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectConcealingBabySwallow(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 3; i < candles.size(); i++) {
            CandleData a = candles.get(i - 3);
            CandleData b = candles.get(i - 2);
            CandleData c = candles.get(i - 1);
            CandleData d = candles.get(i);
            if (isBearishMarubozu(a) && isBearishMarubozu(b) && b.closePrice() < a.closePrice()
                    && isBearish(c) && isBearish(d) && d.closePrice() > c.closePrice()) {
                signals.add(signal(pattern, i, 0.64, "Bearish exhaustion sequence with bullish recovery pressure."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectNeckLine(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            CandleData a = candles.get(i - 1);
            CandleData b = candles.get(i);
            if (isBearish(a) && isBullish(b) && b.openPrice() < a.lowPrice()
                    && b.closePrice() <= a.closePrice() + avgBody(candles, i, 10) * 0.35) {
                signals.add(signal(pattern, i, 0.57, "Weak rebound closes near prior bearish close."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectBullishCompressionReversal(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 2; i < candles.size(); i++) {
            CandleData a = candles.get(i - 2);
            CandleData b = candles.get(i - 1);
            CandleData c = candles.get(i);
            if (downtrend(candles, i) && range(c) < range(a) && c.lowPrice() >= b.lowPrice() - avgBody(candles, i, 10) * 0.30
                    && (isBullish(c) || lowerShadow(c) > body(c))) {
                signals.add(signal(pattern, i, 0.59, "Downtrend compression with bullish reversal pressure."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectStickSandwich(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 2; i < candles.size(); i++) {
            CandleData a = candles.get(i - 2);
            CandleData b = candles.get(i - 1);
            CandleData c = candles.get(i);
            if (isBearish(a) && isBullish(b) && isBearish(c)
                    && nearlyEqual(a.closePrice(), c.closePrice(), avgBody(candles, i, 10) * 0.25)) {
                signals.add(signal(pattern, i, 0.58, "Two matching bearish closes sandwich a bullish candle."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectTriStar(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        for (int i = 2; i < candles.size(); i++) {
            CandleData a = candles.get(i - 2);
            CandleData b = candles.get(i - 1);
            CandleData c = candles.get(i);
            if (isDoji(a) && isDoji(b) && isDoji(c)
                    && (pattern.getDefaultSignal() == SignalType.BUY ? downtrend(candles, i) : uptrend(candles, i))) {
                signals.add(signal(pattern, i, 0.62, "Three consecutive doji after directional move."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectSideBySideWhiteLines(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        boolean bullish = pattern.getDefaultSignal() == SignalType.BUY;
        for (int i = 2; i < candles.size(); i++) {
            CandleData a = candles.get(i - 2);
            CandleData b = candles.get(i - 1);
            CandleData c = candles.get(i);
            boolean gap = bullish ? gapUp(a, b) : gapDown(a, b);
            if (gap && isBullish(b) && isBullish(c)
                    && nearlyEqual(b.openPrice(), c.openPrice(), avgBody(candles, i, 10) * 0.35)) {
                signals.add(signal(pattern, i, 0.59, "Two bullish side-by-side candles after a gap."));
            }
        }
        return signals;
    }

    private List<CandlePatternSignal> detectDirectionalFallback(List<CandleData> candles, CandlePattern pattern) {
        List<CandlePatternSignal> signals = new ArrayList<>();
        boolean bullish = pattern.getDefaultSignal() == SignalType.BUY;
        for (int i = 1; i < candles.size(); i++) {
            CandleData p = candles.get(i - 1);
            CandleData c = candles.get(i);
            boolean reversal = bullish
                    ? isBullish(c) && c.closePrice() > midpoint(p) && (downtrend(candles, i) || lowerShadow(c) > body(c))
                    : isBearish(c) && c.closePrice() < midpoint(p) && (uptrend(candles, i) || upperShadow(c) > body(c));
            if (reversal || (directionMatches(c, pattern) && isLongBody(c, candles, i))) {
                signals.add(signal(pattern, i, 0.52, "Directional heuristic matched for " + pattern.getDisplayName() + "."));
            }
        }
        return signals;
    }

    private CandlePatternSignal signal(CandlePattern pattern, int index, double confidence, String reason) {
        return new CandlePatternSignal(pattern, pattern.getDefaultSignal(), index, confidence, reason);
    }

    private boolean directionMatches(CandleData candle, CandlePattern pattern) {
        return pattern.getDefaultSignal() == SignalType.BUY ? isBullish(candle) : isBearish(candle);
    }

    private boolean isBullish(CandleData candle) {
        return candle.closePrice() > candle.openPrice();
    }

    private boolean isBearish(CandleData candle) {
        return candle.closePrice() < candle.openPrice();
    }

    private boolean isDoji(CandleData candle) {
        double r = range(candle);
        return r > 0 && body(candle) / r <= 0.10;
    }

    private boolean isSmallBody(CandleData candle) {
        double r = range(candle);
        return r > 0 && body(candle) / r <= 0.35;
    }

    private boolean isLongBody(CandleData candle, List<CandleData> candles, int index) {
        return body(candle) >= avgBody(candles, index, 10) * 1.25;
    }

    private boolean isBullishMarubozu(CandleData candle) {
        return isBullish(candle) && body(candle) >= range(candle) * 0.80;
    }

    private boolean isBearishMarubozu(CandleData candle) {
        return isBearish(candle) && body(candle) >= range(candle) * 0.80;
    }

    private boolean closeNearExtreme(CandleData candle, boolean high) {
        double r = range(candle);
        if (r <= 0) {
            return false;
        }
        return high ? (candle.highPrice() - candle.closePrice()) / r <= 0.15
                : (candle.closePrice() - candle.lowPrice()) / r <= 0.15;
    }

    private boolean openNearLow(CandleData candle) {
        double r = range(candle);
        return r > 0 && (candle.openPrice() - candle.lowPrice()) / r <= 0.15;
    }

    private boolean openNearHigh(CandleData candle) {
        double r = range(candle);
        return r > 0 && (candle.highPrice() - candle.openPrice()) / r <= 0.15;
    }

    private boolean gapUp(CandleData previous, CandleData current) {
        return current.lowPrice() > previous.highPrice() || current.openPrice() > previous.closePrice();
    }

    private boolean gapDown(CandleData previous, CandleData current) {
        return current.highPrice() < previous.lowPrice() || current.openPrice() < previous.closePrice();
    }

    private boolean uptrend(List<CandleData> candles, int index) {
        if (index < 3) {
            return false;
        }
        return candles.get(index - 1).closePrice() > candles.get(index - 3).closePrice();
    }

    private boolean downtrend(List<CandleData> candles, int index) {
        if (index < 3) {
            return false;
        }
        return candles.get(index - 1).closePrice() < candles.get(index - 3).closePrice();
    }

    private boolean nearlyEqual(double a, double b, double tolerance) {
        return Math.abs(a - b) <= Math.max(0.00000001, tolerance);
    }

    private double avgBody(List<CandleData> candles, int index, int lookback) {
        int start = Math.max(0, index - lookback);
        int count = 0;
        double sum = 0.0;
        for (int i = start; i < index; i++) {
            sum += body(candles.get(i));
            count++;
        }
        return count == 0 ? Math.max(0.00000001, body(candles.get(index))) : Math.max(0.00000001, sum / count);
    }

    private double body(CandleData candle) {
        return Math.abs(candle.closePrice() - candle.openPrice());
    }

    private double bodyHigh(CandleData candle) {
        return Math.max(candle.openPrice(), candle.closePrice());
    }

    private double bodyLow(CandleData candle) {
        return Math.min(candle.openPrice(), candle.closePrice());
    }

    private double range(CandleData candle) {
        return Math.max(0.0, candle.highPrice() - candle.lowPrice());
    }

    private double upperShadow(CandleData candle) {
        return candle.highPrice() - Math.max(candle.openPrice(), candle.closePrice());
    }

    private double lowerShadow(CandleData candle) {
        return Math.min(candle.openPrice(), candle.closePrice()) - candle.lowPrice();
    }

    private double midpoint(CandleData candle) {
        return (candle.openPrice() + candle.closePrice()) / 2.0;
    }
}
