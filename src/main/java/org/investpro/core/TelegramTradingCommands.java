package org.investpro.core;

import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;
import org.investpro.models.trading.Ticker;
import org.investpro.utils.Side;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Explicit remote actions, isolated from AI-generated text. */
final class TelegramTradingCommands {
    private final SystemCore core;
    private java.util.function.Function<String, String> botControl;
    synchronized void setBotControl(java.util.function.Function<String, String> control) { botControl = control; }
    private final Map<String, Pending> pending = new HashMap<>();
    private final Map<String, Set<String>> watches = new HashMap<>();
    private record Pending(String code, Exchange exchange, String mode, Instant expires,
                           String action, TradePair pair, Side side, double quantity, double price, String orderId,
                           double stopLoss, double takeProfit, boolean trailingPercent) {}

    TelegramTradingCommands(SystemCore core) { this.core = core; }

    synchronized String handle(String user, String command, String[] args) throws Exception {
        Exchange exchange = core.getExchange();
        return switch (command) {
            case "mode", "exchange" -> "Exchange: " + exchange.getName() + "\nMode: "
                    + exchange.getResolvedTradingMode() + "\nChange exchange/mode in the desktop app.";
            case "portfolio" -> {
                var account = exchange.tradingAccount().get(15, TimeUnit.SECONDS);
                yield "Portfolio (" + exchange.getResolvedTradingMode() + ")\n"
                        + Objects.toString(account.getBalances(), "No balance breakdown available");
            }
            case "history" -> {
                var orders = exchange.orderExecution().fetchOrderHistory(null, Instant.now().minusSeconds(604800))
                        .get(15, TimeUnit.SECONDS);
                StringBuilder text = new StringBuilder("Order history, last 7 days (" + exchange.getResolvedTradingMode() + ")\n");
                orders.stream().limit(30).forEach(order -> text.append(order.getSymbol()).append(" ")
                        .append(order.getSide()).append(" ").append(order.getQuantity()).append(" ")
                        .append(order.getStatus()).append("\n"));
                yield text.toString();
            }
            case "watch", "unwatch", "watchlist" -> {
                Set<String> symbols = watches.computeIfAbsent(user, ignored -> new LinkedHashSet<>());
                if (!command.equals("watchlist")) {
                    if (args.length != 2) yield "Usage: /" + command + " BTC/USD";
                    String symbol = pair(args[1]).toString('/');
                    if (command.equals("unwatch")) symbols.remove(symbol);
                    else if (symbols.size() < 30) symbols.add(symbol);
                    else yield "Watchlist limit: 30 symbols.";
                }
                StringBuilder text = new StringBuilder("Your watchlist\n");
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                for (String symbol : symbols) {
                    if (System.nanoTime() >= deadline) { text.append(symbol).append(" quote not refreshed (time limit)\n"); continue; }
                    try {
                        Ticker ticker = exchange.fetchTicker(pair(symbol)).get(5, TimeUnit.SECONDS);
                        text.append(symbol).append(" bid ").append(ticker.getBidPrice())
                                .append(" ask ").append(ticker.getAskPrice()).append("\n");
                    } catch (Exception error) { text.append(symbol).append(" quote unavailable\n"); }
                }
                yield symbols.isEmpty() ? "Watchlist empty. Use /watch BTC/USD." : text.toString();
            }
            case "size" -> {
                if (args.length != 5) yield "Usage: /size EQUITY RISK_PERCENT ENTRY STOP";
                double equity = positive(args[1]), risk = positive(args[2]);
                double entry = positive(args[3]), stop = positive(args[4]);
                if (risk > 100 || entry == stop) yield "Risk must be at most 100%; entry and stop must differ.";
                double budget = equity * risk / 100;
                double quantity = budget / Math.abs(entry - stop);
                if (!Double.isFinite(quantity) || !Double.isFinite(budget)) yield "Values are too large.";
                yield String.format(Locale.ROOT, "Risk budget: %.4f\nQuantity: %.8f units\nNotional: %.4f\n"
                        + "Assumes linear pricing; excludes fees, gaps and contract multipliers.", budget, quantity, quantity * entry);
            }
            case "pause" -> { core.setAutoTradingEnabled(false); yield "Automatic trading paused. Existing orders remain open."; }
            case "botstop" -> botControl == null ? "Desktop bot controls unavailable." : botControl.apply("stop");
            case "buy", "sell", "limit", "stop", "trailing", "bracket", "cancel", "cancelall", "resume", "botstart" -> {
                if (!exchange.isPaperTrading() && !exchange.canSubmitLiveOrders())
                    yield "Live exchange is not authenticated and connected. Connect in the desktop app first.";
                TradePair symbol = null;
                Side side = command.equals("sell") ? Side.SELL : Side.BUY;
                double quantity = 0, price = 0;
                double stopLoss = 0, takeProfit = 0;
                boolean trailingPercent = false;
                String id = null;
                if (command.equals("buy") || command.equals("sell")) {
                    if (args.length != 3) yield "Usage: /" + command + " BTC/USD QUANTITY";
                    symbol = pair(args[1]); quantity = positive(args[2]);
                } else if (Set.of("limit", "stop", "trailing", "bracket").contains(command)) {
                    int expected = command.equals("bracket") ? 7 : command.equals("trailing") ? 6 : 5;
                    if (args.length != expected) yield "Usage: /" + command + " buy|sell SYMBOL QUANTITY PRICE"
                            + (command.equals("bracket") ? " STOP_LOSS TAKE_PROFIT" : command.equals("trailing") ? " amount|percent" : "");
                    side = Side.valueOf(args[1].toUpperCase(Locale.ROOT));
                    if (side != Side.BUY && side != Side.SELL) yield "Side must be buy or sell.";
                    symbol = pair(args[2]); quantity = positive(args[3]); price = positive(args[4]);
                    if (command.equals("bracket")) {
                        stopLoss = positive(args[5]); takeProfit = positive(args[6]);
                        if (side == Side.BUY ? !(stopLoss < price && price < takeProfit) : !(takeProfit < price && price < stopLoss))
                            yield "Bracket prices must put the stop on the loss side and take profit on the profit side.";
                    }
                    if (command.equals("trailing")) {
                        if (!Set.of("amount", "percent").contains(args[5].toLowerCase(Locale.ROOT))) yield "Trailing unit must be amount or percent.";
                        trailingPercent = args[5].equalsIgnoreCase("percent");
                        if (trailingPercent && price > 100) yield "Trailing percent must be at most 100.";
                    }
                } else if (command.equals("cancel")) {
                    if (args.length != 2) yield "Usage: /cancel ORDER_ID";
                    id = args[1];
                } else if (args.length != 1) yield "Usage: /" + command;
                if (command.equals("botstart") && botControl == null) yield "Desktop bot controls unavailable.";
                pending.values().removeIf(value -> Instant.now().isAfter(value.expires()));
                String code = UUID.randomUUID().toString().substring(0, 8);
                pending.put(user, new Pending(code, exchange, exchange.getResolvedTradingMode(),
                        Instant.now().plusSeconds(60), command, symbol, side, quantity, price, id, stopLoss, takeProfit, trailingPercent));
                yield "Review action\nExchange: " + exchange.getName() + "\nMode: " + exchange.getResolvedTradingMode()
                        + "\nAction: " + command + (symbol == null ? "" : " " + side + " " + symbol + " " + quantity
                        + " units" + (price > 0 ? " at " + price : " at market price"))
                        + (id == null ? "" : " " + id)
                        + (command.equals("bracket") ? "\nStop loss: " + stopLoss + "; take profit: " + takeProfit : "")
                        + (command.equals("trailing") ? "\nTrailing distance: " + price + (trailingPercent ? "%" : " price units") : "")
                        + "\nConfirm within 60 seconds: /confirm " + code
                        + "\nDiscard: /abort";
            }
            case "abort" -> { pending.remove(user); yield "Pending action discarded."; }
            case "confirm" -> {
                Pending action = pending.get(user);
                if (args.length != 2 || action == null || !action.code().equals(args[1])) yield "No matching pending action.";
                pending.remove(user); // Consume before contacting broker; uncertain results must never auto-retry.
                if (Instant.now().isAfter(action.expires())) yield "Confirmation expired. Request a new preview.";
                if (exchange != action.exchange() || !exchange.getResolvedTradingMode().equals(action.mode()))
                    yield "Exchange or mode changed. Request a new preview.";
                if (!exchange.isPaperTrading() && !exchange.canSubmitLiveOrders()) yield "Live connection no longer available.";
                if (action.action().equals("botstart")) yield botControl == null ? "Desktop bot controls unavailable." : botControl.apply("start");
                var execution = exchange.orderExecution();
                if (action.action().equals("resume")) {
                    core.setAutoTradingEnabled(true);
                    yield core.isAutoTradingEnabled() ? "Automatic trading enabled." : "Automatic trading blocked by system health.";
                }
                String result;
                if (action.action().equals("cancel")) result = execution.cancelOrder(action.orderId()).get(15, TimeUnit.SECONDS);
                else if (action.action().equals("cancelall")) result = execution.cancelAllOrders().get(15, TimeUnit.SECONDS);
                else if (action.action().equals("bracket")) result = execution.createBracketOrder(action.pair(), action.side(), action.quantity(),
                        action.price(), action.stopLoss(), action.takeProfit()).get(15, TimeUnit.SECONDS);
                else if (action.action().equals("trailing")) result = execution.createTrailingStopOrder(action.pair(), action.side(), action.quantity(),
                        action.price(), action.trailingPercent()).get(15, TimeUnit.SECONDS);
                else if (action.action().equals("limit")) result = execution
                        .createLimitOrder(action.pair(), action.side(), action.quantity(), action.price()).get(15, TimeUnit.SECONDS);
                else if (action.action().equals("stop")) result = execution
                        .createStopOrder(action.pair(), action.side(), action.quantity(), action.price()).get(15, TimeUnit.SECONDS);
                else {
                    Ticker ticker = exchange.fetchTicker(action.pair()).get(10, TimeUnit.SECONDS);
                    double quote = action.side() == Side.BUY ? ticker.getAskPrice() : ticker.getBidPrice();
                    if (!Double.isFinite(quote) || quote <= 0) yield "Valid market quote unavailable; action not submitted.";
                    action.pair().setLast(quote);
                    if (core.getExchange() != exchange || !exchange.getResolvedTradingMode().equals(action.mode()))
                        yield "Exchange or mode changed while fetching the quote. Request a new preview.";
                    if (!exchange.isPaperTrading() && !exchange.canSubmitLiveOrders()) yield "Live connection lost; order not submitted.";
                    result = execution.createMarketOrder(action.pair(), action.side(), action.quantity()).get(15, TimeUnit.SECONDS);
                }
                yield "Exchange response (" + action.mode() + "): " + result + "\nUse /orders or /history to verify status.";
            }
            default -> null;
        };
    }

    private static double positive(String value) {
        double number = Double.parseDouble(value);
        if (!Double.isFinite(number) || number <= 0) throw new IllegalArgumentException("Expected a finite positive number.");
        return number;
    }

    private static TradePair pair(String symbol) throws Exception {
        if (TradePair.isDerivativeProductSymbol(symbol)) return TradePair.fromSymbol(symbol);
        String[] values = symbol.toUpperCase(Locale.ROOT).split("[/_-]", -1);
        if (values.length != 2 || !values[0].matches("[A-Z0-9.]{1,20}") || !values[1].matches("[A-Z0-9.]{1,20}"))
            throw new IllegalArgumentException("Use a symbol such as BTC/USD.");
        return new TradePair(values[0], values[1]);
    }
}
