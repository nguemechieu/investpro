# Trader — Reference

## Macro Frameworks

### Business Cycle Positioning

| Phase | Characteristics | Favored Assets |
|-------|----------------|----------------|
| Early Expansion | Falling rates, rising growth | Equities (cyclicals), Credit, EM |
| Mid Expansion | Stable rates, peak growth | Equities (broad), Commodities |
| Late Expansion | Rising rates, elevated inflation | Commodities, TIPS, Energy |
| Recession | Falling rates, falling growth | Bonds, Defensive equities, Gold, USD |

### Global Macro Themes to Track
- US Fed policy and rate trajectory
- China growth / stimulus cycle
- EUR/USD regime (parity vs. divergence)
- Commodity supercycle indicators
- EM capital flow cycles (DXY inverse relationship)
- Credit spreads (HY/IG) as leading equity indicator

---

## Sector Rotation

### Classic Rotation (by cycle phase)

```
Early → Financials, Consumer Discretionary, Technology
Mid   → Industrials, Materials, Energy
Late  → Energy, Materials, Staples, Healthcare
Recession → Utilities, Healthcare, Staples, Bonds
```

### Intermarket Relationships
- Rates up → Financials up, Growth/Tech down
- USD up → EM down, Commodities down, US Multinationals down
- Oil up → Energy up, Airlines/Consumer down, Staples pressured
- VIX spike → Risk assets down, Gold/JPY up, short vol losses

---

## Options Strategies

### Directional
| Outlook | Strategy | Max Risk | Max Reward |
|---------|----------|----------|------------|
| Strong bull | Long call / call spread | Premium paid | Unlimited (spread: capped) |
| Moderate bull | Bull call spread | Net debit | Spread width − debit |
| Mild bull | Cash-secured put | Strike − premium | Premium |
| Strong bear | Long put / put spread | Premium paid | Unlimited (spread: capped) |
| Moderate bear | Bear put spread | Net debit | Spread width − debit |

### Income / Volatility
| Outlook | Strategy | Note |
|---------|----------|------|
| Low vol expected | Short straddle/strangle | Undefined risk |
| Range-bound | Iron condor | Defined risk |
| High vol expected | Long straddle | Buy before catalyst |
| Moderate vol | Covered call | Own stock, sell OTM call |

### Key Options Greeks to Monitor
- **Delta**: Directional exposure (0.30–0.50 delta for balanced risk)
- **Theta**: Time decay — short options benefit, long options suffer
- **Vega**: Vol sensitivity — buy before events, sell after
- **Gamma**: Rate of delta change — high near expiry
- **IV Rank/Percentile**: >50 = sell vol; <30 = buy vol

---

## Quantitative Signals

### Momentum Indicators
- **RSI**: Overbought >70, Oversold <30; divergences signal reversals
- **MACD**: Crossovers for trend changes; histogram for momentum
- **Rate of Change (ROC)**: Measures price velocity
- **ADX**: >25 trending, <20 ranging

### Sentiment Indicators
- **VIX**: >30 = fear, <15 = complacency
- **Put/Call Ratio**: >1.2 = excessive fear (contrarian bullish), <0.7 = complacency
- **AAII Sentiment Survey**: Contrarian at extremes
- **COT Report**: Track large speculator positions vs. commercials
- **Crypto Funding Rates**: Positive = long-biased (fade on extremes), negative = short-biased

### Flow & Positioning
- **Dark pool prints**: Institutional accumulation at key levels
- **Options flow**: Unusual activity, sweep vs. block, OI changes
- **Short interest**: High short interest = short squeeze potential
- **Insider transactions**: Cluster buys near bottoms

---

## Risk Management Tables

### Kelly Criterion (Simplified)
```
f* = (bp - q) / b
Where:
  b = net odds (R multiple)
  p = win probability
  q = loss probability (1-p)

Example: 40% win rate, 3:1 R/R
f* = (3×0.4 - 0.6) / 3 = (1.2-0.6)/3 = 0.20 (20% of capital)
Use half-Kelly for safety: 10%
```

### Position Sizing by Conviction
| Conviction | Risk per Trade | Rationale |
|------------|----------------|----------|
| Low | 0.25–0.5% | Testing thesis, high uncertainty |
| Medium | 0.75–1.0% | Clear setup, confirmed signal |
| High | 1.5–2.0% | Highest conviction, multiple confirmations |

### Drawdown Protocol
| Portfolio DD | Action |
|-------------|--------|
| -5% | Review all open positions; tighten stops |
| -10% | Reduce gross exposure by 30–50%; full review |
| -15% | Move to flat or minimal exposure; reset |
| -20% | Full stop; do not trade until root cause identified |

---

## Screening Criteria

### Equity Long Candidates
- Revenue growth >15% YoY
- Expanding gross margins
- FCF positive or path to FCF in <18 months
- Insider buying in past 6 months
- Price above 200 DMA (trend confirmation)
- RS rank >80 (outperforming market)

### Equity Short Candidates
- Declining revenue or gross margin compression
- Rising debt with falling coverage ratios
- Multiple insider sells
- Price below 200 DMA, making lower highs
- High short interest already (adds fuel but increases squeeze risk)
- Accounting red flags (receivables growing faster than revenue)

### Crypto Screening
- TVL trend (growing = healthy ecosystem)
- Revenue/fees (protocol sustainability)
- Token distribution (avoid heavy VC unlock schedules)
- Developer activity (GitHub commits, protocol upgrades)
- On-chain large wallet accumulation/distribution
