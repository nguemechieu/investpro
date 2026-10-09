package org.investpro.exchange.binanceus;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;

/** Binance.US symbol permissions: AND between sets, OR within each set. */
final class BinanceUsPermissions {
    private BinanceUsPermissions() {}

    static boolean allows(JsonNode symbol, JsonNode account) {
        if (account == null || !account.path("canTrade").asBoolean(false)) return false;
        Set<String> permissions = new HashSet<>();
        account.path("permissions").forEach(permission -> permissions.add(permission.asText()));
        JsonNode sets = symbol.path("permissionSets");
        if (sets.isArray() && !sets.isEmpty()) {
            for (JsonNode alternatives : sets) {
                boolean matched = false;
                for (JsonNode permission : alternatives) matched |= permissions.contains(permission.asText());
                if (!matched) return false;
            }
            return true;
        }
        JsonNode legacy = symbol.path("permissions");
        if (legacy.isArray() && !legacy.isEmpty()) {
            for (JsonNode permission : legacy) if (permissions.contains(permission.asText())) return true;
            return false;
        }
        return permissions.contains("SPOT");
    }
}
