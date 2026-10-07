package org.investpro.core.execution;

import org.investpro.exchange.contracts.OrderExecutionProvider;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Durable submission barrier. An ambiguous response must never cause an automatic resubmission. */
public final class BotOrderJournal {
    private final Path directory;

    public BotOrderJournal(Path directory) { this.directory = directory; }

    public synchronized String begin(String accountKey, String symbol, OrderExecutionProvider provider) {
        return update(accountKey, state -> {
            String pending = state.getProperty("intent");
            if (pending != null) {
                String orderId = state.getProperty("orderId", "");
                if (orderId.isBlank()) throw new IllegalStateException(
                        "Previous submission outcome is unknown (intent " + pending + "); broker reconciliation is required");
                try {
                    var order = provider.fetchOrder(orderId).get(10, TimeUnit.SECONDS);
                    String status = order.orElseThrow(() -> new IllegalStateException(
                            "Broker cannot reconcile order " + orderId)).getStatus();
                    if (!isTerminal(status)) throw new IllegalStateException(
                            "Previous order " + orderId + " is " + status + "; awaiting a terminal broker status");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Order reconciliation interrupted", exception);
                } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
                    throw new IllegalStateException("Order reconciliation failed; new submission blocked", exception);
                }
            }
            state.clear();
            String intent = UUID.randomUUID().toString();
            state.setProperty("intent", intent);
            state.setProperty("symbol", symbol);
            state.setProperty("status", "SUBMITTING");
            state.setProperty("createdAt", java.time.Instant.now().toString());
            return intent;
        });
    }

    public synchronized void submitted(String accountKey, String intent, String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalStateException("Broker returned no order ID; submission outcome is unknown");
        }
        update(accountKey, state -> {
            if (!intent.equals(state.getProperty("intent"))) throw new IllegalStateException("Order intent ownership changed");
            state.setProperty("orderId", orderId);
            state.setProperty("status", "SUBMITTED");
            return intent;
        });
    }

    /** Operator recovery binds an ambiguous intent to a broker-confirmed order, never clears it blindly. */
    public synchronized void reconcileUnknown(String accountKey, String intent, String orderId,
                                               OrderExecutionProvider provider) {
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("Broker order ID is required");
        update(accountKey, state -> {
            if (!intent.equals(state.getProperty("intent")) || !state.getProperty("orderId", "").isBlank()) {
                throw new IllegalStateException("Intent is not an unresolved submission");
            }
            try {
                var order = provider.fetchOrder(orderId).get(10, TimeUnit.SECONDS).orElseThrow(
                        () -> new IllegalStateException("Broker did not confirm this order"));
                if (order.getSymbol() == null || !canonical(order.getSymbol()).equals(canonical(state.getProperty("symbol")))) {
                    throw new IllegalStateException("Broker order instrument does not match the unresolved intent");
                }
                state.setProperty("orderId", orderId);
                state.setProperty("status", order.getStatus() == null ? "UNKNOWN" : order.getStatus());
                return intent;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Order reconciliation interrupted", exception);
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
                throw new IllegalStateException("Order reconciliation failed", exception);
            }
        });
    }

    private static String canonical(String symbol) {
        return symbol == null ? "" : symbol.replace("/", "").replace("-", "").replace("_", "").toUpperCase(java.util.Locale.ROOT);
    }

    public static boolean isTerminal(String status) {
        if (status == null) return false;
        return switch (status.toUpperCase(java.util.Locale.ROOT)) {
            case "FILLED", "CANCELLED", "CANCELED", "REJECTED", "EXPIRED" -> true;
            default -> false;
        };
    }

    private String update(String accountKey, java.util.function.Function<Properties, String> action) {
        try {
            Files.createDirectories(directory);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(accountKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            Path journal = directory.resolve(hash + ".properties");
            try (FileChannel channel = FileChannel.open(directory.resolve(hash + ".lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var lock = channel.lock()) {
                Properties state = new Properties();
                if (Files.exists(journal)) {
                    if (Files.size(journal) == 0 || Files.size(journal) > 1_048_576) throw new IOException("Order journal is invalid");
                    try (InputStream input = Files.newInputStream(journal)) { state.load(input); }
                    if (!state.containsKey("intent")) throw new IOException("Order journal lacks intent identity");
                }
                String result = action.apply(state);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                state.store(output, "InvestPro bot submission state");
                Path temporary = Files.createTempFile(directory, hash, ".tmp");
                try {
                    try (FileChannel dataChannel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                        ByteBuffer data = ByteBuffer.wrap(output.toByteArray());
                        while (data.hasRemaining()) dataChannel.write(data);
                        dataChannel.force(true);
                    }
                    Files.move(temporary, journal, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } finally { Files.deleteIfExists(temporary); }
                return result;
            }
        } catch (IOException | java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Durable order journal unavailable; submission blocked", exception);
        }
    }
}
