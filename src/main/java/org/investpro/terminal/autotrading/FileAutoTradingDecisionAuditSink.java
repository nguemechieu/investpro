package org.investpro.terminal.autotrading;

import org.investpro.terminal.domain.Instrument;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

public final class FileAutoTradingDecisionAuditSink implements AutoTradingUniverseService.AutoTradingDecisionAuditSink {

    private final Path auditFile;

    public FileAutoTradingDecisionAuditSink(Path auditFile) {
        if (auditFile == null) {
            throw new IllegalArgumentException("auditFile is required");
        }
        this.auditFile = auditFile;
    }

    @Override
    public synchronized void record(Object decision) {
        try {
            Path parent = auditFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (BufferedWriter writer = Files.newBufferedWriter(
                    auditFile,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND)) {
                writer.write(toJsonLine(decision));
                writer.newLine();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to persist auto-trading audit decision to " + auditFile, exception);
        }
    }

    private String toJsonLine(Object decision) {
        if (decision instanceof SymbolEligibility eligibility) {
            Instrument instrument = eligibility.instrument();
            return "{"
                    + field("eventType", "SYMBOL_ELIGIBILITY") + ","
                    + field("recordedAt", Instant.now().toString()) + ","
                    + field("providerId", instrument == null ? "" : instrument.id().providerId()) + ","
                    + field("symbol", instrument == null ? "" : instrument.id().symbol()) + ","
                    + field("assetClass", instrument == null ? "" : String.valueOf(instrument.assetClass())) + ","
                    + field("tradable", String.valueOf(eligibility.tradable())) + ","
                    + field("failureReason", String.valueOf(eligibility.failureReason())) + ","
                    + field("botState", String.valueOf(eligibility.botState())) + ","
                    + field("assignedStrategy", eligibility.assignedStrategy()) + ","
                    + field("latestSignal", eligibility.latestSignal() == null ? "" : eligibility.latestSignal().action()) + ","
                    + numeric("spreadPercent", eligibility.spreadPercent()) + ","
                    + numeric("liquidityScore", eligibility.liquidityScore()) + ","
                    + numeric("volume24h", eligibility.volume24h()) + ","
                    + field("marketDataStatus", eligibility.marketDataStatus()) + ","
                    + numeric("openOrders", eligibility.openOrders()) + ","
                    + numeric("openPositions", eligibility.openPositions()) + ","
                    + field("lastDecisionTime", eligibility.lastDecisionTime().toString())
                    + "}";
        }

        if (decision instanceof UniverseScanResult scanResult) {
            return "{"
                    + field("eventType", "UNIVERSE_SCAN") + ","
                    + field("recordedAt", Instant.now().toString()) + ","
                    + field("providerId", scanResult.providerId()) + ","
                    + field("connectionState", String.valueOf(scanResult.connectionState())) + ","
                    + numeric("discoveredCount", scanResult.discoveredCount()) + ","
                    + numeric("eligibleCount", scanResult.eligibleCount()) + ","
                    + numeric("rejectedCount", scanResult.rejectedCount()) + ","
                    + field("scannedAt", scanResult.scannedAt().toString())
                    + "}";
        }

        return "{"
                + field("eventType", "AUTO_TRADING_DECISION") + ","
                + field("recordedAt", Instant.now().toString()) + ","
                + field("payload", String.valueOf(decision))
                + "}";
    }

    private String field(String name, String value) {
        return "\"" + escape(name) + "\":\"" + escape(value) + "\"";
    }

    private String numeric(String name, double value) {
        return "\"" + escape(name) + "\":" + (Double.isFinite(value) ? String.valueOf(value) : "null");
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
