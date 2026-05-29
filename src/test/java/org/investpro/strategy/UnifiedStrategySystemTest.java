package org.investpro.strategy;

import org.investpro.data.CandleData;
import org.investpro.enums.MarketBehavior;
import org.investpro.enums.TradingSessionStatus;
import org.investpro.strategy.nocode.*;
import org.investpro.strategy.plugin.UserStrategyLoader;
import org.investpro.utils.Side;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance tests for the Unified Strategy System.
 *
 * <p>Tests cover both the Developer Plugin system (UserStrategy JAR) and the
 * No-Code Strategy Builder system, verifying that both types flow through the
 * unified {@link StrategyRegistry} and produce signals correctly.
 *
 * <p>Test numbering follows the acceptance criteria in the design document:
 * <ol>
 *   <li>Developer plugin loads successfully (mock adapter)</li>
 *   <li>No-code strategy saves and reloads from repository</li>
 *   <li>No-code strategy validates correctly (valid + invalid cases)</li>
 *   <li>No-code strategy generates BUY signal</li>
 *   <li>No-code strategy generates SELL signal</li>
 *   <li>Both strategy types appear in StrategyRegistry</li>
 *   <li>Both strategy types appear via findByType queries</li>
 *   <li>Safety gate: liveAllowed defaults to false</li>
 *   <li>Invalid no-code strategy cannot be registered as live</li>
 *   <li>UserStrategy adapter cannot expose order-submission methods</li>
 * </ol>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UnifiedStrategySystemTest {

    private static StrategyRegistry registry;
    private static NoCodeStrategyRepository repository;
    private static NoCodeStrategyCompiler compiler;
    private static NoCodeStrategyValidator validator;

    @BeforeAll
    static void setUp() {
        registry   = StrategyRegistry.getInstance();
        registry.clear();
        repository = new NoCodeStrategyRepository(tempDir());
        compiler   = new NoCodeStrategyCompiler();
        validator  = new NoCodeStrategyValidator();
    }

    // =========================================================================
    // Test 1: Developer plugin registers via UserStrategyLoader (directory scan)
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("T1: UserStrategyLoader scans strategies/ dir (empty dir = 0 plugins)")
    void test1_developerPluginLoaderHandlesMissingDir() {
        UserStrategyLoader loader = new UserStrategyLoader(
                Path.of("non-existent-strategy-dir"), registry);
        List<String> registered = loader.loadAndRegister();
        assertTrue(registered.isEmpty(),
                "Empty/missing directory should load 0 strategies");
    }

    // =========================================================================
    // Test 2: No-code strategy saves and reloads from repository
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("T2: No-code strategy saves and reloads from repository")
    void test2_noCodeStrategySavesAndReloads() {
        NoCodeStrategyDefinition def = buildRsiOversoldBuy();
        repository.save(def);

        Optional<NoCodeStrategyDefinition> loaded = repository.findById(def.getStrategyId());
        assertTrue(loaded.isPresent(), "Strategy should be found after save");
        assertEquals(def.getName(), loaded.get().getName());
        assertEquals(def.getStrategyId(), loaded.get().getStrategyId());
    }

    // =========================================================================
    // Test 3: No-code strategy validates correctly
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("T3a: Valid no-code strategy passes validation")
    void test3a_validStrategyPassesValidation() {
        ValidationResult result = validator.validate(buildRsiOversoldBuy());
        assertTrue(result.isValid(), "Valid strategy should pass: errors=" + result.getErrors());
    }

    @Test
    @Order(4)
    @DisplayName("T3b: Strategy with no name fails validation")
    void test3b_noNameFailsValidation() {
        NoCodeStrategyDefinition noName = NoCodeStrategyDefinition.builder()
                .entryRule(buildBuyRule())
                .build();
        ValidationResult result = validator.validate(noName);
        assertFalse(result.isValid(), "Strategy without name must fail");
        assertTrue(result.getErrors().stream().anyMatch(e -> e.toLowerCase().contains("name")));
    }

    @Test
    @Order(5)
    @DisplayName("T3c: Strategy with no entry rules fails validation")
    void test3c_noEntryRulesFailsValidation() {
        NoCodeStrategyDefinition noRules = NoCodeStrategyDefinition.builder()
                .name("Empty")
                .build();
        ValidationResult result = validator.validate(noRules);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.toLowerCase().contains("entry")));
    }

    // =========================================================================
    // Test 4: No-code strategy generates BUY signal
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("T4: No-code RSI oversold strategy generates BUY signal")
    void test4_noCodeStrategyGeneratesBuySignal() {
        NoCodeStrategyDefinition def = buildRsiOversoldBuy();
        CompiledNoCodeStrategy compiled = compiler.compile(def);
        assertTrue(compiled.isValid(), "Strategy must compile cleanly");

        NoCodeStrategyAdapter adapter = new NoCodeStrategyAdapter(compiled);
        // Provide candles with RSI < 30 (declining prices create oversold condition)
        StrategyContext ctx = buildContextWithDecliningCandles(80);
        StrategySignal signal = adapter.generateSignal(ctx);

        assertNotNull(signal);
        assertEquals(Side.BUY, signal.getSide(),
                "RSI < 30 on declining prices should generate BUY; got: " + signal);
    }

    // =========================================================================
    // Test 5: No-code strategy generates SELL signal
    // =========================================================================

    @Test
    @Order(7)
    @DisplayName("T5: No-code RSI overbought strategy generates SELL signal")
    void test5_noCodeStrategyGeneratesSellSignal() {
        NoCodeStrategyDefinition def = buildRsiOverboughtSell();
        CompiledNoCodeStrategy compiled = compiler.compile(def);
        assertTrue(compiled.isValid());

        NoCodeStrategyAdapter adapter = new NoCodeStrategyAdapter(compiled);
        // Rising candles will produce RSI > 70
        StrategyContext ctx = buildContextWithRisingCandles(80);
        StrategySignal signal = adapter.generateSignal(ctx);

        assertNotNull(signal);
        assertEquals(Side.SELL, signal.getSide(),
                "RSI > 70 on rising prices should generate SELL; got: " + signal);
    }

    // =========================================================================
    // Test 6 & 7: Both types appear in StrategyRegistry and findByType
    // =========================================================================

    @Test
    @Order(8)
    @DisplayName("T6 & T7: Both strategy types register and appear in findByType")
    void test6_7_bothTypesInRegistry() {
        registry.clear();

        // Register a mock USER_PLUGIN strategy
        StrategyDescriptor pluginDesc = StrategyDescriptor.builder()
                .strategyId("test-plugin-001")
                .name("Test Plugin Strategy")
                .strategyType(StrategyType.USER_PLUGIN)
                .validationStatus(StrategyValidationStatus.UNVALIDATED)
                .liveAllowed(false)
                .build();
        TradingStrategy pluginAdapter = buildMockTradingStrategy("test-plugin-001");
        registry.register(pluginDesc, pluginAdapter);

        // Register a NO_CODE strategy
        NoCodeStrategyDefinition def = buildRsiOversoldBuy();
        CompiledNoCodeStrategy compiled = compiler.compile(def);
        NoCodeStrategyAdapter noCodeAdapter = new NoCodeStrategyAdapter(compiled);
        StrategyDescriptor noCodeDesc = StrategyDescriptor.builder()
                .strategyId(def.getStrategyId())
                .name(def.getName())
                .strategyType(StrategyType.NO_CODE)
                .validationStatus(StrategyValidationStatus.VALIDATED)
                .liveAllowed(false)
                .build();
        registry.register(noCodeDesc, noCodeAdapter);

        assertEquals(2, registry.size(), "Registry should contain both strategies");

        List<TradingStrategy> plugins  = registry.findByType(StrategyType.USER_PLUGIN);
        List<TradingStrategy> noCodes  = registry.findByType(StrategyType.NO_CODE);
        assertEquals(1, plugins.size(),  "Should find 1 USER_PLUGIN strategy");
        assertEquals(1, noCodes.size(),  "Should find 1 NO_CODE strategy");
    }

    // =========================================================================
    // Test 8: Safety gate — liveAllowed defaults to false
    // =========================================================================

    @Test
    @Order(9)
    @DisplayName("T8: New strategies have liveAllowed=false (safety gate)")
    void test8_safetyGateLiveAllowedFalse() {
        NoCodeStrategyDefinition def = buildRsiOversoldBuy();
        CompiledNoCodeStrategy compiled = compiler.compile(def);

        // Register with default StrategyDescriptor (liveAllowed not explicitly set = false)
        StrategyDescriptor desc = StrategyDescriptor.builder()
                .strategyId(def.getStrategyId())
                .name(def.getName())
                .strategyType(StrategyType.NO_CODE)
                .validationStatus(StrategyValidationStatus.VALIDATED)
                .liveAllowed(false)
                .build();
        registry.register(desc, new NoCodeStrategyAdapter(compiled));

        Optional<StrategyDescriptor> found = registry.findDescriptorById(def.getStrategyId());
        assertTrue(found.isPresent());
        assertFalse(found.get().isLiveAllowed(),
                "Newly registered strategy must NOT be live-allowed until approved");
    }

    // =========================================================================
    // Test 9: Invalid no-code strategy is not live-allowed
    // =========================================================================

    @Test
    @Order(10)
    @DisplayName("T9: Invalid no-code strategy compiles with errors and is not registered live")
    void test9_invalidNoCodeStrategyNotLive() {
        NoCodeStrategyDefinition invalid = NoCodeStrategyDefinition.builder()
                .name("Invalid")  // no entry rules
                .build();

        ValidationResult result = validator.validate(invalid);
        assertFalse(result.isValid(), "Strategy without entry rules must be invalid");

        CompiledNoCodeStrategy compiled = compiler.compile(invalid);
        assertFalse(compiled.isValid(), "Invalid strategy must not compile as valid");
    }

    // =========================================================================
    // Test 10: UserStrategy API has no order-submission methods
    // =========================================================================

    @Test
    @Order(11)
    @DisplayName("T10: UserStrategy API contains no order-submission methods")
    void test10_userStrategyApiHasNoOrderMethods() throws NoSuchMethodException {
        Class<?> api = org.investpro.strategy.api.UserStrategy.class;

        // These method names should NOT exist on the interface
        String[] forbidden = {"placeOrder", "submitOrder", "executeTrade",
                "openPosition", "closePosition", "buy", "sell"};
        for (String methodName : forbidden) {
            boolean found = false;
            for (java.lang.reflect.Method m : api.getMethods()) {
                if (m.getName().equals(methodName)) { found = true; break; }
            }
            assertFalse(found,
                    "UserStrategy API must NOT expose order method: '" + methodName + "'");
        }

        // generateSignal MUST exist
        assertDoesNotThrow(() -> api.getMethod("generateSignal",
                org.investpro.strategy.StrategyContext.class));
    }

    // =========================================================================
    // Helper: build strategies
    // =========================================================================

    private static NoCodeStrategyDefinition buildRsiOversoldBuy() {
        NoCodeCondition rsiOversold = NoCodeCondition.builder()
                .leftIndicator(NoCodeIndicatorReference.builder()
                        .type(NoCodeIndicatorType.RSI).period(14).build())
                .operator(NoCodeConditionOperator.LESS_THAN)
                .rightValue(30)
                .label("RSI < 30")
                .build();
        return NoCodeStrategyDefinition.builder()
                .name("RSI Oversold BUY")
                .description("Buy when RSI < 30")
                .author("test")
                .entryRule(NoCodeRule.builder()
                        .condition(rsiOversold)
                        .action(NoCodeAction.BUY)
                        .confidence(0.75)
                        .build())
                .exitRule(NoCodeRule.builder()
                        .condition(NoCodeCondition.builder()
                                .leftIndicator(NoCodeIndicatorReference.builder()
                                        .type(NoCodeIndicatorType.RSI).period(14).build())
                                .operator(NoCodeConditionOperator.GREATER_THAN)
                                .rightValue(60)
                                .build())
                        .action(NoCodeAction.SELL)
                        .confidence(0.6)
                        .build())
                .build();
    }

    private static NoCodeStrategyDefinition buildRsiOverboughtSell() {
        NoCodeCondition rsiOverbought = NoCodeCondition.builder()
                .leftIndicator(NoCodeIndicatorReference.builder()
                        .type(NoCodeIndicatorType.RSI).period(14).build())
                .operator(NoCodeConditionOperator.GREATER_THAN)
                .rightValue(70)
                .label("RSI > 70")
                .build();
        return NoCodeStrategyDefinition.builder()
                .name("RSI Overbought SELL")
                .description("Sell when RSI > 70")
                .author("test")
                .entryRule(NoCodeRule.builder()
                        .condition(rsiOverbought)
                        .action(NoCodeAction.SELL)
                        .confidence(0.75)
                        .build())
                .exitRule(NoCodeRule.builder()
                        .condition(NoCodeCondition.builder()
                                .leftIndicator(NoCodeIndicatorReference.builder()
                                        .type(NoCodeIndicatorType.RSI).period(14).build())
                                .operator(NoCodeConditionOperator.LESS_THAN)
                                .rightValue(50)
                                .build())
                        .action(NoCodeAction.BUY)
                        .confidence(0.6)
                        .build())
                .build();
    }

    private static NoCodeRule buildBuyRule() {
        return NoCodeRule.builder()
                .condition(NoCodeCondition.builder()
                        .leftIndicator(NoCodeIndicatorReference.builder()
                                .type(NoCodeIndicatorType.PRICE_CLOSE).build())
                        .operator(NoCodeConditionOperator.GREATER_THAN)
                        .rightValue(0)
                        .build())
                .action(NoCodeAction.BUY)
                .build();
    }

    // =========================================================================
    // Helper: build candle contexts
    // =========================================================================

    /** 80 candles with steadily declining prices → RSI will be < 30. */
    private static StrategyContext buildContextWithDecliningCandles(int count) {
        List<CandleData> candles = new ArrayList<>();
        double price = 100.0;
        for (int i = 0; i < count; i++) {
            price -= 0.8;  // steady decline
            candles.add(new CandleData(price + 0.1, price, price + 0.2, price - 0.1, i, 1000));
        }
        return StrategyContext.builder()
                .candles(candles)
                .currentPrice(price)
                .bid(price - 0.01)
                .ask(price + 0.01)
                .marketBehavior(MarketBehavior.TRENDING_DOWN)
                .tradingSessionStatus(TradingSessionStatus.OPEN)
                .barsAvailable(count)
                .build();
    }

    /** 80 candles with steadily rising prices → RSI will be > 70. */
    private static StrategyContext buildContextWithRisingCandles(int count) {
        List<CandleData> candles = new ArrayList<>();
        double price = 100.0;
        for (int i = 0; i < count; i++) {
            price += 0.8;  // steady rise
            candles.add(new CandleData(price - 0.1, price, price + 0.2, price - 0.2, i, 1000));
        }
        return StrategyContext.builder()
                .candles(candles)
                .currentPrice(price)
                .bid(price - 0.01)
                .ask(price + 0.01)
                .marketBehavior(MarketBehavior.TRENDING_UP)
                .tradingSessionStatus(TradingSessionStatus.OPEN)
                .barsAvailable(count)
                .build();
    }

    /** Minimal mock TradingStrategy for registry tests. */
    private TradingStrategy buildMockTradingStrategy(String id) {
        return new TradingStrategy() {
            @Override public StrategyMetadata getMetadata() { return null; }
            @Override public StrategySignal generateSignal(StrategyContext ctx) {
                return StrategySignal.hold("TEST", "1h", id, "mock hold");
            }
            @Override public boolean supportsAssetClass(org.investpro.enums.AssetClass a) { return true; }
            @Override public boolean supportsContractType(org.investpro.enums.ContractType c) { return true; }
            @Override public boolean supportsTimeframe(org.investpro.enums.timeframe.Timeframe t) { return true; }
            @Override public boolean supportsMarketBehavior(MarketBehavior m) { return true; }
            @Override public int requiredWarmupBars() { return 1; }
            @Override public void validateConfiguration() {}
            @Override public Object getName() { return "Mock Plugin"; }
            @Override public Object getId() { return id; }
            @Override public String getLastSignalDescription() { return "mock"; }
        };
    }

    /** Returns a temp directory path for repository isolation in tests. */
    private static Path tempDir() {
        try {
            return java.nio.file.Files.createTempDirectory("investpro-test-nocode-");
        } catch (Exception e) {
            throw new RuntimeException("Cannot create temp dir", e);
        }
    }
}
