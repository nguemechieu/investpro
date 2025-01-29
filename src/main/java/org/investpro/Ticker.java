package org.investpro;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
@Setter
public class Ticker {

    private static final Logger logger = LoggerFactory.getLogger(Ticker.class);
    private String symbol;
    private double bidPrice;
    private double volume;

    private double askPrice;
    private long timestamp;


    public Ticker() {


    }

    double getBidPrice() {
        return bidPrice;
    }

    public void setBidPrice(double bid) {
        logger.info("{}: Bid price set to {}", symbol, bid);
        // Update the bid price in the relevant data structures or send an event
        this.bidPrice = bid;
    }


    public void setAskPrice(double ask) {
        logger.info("{}: Ask price set to {}", symbol, ask);
        // Update the ask price in the relevant data structures or send an event
        this.askPrice = ask;
    }

    public void setVolume(double volume) {
        logger.info("{}: Volume set to {}", symbol, volume);
        // Update the volume in the relevant data structures or send an event
        this.volume = volume;
    }


    public void setTimestamp(long timestamp) {
        logger.info("{}: Timestamp set to {}", symbol, timestamp);
        // Update the timestamp in the relevant data structures or send an event
        // Assuming this is a time-based data structure
        // This method can be implemented in a more specific way based on the actual data structure

        this.timestamp = timestamp;
    }
}
