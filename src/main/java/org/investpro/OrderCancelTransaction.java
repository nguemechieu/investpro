
package org.investpro;

import org.json.JSONObject;

public class OrderCancelTransaction {
    public String orderID;
    public String requestID;
    public String id;
    public String time;
    public String batchID;
    public String type;
    public String userID;
    public String timeInForce;
    public String positionFill;
    public String reason;
    public String instrument;

    public OrderCancelTransaction(JSONObject orderCancelTransaction) {
        JSONObject jsonObject = orderCancelTransaction;
        this.orderID = jsonObject.getString("orderID");
        this.requestID = jsonObject.getString("requestID");
        this.id = jsonObject.getString("id");
        this.time = jsonObject.getString("time");
        this.batchID = jsonObject.getString("batchID");
        this.type = jsonObject.getString("type");
        this.userID = jsonObject.getString("userID");
        this.timeInForce = jsonObject.getString("timeInForce");
        this.positionFill = jsonObject.getString("positionFill");
        this.reason = jsonObject.getString("reason");
        this.instrument = jsonObject.getString("instrument");
    }

    @Override
    public String toString() {
        return "OrderCancelTransaction{" +
                "orderID='" + orderID + '\'' +
                ", requestID='" + requestID + '\'' +
                ", id='" + id + '\'' +
                ", time='" + time + '\'' +
                ", batchID='" + batchID + '\'' +
                ", type='" + type + '\'' +
                ", userID='" + userID + '\'' +
                ", timeInForce='" + timeInForce + '\'' +
                ", positionFill='" + positionFill + '\'' +
                ", reason='" + reason + '\'' +
                ", instrument='" + instrument + '\'' +
                '}';
    }

    public String getOrderID() {
        return orderID;
    }

    public void setOrderID(String orderID) {
        this.orderID = orderID;
    }

    public String getRequestID() {
        return requestID;
    }

    public void setRequestID(String requestID) {
        this.requestID = requestID;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getBatchID() {
        return batchID;
    }

    public void setBatchID(String batchID) {
        this.batchID = batchID;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUserID() {
        return userID;
    }

    public void setUserID(String userID) {
        this.userID = userID;
    }

    public String getTimeInForce() {
        return timeInForce;
    }

    public void setTimeInForce(String timeInForce) {
        this.timeInForce = timeInForce;
    }

    public String getPositionFill() {
        return positionFill;
    }

    public void setPositionFill(String positionFill) {
        this.positionFill = positionFill;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getInstrument() {
        return instrument;
    }

    public void setInstrument(String instrument) {
        this.instrument = instrument;
    }
}
