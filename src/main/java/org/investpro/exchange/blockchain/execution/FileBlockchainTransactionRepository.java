package org.investpro.exchange.blockchain.execution;

import org.investpro.exchange.blockchain.BlockchainTransactionResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * File-backed repository that survives process restart.
 */
public class FileBlockchainTransactionRepository implements BlockchainTransactionRepository {

    private final Path storageFile;
    private final Map<String, BlockchainTransactionResult> store = new LinkedHashMap<>();

    public FileBlockchainTransactionRepository(Path storageFile) {
        this.storageFile = storageFile;
        loadFromDisk();
    }

    @Override
    public synchronized void save(BlockchainTransactionResult result) {
        store.put(result.transactionId(), result);
        flushToDisk();
    }

    @Override
    public synchronized Optional<BlockchainTransactionResult> load(String transactionId) {
        return Optional.ofNullable(store.get(transactionId));
    }

    @Override
    public synchronized List<BlockchainTransactionResult> search(
            String networkId,
            BlockchainTransactionResult.TransactionOutcome outcome) {
        return store.values().stream()
                .filter(r -> networkId == null || networkId.isBlank() || r.networkId().equalsIgnoreCase(networkId))
                .filter(r -> outcome == null || r.outcome() == outcome)
                .sorted(Comparator.comparing(BlockchainTransactionResult::submittedAt).reversed())
                .toList();
    }

    @Override
    public synchronized List<BlockchainTransactionResult> history(int limit) {
        int normalizedLimit = Math.max(1, limit);
        return store.values().stream()
                .sorted(Comparator.comparing(BlockchainTransactionResult::submittedAt).reversed())
                .limit(normalizedLimit)
                .toList();
    }

    private void loadFromDisk() {
        try {
            if (!Files.exists(storageFile)) {
                Path parent = storageFile.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                return;
            }

            for (String line : Files.readAllLines(storageFile, StandardCharsets.UTF_8)) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                BlockchainTransactionResult value = parse(line);
                if (value != null) {
                    store.put(value.transactionId(), value);
                }
            }
        } catch (IOException ignored) {
            // Keep runtime operational even if historical file is temporarily unreadable.
        }
    }

    private void flushToDisk() {
        try {
            Path parent = storageFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            List<String> lines = new ArrayList<>();
            for (BlockchainTransactionResult value : store.values()) {
                lines.add(serialize(value));
            }
            Files.write(storageFile, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Persistence failures should not stop execution flow.
        }
    }

    private String serialize(BlockchainTransactionResult value) {
        return String.join("\t",
                safe(value.transactionId()),
                safe(value.networkId()),
                safe(value.outcome().name()),
                safe(value.signature()),
                safeLong(value.feeUnitsConsumed()),
                String.valueOf(value.confirmationDepth()),
                safe(value.errorCode()),
                safe(value.errorMessage()),
                String.valueOf(value.submittedAt().toEpochMilli()),
                safeInstant(value.confirmedAt()));
    }

    private BlockchainTransactionResult parse(String line) {
        String[] values = line.split("\\t", -1);
        if (values.length < 10) {
            return null;
        }

        String transactionId = unsafe(values[0]);
        String networkId = unsafe(values[1]);
        BlockchainTransactionResult.TransactionOutcome outcome = BlockchainTransactionResult.TransactionOutcome
                .valueOf(unsafe(values[2]));
        String signature = blankToNull(unsafe(values[3]));
        Long feeUnits = parseLong(values[4]);
        int depth = parseInt(values[5]);
        String errorCode = blankToNull(unsafe(values[6]));
        String errorMessage = blankToNull(unsafe(values[7]));
        Instant submittedAt = Instant.ofEpochMilli(Long.parseLong(values[8]));
        Instant confirmedAt = parseInstant(values[9]);

        return new BlockchainTransactionResult(
                transactionId,
                networkId,
                outcome,
                signature,
                feeUnits,
                depth,
                errorCode,
                errorMessage,
                submittedAt,
                confirmedAt);
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n");
    }

    private String unsafe(String value) {
        return value.replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    private String safeLong(Long value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safeInstant(Instant value) {
        return value == null ? "" : String.valueOf(value.toEpochMilli());
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.parseLong(value);
    }

    private int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.ofEpochMilli(Long.parseLong(value));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
