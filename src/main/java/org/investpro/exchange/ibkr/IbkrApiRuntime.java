package org.investpro.exchange.ibkr;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads the independently installed official SDK without replacing InvestPro's protobuf runtime. */
public final class IbkrApiRuntime {
    private static volatile ClassLoader sdkLoader;

    private IbkrApiRuntime() { }

    public static Class<?> type(String name) throws ClassNotFoundException {
        ClassLoader loader = sdkLoader;
        if (loader == null) {
            synchronized (IbkrApiRuntime.class) {
                if (sdkLoader == null) {
                    Path directory = Path.of(System.getProperty("investpro.ibkr.apiDirectory", "lib/ibkr"));
                    Path api = directory.resolve("TwsApi.jar");
                    Path protobuf = directory.resolve("protobuf-java-4.29.5.jar");
                    if (!Files.isRegularFile(api) || !Files.isRegularFile(protobuf)) {
                        throw new ClassNotFoundException("Install the official IBKR API using setup-ibkr-api.ps1; "
                                + "expected TwsApi.jar and protobuf-java-4.29.5.jar in " + directory.toAbsolutePath());
                    }
                    try {
                        sdkLoader = new URLClassLoader(new java.net.URL[]{api.toUri().toURL(), protobuf.toUri().toURL()},
                                IbkrApiRuntime.class.getClassLoader()) {
                            @Override
                            protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
                                synchronized (getClassLoadingLock(className)) {
                                    if (className.startsWith("com.ib.client.") || className.startsWith("com.google.protobuf.")) {
                                        Class<?> loaded = findLoadedClass(className);
                                        if (loaded == null) loaded = findClass(className);
                                        if (resolve) resolveClass(loaded);
                                        return loaded;
                                    }
                                    return super.loadClass(className, resolve);
                                }
                            }
                        };
                    } catch (java.io.IOException error) {
                        throw new ClassNotFoundException("Unable to load the official IBKR SDK", error);
                    }
                }
                loader = sdkLoader;
            }
        }
        return Class.forName(name, true, loader);
    }
}
