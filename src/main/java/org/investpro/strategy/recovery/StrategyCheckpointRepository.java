package org.investpro.strategy.recovery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.event.EventBusManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Persists strategy checkpoints in SQLite with JSON fallback for resilience.
 */
@Slf4j
public final class StrategyCheckpointRepository {

    private static volatile StrategyCheckpointRepository instance;

    private static final String DEFAULT_DB_URL = "jdbc:sqlite:data/lifecycle.db";
    private static final Path JSON_FALLBACK = Path.of(System.getProperty("user.home"), ".investpro", "assignments",
            "checkpoints.json");

    private final String dbUrl;
    private final ObjectMapper mapper;

    private StrategyCheckpointRepository(String dbUrl) {
        this.dbUrl = dbUrl == null || dbUrl.isBlank() ? DEFAULT_DB_URL : dbUrl;
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        initSchema();
    }

    public static StrategyCheckpointRepository getInstance() {
        return getInstance(System.getProperty("investpro.strategy.lifecycle.dbUrl", DEFAULT_DB_URL));
    }

    public static StrategyCheckpointRepository getInstance(String dbUrl) {
        StrategyCheckpointRepository local = instance;
        if (local == null) {
            synchronized (StrategyCheckpointRepository.class) {
                local = instance;
                if (local == null) {
                    local = new StrategyCheckpointRepository(dbUrl);
                    instance = local;
                }
            }
        }
        return local;
    }

    public void save(StrategyCheckpoint checkpoint) {
        if (checkpoint == null || checkpoint.assignmentId().isBlank()) {
            return;
        }

        String sql = """
                INSERT INTO strategy_checkpoints (
                    assignment_id, strategy_id, symbol, timeframe, exchange, lifecycle_status,
                    broker_position_id, open_order_ids, unrealized_pnl, drawdown,
                    health_level, created_at, updated_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(assignment_id) DO UPDATE SET
                    strategy_id = excluded.strategy_id,
                    symbol = excluded.symbol,
                    timeframe = excluded.timeframe,
                    exchange = excluded.exchange,
                    lifecycle_status = excluded.lifecycle_status,
                    broker_position_id = excluded.broker_position_id,
                    open_order_ids = excluded.open_order_ids,
                    unrealized_pnl = excluded.unrealized_pnl,
                    drawdown = excluded.drawdown,
                    health_level = excluded.health_level,
                    updated_at = excluded.updated_at
                """;

        try (Connection conn = DriverManager.getConnection(dbUrl);
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, checkpoint.assignmentId());
            ps.setString(2, checkpoint.strategyId());
            ps.setString(3, checkpoint.symbol());
            ps.setString(4, checkpoint.timeframe());
            ps.setString(5, checkpoint.exchange());
            ps.setString(6, checkpoint.status());
            ps.setString(7, checkpoint.brokerPositionId());
            ps.setString(8, checkpoint.openOrderIds());
            ps.setDouble(9, checkpoint.unrealizedPnl());
            ps.setDouble(10, checkpoint.drawdown());
            ps.setString(11, checkpoint.healthLevel());
            ps.setString(12, checkpoint.createdAt().toString());
            ps.setString(13, checkpoint.updatedAt().toString());
            ps.executeUpdate();
            EventBusManager.getInstance().publish(AgentEvent.of(
                    AgentEvent.STRATEGY_CHECKPOINT_SAVED,
                    "StrategyCheckpointRepository",
                    checkpoint));
            return;
        } catch (SQLException ex) {
            log.warn("Failed to persist checkpoint to SQLite (fallback to JSON): {}", ex.getMessage());
        }

        saveJsonFallback(checkpoint);
    }

    public Optional<StrategyCheckpoint> loadLatest(String assignmentId) {
        if (assignmentId == null || assignmentId.isBlank()) {
            return Optional.empty();
        }

        String sql = "SELECT * FROM strategy_checkpoints WHERE assignment_id = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl);
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assignmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException ex) {
            log.debug("SQLite checkpoint load failed for {}: {}", assignmentId, ex.getMessage());
        }

        return loadJsonFallback().stream()
                .filter(cp -> cp.assignmentId().equals(assignmentId))
                .max(Comparator.comparing(StrategyCheckpoint::updatedAt));
    }

    public List<StrategyCheckpoint> recoverAllLatest() {
        String sql = "SELECT * FROM strategy_checkpoints";
        List<StrategyCheckpoint> checkpoints = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(dbUrl);
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                checkpoints.add(mapRow(rs));
            }
            if (!checkpoints.isEmpty()) {
                return List.copyOf(checkpoints);
            }
        } catch (SQLException ex) {
            log.debug("SQLite checkpoint recovery failed: {}", ex.getMessage());
        }

        return List.copyOf(loadJsonFallback());
    }

    public void deleteByAssignment(String assignmentId) {
        if (assignmentId == null || assignmentId.isBlank()) {
            return;
        }

        try (Connection conn = DriverManager.getConnection(dbUrl);
                PreparedStatement ps = conn
                        .prepareStatement("DELETE FROM strategy_checkpoints WHERE assignment_id = ?")) {
            ps.setString(1, assignmentId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.debug("Failed deleting checkpoint for assignment {} in SQLite: {}", assignmentId, ex.getMessage());
        }

        List<StrategyCheckpoint> checkpoints = new ArrayList<>(loadJsonFallback());
        checkpoints.removeIf(cp -> cp.assignmentId().equals(assignmentId));
        writeJsonFallback(checkpoints);
    }

    private void initSchema() {
        String ddl = """
                CREATE TABLE IF NOT EXISTS strategy_checkpoints (
                    assignment_id TEXT PRIMARY KEY,
                    strategy_id TEXT,
                    symbol TEXT NOT NULL,
                    timeframe TEXT NOT NULL,
                    exchange TEXT,
                    lifecycle_status TEXT,
                    broker_position_id TEXT,
                    open_order_ids TEXT,
                    unrealized_pnl REAL DEFAULT 0,
                    drawdown REAL DEFAULT 0,
                    health_level TEXT,
                    created_at TEXT,
                    updated_at TEXT
                )
                """;

        try (Connection conn = DriverManager.getConnection(dbUrl); Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
        } catch (SQLException ex) {
            log.warn("Failed to initialize strategy_checkpoints schema: {}", ex.getMessage());
        }
    }

    private StrategyCheckpoint mapRow(ResultSet rs) throws SQLException {
        return new StrategyCheckpoint(
                rs.getString("assignment_id"),
                rs.getString("strategy_id"),
                rs.getString("symbol"),
                rs.getString("timeframe"),
                rs.getString("exchange"),
                rs.getString("lifecycle_status"),
                rs.getString("broker_position_id"),
                rs.getString("open_order_ids"),
                rs.getDouble("unrealized_pnl"),
                rs.getDouble("drawdown"),
                rs.getString("health_level"),
                parseInstant(rs.getString("created_at")),
                parseInstant(rs.getString("updated_at")));
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return Instant.now();
        }
    }

    private void saveJsonFallback(StrategyCheckpoint checkpoint) {
        List<StrategyCheckpoint> checkpoints = new ArrayList<>(loadJsonFallback());
        checkpoints.removeIf(cp -> cp.assignmentId().equals(checkpoint.assignmentId()));
        checkpoints.add(checkpoint);
        writeJsonFallback(checkpoints);
        EventBusManager.getInstance().publish(AgentEvent.of(
                AgentEvent.STRATEGY_CHECKPOINT_SAVED,
                "StrategyCheckpointRepository",
                checkpoint));
    }

    private List<StrategyCheckpoint> loadJsonFallback() {
        try {
            if (!Files.exists(JSON_FALLBACK)) {
                return List.of();
            }
            return mapper.readValue(JSON_FALLBACK.toFile(), new TypeReference<List<StrategyCheckpoint>>() {
            });
        } catch (Exception ex) {
            log.warn("Failed to load checkpoint JSON fallback: {}", ex.getMessage());
            return List.of();
        }
    }

    private void writeJsonFallback(List<StrategyCheckpoint> checkpoints) {
        try {
            Files.createDirectories(JSON_FALLBACK.getParent());
            byte[] data = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(checkpoints);
            Files.write(JSON_FALLBACK, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException ex) {
            log.error("Failed writing checkpoint JSON fallback: {}", ex.getMessage());
        }
    }
}
