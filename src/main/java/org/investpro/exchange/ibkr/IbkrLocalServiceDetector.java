package org.investpro.exchange.ibkr;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class IbkrLocalServiceDetector {

    public static final List<Integer> TWS_AND_GATEWAY_PORTS = List.of(7497, 7496, 4002, 4001);

    public List<DetectionResult> detect(IbkrConnectionProfile profile) {
        String host = profile == null ? IbkrConnectionProfile.DEFAULT_HOST : profile.host();
        List<Integer> ports = new ArrayList<>(TWS_AND_GATEWAY_PORTS);
        int configuredPort = profile == null ? IbkrConnectionProfile.CLIENT_PORTAL_PORT : profile.port();
        if (configuredPort > 0 && !ports.contains(configuredPort)) {
            ports.add(configuredPort);
        }
        if (!ports.contains(IbkrConnectionProfile.CLIENT_PORTAL_PORT)) {
            ports.add(IbkrConnectionProfile.CLIENT_PORTAL_PORT);
        }

        List<DetectionResult> results = new ArrayList<>();
        for (int port : ports) {
            results.add(new DetectionResult(host, port, isReachable(host, port), labelFor(port), Instant.now()));
        }
        return List.copyOf(results);
    }

    public boolean isReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 750);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String labelFor(int port) {
        return switch (port) {
            case 7497 -> "TWS paper";
            case 7496 -> "TWS live";
            case 4002 -> "IB Gateway paper";
            case 4001 -> "IB Gateway live";
            case 5000 -> "Client Portal Gateway";
            default -> "Configured IBKR service";
        };
    }

    public record DetectionResult(String host, int port, boolean reachable, String label, Instant checkedAt) {
    }
}
