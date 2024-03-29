package org.investpro;


import java.util.Date;

public class Order {

    String Symbol;
    double Quantity;
    double Price;
    double Commission;
    double TakeProfit;
    double StopLoss;
    double swap;
    double profit;
    private String status;
    private Date date;
    private String Type;
    private String triggerCondition;
    private String instrument;
    private String accountID;
    private String orderId;
    private String batchID;
    private String requestID;
    private String time;
    private String units;
    private String clientTradeID;
    private String createTime;
    private Long id;
    private String state;
    private String timeInForce;
    private Side side;
    private String replacesOrderID;
    private String positionFill;
    private String partialFill;
    private String clientExtention;
    private String triggerMode;
    private int orderID;

    public Order(Date date, String type, String symbol, double quantity, double price, double commission, double takeProfit, double stopLoss, double swap, double profit) {
        this.date = date;
        Type = type;
        Symbol = symbol;
        Quantity = quantity;
        Price = price;
        Commission = commission;
        TakeProfit = takeProfit;
        StopLoss = stopLoss;
        this.swap = swap;
        this.profit = profit;
    }

    public Order() {

    }

    public String getType() {
        return Type;
    }

    public void setType(String type) {
        Type = type;
    }

    public String getSymbol() {
        return Symbol;
    }

    public void setSymbol(String symbol) {
        Symbol = symbol;
    }

    public double getQuantity() {
        return Quantity;
    }

    public void setQuantity(double quantity) {
        Quantity = quantity;
    }

    public double getPrice() {
        return Price;
    }

    public void setPrice(double price) {
        Price = price;
    }

    public double getCommission() {
        return Commission;
    }

    public void setCommission(double commission) {
        Commission = commission;
    }

    public double getTakeProfit() {
        return TakeProfit;
    }

    public void setTakeProfit(double takeProfit) {
        TakeProfit = takeProfit;
    }

    public double getStopLoss() {
        return StopLoss;
    }

    public void setStopLoss(double stopLoss) {
        StopLoss = stopLoss;
    }

    public double getSwap() {
        return swap;
    }

    public void setSwap(double swap) {
        this.swap = swap;
    }

    public double getProfit() {
        return profit;
    }

    public void setProfit(double profit) {
        this.profit = profit;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTriggerCondition() {
        return triggerCondition;
    }

    public void setTriggerCondition(String triggerCondition) {
        this.triggerCondition = triggerCondition;
    }

    public String getInstrument() {
        return instrument;
    }

    public void setInstrument(String string) {
        this.instrument = string;
    }

    public String getAccountID() {
        return accountID;
    }

    public void setAccountID(String string) {

        this.accountID = string;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String string) {
        this.orderId = string;
    }

    public String getBatchID() {
        return batchID;
    }

    public void setBatchID(String string) {
        this.batchID = string;
    }

    public String getRequestID() {
        return requestID;
    }

    public void setRequestID(String string) {
        this.requestID = string;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String string) {
        this.time = string;
    }

    public String getUnits() {
        return units;
    }

    public void setUnits(String string) {
        this.units = string;
    }

    public String getClientTradeID() {
        return clientTradeID;
    }

    public void setClientTradeID(String clientTradeID) {
        this.clientTradeID = clientTradeID;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getTimeInForce() {
        return timeInForce;
    }

    public void setTimeInForce(String timeInForce) {
        this.timeInForce = timeInForce;
    }

    public Side getSide() {
        return side;
    }

    public void setSide(Side side) {
        this.side = side;
    }

    public void setTradeID(String tradeID) {
        this.clientTradeID = tradeID;
    }

    public String getReplacesOrderID() {
        return replacesOrderID;
    }

    public void setReplacesOrderID(String replacesOrderID) {
        this.replacesOrderID = replacesOrderID;
    }

    public String getPositionFill() {
        return positionFill;
    }

    public void setPositionFill(String string) {
        this.positionFill = string;
    }

    public String getPartialFill() {
        return partialFill;
    }

    public void setPartialFill(String string) {
        this.partialFill = string;
    }

    public void setReason(String string) {

    }

    public void setClientExtensions(String jsonObject) {
        this.clientExtention = jsonObject;
    }

    public String getClientExtention() {
        return clientExtention;
    }

    public String getTriggerMode() {
        return triggerMode;
    }

    public void setTriggerMode(String triggerMode) {
        this.triggerMode = triggerMode;
    }

    public int getOrderID() {
        return orderID;
    }

    public void setOrderID(int orderID) {
        this.orderID = orderID;
    }
}
