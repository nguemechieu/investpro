package org.investpro.asset;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

public final class AssetCatalogRuntime {
    private static final AssetCatalogRuntime INSTANCE = new AssetCatalogRuntime();

    private final List<Consumer<AssetCatalogEvent>> listeners = new CopyOnWriteArrayList<>();
    private final AssetCatalogService service;

    private AssetCatalogRuntime() {
        AssetCatalogMergeService mergeService = new AssetCatalogMergeService();
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "asset-catalog-refresh");
            thread.setDaemon(true);
            return thread;
        });
        ExchangeAssetDiscoveryService discoveryService = new DefaultExchangeAssetDiscoveryService(executor);
        LocalAssetRepository repository = new SqliteLocalAssetRepository(Path.of("data", "asset-catalog.db"));
        AssetRefreshScheduler scheduler = new AssetRefreshScheduler(
                repository,
                discoveryService,
                mergeService,
                executor,
                event -> listeners.forEach(listener -> listener.accept(event)));
        this.service = new AssetCatalogService(repository, scheduler);
    }

    public static AssetCatalogService service() {
        return INSTANCE.service;
    }

    public static void addListener(Consumer<AssetCatalogEvent> listener) {
        if (listener != null) {
            INSTANCE.listeners.add(listener);
        }
    }
}
