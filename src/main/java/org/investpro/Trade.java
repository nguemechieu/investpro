package org.investpro;

import com.google.gson.Gson;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;

import org.investpro.Indicators.Signal;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Trade extends RecursiveTreeObject<Trade> implements Runnable {


    static List<Order> orderList = new ArrayList<>();
    private boolean isBestMatch;
    private boolean isMaker;
    private boolean isBuyer;
    private long time;
    private String commissionAsset;
    private String commission;
    private double quoteQty;
    private double qty;
    private long orderListId;
    private long orderId;
    private double avgPrice;
    private double avgQty;
    private boolean isBuy;
    private boolean isSell;
    private long tradeId;
    private double closePrice;
    private double highestBidPrice;
    Order order;
    Exchange exchange;
    TradePair tradePair;
    double price;
    double amount;
    Money price2;

    public Trade(TradePair tradePair, @NotNull Money price2, @NotNull Money size, Side side, long tradeId, @NotNull Instant time) {
        this.tradePair = tradePair;
        this.price2 = price2;
        this.amount = (double) size.amount();
        this.price = (double) price2.amount();
        this.tradeId = tradeId;
        this.time = time.toEpochMilli();
        this.isBuy = side == Side.BUY;
        this.isSell = side == Side.SELL;
        logger.info("Trade created");
    }

    public static void setTrades(List<Trade> trades) {
        Trade.trades = trades;
    }

    public static CandleData getCandle() {
        return candle;
    }

    public static void setCandle(CandleData candle) {
        Trade.candle = candle;
    }
    public static Logger getLogger() {
        return logger;
    }

    public static void setLogger(Logger logger) {
        Trade.logger = logger;
    }

    public static List<Order> getOrderList() {
        return orderList;
    }

    public static void setOrderList(List<Order> orderList) {
        Trade.orderList = orderList;
    }
    Instant timestamp;
    double fee;

    public boolean isBestMatch() {
        return isBestMatch;
    }

    public void setBestMatch(boolean bestMatch) {
        isBestMatch = bestMatch;
    }

    public boolean isMaker() {
        return isMaker;
    }

    public void setMaker(boolean maker) {
        isMaker = maker;
    }

    public boolean isBuyer() {
        return isBuyer;
    }

    public void setBuyer(boolean buyer) {
        isBuyer = buyer;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getCommissionAsset() {
        return commissionAsset;
    }

    public void setCommissionAsset(String commissionAsset) {
        this.commissionAsset = commissionAsset;
    }

    public String getCommission() {
        return commission;
    }

    public void setCommission(String commission) {
        this.commission = commission;
    }

    public double getQuoteQty() {
        return quoteQty;
    }

    public void setQuoteQty(double quoteQty) {
        this.quoteQty = quoteQty;
    }

    public double getQty() {
        return qty;
    }

    public void setQty(double qty) {
        this.qty = qty;
    }

    public long getOrderListId() {
        return orderListId;
    }

    public void setOrderListId(long orderListId) {
        this.orderListId = orderListId;
    }

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }

    public Side getSide() {
        return side;
    }

    public void setSide(Side side) {
        this.side = side;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public void setOrders(ArrayList<Order> orders) {
        this.orders = orders;
    }

    public ConcurrentHashMap<String, Order> getOrderMap() {
        return orderMap;
    }

    public void setOrderMap(ConcurrentHashMap<String, Order> orderMap) {
        this.orderMap = orderMap;
    }

    public ENUM_ORDER_TYPE getOrder_type() {
        return order_type;
    }

    public void setOrder_type(ENUM_ORDER_TYPE order_type) {
        this.order_type = order_type;
    }

    public double getSize() {
        return size;
    }

    public double getCurrentUnits() {
        return currentUnits;
    }

    public double getInitialMargin() {
        return initialMargin;
    }

    public void setInitialMargin(double initialMargin) {
        this.initialMargin = initialMargin;
    }

    public double getInitialUnits() {
        return initialUnits;
    }

    public double getInitialMarginRequired() {
        return initialMarginRequired;
    }

    public String getState() {
        return state;
    }

    public long getLastTransactionID() {
        return lastTransactionID;
    }

    public String getClientExtensions() {
        return clientExtensions;
    }

    public double getUnrealizedPL() {
        return unrealizedPL;
    }

    public double getMarginUsed() {
        return marginUsed;
    }

    public double getFinancing() {
        return financing;
    }

    public double getDividendAdjustment() {
        return dividendAdjustment;
    }

    public double getRealizedPL() {
        return realizedPL;
    }

    public double getMarginBalance() {
        return marginBalance;
    }

    public void setMarginBalance(double marginBalance) {
        this.marginBalance = marginBalance;
    }

    public double getMarginAvailable() {
        return marginAvailable;
    }

    public void setMarginAvailable(double marginAvailable) {
        this.marginAvailable = marginAvailable;
    }

    public double getMarginBalanceAvailable() {
        return marginBalanceAvailable;
    }

    public void setMarginBalanceAvailable(double marginBalanceAvailable) {
        this.marginBalanceAvailable = marginBalanceAvailable;
    }

    public void setOpenTime(long openTime) {
        this.openTime = openTime;
    }

    public void setCloseTime(long closeTime) {
        this.closeTime = closeTime;
    }

    public ConcurrentHashMap<Long, Trade> getTradeMap() {
        return tradeMap;
    }

    public void setTradeMap(ConcurrentHashMap<Long, Trade> tradeMap) {
        this.tradeMap = tradeMap;
    }
    Signal signal;
    private TradeMode mode;


    public Trade(Order order, TradeMode tradeMode, String symbol, long id, long orderId, long orderListId, double price, String qty, String quoteQty, String commission, String commissionAsset, long time, boolean isBuyer, boolean isMaker, boolean isBestMatch) {
        this.instrument = symbol;
        Trade.id = id;
        this.order = order;
        this.tradeId = id;
        this.orderId = orderId;
        this.orderListId = orderListId;
        this.price = price;
        this.qty = Double.parseDouble(qty);
        this.quoteQty = Double.parseDouble(quoteQty);
        this.commission = commission;
        this.commissionAsset = commissionAsset;
        this.time = time;
        this.isBuyer = isBuyer;
        this.isMaker = isMaker;
        this.isBestMatch = isBestMatch;
        this.avgPrice = 0;
        this.avgQty = 0;
        this.timestamp =
                Instant.now();


        this.mode = tradeMode;
    }

    public void setTransactionType(Side transactionType) {
        this.transactionType = transactionType;
    }

    public long getLocalTradeId() {
        return localTradeId;
    }

    Side side;

    public void setLocalTradeId(long localTradeId) {
        this.localTradeId = localTradeId;
    }

    ArrayList<Order> orders = new ArrayList<>();
    ConcurrentHashMap<String, Order> orderMap = new ConcurrentHashMap<>();
    ENUM_ORDER_TYPE order_type;
 double remaining;
     double size;
    double currentUnits;
    double initialMargin;
    double initialUnits;
    double initialMarginRequired;
    String state;
    long lastTransactionID;
    String clientExtensions;
    double unrealizedPL;
    double marginUsed;
    double financing;
    double dividendAdjustment;
    double realizedPL;
    double marginBalance;
    double marginAvailable;
    double marginBalanceAvailable;

    long  openTime;
    private  String instrument;
    private static Long id;
    long closeTime;
     double volume;
    private String accountID;

    //   {"trades":[{"id":"142950","instrument":"EUR_USD","price":"1.07669","openTime":"2023-03-21T16:56:10.786314295Z","initialUnits":"-1700","initialMarginRequired":"36.6098","state":"OPEN","currentUnits":"-1700","realizedPL":"0.0000","financing":"0.1828","dividendAdjustment":"0.0000","clientExtensions":{"id":"140660466","tag":"0"},"unrealizedPL":"-23.6130","marginUsed":"37.0770"},{"id":"124829","instrument":"USD_CAD","price":"1.38016","openTime":"2023-03-15T14:46:04.088679752Z","initialUnits":"4000","initialMarginRequired":"80.0000","state":"OPEN","currentUnits":"4000","realizedPL":"0.0000","financing":"-0.7802","dividendAdjustment":"0.0000","clientExtensions":{"id":"140494560","tag":"0"},"unrealizedPL":"-48.2803","marginUsed":"80.0000"}],"lastTransactionID":"142955"}

    public Trade(@NotNull TradePair symbol, String id, double orderId, double orderListId, String price, String qty, String quoteQty, long commission, String commissionAsset, long time, boolean isBuyer, boolean isMaker, boolean isBestMatch) {

        this.instrument = symbol.getBaseCurrency().code + "_" + symbol.getCounterCurrency().code;
        Trade.id = Long.valueOf(id);
        this.tradeId = Long.parseLong(id);
        this.orderId = (long) orderId;
        this.orderListId = (long) orderListId;
        this.price = Double.parseDouble(price);
        this.qty = Double.parseDouble(qty);
        this.quoteQty = Double.parseDouble(quoteQty);
        this.commission = String.valueOf(commission);
        this.commissionAsset = commissionAsset;
        this.time = time;
        this.isBuyer = isBuyer;
        this.isMaker = isMaker;
        this.isBestMatch = isBestMatch;
        this.avgPrice = 0;
        this.avgQty = 0;
        this.timestamp = Instant.now();


    }

    static List<Trade> trades=new ArrayList<>();
    ConcurrentHashMap<Long, Trade> tradeMap = new ConcurrentHashMap<>();


    public static CandleData candle;
    static Logger logger = LoggerFactory.getLogger(Trade.class);
    public Trade(@NotNull TradePair tradePair, String symbol, Side side, String price, double quantity, double fee, long orderId, long clientOrderId, long time, @NotNull String isBuyer, @NotNull String isMaker, @NotNull String isBestMatch, @NotNull String isBestMatchMaker, String isBestMatchTaker) {
        this.transactionType = side;
        this.instrument = symbol;
        Trade.id = tradePair.getId();
        this.tradeId = Long.parseLong(UUID.randomUUID().toString());
        this.side = side;
        this.orderId = Long.parseLong(UUID.randomUUID().toString());
        this.orderListId = Long.parseLong(UUID.randomUUID().toString());
        this.price = Double.parseDouble(price);
        this.qty = quantity;
        this.quoteQty = quantity;
        this.commission = String.valueOf(0);
        this.commissionAsset = String.valueOf(fee);
        this.time = time;
        this.isBuyer = isBuyer.equals("1");
        this.isMaker = isMaker.equals("1");
        this.isBestMatch = isBestMatch.equals("1");

        this.avgPrice = Double.parseDouble(price) - Double.parseDouble(price) / 100;
        this.avgQty = qty / 2;
        this.fee = fee;

    }

    public Trade(String symbol, long id, long orderId, long orderListId, String price, String qty, String quoteQty, String commission, String commissionAsset, long time, boolean isBuyer, boolean isMaker, boolean isBestMatch) {
        this.instrument = symbol;
        Trade.id = id;
        this.tradeId = id;
        this.orderId = orderId;
        this.orderListId = orderListId;
        this.price = Double.parseDouble(price);
        this.qty = Double.parseDouble(qty);
        this.quoteQty = Double.parseDouble(quoteQty);
        this.commission = commission;
        this.commissionAsset = commissionAsset;
        this.time = time;
        this.isBuyer = isBuyer;
        this.isMaker = isMaker;
        this.isBestMatch = isBestMatch;
        this.avgPrice = 0;
        this.avgQty = 0;

    }
  public Trade(ENUM_ORDER_TYPE orderType, Long id, @NotNull TradePair instrument,  Side side, double price, Long openTime, int initialUnits, double initialMargin
               , String state, double currentUnits, double realizedPL, double financing, double dividendAdjustment,
               String clientExtensions, double unrealizedPL, double marginUsed) throws IOException, ParseException, URISyntaxException, InterruptedException {
      order_type = orderType;
      Trade.id = id;
      this.instrument = instrument.getBaseCurrency() + "_" + instrument.getCounterCurrency();
      this.price = price;
      this.openTime = openTime;
      this.initialUnits = initialUnits;
      this.initialMargin = initialMargin;
      this.state = state;
      this.currentUnits = (int) currentUnits;
      this.realizedPL = realizedPL;
      this.financing = financing;
      this.dividendAdjustment = dividendAdjustment;
      this.clientExtensions = clientExtensions;
      this.unrealizedPL = unrealizedPL;
      this.marginUsed = marginUsed;
      this.timestamp = Instant.ofEpochSecond(openTime);
      this.side = side;//.equals(TRADE_ORDER_TYPE.BUY)? TRADE_ORDER_TYPE.BUY : TRADE_ORDER_TYPE.SELL ;


      this.order=new Order(
              id, instrument.toString('_'), timestamp.toString(),
              orderType,
              side,
              currentUnits,
              realizedPL,
              financing,
              dividendAdjustment,

              unrealizedPL,
              marginUsed
      );
     tradePair = instrument;


  }
    public Trade(TradePair tradePair, double price, double amount, Side transactionType,
                 long localTradeId, long timestamp, double fee) throws IOException, InterruptedException, ParseException, URISyntaxException {

        this.tradePair = tradePair;
        this.price = price;

        this.amount = amount;
        this.transactionType = transactionType;
        this.localTradeId = localTradeId;
        this.timestamp = Instant.ofEpochSecond(timestamp);

        this.fee = fee;
        logger.info("Trade created");
    }

    public Trade(TradePair tradePair, double price, double size, Side side, long tradeId, long time) throws IOException, ParseException, URISyntaxException, InterruptedException {

        this.tradePair = tradePair;
        this.price = price;
        this.size = size;
        this.side = side;
        this.tradeId = tradeId;
        this.time = time;
    }
    private Side transactionType;
    private long localTradeId;
    public Trade(TradePair tradePair, double price, double amount, Side transactionType,
                 long localTradeId, Instant timestamp, double fee) {
        this.tradePair = tradePair;
        this.price = price;
        this.amount = amount;
        this.transactionType = transactionType;
        this.localTradeId = localTradeId;
        this.timestamp = timestamp;
        this.fee = fee;
    }

    public double getFee() {
        return fee;
    }

    public void setFee(double fee) {
        this.fee = fee;
    }

    public Exchange getExchange() {
        return exchange;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }


    public static int getConnexionInfo() {
        return 1;
    }


    public static Trade fromMessage(String message) {
        logger.info(message);
        return new Gson().fromJson(message, Trade.class);
    }




    public  double getRemaining() {
        return remaining;
    }

    public void setRemaining(double remaining) {
        this.remaining = remaining;
    }

    public void setSize(double size) {
        this.size = size;
    }

    public TradePair getTradePair() {
        return tradePair;
    }

    public double getPrice() {
        return price;
    }

    public void setExchange(Exchange exchange) {
        this.exchange = exchange;
    }

    public double getAmount() {

        return amount;
    }


    private int OrdersTotal() {
        int count = 0;
        for (Order i : orderList) {
            if (i != null) {
                count++;
            }
        }
        return count;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public double getTotal() throws SQLException, ClassNotFoundException {

        // different currencies..maybe involve a TradePair? btc * usd/btc = usd, which
        // is technically what we are doing here
        return DefaultMoney.ofFiat(price, tradePair.getCounterCurrency().code).toDouble();
    }

    @Override
    public void run() {


        try {
            OnTick();
        } catch (IOException | InterruptedException | SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        Trade.id = id;
    }

    public String getInstrument() {
        return instrument;
    }

    public void setInstrument(String instrument) {
        this.instrument = instrument;
    }

    public Long getOpenTime() {

        if (state.equals("OPEN")) {
            return openTime;
        }else {
            return 0L;
        }
    }

    public void setOpenTime(int openTime) {
        this.openTime = openTime;
    }

    public double getVolume() {
        logger.info("Volume");
        logger.info(tradePair.getCounterCurrency().code);

        return initialUnits;
    }

    public Long getCloseTime() {
        logger.info("Close Time");
        logger.info(tradePair.getCounterCurrency().code);

        if (state .equals("CLOSED")) {
            return closeTime;
        }

        return 0L;


    }

    private void OnTick() throws IOException, InterruptedException, SQLException, ClassNotFoundException {
        logger.info("OnTick");
        logger.info(tradePair.getCounterCurrency().code);
        logger.info(tradePair.getBaseCurrency().code);
        logger.info("Trade pair: " + tradePair.toString());
        logger.info("Price: " + price);
        logger.info("Amount: " + amount);
        logger.info("Transaction type: " + transactionType);
        logger.info("Local trade id: " + localTradeId);
        logger.info("Timestamp: " + timestamp);
        logger.info("Fee: " + fee);
        logger.info("Total: " + getTotal());
        logger.info("Orders total: " + OrdersTotal());
        logger.info("Orders: " + orderList.size());
        logger.info("Trades: " + trades.size());
        logger.info("OrderMap: " + orderMap.size());
        logger.info("TradeMap: " + tradeMap.size());
        logger.info("Exchange: " + exchange.getName());
        logger.info("UnrealizedPL: " + unrealizedPL);
        logger.info("MarginUsed: " + marginUsed);
        logger.info("OrderMap: " + orderMap.size());
        logger.info("TradeMap: " + tradeMap.size());
        logger.info("Remaining: " + remaining);
        logger.info("Size: " + size);
        logger.info("Mode: " + mode);


        if (mode.equals(TradeMode.AUTOMATIC)) {

            if (signal.equals(Signal.BUY)) {
                exchange.createOrder(
                        tradePair,
                        Side.BUY,
                        ENUM_ORDER_TYPE.MARKET, price, size,
                        new Date(), 0,
                        0
                );

                TelegramClient.sendMessage(
                        "Opening  Buying order" + tradePair.getCounterCurrency().code + " " + tradePair.getBaseCurrency().code + " at " +
                                price + " " + exchange.getLivePrice(tradePair)
                );
            } else if (signal.equals(Signal.SELL)) {

                exchange.createOrder(
                        tradePair,
                        Side.SELL,
                        ENUM_ORDER_TYPE.MARKET, price, size,
                        new Date(), 0,
                        0
                );

                TelegramClient.sendMessage(
                        "Opening  Selling order" + tradePair.getCounterCurrency().code + " " + tradePair.getBaseCurrency().code + " at " +
                                price + " " + exchange.webSocketClient.getPrice(tradePair)
                );
            } else if (signal.equals(Signal.STOP)) {

                TelegramClient.sendMessage(
                        "Stopping order" + tradePair.getCounterCurrency().code + " " + tradePair.getBaseCurrency().code + " at " +
                                price + " " + exchange.getLivePrice(tradePair)
                );


            } else if (signal.equals(Signal.CloseSELL)) {

                TelegramClient.sendMessage(
                        "Closing Sell order" + tradePair.getCounterCurrency().code + " " + tradePair.getBaseCurrency().code + " at " +
                                price + " " + exchange.getLivePrice(tradePair)

                );


            } else if (signal.equals(Signal.CloseBUY)) {

                TelegramClient.sendMessage(
                        "Closing Buy order" + tradePair.getBaseCurrency().code + " " + tradePair.getCounterCurrency().code + " at " +
                                price + " " + exchange.getLivePrice(tradePair)

                );


            } else if (Objects.equals(signal, Signal.ReduceSize)) {

                TelegramClient.sendMessage(
                        "Reducing order Size " + tradePair.getCounterCurrency().code + " " + tradePair.getBaseCurrency().code + " at " +
                                price + " " + exchange.getLivePrice(tradePair)

                );

            }
        } else if (mode.equals(TradeMode.MANUAL)) {

            TelegramClient.sendMessage(
                    "Manual order" + tradePair.getCounterCurrency().code + " " + tradePair.getBaseCurrency().code + " at " +
                            price + " " + exchange.getLivePrice(tradePair)
            );
        } else if (mode.equals(TradeMode.SIGNAL_ONLY)) {

            TelegramClient.sendMessage(
                    "Signal only order" + tradePair.getCounterCurrency().code + " " + tradePair.getBaseCurrency().code + " at " +
                            price + " " + exchange.getLivePrice(tradePair)
            );
        }
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    public void setCloseTime(int closeTime) {
        this.closeTime = closeTime;
    }

    public void setTradeID(long id) {
        this.localTradeId = id;
    }

    public void setAccountID(String accountID) {
        this.accountID = accountID;
    }

    public String getAccountID() {
        return accountID;
    }

    public void setFinancing(double financing) {
        this.financing = financing;
    }

    public void setRealizedPL(double realizedPL) {
        this.realizedPL = realizedPL;
    }

    public void setMarginUsed(double marginUsed) {
        this.marginUsed = marginUsed;
    }

    public void setInitialUnits(double initialUnits) {
        this.initialUnits = initialUnits;
    }

    public void setInitialMarginRequired(double initialMarginRequired) {
        this.initialMarginRequired =  initialMarginRequired;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setCurrentUnits(double currentUnits) {
        this.currentUnits = currentUnits;
    }

    public void setUnrealizedPL(double unrealizedPL) {
        this.unrealizedPL = unrealizedPL;
    }

    public void setDividendAdjustment(double dividendAdjustment) {
        this.dividendAdjustment = dividendAdjustment;
    }

    public void setClientExtensions(String asText) {
        this.clientExtensions = asText;
    }

    public void setLastTransactionID(long lastTransactionID) {
        this.lastTransactionID = lastTransactionID;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public void close() {
        state = "CLOSED";
        closeTime = Instant.now().getEpochSecond();
        logger.info("Trade closed");
        logger.info(tradePair.getCounterCurrency().code);
        logger.info(tradePair.getBaseCurrency().code);
    }

    public void setTradePair(TradePair tradePair) {
        this.tradePair = tradePair;
    }


    /**
     * Returns the total amount of money this trade was, i.e. {@literal price * amount} in price
     * units.
     *
     * @return
     */


    public Instant getTimestamp() {
        return timestamp;
    }

    public Side getTransactionType() {
        return transactionType;
    }

    @Override
    public String toString() {
        return String.format("Trade [tradePair = %s, price = %s, amount = %s, transactionType = %s, localId = %s, " +
                "timestamp = %s, fee = %s]", tradePair, price, amount, transactionType, localTradeId, timestamp, fee);
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }

        if (object == null || object.getClass() != this.getClass()) {
            return false;
        }

        Trade other = (Trade) object;


        if (Objects.equals(amount, other.amount)) if (Objects.equals(transactionType, other.transactionType))
            if (Objects.equals(localTradeId, other.localTradeId))
                return Objects.equals(timestamp, other.timestamp);
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tradePair, price, amount, transactionType, localTradeId, timestamp);
    }

    public double getAvgPrice() {
        return avgPrice;
    }

    public void setAvgPrice(double avgPrice) {
        this.avgPrice = avgPrice;
    }

    public double getAvgQty() {
        return avgQty;
    }

    public Object isBuy() {
        return isBuy;
    }

    public Object isSell() {
        return isSell;
    }

    public Object getTradeId() {
        return tradeId;
    }

    public double getClosePrice() {
        return closePrice;
    }

    public double getHighestBidPrice() {
        return highestBidPrice;
    }
}
