package org.investpro.ai.local.grpc.generated;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Hand-written POJO replacing the protobuf-generated SignalReviewRequest.
 * Serialised to JSON and POSTed to the Python AI {@code /analyze-signal} endpoint.
 *
 * <p><strong>CRITICAL:</strong> This object carries signal metadata for AI advisory
 * review only. It does NOT trigger any order submission.</p>
 */
public final class SignalReviewRequest {

    @JsonProperty("symbol")        private String symbol = "";
    @JsonProperty("timeframe")     private String timeframe = "";
    @JsonProperty("side")          private String side = "";
    @JsonProperty("confidence")    private double confidence;
    @JsonProperty("price")         private double price;
    @JsonProperty("spread")        private double spread;
    @JsonProperty("volatility")    private double volatility;
    @JsonProperty("volume")        private double volume;
    @JsonProperty("strategy_name") private String strategyName = "";
    @JsonProperty("market_regime") private String marketRegime = "";

    private SignalReviewRequest() {}

    public String getSymbol()        { return symbol; }
    public String getTimeframe()     { return timeframe; }
    public String getSide()          { return side; }
    public double getConfidence()    { return confidence; }
    public double getPrice()         { return price; }
    public double getSpread()        { return spread; }
    public double getVolatility()    { return volatility; }
    public double getVolume()        { return volume; }
    public String getStrategyName()  { return strategyName; }
    public String getMarketRegime()  { return marketRegime; }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private final SignalReviewRequest instance = new SignalReviewRequest();

        public Builder setSymbol(String v)        { instance.symbol = v != null ? v : "";        return this; }
        public Builder setTimeframe(String v)     { instance.timeframe = v != null ? v : "";    return this; }
        public Builder setSide(String v)          { instance.side = v != null ? v : "";         return this; }
        public Builder setConfidence(double v)    { instance.confidence = v;                    return this; }
        public Builder setPrice(double v)         { instance.price = v;                         return this; }
        public Builder setSpread(double v)        { instance.spread = v;                        return this; }
        public Builder setVolatility(double v)    { instance.volatility = v;                    return this; }
        public Builder setVolume(double v)        { instance.volume = v;                        return this; }
        public Builder setStrategyName(String v)  { instance.strategyName = v != null ? v : ""; return this; }
        public Builder setMarketRegime(String v)  { instance.marketRegime = v != null ? v : ""; return this; }

        public SignalReviewRequest build() {
            return instance;
        }
    }
}
