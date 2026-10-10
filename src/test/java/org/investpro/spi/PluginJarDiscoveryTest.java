package org.investpro.spi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class PluginJarDiscoveryTest {
    @TempDir Path directory;

    @Test void pluginJarServicesAreLoadedEvenAfterAnInvalidProvider() throws Exception {
        try (var jar = new JarOutputStream(Files.newOutputStream(directory.resolve("test-plugin.jar")))) {
            jar.putNextEntry(new JarEntry("META-INF/services/org.investpro.spi.StrategyProvider"));
            jar.write(("missing.plugin.BrokenProvider\norg.investpro.strategy.providers.DefaultStrategyProvider\n")
                    .getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        // Parent supplies the API/classes but excludes application service resources.
        ClassLoader parent = new ClassLoader(getClass().getClassLoader()) {
            @Override public java.util.Enumeration<java.net.URL> getResources(String name) throws java.io.IOException {
                return name.startsWith("META-INF/services/org.investpro.spi.")
                        ? java.util.Collections.emptyEnumeration() : super.getResources(name);
            }
        };
        try (var loader = (URLClassLoader) PluginJarLoader.createClassLoader(directory, parent)) {
            var registry = PluginRegistry.load(loader);
            assertEquals(1, registry.strategyProviders().size());
            assertEquals("UNIFIED_STRATEGY", registry.strategyProviders().getFirst().id());
        }
    }

    @Test void missingDirectoryKeepsApplicationClassLoader() {
        var parent = getClass().getClassLoader();
        assertSame(parent, PluginJarLoader.createClassLoader(directory.resolve("absent"), parent));
    }
}
