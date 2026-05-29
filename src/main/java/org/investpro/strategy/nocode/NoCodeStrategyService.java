package org.investpro.strategy.nocode;

import lombok.extern.slf4j.Slf4j;
import org.investpro.strategy.StrategyDescriptor;
import org.investpro.strategy.StrategyRegistry;
import org.investpro.strategy.StrategyType;
import org.investpro.strategy.StrategyValidationStatus;

import java.util.List;

/**
 * Application-level service that binds the no-code strategy persistence layer
 * ({@link NoCodeStrategyRepository}) to the central {@link StrategyRegistry}.
 *
 * <p>Call {@link #loadAllAndRegister()} at startup to populate the registry
 * with all persisted no-code strategies. Call {@link #save(NoCodeStrategyDefinition)}
 * to persist and register a newly built strategy in one step.</p>
 *
 * <p>This class is not thread-safe on its own. Callers should synchronise
 * externally if multiple threads interact with the same instance.</p>
 */
@Slf4j
public class NoCodeStrategyService {

    private final NoCodeStrategyRepository repository;
    private final NoCodeStrategyCompiler compiler;
    private final StrategyRegistry registry;

    /** Creates a service with default repository and registry. */
    public NoCodeStrategyService() {
        this(new NoCodeStrategyRepository(),
             new NoCodeStrategyCompiler(),
             StrategyRegistry.getInstance());
    }

    /** Creates a service with custom dependencies (useful for testing). */
    public NoCodeStrategyService(NoCodeStrategyRepository repository,
                                 NoCodeStrategyCompiler compiler,
                                 StrategyRegistry registry) {
        this.repository = repository;
        this.compiler   = compiler;
        this.registry   = registry;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Persists a strategy definition and registers it in the {@link StrategyRegistry}.
     *
     * <p>The strategy must pass validation ({@link NoCodeStrategyValidator}) before
     * it can be compiled. Invalid strategies are saved to the repository but are
     * NOT registered (a warning is logged).</p>
     *
     * @param def the strategy definition to save and register
     * @return the compiled result (check {@link CompiledNoCodeStrategy#isValid()})
     */
    public CompiledNoCodeStrategy save(NoCodeStrategyDefinition def) {
        repository.save(def);
        CompiledNoCodeStrategy compiled = compiler.compile(def);
        if (compiled.isValid()) {
            registerCompiled(compiled);
        } else {
            log.warn("Strategy '{}' saved but NOT registered due to validation errors: {}",
                    def.getName(), compiled.getValidationResult().getErrors());
        }
        return compiled;
    }

    /**
     * Loads all persisted no-code strategies and registers valid ones.
     *
     * @return number of strategies successfully registered
     */
    public int loadAllAndRegister() {
        List<NoCodeStrategyDefinition> all = repository.findAll();
        int count = 0;
        for (NoCodeStrategyDefinition def : all) {
            CompiledNoCodeStrategy compiled = compiler.compile(def);
            if (compiled.isValid()) {
                registerCompiled(compiled);
                count++;
            } else {
                log.warn("Skipping persisted strategy '{}' (validation errors): {}",
                        def.getName(), compiled.getValidationResult().getErrors());
            }
        }
        log.info("Loaded and registered {} no-code strategy/strategies", count);
        return count;
    }

    /**
     * Deletes a strategy from repository and unregisters it from the registry.
     *
     * @param strategyId the strategy ID
     * @return true if successfully deleted
     */
    public boolean delete(String strategyId) {
        registry.unregister(strategyId);
        return repository.delete(strategyId);
    }

    /** @return the underlying repository for advanced CRUD operations. */
    public NoCodeStrategyRepository getRepository() {
        return repository;
    }

    // =========================================================================
    // Private
    // =========================================================================

    private void registerCompiled(CompiledNoCodeStrategy compiled) {
        NoCodeStrategyDefinition def = compiled.getDefinition();
        NoCodeStrategyAdapter adapter = new NoCodeStrategyAdapter(compiled);
        StrategyDescriptor descriptor = StrategyDescriptor.builder()
                .strategyId(def.getStrategyId())
                .name(def.getName())
                .description(def.getDescription() != null ? def.getDescription() : "")
                .strategyType(StrategyType.NO_CODE)
                .source("NoCode:" + def.getStrategyId())
                .version(def.getVersion())
                .author(def.getAuthor() != null ? def.getAuthor() : "user")
                .warmupBars(compiled.getWarmupBars())
                .validationStatus(StrategyValidationStatus.VALIDATED)
                .liveAllowed(false)
                .build();
        registry.register(descriptor, adapter);
    }
}
