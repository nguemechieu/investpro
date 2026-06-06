package org.investpro.ui.tradingdesk;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.models.Account;
import org.investpro.models.trading.TradePair;

public final class TradingDeskState {

    private final StringProperty selectedExchange = new SimpleStringProperty("");
    private final ObjectProperty<TradePair> selectedTradePair = new SimpleObjectProperty<>();
    private final ObjectProperty<Timeframe> selectedTimeframe = new SimpleObjectProperty<>();
    private final BooleanProperty connected = new SimpleBooleanProperty(false);
    private final BooleanProperty liveMode = new SimpleBooleanProperty(true);
    private final BooleanProperty paperMode = new SimpleBooleanProperty(false);
    private final BooleanProperty streaming = new SimpleBooleanProperty(false);
    private final ObjectProperty<Account> selectedAccount = new SimpleObjectProperty<>();
    private final StringProperty statusMessage = new SimpleStringProperty("");

    public StringProperty selectedExchangeProperty() {
        return selectedExchange;
    }

    public String getSelectedExchange() {
        return selectedExchange.get();
    }

    public void setSelectedExchange(String value) {
        selectedExchange.set(value == null ? "" : value);
    }

    public ObjectProperty<TradePair> selectedTradePairProperty() {
        return selectedTradePair;
    }

    public TradePair getSelectedTradePair() {
        return selectedTradePair.get();
    }

    public void setSelectedTradePair(TradePair value) {
        selectedTradePair.set(value);
    }

    public ObjectProperty<Timeframe> selectedTimeframeProperty() {
        return selectedTimeframe;
    }

    public Timeframe getSelectedTimeframe() {
        return selectedTimeframe.get();
    }

    public void setSelectedTimeframe(Timeframe value) {
        selectedTimeframe.set(value);
    }

    public BooleanProperty connectedProperty() {
        return connected;
    }

    public boolean isConnected() {
        return connected.get();
    }

    public void setConnected(boolean value) {
        connected.set(value);
    }

    public BooleanProperty liveModeProperty() {
        return liveMode;
    }

    public boolean isLiveMode() {
        return liveMode.get();
    }

    public void setLiveMode(boolean value) {
        liveMode.set(value);
        paperMode.set(!value);
    }

    public BooleanProperty paperModeProperty() {
        return paperMode;
    }

    public boolean isPaperMode() {
        return paperMode.get();
    }

    public void setPaperMode(boolean value) {
        paperMode.set(value);
        liveMode.set(!value);
    }

    public BooleanProperty streamingProperty() {
        return streaming;
    }

    public boolean isStreaming() {
        return streaming.get();
    }

    public void setStreaming(boolean value) {
        streaming.set(value);
    }

    public ObjectProperty<Account> selectedAccountProperty() {
        return selectedAccount;
    }

    public Account getSelectedAccount() {
        return selectedAccount.get();
    }

    public void setSelectedAccount(Account value) {
        selectedAccount.set(value);
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    public String getStatusMessage() {
        return statusMessage.get();
    }

    public void setStatusMessage(String value) {
        statusMessage.set(value == null ? "" : value);
    }
}
