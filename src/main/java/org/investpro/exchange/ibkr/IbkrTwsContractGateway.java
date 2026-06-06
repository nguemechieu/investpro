package org.investpro.exchange.ibkr;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public final class IbkrTwsContractGateway {

    private final IbkrConnectionManager connectionManager;
    private final AtomicInteger requestIds = new AtomicInteger(20_000);
    private final Map<Integer, CopyOnWriteArrayList<IbkrContractCandidate>> matchingSymbolRequests = new ConcurrentHashMap<>();
    private final Map<Integer, CopyOnWriteArrayList<IbkrResolvedContract>> contractDetailRequests = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<List<IbkrContractCandidate>>> matchingSymbolFutures = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<IbkrResolvedContract>> contractDetailFutures = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "ibkr-tws-contract-timeouts");
        thread.setDaemon(true);
        return thread;
    });

    private volatile Object clientSocket;

    public IbkrTwsContractGateway(IbkrConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public CompletableFuture<List<IbkrContractCandidate>> reqMatchingSymbols(String pattern, Duration timeout) {
        if (!isOfficialApiAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("API is not ready."));
        }
        if (connectionManager == null || !connectionManager.isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("IBKR session is not connected."));
        }
        int requestId = requestIds.incrementAndGet();
        CompletableFuture<List<IbkrContractCandidate>> future = new CompletableFuture<>();
        matchingSymbolRequests.put(requestId, new CopyOnWriteArrayList<>());
        matchingSymbolFutures.put(requestId, future);
        scheduleTimeout(requestId, future, timeout);

        try {
            Object client = ensureClient();
            Method reqMatchingSymbols = client.getClass().getMethod("reqMatchingSymbols", int.class, String.class);
            reqMatchingSymbols.invoke(client, requestId, pattern);
        } catch (Exception exception) {
            cleanupMatching(requestId);
            future.completeExceptionally(new IllegalStateException("API is not ready.", exception));
        }
        return future;
    }

    public CompletableFuture<IbkrResolvedContract> reqContractDetails(IbkrContractCandidate candidate,
            Duration timeout) {
        if (!isOfficialApiAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("API is not ready."));
        }
        if (connectionManager == null || !connectionManager.isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("IBKR session is not connected."));
        }
        int requestId = requestIds.incrementAndGet();
        CompletableFuture<IbkrResolvedContract> future = new CompletableFuture<>();
        contractDetailRequests.put(requestId, new CopyOnWriteArrayList<>());
        contractDetailFutures.put(requestId, future);
        scheduleTimeout(requestId, future, timeout);

        try {
            Object contract = buildContract(candidate);
            Object client = ensureClient();
            Method reqContractDetails = client.getClass().getMethod("reqContractDetails", int.class,
                    Class.forName("com.ib.client.Contract"));
            reqContractDetails.invoke(client, requestId, contract);
        } catch (Exception exception) {
            cleanupDetails(requestId);
            future.completeExceptionally(new IllegalStateException("API is not ready.", exception));
        }
        return future;
    }

    public boolean isOfficialApiAvailable() {
        return classExists("com.ib.client.EWrapper")
                && classExists("com.ib.client.EClientSocket")
                && classExists("com.ib.client.EJavaSignal")
                && classExists("com.ib.client.Contract")
                && classExists("com.ib.client.ContractDetails");
    }

    private Object ensureClient() throws Exception {
        Object existing = clientSocket;
        if (existing != null && isConnected(existing)) {
            return existing;
        }

        Class<?> wrapperType = Class.forName("com.ib.client.EWrapper");
        Object wrapper = Proxy.newProxyInstance(
                wrapperType.getClassLoader(),
                new Class<?>[] { wrapperType },
                new TwsCallbackHandler());
        Object signal = Class.forName("com.ib.client.EJavaSignal").getConstructor().newInstance();
        Object client = Class.forName("com.ib.client.EClientSocket")
                .getConstructor(wrapperType, Class.forName("com.ib.client.EReaderSignal"))
                .newInstance(wrapper, signal);
        Method eConnect = client.getClass().getMethod("eConnect", String.class, int.class, int.class);
        eConnect.invoke(client, connectionManager.getHost(), connectionManager.getPort(), connectionManager.getClientId());
        clientSocket = client;
        return client;
    }

    private Object buildContract(IbkrContractCandidate candidate) throws Exception {
        Object contract = Class.forName("com.ib.client.Contract").getConstructor().newInstance();
        invokeIfPresent(contract, "conid", candidate.conId() == null ? null : candidate.conId().intValue());
        invokeIfPresent(contract, "symbol", candidate.symbol());
        invokeIfPresent(contract, "secType", firstNonBlank(candidate.secType(), candidate.securityType().ibkrCode()));
        invokeIfPresent(contract, "exchange", firstNonBlank(candidate.exchange(), defaultExchange(candidate)));
        invokeIfPresent(contract, "currency", firstNonBlank(candidate.currency(), "USD"));
        invokeIfPresent(contract, "primaryExch", candidate.primaryExchange());
        invokeIfPresent(contract, "localSymbol", candidate.localSymbol());
        invokeIfPresent(contract, "tradingClass", candidate.tradingClass());
        invokeIfPresent(contract, "lastTradeDateOrContractMonth", candidate.lastTradeDateOrContractMonth());
        invokeIfPresent(contract, "multiplier", candidate.multiplier());
        return contract;
    }

    private final class TwsCallbackHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            try {
                if ("symbolSamples".equals(name) && args != null && args.length >= 2) {
                    handleSymbolSamples((Integer) args[0], args[1]);
                } else if ("contractDetails".equals(name) && args != null && args.length >= 2) {
                    handleContractDetails((Integer) args[0], args[1]);
                } else if ("contractDetailsEnd".equals(name) && args != null && args.length >= 1) {
                    completeContractDetails((Integer) args[0]);
                } else if ("error".equals(name) && args != null && args.length >= 2) {
                    handleError(args);
                }
            } catch (Exception exception) {
                log.debug("Unable to handle IBKR TWS callback {}: {}", name, exception.getMessage());
            }
            return defaultValue(method.getReturnType());
        }
    }

    private void handleSymbolSamples(int requestId, Object descriptions) throws Exception {
        CopyOnWriteArrayList<IbkrContractCandidate> candidates = matchingSymbolRequests.get(requestId);
        CompletableFuture<List<IbkrContractCandidate>> future = matchingSymbolFutures.get(requestId);
        if (candidates == null || future == null) {
            return;
        }
        int length = descriptions == null ? 0 : Array.getLength(descriptions);
        for (int index = 0; index < length; index++) {
            Object description = Array.get(descriptions, index);
            Object contract = invoke(description, "contract");
            String secType = text(contract, "secType");
            candidates.add(new IbkrContractCandidate(
                    longValue(contract, "conid"),
                    text(contract, "symbol"),
                    text(description, "derivativeSecTypes"),
                    IbkrSecurityType.fromIbkrCode(secType),
                    secType,
                    text(contract, "exchange"),
                    text(contract, "primaryExch"),
                    text(contract, "currency"),
                    text(contract, "localSymbol"),
                    text(contract, "tradingClass"),
                    text(contract, "lastTradeDateOrContractMonth"),
                    text(contract, "multiplier"),
                    text(description, "derivativeSecTypes"),
                    "TWS_API",
                    ""));
        }
        cleanupMatching(requestId);
        future.complete(List.copyOf(candidates));
    }

    private void handleContractDetails(int requestId, Object details) throws Exception {
        CopyOnWriteArrayList<IbkrResolvedContract> results = contractDetailRequests.get(requestId);
        if (results == null) {
            return;
        }
        Object contract = invoke(details, "contract");
        String secType = text(contract, "secType");
        Instant now = Instant.now();
        results.add(new IbkrResolvedContract(
                longValue(contract, "conid") == null ? 0L : longValue(contract, "conid"),
                text(contract, "symbol"),
                text(contract, "localSymbol"),
                secType,
                text(contract, "currency"),
                text(contract, "exchange"),
                text(contract, "primaryExch"),
                text(contract, "tradingClass"),
                text(contract, "multiplier"),
                text(contract, "lastTradeDateOrContractMonth"),
                doubleValue(contract, "strike"),
                text(contract, "right"),
                longValue(details, "underConid"),
                doubleValue(details, "minTick"),
                text(details, "marketRuleIds"),
                text(details, "longName"),
                text(details, "category"),
                text(details, "subcategory"),
                "TWS_API",
                now,
                now,
                ""));
    }

    private void completeContractDetails(int requestId) {
        CopyOnWriteArrayList<IbkrResolvedContract> results = contractDetailRequests.get(requestId);
        CompletableFuture<IbkrResolvedContract> future = contractDetailFutures.get(requestId);
        cleanupDetails(requestId);
        if (future == null) {
            return;
        }
        if (results == null || results.isEmpty()) {
            future.completeExceptionally(new IllegalStateException("No matching contract found."));
        } else if (results.size() > 1) {
            future.completeExceptionally(new IllegalStateException("Contract is ambiguous."));
        } else {
            future.complete(results.getFirst());
        }
    }

    private void handleError(Object[] args) {
        int requestId = args[0] instanceof Integer value ? value : -1;
        if (requestId < 0) {
            return;
        }
        String message = args.length >= 4 ? String.valueOf(args[3]) : "IBKR API error.";
        CompletableFuture<List<IbkrContractCandidate>> searchFuture = matchingSymbolFutures.remove(requestId);
        if (searchFuture != null) {
            cleanupMatching(requestId);
            searchFuture.completeExceptionally(new IllegalStateException(message));
        }
        CompletableFuture<IbkrResolvedContract> detailsFuture = contractDetailFutures.remove(requestId);
        if (detailsFuture != null) {
            cleanupDetails(requestId);
            detailsFuture.completeExceptionally(new IllegalStateException(message));
        }
    }

    private void scheduleTimeout(int requestId, CompletableFuture<?> future, Duration timeout) {
        long millis = timeout == null ? 12_000L : Math.max(1_000L, timeout.toMillis());
        timeoutScheduler.schedule(() -> {
            if (!future.isDone()) {
                cleanupMatching(requestId);
                cleanupDetails(requestId);
                future.completeExceptionally(new IllegalStateException("Contract details request timed out."));
            }
        }, millis, TimeUnit.MILLISECONDS);
    }

    private void cleanupMatching(int requestId) {
        matchingSymbolRequests.remove(requestId);
        matchingSymbolFutures.remove(requestId);
    }

    private void cleanupDetails(int requestId) {
        contractDetailRequests.remove(requestId);
        contractDetailFutures.remove(requestId);
    }

    private boolean isConnected(Object client) {
        try {
            Object result = invoke(client, "isConnected");
            return Boolean.TRUE.equals(result);
        } catch (Exception exception) {
            return false;
        }
    }

    private Object invoke(Object target, String methodName) throws Exception {
        if (target == null) {
            return null;
        }
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private void invokeIfPresent(Object target, String methodName, Object value) {
        if (target == null || value == null || value.toString().isBlank()) {
            return;
        }
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                continue;
            }
            try {
                method.invoke(target, value);
                return;
            } catch (Exception ignored) {
                // Try another overload.
            }
        }
    }

    private String text(Object target, String methodName) {
        try {
            Object value = invoke(target, methodName);
            return value == null ? "" : value.toString();
        } catch (Exception exception) {
            return "";
        }
    }

    private Long longValue(Object target, String methodName) {
        try {
            Object value = invoke(target, methodName);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value != null && !value.toString().isBlank()) {
                return Long.parseLong(value.toString());
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private Double doubleValue(Object target, String methodName) {
        try {
            Object value = invoke(target, methodName);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value != null && !value.toString().isBlank()) {
                return Double.parseDouble(value.toString());
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private Object defaultValue(Class<?> returnType) {
        if (returnType == Void.TYPE) {
            return null;
        }
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Integer.TYPE || returnType == Long.TYPE || returnType == Short.TYPE || returnType == Byte.TYPE) {
            return 0;
        }
        if (returnType == Double.TYPE || returnType == Float.TYPE) {
            return 0.0;
        }
        return null;
    }

    private boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    private String defaultExchange(IbkrContractCandidate candidate) {
        String secType = firstNonBlank(candidate.secType(), candidate.securityType().ibkrCode());
        if ("CASH".equalsIgnoreCase(secType)) {
            return "IDEALPRO";
        }
        if ("FUT".equalsIgnoreCase(secType)) {
            return "GLOBEX";
        }
        return "SMART";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
