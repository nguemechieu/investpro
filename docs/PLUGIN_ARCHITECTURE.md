# InvestPro Plugin Architecture

InvestPro uses Java's `ServiceLoader` to discover platform extensions without hardcoding every exchange, strategy, indicator, risk module, or market data provider into the core application.

## What ServiceLoader Does

`ServiceLoader` is a Java standard-library discovery mechanism. A plugin jar includes:

- An implementation class, such as `com.example.MyExchangeProvider`
- A registration file under `META-INF/services/`
- One provider class name per line in that registration file

At startup, InvestPro loads providers for the SPI interfaces in `org.investpro.spi`.

## SPI Interfaces

Core provider interfaces:

- `org.investpro.spi.ExchangeProvider`
- `org.investpro.spi.StrategyProvider`
- `org.investpro.spi.IndicatorProvider`
- `org.investpro.spi.RiskModuleProvider`
- `org.investpro.spi.MarketDataProvider`

All providers extend `InvestProPlugin` and expose:

- `id()`
- `displayName()`
- `version()`
- `enabledByDefault()`

Provider ids are normalized by:

- trimming whitespace
- uppercasing
- replacing spaces and hyphens with underscores

Aliases resolve to the same exchange provider.

## Adding an Exchange Provider

Create a class:

```java
package com.example.investpro;

import org.investpro.exchange.Exchange;
import org.investpro.spi.ExchangeProvider;
import org.investpro.spi.ExchangeProviderContext;

import java.util.Set;

public final class ExampleExchangeProvider implements ExchangeProvider {
    public String id() { return "EXAMPLE"; }
    public String displayName() { return "Example Exchange"; }
    public String version() { return "1.0"; }
    public boolean enabledByDefault() { return true; }
    public Set<String> aliases() { return Set.of("example", "example-exchange"); }
    public boolean supportsPaperTrading() { return true; }
    public boolean supportsLiveTrading() { return true; }

    public Exchange create(ExchangeProviderContext context) {
        var credentials = context.credentialResolver().resolve("example");
        return new ExampleExchange(credentials);
    }
}
```

Register it in:

```text
META-INF/services/org.investpro.spi.ExchangeProvider
```

File contents:

```text
com.example.investpro.ExampleExchangeProvider
```

## Adding a Strategy Provider

Implement `StrategyProvider` and return an existing `TradingStrategy` implementation:

```java
public final class ExampleStrategyProvider implements StrategyProvider {
    public String id() { return "EXAMPLE_STRATEGY"; }
    public String displayName() { return "Example Strategy"; }
    public String version() { return "1.0"; }
    public boolean enabledByDefault() { return true; }
    public String category() { return "TREND_FOLLOWING"; }
    public Set<String> supportedMarketTypes() { return Set.of("CRYPTO", "FOREX"); }
    public TradingStrategy create(StrategyProviderContext context) {
        return new ExampleStrategy();
    }
}
```

Register it in:

```text
META-INF/services/org.investpro.spi.StrategyProvider
```

## Adding an Indicator Provider

Implement `IndicatorProvider` and return a `ChartIndicator`:

```java
public final class ExampleIndicatorProvider implements IndicatorProvider {
    public String id() { return "EXAMPLE_INDICATOR"; }
    public String displayName() { return "Example Indicator"; }
    public String version() { return "1.0"; }
    public boolean enabledByDefault() { return true; }
    public String indicatorName() { return "Example"; }
    public Set<String> supportedInputs() { return Set.of("close"); }
    public ChartIndicator create(IndicatorProviderContext context) {
        return new ExampleIndicator(context.intConfig("period", 20));
    }
}
```

Register it in:

```text
META-INF/services/org.investpro.spi.IndicatorProvider
```

## External Plugin Jars

External plugin jars should include compiled provider classes and `META-INF/services` files. Later, InvestPro can load jars from:

```text
plugins/
```

`PluginJarLoader` is included as the planned entry point for scanning those jars with a `URLClassLoader`. Hot reload is intentionally not implemented yet; plugins should be loaded during startup to avoid UI-thread stalls and inconsistent runtime state.

## Safety Rules

InvestPro's `PluginRegistry`:

- catches `ServiceConfigurationError`
- ignores blank provider ids
- logs loaded providers
- keeps the first provider when duplicate ids are found
- resolves aliases without crashing startup
- lets `ExchangeFactory` fall back to legacy hardcoded creation when no provider exists

The JavaFX `PluginManagerPanel` shows the currently loaded providers in tabs for Exchanges, Strategies, Indicators, Risk Modules, and Market Data.
