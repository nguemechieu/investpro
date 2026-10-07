package org.investpro.ui.tradingdesk;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.models.trading.TradePair;

public final class TradingDeskState {

    private final StringProperty selectedExchange = new SimpleStringProperty("");
    private final ObjectProperty<TradePair> selectedTradePair = new SimpleObjectProperty<>();
    private final ObjectProperty<Timeframe> selectedTimeframe = new SimpleObjectProperty<>();
    private final BooleanProperty connected = new SimpleBooleanProperty(false);
    private final BooleanProperty liveMode = new SimpleBooleanProperty(true);
    private final BooleanProperty paperMode = new SimpleBooleanProperty(false);
    private final BooleanProperty streaming = new SimpleBooleanProperty(false);

    private final StringProperty statusMessage = new SimpleStringProperty("");


    public String getSelectedExchange() {
        return selectedExchange.get();
    }

    public void setSelectedExchange(String value) {
        selectedExchange.set(value == null ? "" : value);
    }


    public TradePair getSelectedTradePair() {
        return selectedTradePair.get();
    }

    public void setSelectedTradePair(TradePair value) {
        selectedTradePair.set(value);
    }



    public Timeframe getSelectedTimeframe() {
        return selectedTimeframe.get();
    }

    public void setSelectedTimeframe(Timeframe value) {
        selectedTimeframe.set(value);
    }

    public boolean isConnected() {
        return connected.get();
    }

    public void setConnected(boolean value) {
        connected.set(value);
    }



    public boolean isLiveMode() {
        return liveMode.get();
    }

    public void setLiveMode(boolean value) {
        liveMode.set(value);
        paperMode.set(!value);
    }



    public void setPaperMode(boolean value) {
        paperMode.set(value);
        liveMode.set(!value);
    }

    public boolean isStreaming() {
        return streaming.get();
    }

    public void setStreaming(boolean value) {
        streaming.set(value);
    }


    public void setStatusMessage(String value) {
        statusMessage.set(value == null ? "" : value);
    }
}
