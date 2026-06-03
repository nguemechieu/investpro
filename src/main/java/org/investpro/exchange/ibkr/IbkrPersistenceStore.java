package org.investpro.exchange.ibkr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.Position;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public final class IbkrPersistenceStore {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final Path baseDir;

    public IbkrPersistenceStore() {
        this(Path.of("data", "ibkr"));
    }

    public IbkrPersistenceStore(Path baseDir) {
        this.baseDir = baseDir;
        ensureDirectory(baseDir);
    }

    public void persistPositions(List<Position> positions) {
        writeJson(baseDir.resolve("positions.json"), positions);
    }

    public void persistOrders(List<OpenOrder> orders) {
        writeJson(baseDir.resolve("orders.json"), orders);
    }

    public void persistExecutions(List<String> executions) {
        writeJson(baseDir.resolve("executions.json"), executions);
    }

    public void persistAccount(IbkrAccountSnapshot snapshot) {
        writeJson(baseDir.resolve("account.json"), snapshot);
    }

    private void ensureDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create IBKR persistence directory: " + dir, exception);
        }
    }

    private void writeJson(Path path, Object value) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
        } catch (IOException exception) {
            log.warn("Unable to persist IBKR state to {}", path, exception);
        }
    }
}
