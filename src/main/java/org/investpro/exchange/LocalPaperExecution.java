package org.investpro.exchange;

import org.investpro.exchange.contracts.OrderExecutionProvider;
import org.investpro.models.Account;
import org.investpro.models.trading.*;
import org.investpro.utils.Side;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Session-local simulation. This class has no broker or network reference. */
public final class LocalPaperExecution {
    private final Map<String, Order> orders = new LinkedHashMap<>();
    private final Map<String, TradePair> pairs = new HashMap<>();
    private final Map<String, Double> holdings = new HashMap<>();
    private final Map<String, Position> positions = new LinkedHashMap<>();
    private final Map<String, Double> marketPrices = new HashMap<>();
    private double cash = 10_000;
    private long sequence;
    private final OrderExecutionProvider provider = (OrderExecutionProvider) Proxy.newProxyInstance(
            OrderExecutionProvider.class.getClassLoader(), new Class<?>[]{OrderExecutionProvider.class},
            (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "toString" -> "LocalPaperExecution";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> null;
                    };
                }
                synchronized (this) {
                    try {
                        return dispatch(method.getName(), args == null ? new Object[0] : args);
                    } catch (RuntimeException error) {
                        if (CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
                            return CompletableFuture.failedFuture(error);
                        }
                        throw error;
                    }
                }
            });

    public OrderExecutionProvider provider() { return provider; }

    public synchronized Account account(String exchangeId) {
        Account account = new Account();
        account.setAccountId("paper-" + exchangeId);
        account.setExchangeId(exchangeId);
        account.setUsername("Local Paper");
        account.setPaperTrading(true);
        account.setConnected(true);
        account.setAvailableBalance(cash);
        account.setTotalBalance(cash);
        double equity = cash + positions.values().stream().filter(Position::isOpen)
                .mapToDouble(p -> p.getQuantity() * p.getCurrentPrice()).sum();
        account.setEquity(equity);
        account.setOpenPositionCount((int) positions.values().stream().filter(Position::isOpen).count());
        Map<String, Double> balances = new LinkedHashMap<>(holdings);
        balances.put("USD", cash);
        account.setBalances(balances);
        account.setAvailableBalances(balances);
        return account;
    }

    public synchronized List<Position> positions() {
        return positions.values().stream().filter(Position::isOpen).map(p -> {
            Position copy = new Position(p.getTradePair(), p.getSide(), p.getQuantity(), p.getEntryPrice());
            copy.setPositionId(p.getPositionId());
            copy.setCurrentPrice(p.getCurrentPrice());
            copy.setStopLoss(p.getStopLoss());
            copy.setTakeProfit(p.getTakeProfit());
            return copy;
        }).toList();
    }

    /** Local protective exits and pending entries react only to observed market prices. */
    public synchronized void updateMarketPrice(TradePair pair, double price) {
        if (!(price > 0) || !Double.isFinite(price)) return;
        String symbol = pair.toString('/');
        pair.setLast(price);
        marketPrices.put(symbol, price);
        pairs.put(symbol, pair);
        for (Order order : new ArrayList<>(orders.values())) {
            if (!symbol.equals(order.getSymbol()) || !"OPEN".equals(order.getStatus())) continue;
            boolean limitReached = order.getSide() == Side.BUY ? price <= order.getPrice() : price >= order.getPrice();
            if (("LIMIT".equals(order.getType()) || "BRACKET".equals(order.getType())) && limitReached) {
                try {
                    fill(order, price);
                    order.setStatus("FILLED");
                } catch (IllegalArgumentException error) {
                    order.setStatus("REJECTED");
                }
            }
        }
        Position position = positions.get(symbol);
        if (position == null || !position.isOpen()) return;
        position.updateCurrentPrice(price);
        if (position.getStopLoss() > 0 && price <= position.getStopLoss()
                || position.getTakeProfit() > 0 && price >= position.getTakeProfit()) {
            close(pair);
        }
    }

    public synchronized CompletableFuture<String> close(TradePair pair) {
        Position position = positions.get(pair.toString('/'));
        if (position == null || !position.isOpen()) return CompletableFuture.failedFuture(
                new IllegalStateException("No local paper position to close"));
        Order exit = new Order();
        exit.setSymbol(pair.toString('/'));
        exit.setSide(Side.SELL);
        exit.setType("MARKET");
        exit.setQuantity(position.getQuantity());
        return submit(exit);
    }

    private Object dispatch(String method, Object[] args) {
        if (method.equals("createOrder") && args.length > 1) {
            Order order = new Order();
            order.setSymbol(((TradePair) args[1]).toString('/'));
            pairs.put(order.getSymbol(), (TradePair) args[1]);
            order.setType((String) args[2]);
            order.setPrice(((Number) args[3]).doubleValue());
            order.setQuantity(((Number) args[4]).doubleValue());
            order.setSide((Side) args[5]);
            order.setStopLoss(((Number) args[6]).doubleValue());
            order.setTakeProfit(((Number) args[7]).doubleValue());
            return order;
        }
        if (method.equals("createOrder")) {
            return submit((Order) args[0]);
        }
        if (method.startsWith("create") || method.startsWith("place")) {
            TradePair pair = (TradePair) args[0];
            pairs.put(pair.toString('/'), pair);
            Order order = new Order();
            order.setSymbol(pair.toString('/'));
            order.setSide((Side) args[1]);
            order.setQuantity(((Number) args[2]).doubleValue());
            order.setType(method.contains("Market") ? "MARKET" : method.contains("Limit") ? "LIMIT"
                    : method.contains("Bracket") ? "BRACKET" : method.contains("Trailing") ? "TRAILING_STOP" : "STOP");
            order.setPrice(args.length > 3 ? ((Number) args[3]).doubleValue() : pair.getLastPrice());
            if (method.contains("Bracket")) {
                order.setStopLoss(((Number) args[4]).doubleValue());
                order.setTakeProfit(((Number) args[5]).doubleValue());
            }
            return submit(order);
        }
        return switch (method) {
            case "cancelOrder" -> CompletableFuture.completedFuture(cancel((String) args[0]));
            case "cancelOrders" -> CompletableFuture.completedFuture(((List<?>) args[0]).stream()
                    .map(id -> cancel((String) id)).toList());
            case "cancelAllOrders" -> {
                orders.keySet().forEach(this::cancel);
                yield CompletableFuture.completedFuture("paper-cancelled");
            }
            case "fetchOrder" -> CompletableFuture.completedFuture(Optional.ofNullable(orders.get((String) args[0])));
            case "fetchOrderHistory" -> CompletableFuture.completedFuture(orders.values().stream()
                    .filter(o -> args[0] == null || o.getSymbol().equals(((TradePair) args[0]).toString('/')))
                    .filter(o -> args[1] == null || !o.getDate().toInstant().isBefore((Instant) args[1])).toList());
            case "fetchOpenOrders", "fetchAllOpenOrders" -> CompletableFuture.completedFuture(orders.entrySet().stream()
                    .filter(e -> "OPEN".equals(e.getValue().getStatus()))
                    .filter(e -> args.length == 0 || args[0] == null
                            || e.getValue().getSymbol().equals(((TradePair) args[0]).toString('/')))
                    .map(e -> {
                        Order o = e.getValue();
                        OpenOrder open = new OpenOrder();
                        open.setOrderId(e.getKey());
                        open.setTradePair(pairs.get(o.getSymbol()));
                        open.setSide(o.getSide());
                        open.setPrice(o.getPrice());
                        open.setSize(o.getQuantity());
                        open.setRemainingSize(o.getQuantity());
                        open.setStatus(OpenOrder.OrderStatus.OPEN);
                        return open;
                    }).toList());
            default -> CompletableFuture.failedFuture(new UnsupportedOperationException("Unsupported local paper operation."));
        };
    }

    private CompletableFuture<String> submit(Order order) {
        if (order.getQuantity() <= 0 || !Double.isFinite(order.getQuantity()) || order.getSide() == null) {
            throw new IllegalArgumentException("Paper order requires a valid side and positive quantity.");
        }
        boolean market = "MARKET".equalsIgnoreCase(order.getType())
                || "BRACKET".equals(order.getType()) && order.getPrice() == 0;
        if (!market && (!(order.getPrice() > 0) || !Double.isFinite(order.getPrice()))) {
            throw new IllegalArgumentException("Pending paper order requires a positive price.");
        }
        if (market) {
            TradePair pair = pairs.get(order.getSymbol());
            double price = marketPrices.getOrDefault(order.getSymbol(), pair == null ? order.getPrice() : pair.getLastPrice());
            if (!(price > 0) || !Double.isFinite(price)) {
                throw new IllegalArgumentException("Paper market order requires a current market price.");
            }
            fill(order, price);
        }
        order.setId(++sequence);
        order.setDate(new Date());
        order.setStatus(market ? "FILLED" : "OPEN");
        String id = "paper-" + sequence;
        orders.put(id, order);
        return CompletableFuture.completedFuture(id);
    }

    private void fill(Order order, double price) {
        double quantity = order.getQuantity();
        double held = holdings.getOrDefault(order.getSymbol(), 0.0);
        if (order.getSide() == Side.BUY && cash < price * quantity
                || order.getSide() == Side.SELL && held < quantity) {
            throw new IllegalArgumentException("Insufficient local paper balance.");
        }
        double signed = order.getSide() == Side.BUY ? quantity : -quantity;
        cash -= price * signed;
        holdings.put(order.getSymbol(), held + signed);
        order.setPrice(price);
        Position existing = positions.get(order.getSymbol());
        if (order.getSide() == Side.BUY) {
            double entry = existing != null && existing.isOpen()
                    ? (existing.getEntryPrice() * held + price * quantity) / (held + quantity) : price;
            Position position = new Position(pairs.get(order.getSymbol()), Side.BUY, held + quantity, entry);
            position.setCurrentPrice(price);
            position.setStopLoss(order.getStopLoss());
            position.setTakeProfit(order.getTakeProfit());
            positions.put(order.getSymbol(), position);
        } else if (existing != null) {
            existing.setQuantity(held - quantity);
            existing.setOpen(held > quantity);
        }
    }

    private String cancel(String id) {
        Order order = orders.get(id);
        if (order != null && "OPEN".equals(order.getStatus())) { order.setStatus("CANCELLED"); }
        return id;
    }
}
