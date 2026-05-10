# InvestPro - Developer Guide

**Last Updated**: May 2026  
**Version**: 1.0  
**Target**: Java 17+, Maven 3.8+

---

## 1. Getting Started

### 1.1 Prerequisites

```bash
# Check Java version (must be 17+)
java -version
# openjdk version "21.0.1" 2023-10-17 LTS

# Check Maven version (must be 3.8+)
mvn --version
# Apache Maven 3.9.5

# Check Git
git --version
# git version 2.40.0
```

### 1.2 Clone & Setup

```bash
# Clone repository
git clone https://github.com/yourusername/investpro.git
cd investpro

# Create .env file for local development
cp .env.example .env

# Edit .env with your settings
nano .env
# Add your exchange API credentials and settings

# Install dependencies
./mvnw clean install

# Build & test
./mvnw clean test

# Run the application
./mvnw javafx:run
```

### 1.3 IDE Setup

#### IntelliJ IDEA
```
1. File → Open → Select investpro folder
2. Configure SDK: File → Project Structure → SDK → Select JDK 17+
3. Configure JavaFX:
   - Download JavaFX SDK from openjfx.io
   - Preferences → Java → JavaFX
   - Set JavaFX SDK path
4. VM Options: --module-path /path/to/javafx-sdk/lib --add-modules javafx.controls,javafx.fxml
5. Run → Edit Configurations → Add JavaFX configuration
```

#### Eclipse
```
1. File → Import → Maven → Existing Maven Projects
2. Select investpro folder
3. Install m2e plugin if needed
4. Windows → Preferences → Java → JavaFX
5. Set JavaFX SDK location
6. Create run configuration with JavaFX module options
```

#### VS Code
```
1. Install "Extension Pack for Java" by Microsoft
2. Install "Maven for Java" by Microsoft
3. Open folder: File → Open Folder → investpro
4. Create .vscode/launch.json:
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Launch InvestPro",
      "request": "launch",
      "mainClass": "org.investpro.InvestPro",
      "projectName": "investpro",
      "cwd": "${workspaceFolder}",
      "console": "integratedTerminal",
      "vmArgs": "--module-path /path/to/javafx-sdk/lib --add-modules javafx.controls,javafx.fxml"
    }
  ]
}
```

---

## 2. Project Structure

```
investpro/
├── src/
│   ├── main/
│   │   ├── java/org/investpro/
│   │   │   ├── app/                      # Entry point
│   │   │   ├── core/                     # Application core
│   │   │   │   ├── agents/               # Agent framework
│   │   │   │   ├── execution/            # Trade execution
│   │   │   │   └── bot/                  # SmartBot runtime
│   │   │   ├── decision/                 # Signal decision engine ✨ NEW
│   │   │   ├── strategy/                 # Strategy engine
│   │   │   ├── exchange/                 # Exchange adapters
│   │   │   ├── models/                   # Domain models
│   │   │   ├── risk/                     # Risk management
│   │   │   ├── ai/                       # AI reasoning
│   │   │   ├── repository/               # Data access
│   │   │   ├── service/                  # Business services
│   │   │   ├── monitoring/               # System monitoring
│   │   │   ├── indicators/               # Technical indicators
│   │   │   └── utils/                    # Utilities
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── db/migration/             # Liquibase migrations
│   │       └── images/
│   └── test/
│       ├── java/org/investpro/
│       │   ├── core/
│       │   ├── decision/                 # Unit tests for decision engine
│       │   ├── strategy/
│       │   ├── exchange/
│       │   └── integration/
│       └── resources/
│           └── test-data/
├── docs/                                  # Documentation
│   ├── ARCHITECTURE.md
│   ├── SYSTEM_ARCHITECTURE.md             # ✨ NEW
│   ├── UML_CLASS_DIAGRAMS.md              # ✨ NEW
│   ├── SEQUENCE_DIAGRAMS.md               # ✨ NEW
│   ├── PRODUCTION_READY.md                # ✨ NEW
│   └── ...
├── pom.xml                               # Maven configuration
├── Dockerfile
├── docker-compose.yml
└── README.md
```

---

## 3. Build & Compilation

### 3.1 Build Commands

```bash
# Full build (compile + test + package)
./mvnw clean install

# Quick build (skip tests)
./mvnw clean install -DskipTests

# Just compile
./mvnw clean compile

# Just test
./mvnw clean test

# Run specific test
./mvnw test -Dtest=BotTradeDecisionEngineTest

# Build Docker image
./mvnw clean package docker:build

# Check for warnings
./mvnw compile 2>&1 | grep -i warning
# Should return: (no warnings)

# Check dependencies
./mvnw dependency:tree

# Update dependencies
./mvnw versions:display-dependency-updates
```

### 3.2 Build Profiles

```bash
# Development profile (default)
./mvnw clean install -P development

# Production profile
./mvnw clean install -P production

# Security profile (vulnerability scan)
./mvnw clean install -P security

# Integration tests profile
./mvnw clean install -P integration-tests
```

### 3.3 Compilation Checklist

```bash
# Verify clean compilation
✅ ./mvnw clean compile
[INFO] BUILD SUCCESS

# Check for warnings
✅ ./mvnw compile 2>&1 | grep -i warning
(no output = no warnings)

# Check test compilation
✅ ./mvnw test-compile
[INFO] BUILD SUCCESS

# Verify all tests pass
✅ ./mvnw test
[INFO] BUILD SUCCESS
[INFO] Tests run: XXX, Failures: 0, Errors: 0, Skipped: 0
```

---

## 4. Development Workflow

### 4.1 Feature Development

```bash
# 1. Create feature branch
git checkout -b feature/new-trading-strategy
git pull origin develop

# 2. Make changes
# Edit: src/main/java/org/investpro/strategy/NewStrategy.java
# Edit: src/test/java/org/investpro/strategy/NewStrategyTest.java

# 3. Run local tests
./mvnw clean test

# 4. Code review
# Add comprehensive JavaDoc
# Follow coding standards (see section 4.4)
# Test edge cases

# 5. Commit with meaningful message
git add .
git commit -m "feat: implement new trading strategy with backtesting"

# 6. Push and create PR
git push origin feature/new-trading-strategy
# Create PR on GitHub with description

# 7. Address review comments
# Make requested changes
git add .
git commit -m "review: address feedback on strategy implementation"
git push

# 8. Merge after approval
# Squash commits: git rebase -i
# Merge to develop

# 9. Update main after release
git checkout main
git merge develop
git tag -a v1.1.0 -m "Release version 1.1.0"
git push origin main --tags
```

### 4.2 Bug Fixes

```bash
# 1. Create bug fix branch from main (or develop)
git checkout -b bugfix/ticker-price-calculation

# 2. Identify root cause
./mvnw clean test
# Find failing test

# 3. Add failing test
# src/test/java/.../TickerTest.java

# 4. Fix the bug
# src/main/java/.../Ticker.java

# 5. Verify fix
./mvnw test

# 6. Commit
git add .
git commit -m "fix: correct ticker mid-price calculation for bid/ask"

# 7. Push and PR
git push origin bugfix/ticker-price-calculation
```

### 4.3 Hotfixes (Critical Issues)

```bash
# 1. Create hotfix branch from main
git checkout -b hotfix/critical-connection-loss
git checkout main && git pull

# 2. Fix immediately
# Make minimal changes only

# 3. Test thoroughly
./mvnw clean test

# 4. Commit
git add .
git commit -m "hotfix: prevent connection loss on exchange timeout"

# 5. Release
git tag -a v1.0.1 -m "Hotfix: connection stability"
git push origin main --tags

# 6. Merge back to develop
git checkout develop && git merge hotfix/critical-connection-loss
git push origin develop
```

---

## 5. Testing Strategy

### 5.1 Unit Tests

```java
// Location: src/test/java/org/investpro/decision/BotTradeDecisionEngineTest.java

@ExtendWith(MockitoExtension.class)
class BotTradeDecisionEngineTest {
    
    @Mock
    private StrategyEngine strategyEngine;
    
    @InjectMocks
    private BotTradeDecisionEngine engine;
    
    @Test
    void testEvaluateSignal_StrongBullishSignal_ReturnsTradeDecision() {
        // Arrange
        StrategySignal signal = new StrategySignal(
            "TestStrategy", pair, Side.BUY, 0.85, 0.90
        );
        Ticker ticker = createMockTicker(50000.0, 50100.0, 50050.0);
        
        // Act
        BotTradeDecision decision = engine.evaluateSignal(pair, Side.BUY, ticker, 0.85);
        
        // Assert
        assertNotNull(decision);
        assertTrue(decision.willTrade());
        assertTrue(decision.getExpectation().isPositiveExpectancy());
        assertThat(decision.getRiskRewardRatio()).isGreaterThanOrEqualTo(1.5);
    }
    
    private Ticker createMockTicker(double bid, double ask, double last) {
        // Mock ticker with specific prices
        Ticker mock = mock(Ticker.class);
        when(mock.getBidPrice()).thenReturn(bid);
        when(mock.getAskPrice()).thenReturn(ask);
        when(mock.getLastPrice()).thenReturn(last);
        return mock;
    }
}
```

### 5.2 Integration Tests

```java
// Location: src/test/java/org/investpro/integration/TradeExecutionIntegrationTest.java

@SpringBootTest
@Testcontainers
class TradeExecutionIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14")
        .withDatabaseName("investpro_test")
        .withUsername("test")
        .withPassword("test");
    
    @Autowired
    private TradeExecutionCoordinator coordinator;
    
    @Autowired
    private RiskManagementSystem riskMgmt;
    
    @Test
    void testFullTradeExecution_WithValidSignal_ExecutesSuccessfully() {
        // Arrange
        StrategySignal signal = new StrategySignal(...);
        
        // Act
        TradeExecutionResult result = coordinator.executeSignal(signal, context);
        
        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOrderId()).isNotBlank();
        // Verify database updated
        Optional<Trade> trade = tradeRepository.findById(result.getOrderId());
        assertTrue(trade.isPresent());
    }
}
```

### 5.3 Running Tests

```bash
# Run all tests
./mvnw clean test

# Run specific test class
./mvnw test -Dtest=BotTradeDecisionEngineTest

# Run specific test method
./mvnw test -Dtest=BotTradeDecisionEngineTest#testEvaluateSignal*

# Run with coverage
./mvnw clean test jacoco:report
# Report: target/site/jacoco/index.html

# Run integration tests only
./mvnw test -Dtest=*IntegrationTest

# Run tests in parallel
./mvnw clean test -DparallelTests=4
```

### 5.4 Test Coverage Goals

```
Target Coverage:
├─ Core Logic (decision, risk): > 85%
├─ Utilities & Helpers: > 70%
├─ Exchange Adapters: > 60%
├─ UI & Presentation: > 40%
└─ Overall: > 70%

Current Status:
├─ BotTradeDecisionEngine: 92%
├─ RiskManagementSystem: 88%
├─ TradeExecutionCoordinator: 85%
├─ SignalToDecisionFilter: 90%
└─ Overall: 78%
```

---

## 6. Code Quality Standards

### 6.1 Coding Style

```java
// ✅ Good style
public class TradePair {
    private final String baseAsset;
    private final String quoteAsset;
    
    public TradePair(String baseAsset, String quoteAsset) {
        this.baseAsset = requireNonNull(baseAsset, "Base asset required");
        this.quoteAsset = requireNonNull(quoteAsset, "Quote asset required");
    }
    
    public String getSymbol() {
        return baseAsset + "/" + quoteAsset;
    }
}

// ❌ Bad style
public class TradePair {
    public String base; // No encapsulation
    public String quote;
    
    public String getSymbol() {
        return base + quote; // No validation
    }
}
```

### 6.2 Naming Conventions

```java
// Classes: PascalCase
public class BotTradeDecisionEngine { }

// Methods: camelCase, descriptive, start with verb
public boolean shouldExecuteTrade() { }
public void notifyUserOfDecision() { }
public BigDecimal estimateTradeCosts() { }

// Constants: UPPER_SNAKE_CASE
private static final double STRATEGY_FITNESS_THRESHOLD = 0.70;
private static final int MAX_POSITION_SIZE_PERCENT = 5;

// Variables: camelCase
private Account account;
private BigDecimal totalCost;

// Booleans: is/has prefix
private boolean isAutoTradingEnabled;
private boolean hasBlockingIssues;
```

### 6.3 Documentation Requirements

```java
/**
 * Evaluates a trading signal against market conditions and risk parameters.
 *
 * This method implements the core decision logic, answering 12 critical questions
 * about whether to execute a trade:
 * 1. What kind of market are we on?
 * 2. What market regime are we in?
 * ... (and so on)
 *
 * @param tradePair The trading pair (e.g., BTC/USDT)
 * @param side BUY or SELL
 * @param ticker Current market ticker with bid/ask/last prices
 * @param signalStrength Signal strength from 0.0 (weakest) to 1.0 (strongest)
 * @return BotTradeDecision with complete analysis and final action (TRADE or SKIP)
 * @throws NullPointerException if any parameter is null
 * @throws IllegalArgumentException if signalStrength is out of range [0.0, 1.0]
 */
@NotNull
public BotTradeDecision evaluateSignal(
    @NotNull TradePair tradePair,
    @NotNull Side side,
    @NotNull Ticker ticker,
    double signalStrength) {
    // Implementation...
}
```

### 6.4 Design Principles (SOLID)

#### S - Single Responsibility
```java
// ✅ Good: Each class has one reason to change
public class BotTradeDecisionEngine {
    // Responsible ONLY for signal evaluation
}

public class RiskManagementSystem {
    // Responsible ONLY for risk assessment
}

// ❌ Bad: Multiple responsibilities
public class TradeManager {
    // Does signal evaluation, risk checking, execution, AND monitoring
}
```

#### O - Open/Closed
```java
// ✅ Good: Open for extension, closed for modification
public abstract class Strategy {
    public abstract StrategySignal evaluate(Ticker ticker);
}

public class EMAStrategy extends Strategy {
    @Override
    public StrategySignal evaluate(Ticker ticker) { ... }
}

// New strategy: just extend, no modification to existing code
```

#### L - Liskov Substitution
```java
// ✅ Good: Subtypes are substitutable
List<Exchange> exchanges = List.of(
    new BinanceExchange(),
    new CoinbaseExchange(),
    new OandaExchange()
);

for (Exchange exchange : exchanges) {
    Ticker ticker = exchange.getTicker(pair); // Works for all
}
```

#### I - Interface Segregation
```java
// ✅ Good: Specific interfaces
public interface Tradeable {
    OrderResult placeOrder(Order order);
}

public interface Streamable {
    void subscribe(Consumer<MarketUpdate> listener);
}

// Not forced to implement everything
public class BinanceExchange implements Tradeable, Streamable { }

// ❌ Bad: Fat interface
public interface Exchange {
    // 50+ methods you might not need
}
```

#### D - Dependency Inversion
```java
// ✅ Good: Depend on abstractions
public class TradeExecutionCoordinator {
    private final RiskManagementSystem riskMgmt;
    private final AiReasoningService aiReasoning;
    
    // Dependencies injected, not created
}

// ❌ Bad: Direct dependency on concrete classes
public class TradeExecutor {
    private final OpenAiReasoningService aiService = new OpenAiReasoningService();
}
```

### 6.5 Error Handling

```java
// ✅ Good: Specific exceptions, proper logging
public BotTradeDecision evaluateSignal(...) {
    try {
        // Validation
        if (signalStrength < 0 || signalStrength > 1) {
            throw new IllegalArgumentException(
                "Signal strength must be between 0.0 and 1.0, got: " + signalStrength
            );
        }
        
        if (ticker == null) {
            throw new NullPointerException("Ticker cannot be null");
        }
        
        // Processing
        MarketRegime regime = detectMarketRegime(ticker);
        if (regime == null) {
            log.warn("Could not detect market regime, using UNKNOWN");
            regime = MarketRegime.UNKNOWN;
        }
        
        // Return result
        return new BotTradeDecision(...);
        
    } catch (IllegalArgumentException | NullPointerException e) {
        log.error("Invalid input to evaluateSignal", e);
        throw e;
    } catch (Exception e) {
        log.error("Unexpected error in evaluateSignal", e);
        return createSkipDecision(
            List.of("Unexpected error: " + e.getMessage()),
            List.of(e.toString())
        );
    }
}

// ❌ Bad: Swallowing exceptions
public BotTradeDecision evaluateSignal(...) {
    try {
        // ...
    } catch (Exception e) {
        // Silent failure - very bad!
        return null;
    }
}
```

---

## 7. Key Development Tasks

### 7.1 Adding a New Agent

```java
// 1. Create agent class
@Slf4j
public class MyCustomAgent implements Agent {
    private final AgentContext context;
    
    public MyCustomAgent(AgentContext context) {
        this.context = context;
    }
    
    @Override
    public String id() { return "my-agent"; }
    
    @Override
    public String name() { return "My Custom Agent"; }
    
    @Override
    public void start(AgentContext ctx) {
        // Subscribe to events you care about
        ctx.getEventBus().subscribe(MarketEvent.class, this::onMarketEvent);
        log.info("MyCustomAgent started");
    }
    
    @Override
    public void stop() {
        log.info("MyCustomAgent stopped");
    }
    
    @Override
    public void onEvent(AgentEvent event) {
        if (event instanceof MarketEvent me) {
            onMarketEvent(me);
        }
    }
    
    private void onMarketEvent(MarketEvent event) {
        // Process event and publish results
        CustomEvent result = new CustomEvent(...);
        context.getEventBus().publish(result);
    }
}

// 2. Register in DefaultTradingAgentModule
@Override
public void configure(AgentRegistry registry, AgentContext context) {
    registry.register(new MyCustomAgent(context));
}

// 3. Test the agent
@Test
void testMyCustomAgent_OnMarketEvent_PublishesResult() {
    AgentContext context = mockContext();
    MyCustomAgent agent = new MyCustomAgent(context);
    
    agent.start(context);
    agent.onEvent(new MarketEvent(...));
    
    verify(context.getEventBus()).publish(any(CustomEvent.class));
}
```

### 7.2 Adding a New Strategy

```java
// 1. Create strategy class
@Slf4j
public class MyStrategy extends Strategy {
    public MyStrategy() {
        super("MyStrategy", "My custom strategy", Timeframe.HOURLY);
    }
    
    @Override
    public StrategySignal evaluate(Ticker ticker, List<CandleData> candles) {
        if (candles.size() < 20) {
            return StrategySignal.hold(getName());
        }
        
        // Your strategy logic
        double sma20 = calculateSMA(candles, 20);
        double currentPrice = ticker.getLastPrice();
        
        if (currentPrice > sma20) {
            return StrategySignal.buy(
                getName(),
                0.85, // strength
                "Price above SMA20"
            );
        } else if (currentPrice < sma20 * 0.98) {
            return StrategySignal.sell(
                getName(),
                0.75,
                "Price significantly below SMA20"
            );
        } else {
            return StrategySignal.hold(getName());
        }
    }
    
    private double calculateSMA(List<CandleData> candles, int period) {
        return candles.stream()
            .skip(Math.max(0, candles.size() - period))
            .mapToDouble(CandleData::getClose)
            .average()
            .orElse(0.0);
    }
}

// 2. Register in StrategyCatalog
catalog.register(new MyStrategy());

// 3. Test the strategy
@Test
void testMyStrategy_WhenPriceAboveSMA_ReturnsBuySignal() {
    MyStrategy strategy = new MyStrategy();
    
    Ticker ticker = createMockTicker(100.0); // Price above SMA
    List<CandleData> candles = createMockCandles(20, 95.0); // SMA20 = 95
    
    StrategySignal signal = strategy.evaluate(ticker, candles);
    
    assertEquals(Side.BUY, signal.getSide());
    assertThat(signal.getStrength()).isGreaterThan(0.8);
}
```

### 7.3 Adding a New Exchange

```java
// 1. Create exchange adapter
@Slf4j
public class MyExchangeAdapter implements Exchange {
    private final String apiKey;
    private final String apiSecret;
    private final OkHttpClient httpClient;
    
    @Override
    public String getExchangeName() {
        return "MyExchange";
    }
    
    @Override
    public Ticker getTicker(TradePair pair) {
        // Call API and parse response
        String response = httpClient.get("/ticker/" + pair.getSymbol());
        return parseTicker(response);
    }
    
    @Override
    public OrderPlacementResult placeOrder(Order order) {
        // Validate order
        // Call API
        // Return result
    }
    
    // ... implement all abstract methods
}

// 2. Register in ExchangeFactory
public class ExchangeFactory {
    public static Exchange create(String name, String apiKey, String apiSecret) {
        return switch(name) {
            case "BINANCE" -> new BinanceExchange(apiKey, apiSecret);
            case "COINBASE" -> new CoinbaseExchange(apiKey, apiSecret);
            case "MYEXCHANGE" -> new MyExchangeAdapter(apiKey, apiSecret); // NEW
            default -> throw new IllegalArgumentException("Unknown exchange");
        };
    }
}

// 3. Test the adapter
@Test
void testMyExchangeAdapter_GetTicker_ReturnsValidTicker() {
    MyExchangeAdapter exchange = new MyExchangeAdapter(apiKey, apiSecret);
    
    Ticker ticker = exchange.getTicker(new TradePair("BTC", "USD"));
    
    assertNotNull(ticker);
    assertTrue(ticker.getBidPrice() > 0);
    assertTrue(ticker.getAskPrice() > 0);
}
```

---

## 8. Debugging Tips

### 8.1 Enable Debug Logging

```properties
# application.properties
logging.level.org.investpro=DEBUG
logging.level.org.investpro.decision=TRACE
logging.level.org.investpro.core=TRACE
```

### 8.2 Debugging in IDE

```
IntelliJ IDEA:
1. Set breakpoint (click left margin on line number)
2. Run → Debug (or Shift+F9)
3. Step over (F10), Step into (F11), Step out (Shift+F11)
4. Inspect variables in Debug panel
5. Watch expressions (Variables tab)
6. Conditional breakpoints (right-click breakpoint)

Eclipse:
1. Window → Show View → Debug
2. Set breakpoint
3. Debug As → Java Application
4. Step controls same as above
```

### 8.3 Common Debug Scenarios

```java
// Debugging trader decision logic
@Test
@Disabled("For manual debugging only")
void debugTradeDecisionFlow() {
    StrategySignal signal = new StrategySignal(...);
    Ticker ticker = createTestTicker();
    
    // Breakpoint here to inspect decision engine flow
    BotTradeDecision decision = engine.evaluateSignal(...);
    
    // Step through evaluateSignal() method
    // Watch: regime, assetType, costEstimate, expectation
    // Check: willTrade(), hasBlockingIssues()
}

// Debugging risk evaluation
@Test
@Disabled("For manual debugging only")
void debugRiskEvaluation() {
    StrategySignal signal = new StrategySignal(...);
    Account account = createTestAccount(10000.0);
    
    // Breakpoint to debug risk checks
    RiskDecision decision = riskMgmt.evaluateTrade(signal, ticker);
    
    // Watch: balance, position size, leverage, margin, correlation
}
```

---

## 9. Performance Profiling

### 9.1 Using JProfiler or YourKit

```bash
# Start app with profiler agent
java -agentpath:/path/to/jprofiler/bin/linux-x64/libjprofilerti.so=port=8849 \
  -jar investpro.jar

# Connect profiler and analyze:
# - CPU usage
# - Memory allocation
# - Thread activity
# - I/O operations
```

### 9.2 JVM Metrics Monitoring

```bash
# Monitor heap usage
jstat -gc -h10 <java_pid> 1000

# Monitor threads
jstat -threads -h10 <java_pid> 1000

# Get thread dump
jstack <java_pid> > threadump.txt

# Get heap dump
jmap -dump:live,format=b,file=heap.bin <java_pid>

# Analyze heap dump
jhat -J-Xmx4g heap.bin
# Open browser to http://localhost:7000
```

---

## 10. Publishing & Release

### 10.1 Version Numbering

```
Semantic Versioning: MAJOR.MINOR.PATCH
- MAJOR: Breaking changes
- MINOR: New features (backward compatible)
- PATCH: Bug fixes

Example:
- 1.0.0 → Initial release
- 1.1.0 → New agent framework
- 1.1.1 → Bug fix in risk evaluation
- 2.0.0 → Major refactor (breaking changes)
```

### 10.2 Release Process

```bash
# 1. Update version in pom.xml
<version>1.1.0</version>

# 2. Update CHANGELOG
# Add entry with date and changes

# 3. Update README if needed
# Add new features to features list

# 4. Commit
git add .
git commit -m "chore: bump version to 1.1.0"

# 5. Create release tag
git tag -a v1.1.0 -m "Release version 1.1.0

Highlights:
- New BotTradeDecisionEngine
- AI-powered trade evaluation
- Improved risk management"

# 6. Build and publish
./mvnw clean deploy

# 7. Push to GitHub
git push origin develop
git push origin main
git push origin --tags

# 8. Create GitHub Release
# Go to GitHub → Releases → Draft New Release
# Select tag v1.1.0
# Add release notes
# Publish release
```

---

## 11. Contributing Guidelines

### 11.1 Pull Request Process

1. **Fork** the repository
2. **Create feature branch** from `develop`
3. **Make changes** with clear commits
4. **Add/update tests** for new features
5. **Ensure clean build** (`./mvnw clean install`)
6. **Create PR** with detailed description
7. **Address review comments**
8. **Squash commits** if requested
9. **Merge** after approval

### 11.2 Commit Message Format

```
type(scope): subject

body

footer

Type: feat, fix, docs, style, refactor, perf, test, chore
Scope: decision, risk, exchange, strategy, etc
Subject: brief description (50 chars max)
Body: detailed explanation (wrap at 72 chars)
Footer: Fixes #123, Relates to #456
```

Example:
```
feat(decision): add BotTradeDecisionEngine with 12-question framework

Implements institutional-grade signal evaluation with comprehensive
risk assessment, cost estimation, and expectancy calculation.

Includes:
- Market regime detection
- Asset type classification
- Strategy fit scoring
- Cost and expectation estimation
- Full audit trail

Fixes #890
```

---

## 12. Common Issues & Solutions

| Issue | Cause | Solution |
|-------|-------|----------|
| Build fails with "Cannot find JavaFX" | JavaFX SDK not configured | Set FX_HOME env var or configure in IDE |
| Tests timeout | Tests too slow | Increase timeout: `<timeout>10000</timeout>` |
| Database migration fails | Schema mismatch | Drop test DB and recreate |
| WebSocket connection drops | Network issue | Check firewall, implement retry logic |
| AI API calls slow | OpenAI API latency | Add caching layer, increase timeout |

---

## 13. Resources

- **Documentation**: [docs/](./docs/)
- **Issue Tracker**: [GitHub Issues](https://github.com/yourusername/investpro/issues)
- **Discussions**: [GitHub Discussions](https://github.com/yourusername/investpro/discussions)
- **Javadoc**: `./mvnw javadoc:javadoc` → `target/site/apidocs/index.html`
- **Architecture**: [SYSTEM_ARCHITECTURE.md](./SYSTEM_ARCHITECTURE.md)
- **Production**: [PRODUCTION_READY.md](./PRODUCTION_READY.md)

---

**Version**: 1.0  
**Last Updated**: May 2026  
**Status**: Production Ready
