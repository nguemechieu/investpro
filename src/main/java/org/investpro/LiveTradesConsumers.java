package org.investpro;

import java.util.ArrayList;

public interface LiveTradesConsumers {
    void consume(LiveTrade liveTrade);
    void close();
    void start();
    ArrayList<LiveTrade> getLiveTrades();
    void setLiveTrades(ArrayList<LiveTrade> liveTrades);
    void setTradePairs(ArrayList<String> tradePairs);
    ArrayList<String> getTradePairs();
    void setAccount(Account account);
    Account getAccount();

}
