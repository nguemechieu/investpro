package org.investpro;

import org.bounce.message.Message;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

public class OrderCreateTransaction {

    //    public OrderCreateTransaction(JSONObject orderCreateTransaction) {
//        System.out.println(jsonObject.getJSONObject("orderCancelTransaction").getString("id"));
//        System.out.println(jsonObject.getJSONObject("lastTransactionID"));
//        System.out.println(jsonObject.getJSONObject("relatedTransactionIDs"));
//        System.out.println(jsonObject.getJSONObject("orderCancelTransaction").getString("orderID"));
//        System.out.println(jsonObject.getJSONObject("orderCancelTransaction").getString("requestID"));
//        System.out.println(jsonObject.getJSONObject("orderCancelTransaction").getString("id"));
//        System.out.println(jsonObject.getJSONObject("orderCancelTransaction").getString("time"));
//        System.out.println(jsonObject.getJSONObject("orderCancelTransaction").getString("batchID"));
//        System.out.println(jsonObject.getJSONObject("orderCancelTransaction").getString("type"));
//        System.out.println(jsonObject.getJSONObject("orderCancelTransaction").getString("userID"));
//        System.out.println(jsonObject.getJSONObject("orderCancelTransaction").getString("timeInForce"));
//        System.out.println(jsonObject.getJSONObject("orderCancelTransaction").getString("positionFill"));
//        System.out.println(jsonObject.getJSONObject("orderCancelTransaction").getString("reason"));
//        System.out.println(jsonObject.getJSONObject("orderCancelTransaction").getString("instrument"));
    public String id;
    public String orderID;
    public String requestID;
    public String time;
    public String batchID;
    public String type;
    public String userID;
    public String timeInForce;
    public String positionFill;
    public String reason;
    public String instrument;

    public OrderCreateTransaction(@NotNull JSONObject jsonObject) {
        if (jsonObject.has("orderCancelTransaction")) {
            this.id = jsonObject.getJSONObject("orderCancelTransaction").getString("id");
            this.orderID = jsonObject.getJSONObject("orderCancelTransaction").getString("orderID");
            this.requestID = jsonObject.getJSONObject("orderCancelTransaction").getString("requestID");
            this.time = jsonObject.getJSONObject("orderCancelTransaction").getString("time");
            this.batchID = jsonObject.getJSONObject("orderCancelTransaction").getString("batchID");
            this.type = jsonObject.getJSONObject("orderCancelTransaction").getString("type");
            this.userID = jsonObject.getJSONObject("orderCancelTransaction").getString("userID");
            this.timeInForce = jsonObject.getJSONObject("orderCancelTransaction").getString("timeInForce");
            this.positionFill = jsonObject.getJSONObject("orderCancelTransaction").getString("positionFill");
            this.reason = jsonObject.getJSONObject("orderCancelTransaction").getString("reason");
            this.instrument = jsonObject.getJSONObject("orderCancelTransaction").getString("instrument");
            this.orderID = jsonObject.getString("orderID");

        } else {
//            "accountID": "001-001-2783446-002",
//                    "reason": "CLIENT_ORDER",
//                    "requestID": "43080139052008057",
//                    "instrument": "EUR_HKD",
//                    "id": "143321",
//                    "time": "2023-04-10T23:55:48.747788856Z",
//                    "units": "1000",
//                    "batchID": "143321",
//                    "type": "MARKET_ORDER",
//                    "userID": 2783446,
//                    "timeInForce": "FOK",
//                    "positionFill": "DEFAULT"


            this.id = jsonObject.getString("id");
            this.orderID = id;
            this.requestID = jsonObject.getString("requestID");
            this.time = jsonObject.getString("time");
            this.batchID = jsonObject.getString("batchID");
            this.type = jsonObject.getString("type");

            this.timeInForce = jsonObject.getString("timeInForce");
            this.positionFill = jsonObject.getString("positionFill");
            this.reason = jsonObject.getString("reason");

        }
    }

    @Override
    public String toString() {
        return STR."OrderCreateTransaction{id='\{id}\{'\''}, orderID='\{orderID}\{'\''}, requestID='\{requestID}\{'\''}, time='\{time}\{'\''}, batchID='\{batchID}\{'\''}, type='\{type}\{'\''}, userID='\{userID}\{'\''}, timeInForce='\{timeInForce}\{'\''}, positionFill='\{positionFill}\{'\''}, reason='\{reason}\{'\''}, instrument='\{instrument}\{'\''}\{'}'}";
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