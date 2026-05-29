package org.investpro.strategy.persistence;

import lombok.extern.slf4j.Slf4j;
import org.investpro.decision.MarketRegime;
import org.investpro.strategy.lifecycle.StrategyLifecycleRecord;
import org.investpro.strategy.lifecycle.StrategyLifecycleStatus;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Persists and recovers strategy lifecycle records via SQLite (JDBC).
 *
 * <p>Creates 4 tables if they do not exist:
 * <ul>
 *   <li>{@code strategy_assignments} — core assignment data</li>
 *   <li>{@code lifecycle_events} — status transition audit log</li>
 *   <li>{@code ai_reviews} — serialised AI review summaries</li>
 *   <li>{@code health_snapshots} — periodic health score snapshots</li>
 * </ul>
 */
@Slf4j
public class StrategyLifecyclePersistenceService {

    private static volatile StrategyLifecyclePersistenceService instance;

    private static final String DEFAULT_DB_URL = "jdbc:sqlite:data/lifecycle.db";
    private final String dbUrl;

    private StrategyLifecyclePersistenceService(String dbUrl) {
        this.dbUrl = dbUrl;
        initSchema();
        log.info("StrategyLifecyclePersistenceService initialised: url={}", dbUrl);
    }

    /**
     * Returns the singleton instance using the default SQLite database path.
     *
     * @return singleton StrategyLifecyclePersistenceService
     */
    public static StrategyLifecyclePersistenceService getInstance() {
        return getInstance(System.getProperty("investpro.strategy.lifecycle.dbUrl", DEFAULT_DB_URL));
    }

    /**
     * Returns the singleton instance using the specified database URL.
     *
     * @param dbUrl JDBC database URL
     * @return singleton StrategyLifecyclePersistenceService
     */
    public static StrategyLifecyclePersistenceService getInstance(String dbUrl) {
        StrategyLifecyclePersistenceService local = instance;
        if (local == null) {
            synchronized (StrategyLifecyclePersistenceService.class) {
                local = instance;
                if (local == null) {
                    local = new StrategyLifecyclePersistenceService(
                            dbUrl != null ? dbUrl : DEFAULT_DB_URL);
                    instance = local;
                }
            }
        }
        return local;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Upserts a strategy lifecycle record into persistent storage.
     *
     * @param record the record to persist
     */
    public void upsert(StrategyLifecycleRecord record) {
        if (record == null) return;
        String sql = """
                INSERT INTO strategy_assignments (
                    assignment_id, strategy_id, strategy_name, symbol, timeframe,
                    lifecycle_status, assignment_score, confidence, ai_confidence,
                    market_regime, assignment_mode, assigned_by, assignment_reason,
                    assigned_at, created_at, updated_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(assignment_id) DO UPDATE SET
                    lifecycle_status = excluded.lifecycle_status,
                    assignment_score = excluded.assignment_score,
                    confidence       = excluded.confidence,
                    ai_confidence    = excluded.ai_confidence,
                    market_regime    = excluded.market_regime,
                    updated_at       = excluded.updated_at
                """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.getAssignmentId());
            ps.setString(2, record.getStrategyId());
            ps.setString(3, record.getStrategyName());
            ps.setString(4, record.getSymbol());
            ps.setString(5, record.getTimeframe());
            ps.setString(6, record.getLifecycleStatus() != null ? record.getLifecycleStatus().name() : "DISCOVERED");
            ps.setDouble(7, record.getAssignmentScore());
            ps.setDouble(8, record.getConfidence());
            ps.setDouble(9, record.getAiConfidence());
            ps.setString(10, record.getMarketRegime() != null ? record.getMarketRegime().name() : "UNKNOWN");
            ps.setString(11, record.getAssignmentMode());
            ps.setString(12, record.getAssignedBy());
            ps.setString(13, record.getAssignmentReason());
            ps.setString(14, record.getAssignedAt() != null ? record.getAssignedAt().toString() : Instant.now().toString());
            ps.setString(15, record.getCreatedAt() != null ? record.getCreatedAt().toString() : Instant.now().toString());
            ps.setString(16, Instant.now().toString());
            ps.executeUpdate();
            log.debug("Upserted assignment={}", record.getAssignmentId());
        } catch (SQLException ex) {
            log.error("Failed to upsert assignment={}: {}", record.getAssignmentId(), ex.getMessage());
        }
    }

    /**
     * Logs a lifecycle status transition event.
     *
     * @param assignmentId the assignment identifier
     * @param oldStatus    the previous status
     * @param newStatus    the new status
     * @param reason       the reason for the transition
     */
    public void logLifecycleEvent(String assignmentId, StrategyLifecycleStatus oldStatus,
                                   StrategyLifecycleStatus newStatus, String reason) {
        String sql = """
                INSERT INTO lifecycle_events (assignment_id, old_status, new_status, reason, occurred_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assignmentId);
            ps.setString(2, oldStatus != null ? oldStatus.name() : "UNKNOWN");
            ps.setString(3, newStatus != null ? newStatus.name() : "UNKNOWN");
            ps.setString(4, reason);
            ps.setString(5, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to log lifecycle event for assignment={}: {}", assignmentId, ex.getMessage());
        }
    }

    /**
     * Saves an AI review summary for an assignment.
     *
     * @param assignmentId   the assignment identifier
     * @param reviewType     type of review (STRATEGY, SIGNAL, HEALTH, REPLACEMENT)
     * @param approved       whether the review resulted in approval
     * @param confidence     confidence level (0.0–1.0)
     * @param reasoning      human-readable reasoning summary
     */
    public void saveAIReview(String assignmentId, String reviewType,
                              boolean approved, double confidence, String reasoning) {
        String sql = """
                INSERT INTO ai_reviews (assignment_id, review_type, approved, confidence, reasoning, reviewed_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assignmentId);
            ps.setString(2, reviewType);
            ps.setInt(3, approved ? 1 : 0);
            ps.setDouble(4, confidence);
            ps.setString(5, reasoning);
            ps.setString(6, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to save AI review for assignment={}: {}", assignmentId, ex.getMessage());
        }
    }

    /**
     * Saves a health score snapshot for an assignment.
     *
     * @param assignmentId the assignment identifier
     * @param healthScore  composite health score (0–100)
     * @param healthLevel  health level label
     */
    public void saveHealthSnapshot(String assignmentId, double healthScore, String healthLevel) {
        String sql = """
                INSERT INTO health_snapshots (assignment_id, health_score, health_level, captured_at)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assignmentId);
            ps.setDouble(2, healthScore);
            ps.setString(3, healthLevel);
            ps.setString(4, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to save health snapshot for assignment={}: {}", assignmentId, ex.getMessage());
        }
    }

    /**
     * Loads all assignments from the database for recovery on startup.
     *
     * @return list of recovered StrategyLifecycleRecord instances
     */
    public List<StrategyLifecycleRecord> recoverAll() {
        String sql = "SELECT * FROM strategy_assignments";
        List<StrategyLifecycleRecord> records = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                records.add(mapRow(rs));
            }
            log.info("Recovered {} lifecycle records from persistence", records.size());
        } catch (SQLException ex) {
            log.error("Failed to recover lifecycle records: {}", ex.getMessage());
        }
        return Collections.unmodifiableList(records);
    }

    /**
     * Loads a single assignment record by ID.
     *
     * @param assignmentId the assignment identifier
     * @return Optional containing the record, or empty if not found
     */
    public Optional<StrategyLifecycleRecord> load(String assignmentId) {
        String sql = "SELECT * FROM strategy_assignments WHERE assignment_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assignmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException ex) {
            log.error("Failed to load assignment={}: {}", assignmentId, ex.getMessage());
        }
        return Optional.empty();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    private void initSchema() {
        String[] ddls = {
            """
            CREATE TABLE IF NOT EXISTS strategy_assignments (
                assignment_id    TEXT PRIMARY KEY,
                strategy_id      TEXT NOT NULL,
                strategy_name    TEXT,
                symbol           TEXT NOT NULL,
                timeframe        TEXT NOT NULL,
                lifecycle_status TEXT NOT NULL,
                assignment_score REAL DEFAULT 0,
                confidence       REAL DEFAULT 0,
                ai_confidence    REAL DEFAULT 0,
                market_regime    TEXT DEFAULT 'UNKNOWN',
                assignment_mode  TEXT DEFAULT 'AUTO',
                assigned_by      TEXT,
                assignment_reason TEXT,
                assigned_at      TEXT,
                created_at       TEXT,
                updated_at       TEXT
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS lifecycle_events (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                assignment_id TEXT NOT NULL,
                old_status    TEXT,
                new_status    TEXT NOT NULL,
                reason        TEXT,
                occurred_at   TEXT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS ai_reviews (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                assignment_id TEXT NOT NULL,
                review_type   TEXT NOT NULL,
                approved      INTEGER NOT NULL,
                confidence    REAL NOT NULL,
                reasoning     TEXT,
                reviewed_at   TEXT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS health_snapshots (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                assignment_id TEXT NOT NULL,
                health_score  REAL NOT NULL,
                health_level  TEXT NOT NULL,
                captured_at   TEXT NOT NULL
            )
            """
        };
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            for (String ddl : ddls) {
                stmt.execute(ddl);
            }
            log.debug("Database schema initialised");
        } catch (SQLException ex) {
            log.error("Failed to initialise database schema: {}", ex.getMessage());
        }
    }

    private StrategyLifecycleRecord mapRow(ResultSet rs) throws SQLException {
        return StrategyLifecycleRecord.builder()
                .assignmentId(rs.getString("assignment_id"))
                .strategyId(rs.getString("strategy_id"))
                .strategyName(rs.getString("strategy_name"))
                .symbol(rs.getString("symbol"))
                .timeframe(rs.getString("timeframe"))
                .lifecycleStatus(parseStatus(rs.getString("lifecycle_status")))
                .assignmentScore(rs.getDouble("assignment_score"))
                .confidence(rs.getDouble("confidence"))
                .aiConfidence(rs.getDouble("ai_confidence"))
                .marketRegime(parseRegime(rs.getString("market_regime")))
                .assignmentMode(rs.getString("assignment_mode"))
                .assignedBy(rs.getString("assigned_by"))
                .assignmentReason(rs.getString("assignment_reason"))
                .assignedAt(parseInstant(rs.getString("assigned_at")))
                .createdAt(parseInstant(rs.getString("created_at")))
                .updatedAt(parseInstant(rs.getString("updated_at")))
                .promotionHistory(new ArrayList<>())
                .demotionHistory(new ArrayList<>())
                .build();
    }

    private StrategyLifecycleStatus parseStatus(String s) {
        if (s == null) return StrategyLifecycleStatus.DISCOVERED;
        try { return StrategyLifecycleStatus.valueOf(s); }
        catch (IllegalArgumentException ex) { return StrategyLifecycleStatus.DISCOVERED; }
    }

    private MarketRegime parseRegime(String s) {
        if (s == null) return MarketRegime.UNKNOWN;
        try { return MarketRegime.valueOf(s); }
        catch (IllegalArgumentException ex) { return MarketRegime.UNKNOWN; }
    }

    private Instant parseInstant(String s) {
        if (s == null) return Instant.now();
        try { return Instant.parse(s); }
        catch (Exception ex) { return Instant.now(); }
    }
}
