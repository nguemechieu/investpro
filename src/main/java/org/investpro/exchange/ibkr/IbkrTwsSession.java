package org.investpro.exchange.ibkr;

import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/** One official EClientSocket/EReader session for accounts, contracts, quotes and orders. */
public class IbkrTwsSession {
    private final CopyOnWriteArrayList<BiConsumer<String, Object[]>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicInteger orderIds = new AtomicInteger(-1);
    private final AtomicInteger requestIds = new AtomicInteger(1_000_000);
    private final AtomicInteger sessionGeneration = new AtomicInteger();
    private final ConcurrentHashMap<Integer, CompletableFuture<String>> pendingOrders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, CompletableFuture<String>> pendingCancels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, double[]> quotes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> quoteIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, CompletableFuture<Ticker>> quoteWaiters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, Double>> summaries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, List<org.investpro.data.CandleData>> bars = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, CompletableFuture<List<org.investpro.data.CandleData>>> histories = new ConcurrentHashMap<>();
    private volatile Object client;
    private volatile Object signal;
    private volatile Thread pump;
    private volatile IbkrConnectionProfile profile;
    private volatile List<String> accounts = List.of();
    private volatile String selectedAccount = "";
    private volatile String baseCurrency = "BASE";
    private volatile String failure = "Not connected to IBKR API.";
    private volatile boolean apiAvailable;
    private volatile CompletableFuture<Void> ready = new CompletableFuture<>();
    private volatile CompletableFuture<IbkrAccountSnapshot> summaryFuture;
    private volatile int summaryRequest;
    private volatile IbkrAccountSnapshot snapshot;
    private final Map<Integer, Position> positions = new ConcurrentHashMap<>();
    private final Map<Integer, OpenOrder> openOrders = new ConcurrentHashMap<>();
    private volatile CompletableFuture<List<Position>> positionFuture;
    private volatile CompletableFuture<List<OpenOrder>> openOrderFuture;

    public synchronized CompletableFuture<List<Position>> positions() {
        if (positionFuture != null && !positionFuture.isDone()) return positionFuture;
        positions.clear();
        positionFuture = new CompletableFuture<>();
        try { call(client(), "reqPositions"); }
        catch (RuntimeException error) { positionFuture.completeExceptionally(error); }
        return positionFuture.orTimeout(15, TimeUnit.SECONDS);
    }

    public synchronized CompletableFuture<List<OpenOrder>> openOrders() {
        if (openOrderFuture != null && !openOrderFuture.isDone()) return openOrderFuture;
        openOrders.clear();
        openOrderFuture = new CompletableFuture<>();
        // Request this client's orders; cancellation remains scoped to their broker IDs.
        try { call(client(), "reqOpenOrders"); }
        catch (RuntimeException error) { openOrderFuture.completeExceptionally(error); }
        return openOrderFuture.orTimeout(15, TimeUnit.SECONDS);
    }

    private static TradePair pair(Object contract) {
        try { return new TradePair(String.valueOf(call(contract, "symbol")), String.valueOf(call(contract, "currency"))); }
        catch (Exception error) { throw new IllegalStateException("Unable to map IBKR contract", error); }
    }

    public void addListener(BiConsumer<String, Object[]> listener) { listeners.addIfAbsent(listener); }

    public synchronized void connect(IbkrConnectionProfile connectionProfile, String accountId) {
        disconnect();
        int generation = sessionGeneration.get();
        profile = connectionProfile;
        selectedAccount = accountId == null ? "" : accountId.trim();
        ready = new CompletableFuture<>();
        accounts = List.of();
        orderIds.set(-1);
        snapshot = null;
        failure = "Waiting for IBKR API handshake and managed accounts.";
        try {
            Class<?> wrapperType = IbkrApiRuntime.type("com.ib.client.EWrapper");
            Object wrapper = Proxy.newProxyInstance(wrapperType.getClassLoader(), new Class<?>[]{wrapperType},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return switch (method.getName()) {
                                case "toString" -> "InvestPro IBKR API callbacks";
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "equals" -> proxy == args[0];
                                default -> null;
                            };
                        }
                        if (sessionGeneration.get() == generation)
                            callback(method.getName(), args == null ? new Object[0] : args);
                        return null;
                    });
            signal = IbkrApiRuntime.type("com.ib.client.EJavaSignal").getConstructor().newInstance();
            client = IbkrApiRuntime.type("com.ib.client.EClientSocket")
                    .getConstructor(wrapperType, IbkrApiRuntime.type("com.ib.client.EReaderSignal"))
                    .newInstance(wrapper, signal);
            call(client, "eConnect", profile.host(), profile.port(), profile.clientId());
            if (!socketConnected()) throw new IllegalStateException(failure);
            Object reader = IbkrApiRuntime.type("com.ib.client.EReader")
                    .getConstructor(client.getClass(), IbkrApiRuntime.type("com.ib.client.EReaderSignal"))
                    .newInstance(client, signal);
            ((Thread) reader).setDaemon(true);
            ((Thread) reader).start();
            Object currentClient = client;
            Object currentSignal = signal;
            pump = new Thread(() -> {
                try {
                    while (client == currentClient && Boolean.TRUE.equals(call(currentClient, "isConnected"))) {
                        call(currentSignal, "waitForSignal");
                        call(reader, "processMsgs");
                    }
                } catch (RuntimeException error) {
                    if (client == currentClient) failSession("IBKR reader failed: " + root(error));
                }
            }, "ibkr-api-reader");
            pump.setDaemon(true);
            pump.start();
            ready.get(15, TimeUnit.SECONDS);
            selectAccount();
            failure = "IBKR API connected; managed account selected.";
        } catch (Exception error) {
            String message = "IBKR API connection failed: " + root(error);
            disconnect();
            failure = message;
            throw new IllegalStateException(message, error);
        }
    }

    public synchronized void disconnect() {
        sessionGeneration.incrementAndGet();
        Object previous = client;
        client = null;
        if (previous != null) {
            try { call(previous, "eDisconnect"); } catch (RuntimeException ignored) { }
        }
        if (signal != null) { try { call(signal, "issueSignal"); } catch (RuntimeException ignored) { } }
        if (pump != null) pump.interrupt();
        failSession("IBKR API disconnected; check order status before retrying any pending submission.");
        quotes.clear();
        quoteIds.clear();
        quoteWaiters.clear();
        summaries.clear();
        snapshot = null;
    }

    public boolean socketConnected() {
        Object current = client;
        return current != null && Boolean.TRUE.equals(call(current, "isConnected"));
    }

    public IbkrSessionState state() {
        if (profile == null) return IbkrSessionState.disconnected(null, failure);
        boolean connected = socketConnected();
        boolean apiReady = connected && apiAvailable;
        return new IbkrSessionState(profile.mode(), profile.host(), profile.port(), profile.clientId(), profile.paper(),
                connected, apiReady, !accounts.isEmpty(), snapshot != null, !quotes.isEmpty(), false,
                apiReady && !selectedAccount.isBlank(), failure, connected ? Instant.now() : null, accounts);
    }

    public Object client() {
        if (!state().connectionSuccessful()) throw new IllegalStateException(failure);
        return client;
    }

    public synchronized CompletableFuture<IbkrAccountSnapshot> accountSnapshot() {
        if (snapshot != null && snapshot.updatedAt().plusSeconds(10).isAfter(Instant.now()))
            return CompletableFuture.completedFuture(snapshot);
        if (summaryFuture != null && !summaryFuture.isDone()) return summaryFuture;
        Object current = client();
        summaries.clear();
        summaryRequest = requestIds.incrementAndGet();
        summaryFuture = new CompletableFuture<>();
        call(current, "reqAccountSummary", summaryRequest, "All",
                "NetLiquidation,AvailableFunds,MaintMarginReq,BuyingPower,TotalCashValue");
        return summaryFuture.orTimeout(15, TimeUnit.SECONDS).whenComplete((result, error) -> {
            try { call(current, "cancelAccountSummary", summaryRequest); } catch (RuntimeException ignored) { }
        });
    }

    public CompletableFuture<Ticker> ticker(IbkrResolvedContract contract) {
        Object current = client();
        int id = quoteIds.computeIfAbsent(contract.conId(), ignored -> {
            int request = requestIds.incrementAndGet();
            quotes.put(request, new double[4]);
            quoteWaiters.put(request, new CompletableFuture<>());
            call(current, "reqMktData", request, contract(contract), "", false, false, List.of());
            return request;
        });
        double[] prices = quotes.get(id);
        synchronized (prices) {
            Ticker available = ticker(prices);
            if (available != null) return CompletableFuture.completedFuture(available);
        }
        return quoteWaiters.get(id).orTimeout(12, TimeUnit.SECONDS);
    }

    public CompletableFuture<String> submit(IbkrResolvedContract contract, Side side, double quantity,
                                            String type, double price, double stopPrice) {
        if (!Double.isFinite(quantity) || quantity <= 0)
            return CompletableFuture.failedFuture(new IllegalArgumentException("IBKR quantity must be positive and finite."));
        try {
            Object current = client();
            String account = selectAccount();
            Object order = IbkrApiRuntime.type("com.ib.client.Order").getConstructor().newInstance();
            call(order, "account", account);
            call(order, "action", side == Side.BUY ? "BUY" : "SELL");
            call(order, "orderType", type);
            Object decimal = IbkrApiRuntime.type("com.ib.client.Decimal").getMethod("get", double.class).invoke(null, quantity);
            call(order, "totalQuantity", decimal);
            call(order, "tif", "DAY");
            if (type.contains("LMT")) {
                if (!Double.isFinite(price) || price <= 0) throw new IllegalArgumentException("Invalid IBKR limit price.");
                call(order, "lmtPrice", price);
            }
            if (type.contains("STP") || type.equals("TRAIL")) {
                if (!Double.isFinite(stopPrice) || stopPrice <= 0) throw new IllegalArgumentException("Invalid IBKR stop price.");
                call(order, "auxPrice", stopPrice);
            }
            call(order, "transmit", true);
            int id = orderIds.getAndIncrement();
            if (id < 0) throw new IllegalStateException("IBKR has not supplied nextValidId.");
            CompletableFuture<String> result = new CompletableFuture<>();
            pendingOrders.put(id, result);
            call(current, "placeOrder", id, contract(contract), order);
            result.orTimeout(20, TimeUnit.SECONDS).whenComplete((value, error) -> pendingOrders.remove(id));
            return result.exceptionallyCompose(error -> CompletableFuture.failedFuture(new IllegalStateException(
                    "IBKR order " + id + ": " + root(error) + ". Verify broker order status before retrying.", error)));
        } catch (Exception error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    public CompletableFuture<List<org.investpro.data.CandleData>> history(IbkrResolvedContract resolved, int seconds) {
        String size = switch (seconds) {
            case 60 -> "1 min"; case 300 -> "5 mins"; case 900 -> "15 mins";
            case 1800 -> "30 mins"; case 3600 -> "1 hour"; case 14400 -> "4 hours"; case 86400 -> "1 day";
            default -> throw new IllegalArgumentException("Unsupported IBKR candle interval: " + seconds);
        };
        int id = requestIds.incrementAndGet();
        List<org.investpro.data.CandleData> candles = new CopyOnWriteArrayList<>();
        CompletableFuture<List<org.investpro.data.CandleData>> result = new CompletableFuture<>();
        bars.put(id, candles);
        histories.put(id, result);
        try {
            String duration = seconds >= 86400 ? "1 Y" : seconds >= 3600 ? "1 M" : "2 D";
            call(client(), "reqHistoricalData", id, contract(resolved), "", duration, size,
                    resolved.secType().equals("CASH") ? "MIDPOINT" : "TRADES", 0, 2, false, List.of());
        } catch (RuntimeException error) { result.completeExceptionally(error); }
        return result.orTimeout(25, TimeUnit.SECONDS).whenComplete((value, error) -> {
            bars.remove(id); histories.remove(id);
            if (error != null) { try { call(client(), "cancelHistoricalData", id); } catch (RuntimeException ignored) { } }
        });
    }

    public CompletableFuture<String> cancel(String orderId) {
        try {
            int id = Integer.parseInt(orderId);
            Object cancel = IbkrApiRuntime.type("com.ib.client.OrderCancel").getConstructor().newInstance();
            CompletableFuture<String> result = new CompletableFuture<>();
            pendingCancels.put(id, result);
            call(client(), "cancelOrder", id, cancel);
            return result.orTimeout(20, TimeUnit.SECONDS).whenComplete((value, error) -> pendingCancels.remove(id));
        } catch (Exception error) { return CompletableFuture.failedFuture(error); }
    }

    public static Object contract(IbkrResolvedContract resolved) {
        try {
            Object contract = IbkrApiRuntime.type("com.ib.client.Contract").getConstructor().newInstance();
            call(contract, "conid", Math.toIntExact(resolved.conId()));
            call(contract, "symbol", resolved.symbol());
            call(contract, "secType", resolved.secType());
            call(contract, "currency", resolved.currency());
            call(contract, "exchange", resolved.exchange().isBlank() ? "SMART" : resolved.exchange());
            call(contract, "primaryExch", resolved.primaryExchange());
            return contract;
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    void callback(String name, Object[] args) {
        try {
            switch (name) {
                case "position" -> {
                    if (selectedAccount.equals(args[0])) {
                        Object contract = args[1];
                        double quantity = Double.parseDouble(args[2].toString());
                        int conId = (Integer) call(contract, "conid");
                        if (quantity == 0) positions.remove(conId);
                        else {
                            Position position = new Position(pair(contract));
                            position.setPositionId(String.valueOf(conId));
                            position.setSide(quantity > 0 ? Side.BUY : Side.SELL);
                            position.setQuantity(Math.abs(quantity));
                            // IBKR's avgCost includes the contract multiplier for derivatives.
                            String multiplier = String.valueOf(call(contract, "multiplier"));
                            double scale = multiplier.isBlank() ? 1 : Double.parseDouble(multiplier);
                            position.setEntryPrice(((Number) args[3]).doubleValue() / scale);
                            positions.put(conId, position);
                        }
                    }
                }
                case "positionEnd" -> {
                    if (positionFuture != null) positionFuture.complete(List.copyOf(positions.values()));
                }
                case "openOrder" -> {
                    Object brokerOrder = args[2];
                    if (selectedAccount.equals(call(brokerOrder, "account"))) {
                        int id = (Integer) args[0];
                        OpenOrder order = new OpenOrder();
                        order.setOrderId(String.valueOf(id));
                        order.setTradePair(pair(args[1]));
                        order.setSide("SELL".equals(String.valueOf(call(brokerOrder, "action"))) ? Side.SELL : Side.BUY);
                        order.setOrderType(switch (String.valueOf(call(brokerOrder, "orderType"))) {
                            case "LMT" -> OpenOrder.OrderType.LIMIT;
                            case "STP" -> OpenOrder.OrderType.STOP_LOSS;
                            case "STP LMT" -> OpenOrder.OrderType.STOP_LIMIT;
                            case "TRAIL" -> OpenOrder.OrderType.TRAILING_STOP;
                            default -> OpenOrder.OrderType.MARKET;
                        });
                        double price = ((Number) call(brokerOrder, "lmtPrice")).doubleValue();
                        order.setPrice(price == Double.MAX_VALUE ? 0 : price);
                        order.setSize(Double.parseDouble(call(brokerOrder, "totalQuantity").toString()));
                        order.setRemainingSize(order.getSize());
                        order.setStatus(OpenOrder.OrderStatus.OPEN);
                        order.setExchange("INTERACTIVE_BROKERS");
                        openOrders.put(id, order);
                    }
                }
                case "openOrderEnd" -> {
                    if (openOrderFuture != null) openOrderFuture.complete(List.copyOf(openOrders.values()));
                }
                case "nextValidId" -> { orderIds.accumulateAndGet((Integer) args[0], Math::max); completeReady(); }
                case "managedAccounts" -> {
                    accounts = Arrays.stream(((String) args[0]).split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
                    completeReady();
                }
                case "connectionClosed" -> failSession("IBKR API connection closed.");
                case "historicalData" -> {
                    List<org.investpro.data.CandleData> candles = bars.get((Integer) args[0]);
                    if (candles != null) {
                        Object bar = args[1];
                        String time = String.valueOf(call(bar, "time"));
                        long epoch = time.length() == 8
                                ? java.time.LocalDate.parse(time, java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                                        .atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond()
                                : Long.parseLong(time);
                        candles.add(new org.investpro.data.CandleData((Double) call(bar, "open"),
                                (Double) call(bar, "close"), (Double) call(bar, "high"), (Double) call(bar, "low"),
                                Math.toIntExact(epoch), Double.parseDouble(String.valueOf(call(bar, "volume")))));
                    }
                }
                case "historicalDataEnd" -> {
                    int id = (Integer) args[0];
                    CompletableFuture<List<org.investpro.data.CandleData>> history = histories.get(id);
                    if (history != null) history.complete(List.copyOf(bars.get(id)));
                }
                case "accountSummary" -> {
                    if ((Integer) args[0] == summaryRequest) {
                        if (selectedAccount.equals(args[1]) && "NetLiquidation".equals(args[2]))
                            baseCurrency = (String) args[4];
                        try { summaries.computeIfAbsent((String) args[1], ignored -> new ConcurrentHashMap<>())
                                .put((String) args[2], Double.parseDouble((String) args[3])); }
                        catch (NumberFormatException ignored) { }
                    }
                }
                case "accountSummaryEnd" -> {
                    if ((Integer) args[0] == summaryRequest && summaryFuture != null) {
                        Map<String, Double> values = summaries.get(selectAccount());
                        if (values == null || !values.containsKey("NetLiquidation"))
                            summaryFuture.completeExceptionally(new IllegalStateException("IBKR did not return account summary values."));
                        else {
                            snapshot = new IbkrAccountSnapshot(selectedAccount, "Interactive Brokers", profile.paper(),
                                    values.get("NetLiquidation"), values.getOrDefault("AvailableFunds", 0.0),
                                    values.getOrDefault("MaintMarginReq", 0.0), values.getOrDefault("BuyingPower", 0.0),
                                    Map.of(baseCurrency, values.getOrDefault("TotalCashValue", 0.0)), Instant.now());
                            summaryFuture.complete(snapshot);
                        }
                    }
                }
                case "tickPrice" -> {
                    int id = (Integer) args[0], field = (Integer) args[1];
                    double price = (Double) args[2];
                    double[] prices = quotes.get(id);
                    if (prices != null && price > 0 && Double.isFinite(price)) synchronized (prices) {
                        if (field == 1 || field == 66) prices[0] = price;
                        if (field == 2 || field == 67) prices[1] = price;
                        if (field == 4 || field == 68) prices[2] = price;
                        if (field == 1 || field == 2 || field == 4 || field == 66 || field == 67 || field == 68)
                            prices[3] = System.currentTimeMillis();
                        Ticker ticker = ticker(prices);
                        if (ticker != null) quoteWaiters.get(id).complete(ticker);
                    }
                }
                case "orderStatus" -> {
                    int id = (Integer) args[0];
                    String status = (String) args[1];
                    if (List.of("Submitted", "PreSubmitted", "Filled").contains(status)) {
                        CompletableFuture<String> order = pendingOrders.get(id);
                        if (order != null) order.complete(String.valueOf(id));
                    } else if (List.of("Cancelled", "ApiCancelled", "Inactive").contains(status)) {
                        CompletableFuture<String> order = pendingOrders.get(id);
                        if (order != null) order.completeExceptionally(new IllegalStateException("Broker status: " + status));
                        CompletableFuture<String> cancel = pendingCancels.get(id);
                        if (cancel != null) cancel.complete(String.valueOf(id));
                    }
                }
                case "error" -> handleError(args);
                default -> { }
            }
            for (BiConsumer<String, Object[]> listener : listeners) listener.accept(name, args);
        } catch (RuntimeException error) {
            if (summaryFuture != null && !summaryFuture.isDone()) summaryFuture.completeExceptionally(error);
        }
    }

    private void handleError(Object[] args) {
        if (args.length == 1) {
            failSession("IBKR API: " + args[0]);
            return;
        }
        if (args.length < 3 || !(args[0] instanceof Integer id)) return;
        int codeIndex = args[1] instanceof Long ? 2 : 1;
        int code = (Integer) args[codeIndex];
        String message = "IBKR " + code + ": " + args[codeIndex + 1];
        if (List.of(502, 503, 504, 326, 1100, 1300).contains(code)) failSession(message);
        if (code == 1101 || code == 1102) {
            if (code == 1101) {
                quotes.clear();
                quoteIds.clear();
                quoteWaiters.clear();
            }
            apiAvailable = orderIds.get() >= 0 && !accounts.isEmpty();
            failure = message;
        }
        if (code >= 2100 && code <= 2199) return;
        CompletableFuture<String> order = pendingOrders.get(id);
        if (order != null) order.completeExceptionally(new IllegalStateException(message));
        CompletableFuture<Ticker> quote = quoteWaiters.get(id);
        if (quote != null) quote.completeExceptionally(new IllegalStateException(message));
        CompletableFuture<List<org.investpro.data.CandleData>> history = histories.get(id);
        if (history != null) history.completeExceptionally(new IllegalStateException(message));
        if (id == summaryRequest && summaryFuture != null) summaryFuture.completeExceptionally(new IllegalStateException(message));
    }

    private void completeReady() {
        if (orderIds.get() >= 0 && !accounts.isEmpty()) { apiAvailable = true; ready.complete(null); }
    }

    private String selectAccount() {
        String normalized = selectedAccount == null ? "" : selectedAccount.trim();
        if (normalized.isBlank() && !accounts.isEmpty()) {
            selectedAccount = accounts.getFirst();
            return selectedAccount;
        }
        if (accounts.isEmpty()) {
            throw new IllegalStateException("No IBKR managed accounts were returned for this gateway session.");
        }
        if (accounts.contains(normalized)) return normalized;
        String matchingAccount = accounts.stream()
                .filter(account -> account.equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(null);
        if (matchingAccount != null) {
            selectedAccount = matchingAccount;
            return matchingAccount;
        }
        if (accounts.size() == 1) {
            selectedAccount = accounts.getFirst();
            return selectedAccount;
        }
        throw new IllegalStateException(
                "Select an IBKR account ID belonging to this gateway session. Available accounts: "
                        + String.join(", ", accounts));
    }

    private void failSession(String message) {
        apiAvailable = false;
        failure = message;
        IllegalStateException error = new IllegalStateException(message);
        ready.completeExceptionally(error);
        pendingOrders.values().forEach(future -> future.completeExceptionally(error));
        pendingCancels.values().forEach(future -> future.completeExceptionally(error));
        quoteWaiters.values().forEach(future -> future.completeExceptionally(error));
        histories.values().forEach(future -> future.completeExceptionally(error));
        if (summaryFuture != null) summaryFuture.completeExceptionally(error);
        if (positionFuture != null) positionFuture.completeExceptionally(error);
        if (openOrderFuture != null) openOrderFuture.completeExceptionally(error);
    }

    private static Ticker ticker(double[] prices) {
        double mid = prices[2] > 0 ? prices[2] : prices[0] > 0 && prices[1] > 0 ? (prices[0] + prices[1]) / 2 : 0;
        return mid > 0 ? new Ticker(mid, prices[0], prices[1], mid, mid, mid, 0, (long) prices[3]) : null;
    }

    public static Object call(Object target, String name, Object... args) {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            Class<?>[] types = method.getParameterTypes();
            boolean match = true;
            for (int i = 0; i < args.length; i++) {
                Class<?> type = types[i];
                if (type == int.class) type = Integer.class;
                if (type == double.class) type = Double.class;
                if (type == boolean.class) type = Boolean.class;
                if (type == long.class) type = Long.class;
                if (args[i] != null && !type.isInstance(args[i])) {
                    match = false;
                    break;
                }
            }
            if (!match) continue;
            try { return method.invoke(target, args); }
            catch (Exception error) { throw new IllegalStateException("IBKR " + name + " failed: " + root(error), error); }
        }
        throw new IllegalStateException("Official IBKR API method unavailable: " + name);
    }

    private static String root(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
