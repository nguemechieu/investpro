package org.investpro.ai.local.grpc.generated;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Hand-written POJO replacing the protobuf-generated BacktestReviewRequest.
 * Serialised to JSON and POSTed to the Python AI {@code /review-backtest} endpoint.
 */
public final class BacktestReviewRequest {

    @JsonProperty("strategy_id")   private String strategyId = "";
    @JsonProperty("strategy_name") private String strategyName = "";
    @JsonProperty("symbol")        private String symbol = "";
    @JsonProperty("timeframe")     private String timeframe = "";
    @JsonProperty("total_trades")  private int    totalTrades;
    @JsonProperty("win_rate")      private double winRate;
    @JsonProperty("profit_factor") private double profitFactor;
    @JsonProperty("max_drawdown")  private double maxDrawdown;
    @JsonProperty("sharpe_ratio")  private double sharpeRatio;
    @JsonProperty("expectancy")    private double expectancy;
    @JsonProperty("sample_size")   private int    sampleSize;

    private BacktestReviewRequest() {}

    public String getStrategyId()   { return strategyId; }
    public String getStrategyName() { return strategyName; }
    public String getSymbol()       { return symbol; }
    public String getTimeframe()    { return timeframe; }
    public int    getTotalTrades()  { return totalTrades; }
    public double getWinRate()      { return winRate; }
    public double getProfitFactor() { return profitFactor; }
    public double getMaxDrawdown()  { return maxDrawdown; }
    public double getSharpeRatio()  { return sharpeRatio; }
    public double getExpectancy()   { return expectancy; }
    public int    getSampleSize()   { return sampleSize; }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private final BacktestReviewRequest instance = new BacktestReviewRequest();

        public Builder setStrategyId(String v)   { instance.strategyId   = v != null ? v : ""; return this; }
        public Builder setStrategyName(String v) { instance.strategyName = v != null ? v : ""; return this; }
        public Builder setSymbol(String v)       { instance.symbol       = v != null ? v : ""; return this; }
        public Builder setTimeframe(String v)    { instance.timeframe    = v != null ? v : ""; return this; }
        public Builder setTotalTrades(int v)     { instance.totalTrades  = v;                  return this; }
        public Builder setWinRate(double v)      { instance.winRate      = v;                  return this; }
        public Builder setProfitFactor(double v) { instance.profitFactor = v;                  return this; }
        public Builder setMaxDrawdown(double v)  { instance.maxDrawdown  = v;                  return this; }
        public Builder setSharpeRatio(double v)  { instance.sharpeRatio  = v;                  return this; }
        public Builder setExpectancy(double v)   { instance.expectancy   = v;                  return this; }
        public Builder setSampleSize(int v)      { instance.sampleSize   = v;                  return this; }

        public BacktestReviewRequest build() {
            return instance;
        }
    }
}
