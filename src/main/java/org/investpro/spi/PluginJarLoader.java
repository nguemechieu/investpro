package org.investpro.spi;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Data
public final class PluginJarLoader {

    private PluginJarLoader() {
    }

    public static ClassLoader createClassLoader(Path pluginsDirectory, ClassLoader parent) {
        if (pluginsDirectory == null || !Files.isDirectory(pluginsDirectory)) {
            log.debug("Plugin directory does not exist yet: {}", pluginsDirectory);
            return parent;
        }

        try {
            List<URL> jarUrls;
            try (var paths = Files.list(pluginsDirectory)) {
                jarUrls = paths
                        .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".jar"))
                        .map(path -> {
                            try {
                                return path.toUri().toURL();
                            } catch (Exception exception) {
                                log.warn("Ignoring invalid plugin jar path: {}", path, exception);
                                return null;
                            }
                        })
                        .filter(url -> url != null)
                        .toList();
            }

            if (jarUrls.isEmpty()) {
                return parent;
            }

            log.info("Prepared plugin class loader for {} plugin jars from {}", jarUrls.size(), pluginsDirectory);
            return new URLClassLoader(jarUrls.toArray(URL[]::new), parent);
        } catch (Exception exception) {
            log.warn("Unable to scan plugin directory: {}", pluginsDirectory, exception);
            return parent;
        }
    }
}
