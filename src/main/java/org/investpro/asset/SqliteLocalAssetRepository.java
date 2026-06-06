package org.investpro.asset;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public final class SqliteLocalAssetRepository implements LocalAssetRepository {
    private final String jdbcUrl;

    public SqliteLocalAssetRepository(Path dbPath) {
        try {
            Path parent = dbPath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create asset catalog data directory", exception);
        }
        this.jdbcUrl = "jdbc:sqlite:" + dbPath;
        migrate();
    }

    @Override
    public List<AssetCatalogEntry> findByExchange(ExchangeId exchangeId) {
        String sql = "SELECT * FROM asset_catalog WHERE exchange_id=? ORDER BY symbol";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, exchangeId.id());
            try (ResultSet rs = ps.executeQuery()) {
                List<AssetCatalogEntry> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(map(rs));
                }
                return result;
            }
        } catch (SQLException exception) {
            log.warn("Unable to read asset catalog for {}", exchangeId, exception);
            return List.of();
        }
    }

    @Override
    public Optional<AssetCatalogEntry> findById(String id) {
        String sql = "SELECT * FROM asset_catalog WHERE id=?";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException exception) {
            log.warn("Unable to read asset {}", id, exception);
            return Optional.empty();
        }
    }

    @Override
    public void upsert(AssetCatalogEntry asset) {
        upsertAll(List.of(asset));
    }

    @Override
    public void upsertAll(List<AssetCatalogEntry> assets) {
        if (assets == null || assets.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO asset_catalog (
                    id, exchange_id, symbol, raw_exchange_symbol, base_asset, quote_asset,
                    asset_type, status, tradability_status, order_submission_allowed,
                    supports_market_orders, supports_limit_orders, supports_stop_orders,
                    supports_live_trading, supports_paper_trading, min_order_size, max_order_size,
                    price_increment, quantity_increment, base_increment, quote_increment,
                    issuer, home_domain, requires_trustline, trustline_exists,
                    liquidity_pool_available, verified, reversed_pair_supported, manually_added,
                    last_seen_at, last_refreshed_at, metadata_json
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET
                    symbol=excluded.symbol,
                    raw_exchange_symbol=excluded.raw_exchange_symbol,
                    base_asset=excluded.base_asset,
                    quote_asset=excluded.quote_asset,
                    asset_type=excluded.asset_type,
                    status=excluded.status,
                    tradability_status=excluded.tradability_status,
                    order_submission_allowed=excluded.order_submission_allowed,
                    supports_market_orders=excluded.supports_market_orders,
                    supports_limit_orders=excluded.supports_limit_orders,
                    supports_stop_orders=excluded.supports_stop_orders,
                    supports_live_trading=excluded.supports_live_trading,
                    supports_paper_trading=excluded.supports_paper_trading,
                    min_order_size=excluded.min_order_size,
                    max_order_size=excluded.max_order_size,
                    price_increment=excluded.price_increment,
                    quantity_increment=excluded.quantity_increment,
                    base_increment=excluded.base_increment,
                    quote_increment=excluded.quote_increment,
                    issuer=excluded.issuer,
                    home_domain=excluded.home_domain,
                    requires_trustline=excluded.requires_trustline,
                    trustline_exists=excluded.trustline_exists,
                    liquidity_pool_available=excluded.liquidity_pool_available,
                    verified=excluded.verified,
                    reversed_pair_supported=excluded.reversed_pair_supported,
                    manually_added=asset_catalog.manually_added OR excluded.manually_added,
                    last_seen_at=excluded.last_seen_at,
                    last_refreshed_at=excluded.last_refreshed_at,
                    metadata_json=excluded.metadata_json
                """;
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (AssetCatalogEntry asset : assets) {
                bind(ps, asset);
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to upsert asset catalog", exception);
        }
    }

    @Override
    public Optional<Instant> lastRefreshAt(ExchangeId exchangeId) {
        String sql = "SELECT refreshed_at FROM asset_catalog_refresh WHERE exchange_id=? AND status='SUCCESS' ORDER BY refreshed_at DESC LIMIT 1";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, exchangeId.id());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(parseInstant(rs.getString(1))) : Optional.empty();
            }
        } catch (SQLException exception) {
            log.warn("Unable to read asset refresh metadata for {}", exchangeId, exception);
            return Optional.empty();
        }
    }

    @Override
    public void recordRefresh(ExchangeId exchangeId, Instant refreshedAt, String status, String message) {
        String sql = "INSERT INTO asset_catalog_refresh (exchange_id, refreshed_at, status, message) VALUES (?,?,?,?)";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, exchangeId.id());
            ps.setString(2, text(refreshedAt));
            ps.setString(3, status);
            ps.setString(4, message);
            ps.executeUpdate();
        } catch (SQLException exception) {
            log.warn("Unable to record asset refresh for {}", exchangeId, exception);
        }
    }

    private Connection connect() throws SQLException {
        Connection conn = DriverManager.getConnection(jdbcUrl);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
        }
        return conn;
    }

    private void migrate() {
        try (Connection conn = connect(); Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS asset_catalog (
                        id TEXT PRIMARY KEY,
                        exchange_id TEXT NOT NULL,
                        symbol TEXT NOT NULL,
                        raw_exchange_symbol TEXT,
                        base_asset TEXT,
                        quote_asset TEXT,
                        asset_type TEXT,
                        status TEXT,
                        tradability_status TEXT,
                        order_submission_allowed INTEGER,
                        supports_market_orders INTEGER,
                        supports_limit_orders INTEGER,
                        supports_stop_orders INTEGER,
                        supports_live_trading INTEGER,
                        supports_paper_trading INTEGER,
                        min_order_size TEXT,
                        max_order_size TEXT,
                        price_increment TEXT,
                        quantity_increment TEXT,
                        base_increment TEXT,
                        quote_increment TEXT,
                        issuer TEXT,
                        home_domain TEXT,
                        requires_trustline INTEGER,
                        trustline_exists INTEGER,
                        liquidity_pool_available INTEGER,
                        verified INTEGER,
                        reversed_pair_supported INTEGER,
                        manually_added INTEGER,
                        last_seen_at TEXT,
                        last_refreshed_at TEXT,
                        metadata_json TEXT
                    )
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_asset_catalog_exchange ON asset_catalog(exchange_id, symbol)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS asset_catalog_refresh (
                        exchange_id TEXT NOT NULL,
                        refreshed_at TEXT NOT NULL,
                        status TEXT NOT NULL,
                        message TEXT
                    )
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to migrate asset catalog schema", exception);
        }
    }

    private static void bind(PreparedStatement ps, AssetCatalogEntry asset) throws SQLException {
        int i = 1;
        ps.setString(i++, asset.id());
        ps.setString(i++, asset.exchangeId().id());
        ps.setString(i++, asset.symbol());
        ps.setString(i++, asset.rawExchangeSymbol());
        ps.setString(i++, asset.baseAsset());
        ps.setString(i++, asset.quoteAsset());
        ps.setString(i++, asset.assetType().name());
        ps.setString(i++, asset.status().name());
        ps.setString(i++, asset.tradabilityStatus().name());
        ps.setInt(i++, asset.orderSubmissionAllowed() ? 1 : 0);
        ps.setInt(i++, asset.supportsMarketOrders() ? 1 : 0);
        ps.setInt(i++, asset.supportsLimitOrders() ? 1 : 0);
        ps.setInt(i++, asset.supportsStopOrders() ? 1 : 0);
        ps.setInt(i++, asset.supportsLiveTrading() ? 1 : 0);
        ps.setInt(i++, asset.supportsPaperTrading() ? 1 : 0);
        ps.setString(i++, decimal(asset.minOrderSize()));
        ps.setString(i++, decimal(asset.maxOrderSize()));
        ps.setString(i++, decimal(asset.priceIncrement()));
        ps.setString(i++, decimal(asset.quantityIncrement()));
        ps.setString(i++, decimal(asset.baseIncrement()));
        ps.setString(i++, decimal(asset.quoteIncrement()));
        ps.setString(i++, asset.issuer());
        ps.setString(i++, asset.homeDomain());
        ps.setInt(i++, asset.requiresTrustline() ? 1 : 0);
        ps.setInt(i++, asset.trustlineExists() ? 1 : 0);
        ps.setInt(i++, asset.liquidityPoolAvailable() ? 1 : 0);
        ps.setInt(i++, asset.verified() ? 1 : 0);
        ps.setInt(i++, asset.reversedPairSupported() ? 1 : 0);
        ps.setInt(i++, asset.manuallyAdded() ? 1 : 0);
        ps.setString(i++, text(asset.lastSeenAt()));
        ps.setString(i++, text(asset.lastRefreshedAt()));
        ps.setString(i, asset.metadataJson());
    }

    private static AssetCatalogEntry map(ResultSet rs) throws SQLException {
        return new AssetCatalogEntry(
                rs.getString("id"),
                ExchangeId.from(rs.getString("exchange_id")),
                rs.getString("symbol"),
                rs.getString("raw_exchange_symbol"),
                rs.getString("base_asset"),
                rs.getString("quote_asset"),
                enumValue(AssetType.class, rs.getString("asset_type"), AssetType.UNKNOWN),
                enumValue(AssetStatus.class, rs.getString("status"), AssetStatus.UNKNOWN),
                enumValue(TradabilityStatus.class, rs.getString("tradability_status"), TradabilityStatus.UNKNOWN),
                rs.getInt("order_submission_allowed") == 1,
                rs.getInt("supports_market_orders") == 1,
                rs.getInt("supports_limit_orders") == 1,
                rs.getInt("supports_stop_orders") == 1,
                rs.getInt("supports_live_trading") == 1,
                rs.getInt("supports_paper_trading") == 1,
                decimal(rs.getString("min_order_size")),
                decimal(rs.getString("max_order_size")),
                decimal(rs.getString("price_increment")),
                decimal(rs.getString("quantity_increment")),
                decimal(rs.getString("base_increment")),
                decimal(rs.getString("quote_increment")),
                rs.getString("issuer"),
                rs.getString("home_domain"),
                rs.getInt("requires_trustline") == 1,
                rs.getInt("trustline_exists") == 1,
                rs.getInt("liquidity_pool_available") == 1,
                rs.getInt("verified") == 1,
                rs.getInt("reversed_pair_supported") == 1,
                rs.getInt("manually_added") == 1,
                parseInstant(rs.getString("last_seen_at")),
                parseInstant(rs.getString("last_refreshed_at")),
                rs.getString("metadata_json"));
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static BigDecimal decimal(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private static String text(Instant instant) {
        return (instant == null ? Instant.now() : instant).toString();
    }

    private static Instant parseInstant(String value) {
        return value == null || value.isBlank() ? Instant.EPOCH : Instant.parse(value);
    }
}
