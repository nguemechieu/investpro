package org.investpro;

import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Date;

public class Order extends RecursiveTreeObject<Order> {
    double dividendAdjustment;
    double financing;
    double realizedPL;
    double currentUnits;
    ENUM_ORDER_TYPE orderType;
    String instrument;
    double marginUsed;
    double unrealizedPL;
    String triggerMode;
    String replacesOrderID;
    String time;
    String orderListId;
    private String clientOrderId;
    private String selfTradePreventionMode;
    private String updateTime;
    private String origQuoteOrderQty;
    private String isWorking;
    String reason;
    String positionFill;
    private String accountID;
    private String userID;
    private String batchID;
    private String requestID;
    private JSONObject clientExtensions;
    private String partialFill;
    private String order_type;
    private String type;

    public Order(String price, String timeInForce, String symbol, String orderId, String orderListId, String clientOrderId, String origQty, String executedQty, String cummulativeQuoteQty, String status, String type, String side, String stopPrice, String icebergQty, String time, String updateTime, String isWorking, String origQuoteOrderQty, String selfTradePreventionMode) {

        this.price = price;
        this.timeInForce = timeInForce;
        this.symbol = symbol;
        this.orderId = orderId;
        this.orderListId = orderListId;
        this.clientOrderId = clientOrderId;
        this.origQty = origQty;
        this.executedQty = executedQty;
        this.cummulativeQuoteQty = cummulativeQuoteQty;
        this.status = status;
        this.type = type;
        this.side = Side.valueOf(side);
        this.stopPrice = stopPrice;
        this.icebergQty = icebergQty;
        this.updateTime = updateTime;
        this.isWorking = isWorking;
        this.origQuoteOrderQty = origQuoteOrderQty;
        this.selfTradePreventionMode = selfTradePreventionMode;
    }

    public Order(Long id, @NotNull TradePair tradePair, String timestamp, String order_type, Side side, double remaining, double fee, double lotSize, double price

            , double stopLoss, double takeProfit
    ) {
        this.id = id;
        this.timestamp = timestamp;
        this.order_type = order_type;
        this.remaining = remaining;
        this.fee = fee;
        this.lotSize = lotSize;
        this.price = String.valueOf(price);
        this.stopLoss = stopLoss;
        this.symbol = tradePair.getCounterCurrency().getSymbol();
        this.type = order_type;

        this.currency = tradePair.getCounterCurrency().getSymbol();
        this.created = String.valueOf(new Date());
        this.takeProfit = takeProfit;
        this.updated = new Date();
        this.side = side;
        this.tradePair = tradePair;


    }

    public Order(String price, String timeInForce, String symbol, String orderId, String orderListId, String clientOrderId, String origQty, String executedQty, String cummulativeQuoteQty, String status, String type, String side, String stopPrice, String icebergQty, String updateTime, String isWorking, String origQuoteOrderQty, String selfTradePreventionMode) {
        this.price = price;
        this.timeInForce = timeInForce;
        this.symbol = symbol;
        this.orderId = orderId;
        this.orderListId = orderListId;
        this.clientOrderId = clientOrderId;
        this.origQty = origQty;
        this.executedQty = executedQty;
        this.cummulativeQuoteQty = cummulativeQuoteQty;
        this.status = status;
        this.type = type;
        this.side = Side.valueOf(side);
        this.stopPrice = stopPrice;
        this.icebergQty = icebergQty;
        this.updateTime = updateTime;
        this.isWorking = isWorking;
        this.origQuoteOrderQty = origQuoteOrderQty;
        this.selfTradePreventionMode = selfTradePreventionMode;
    }

    public Order(@NotNull JSONArray names) {
//        "orders": [
//        {
//            "triggerCondition": "DEFAULT",
//                "createTime": "2023-04-10T21:03:37.580958630Z",
//                "price": "1.08387",
//                "clientTradeID": "141168538",
//                "id": "143300",
//                "state": "PENDING",
//                "type": "STOP_LOSS",
//                "timeInForce": "GTC",
//                "triggerMode": "TOP_OF_BOOK",
//                "tradeID": "143295",
//                "replacesOrderID": "143297"
//        },
//        {
//            "triggerCondition": "DEFAULT",
//                "createTime": "2023-04-10T21:03:01.552862838Z",
//                "price": "1.09677",
//                "clientTradeID": "141168538",
//                "id": "143298",
//                "state": "PENDING",
//                "type": "TAKE_PROFIT",
//                "timeInForce": "GTC",
//                "tradeID": "143295"
//        }

        this.triggerCondition = names.getString(0);
        this.createTime = names.getString(1);
        this.price = names.getString(2);
        this.clientTradeID = names.getString(3);
        // this.id = Long.valueOf(names.getString(4));
        this.state = names.getString(5);
        // this.type = ENUM_ORDER_TYPE.valueOf(names.getString(6));
        this.timeInForce = names.getString(7);
        this.triggerMode = names.getString(8);
        this.tradeID = names.getString(9);
        this.replacesOrderID = names.getString(10);


    }

    public Order() {

    }

    public Order(Long id, String instrument, String timestamp, ENUM_ORDER_TYPE orderType, Side side, double currentUnits, double realizedPL, double financing, double dividendAdjustment, double unrealizedPL, double marginUsed) {
        this.id = id;
        this.timestamp = timestamp;
        this.instrument = instrument;
        this.orderType = orderType;
        this.side = side;
        this.currentUnits = currentUnits;
        this.realizedPL = realizedPL;
        this.financing = financing;
        this.dividendAdjustment = dividendAdjustment;
        this.unrealizedPL = unrealizedPL;
        this.marginUsed = marginUsed;
        this.created = String.valueOf(new Date());
        this.updated = new Date();
    }

    public double getDividendAdjustment() {
        return dividendAdjustment;
    }

    public void setDividendAdjustment(double dividendAdjustment) {
        this.dividendAdjustment = dividendAdjustment;
    }

    public double getFinancing() {
        return financing;
    }

    public void setFinancing(double financing) {
        this.financing = financing;
    }

    public double getRealizedPL() {
        return realizedPL;
    }

    public void setRealizedPL(double realizedPL) {
        this.realizedPL = realizedPL;
    }

    public double getCurrentUnits() {
        return currentUnits;
    }

    public void setCurrentUnits(double currentUnits) {
        this.currentUnits = currentUnits;
    }

    @Override
    public String toString() {
        return "Order{" +
                "dividendAdjustment=" + dividendAdjustment +
                ", financing=" + financing +
                ", realizedPL=" + realizedPL +
                ", currentUnits=" + currentUnits +
                ", orderType=" + orderType +
                ", instrument='" + instrument + '\'' +
                ", marginUsed=" + marginUsed +
                ", unrealizedPL=" + unrealizedPL +
                ", triggerMode='" + triggerMode + '\'' +
                ", replacesOrderID='" + replacesOrderID + '\'' +
                ", time='" + time + '\'' +
                ", orderListId='" + orderListId + '\'' +
                ", clientOrderId='" + clientOrderId + '\'' +
                ", selfTradePreventionMode='" + selfTradePreventionMode + '\'' +
                ", updateTime='" + updateTime + '\'' +
                ", origQuoteOrderQty='" + origQuoteOrderQty + '\'' +
                ", isWorking='" + isWorking + '\'' +
                ", stopPrice='" + stopPrice + '\'' +
                ", orderId='" + orderId + '\'' +
                ", accountID='" + accountID + '\'' +
                ", userID='" + userID + '\'' +
                ", batchID='" + batchID + '\'' +
                ", requestID='" + requestID + '\'' +
                ", clientExtensions=" + clientExtensions +
                ", partialFill='" + partialFill + '\'' +
                ", created='" + created + '\'' +
                ", clientTradeID1='" + clientTradeID1 + '\'' +
                ", triggerCondition='" + triggerCondition + '\'' +
                ", createTime='" + createTime + '\'' +
                ", price='" + price + '\'' +
                ", clientTradeID='" + clientTradeID + '\'' +
                ", state='" + state + '\'' +
                ", timeInForce='" + timeInForce + '\'' +
                ", tradeID='" + tradeID + '\'' +
                ", executedQty='" + executedQty + '\'' +
                ", origQty='" + origQty + '\'' +
                ", cummulativeQuoteQty='" + cummulativeQuoteQty + '\'' +
                ", icebergQty='" + icebergQty + '\'' +
                ", tradePair=" + tradePair +
                ", timestamp='" + timestamp + '\'' +
                ", order_type='" + order_type + '\'' +
                ", remaining=" + remaining +
                ", fee=" + fee +
                ", lotSize=" + lotSize +
                ", stopLoss=" + stopLoss +
                ", symbol='" + symbol + '\'' +
                ", type='" + type + '\'' +
                ", orders=" + orders +
                ", orderID=" + orderID +
                ", ticket=" + ticket +
                ", id=" + id +
                ", total=" + total +
                ", currency='" + currency + '\'' +
                ", takeProfit=" + takeProfit +
                ", updated=" + updated +
                ", closed=" + closed +
                ", status='" + status + '\'' +
                ", side=" + side +
                ", filled='" + filled + '\'' +
                ", unit='" + unit + '\'' +
                ", reason='" + reason + '\'' +
                ", positionFill='" + positionFill + '\'' +
                '}';
    }

    public ENUM_ORDER_TYPE getOrderType() {
        return orderType;
    }

    private String stopPrice;
    private String orderId;

    public void setOrderType(ENUM_ORDER_TYPE orderType) {
        this.orderType = orderType;
    }

    public String getInstrument() {
        return instrument;
    }

    public void setInstrument(String string) {
        this.instrument = string;
    }

    public double getMarginUsed() {
        return marginUsed;
    }

    public void setMarginUsed(double marginUsed) {
        this.marginUsed = marginUsed;
    }

    public double getUnrealizedPL() {
        return unrealizedPL;
    }

    public void setUnrealizedPL(double unrealizedPL) {
        this.unrealizedPL = unrealizedPL;
    }

    public String getTriggerMode() {
        return triggerMode;
    }

    public void setTriggerMode(String triggerMode) {
        this.triggerMode = triggerMode;
    }

    public String getReplacesOrderID() {
        return replacesOrderID;
    }

    public void setReplacesOrderID(String replacesOrderID) {
        this.replacesOrderID = replacesOrderID;
    }

    public String getAccountID() {
        return accountID;
    }


    public String getClientOrderId() {
        return clientOrderId;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public String getSelfTradePreventionMode() {
        return selfTradePreventionMode;
    }

    public void setSelfTradePreventionMode(String selfTradePreventionMode) {
        this.selfTradePreventionMode = selfTradePreventionMode;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    public String getOrigQuoteOrderQty() {
        return origQuoteOrderQty;
    }

    public void setOrigQuoteOrderQty(String origQuoteOrderQty) {
        this.origQuoteOrderQty = origQuoteOrderQty;
    }

    public String getIsWorking() {
        return isWorking;
    }

    public void setIsWorking(String isWorking) {
        this.isWorking = isWorking;
    }

    public String getOrderListId() {
        return orderListId;
    }

    public void setOrderListId(String orderListId) {
        this.orderListId = orderListId;
    }

    public void setStopPrice(String stopPrice) {
        this.stopPrice = stopPrice;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
    protected String created;
    String clientTradeID1;
    String triggerCondition, createTime,//": "2023-03-30T20:00:00.583871679Z",
            price,//": "1.08063",
            clientTradeID,//": "140930333",

    state,//": "PENDING",

    timeInForce,//": "GTC",
            tradeID;//": "143118"
    private String executedQty;
    private String origQty;
    private String cummulativeQuoteQty;
    private String icebergQty;
    private TradePair tradePair;
    private String timestamp;

    public void setAccountID(String string) {
        this.accountID = string;
    }
    private double remaining;
    private double fee;
    private double lotSize;
    private double stopLoss;
    private String symbol;

    public String getUserID() {
        return userID;
    }
    private JSONArray orders;
    public Order(String clientTradeID, String triggerCondition, String createTime, String price, String clientTradeID1, String state, String timeInForce, String tradeID) {
        this.clientTradeID = clientTradeID;
        this.triggerCondition = triggerCondition;
        this.createTime = createTime;
        this.price = price;
        this.clientTradeID1 = clientTradeID1;
        this.state = state;
        this.timeInForce = timeInForce;
        this.tradeID = tradeID;
    }

    public String getClientTradeID1() {
        return clientTradeID1;
    }

    public void setClientTradeID1(String clientTradeID1) {
        this.clientTradeID1 = clientTradeID1;
    }

    public String getExecutedQty() {
        return executedQty;
    }

    public void setExecutedQty(String executedQty) {
        this.executedQty = executedQty;
    }

    private static int lastError;
    private int orderID;

    int ticket=orderID;
    protected Long id;
    protected double total;
    protected String currency;

    public String getOrigQty() {
        return origQty;
    }
    protected double takeProfit;
    protected Date updated;
    protected Date closed;
    protected String status;
    private Side side;
    private String filled;
    private String unit;

    public void setOrigQty(String origQty) {
        this.origQty = origQty;
    }


    public int getOrderID() {
        return orderID;
    }

    public void setOrderID(int orderID) {
        this.orderID = orderID;
    }

    public void setSide(Side side) {
        this.side = side;
    }


    public void setFilled(String filled) {
        this.filled = filled;
    }

    public void setCummulativeQuoteQty(String cummulativeQuoteQty) {
        this.cummulativeQuoteQty = cummulativeQuoteQty;
    }

    public JSONArray getOrders() {
        return orders;
    }

    public void setUserID(String string) {
        this.userID = string;
    }

    public String getBatchID() {
        return batchID;
    }

    public double getLotSize() {
        return lotSize;
    }

    public void setLotSize(double lotSize) {
        this.lotSize = lotSize;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public void setOrders(JSONArray orders) {
        this.orders = orders;
    }

    public String getOpenTime() {
        return timestamp;
    }

    public static int getLastError() {
        return lastError;
    }

    public static void setLastError(int lastError) {
        Order.lastError = lastError;
    }

    @Contract(pure = true)
    public static @NotNull String getErrorDescription(int err) {
        return "Error " + err;
    }



    public  int getTicket() {
        return ticket;
    }

    public  void setTicket(int ticket) {
        this.ticket = ticket;
    }

    public  double getStopLoss() {
        return stopLoss;
    }

    public void setStopLoss(double stopLoss) {
        this.stopLoss = stopLoss;
    }

    public  double getOpenPrice() {
        return Double.parseDouble(price);
    }

    public  String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setBatchID(String string) {
        this.batchID = string;
    }

    public String getRequestID() {
        return requestID;
    }

    public double getTakeProfit() {
        return takeProfit;
    }

    public void setTakeProfit(double takeProfit) {
        this.takeProfit = takeProfit;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getId() {
        return String.valueOf(id);
    }

    public void setId(Long id) {
        this.id = id;
    }

    public double getPrice() {
        return Double.parseDouble(price);
    }

    public void setPrice(double price) {
        this.price = String.valueOf(price);
    }

    public double getTotal() {
        return total;
    }

    public void setTotal(double total) {
        this.total = total;
    }

    public double getRemaining() {
        return remaining;
    }

    public void setRemaining(double remaining) {
        this.remaining = remaining;
    }

    public double getFee() {
        return fee;
    }

    public void setFee(double fee) {
        this.fee = fee;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getCreated() {
        return created;
    }

    public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }

    public Date getClosed() {
        return closed;
    }

    public void setClosed(Date closed) {
        this.closed = closed;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void showOrderDetails() {
        System.out.println("id: " + id);
        System.out.println("order_type: " + order_type);
        System.out.println("lotSize: " + lotSize);
        System.out.println("price: " + price);
        System.out.println("total: " + total);
        System.out.println("remaining: " + remaining);
        System.out.println("fee: " + fee);
        System.out.println("currency: " + currency);
        System.out.println("created: " + created);
        System.out.println("updated: " + updated);
        System.out.println("closed: " + closed);
        System.out.println("status: " + status);
        System.out.println("symbol: " + symbol);
        System.out.println("type: " + type);
    }

    public Side getSide() {
        return side;
    }

    public TradePair getTradePair() {
        return tradePair;
    }

    public void setTradePair(TradePair tradePair) {
        this.tradePair = tradePair;
    }

    public void setCreated(String created) {
        this.created = created;
    }

    public String getOrderId() {
        return String.valueOf(orderID);
    }

    public String getFilled() {
        return filled;
    }

    public String getUnit() {
        return unit;
    }

    public String getTimeInForce() {
        return "GTC";
    }

    public String getTime() {
        return timestamp;
    }

    public String getTriggerCondition() {
        return triggerCondition;
    }

    public void setTriggerCondition(String triggerCondition) {
        this.triggerCondition = triggerCondition;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getClientTradeID() {
        return clientTradeID;
    }

    public void setClientTradeID(String clientTradeID) {
        this.clientTradeID = clientTradeID;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setTimeInForce(String timeInForce) {
        this.timeInForce = timeInForce;
    }

    public String getTradeID() {
        return tradeID;
    }

    public void setTradeID(String tradeID) {
        this.tradeID = tradeID;
    }

    public String getStopPrice() {
        return String.valueOf(stopLoss);
    }

    public void setStopPrice(double stopLoss) {
        this.stopLoss = stopLoss;
    }


    public String getIcebergQty() {
        return icebergQty;
    }

    public void setIcebergQty(String icebergQty) {
        this.icebergQty = icebergQty;
    }

    public void setRequestID(String string) {
        this.requestID = string;
    }

    public String getPartialFill() {
        return partialFill;
    }

    public void setPartialFill(String string) {
        this.partialFill = string;
    }

    public String getCummulativeQuoteQty() {
        return cummulativeQuoteQty;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String string) {
        this.reason = string;
    }

    public String getPositionFill() {
        return positionFill;
    }

    public void setPositionFill(String string) {
        this.positionFill = string;
    }

    public String getOrder_type() {
        return order_type;
    }

    public void setOrder_type(String order_type) {
        this.order_type = order_type;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setTime(String string) {
        this.timestamp = string;
    }

    public void setUnits(String string) {
        this.unit = string;
    }

    public JSONObject getClientExtensions() {
        return clientExtensions;
    }

    public void setClientExtensions(JSONObject jsonObject) {
        this.clientExtensions = jsonObject;
    }
}
