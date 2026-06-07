package org.investpro.ai;

import org.investpro.config.AppConfig;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class AiModelCatalog {

    private static final String CFG_DEFAULT_MODEL = "ai.defaultModel";
    private static final String CFG_FREE_MODELS = "ai.freeModelsEnabled";
    private static final String CFG_PAID_MODELS = "ai.paidModelsEnabled";

    private static final Set<AiFeature> STRATEGY_FEATURES = Set.of(
            AiFeature.STRATEGY_DESIGNER,
            AiFeature.MARKET_ANALYSIS,
            AiFeature.ALGORITHM_INTELLIGENCE);

    private static final List<AiModelDefinition> MODELS = List.of(
            model("ANTHROPIC_CLAUDE_3_HAIKU", AiProvider.ANTHROPIC, "Anthropic Claude 3 Haiku", "Fast Anthropic model for compact strategy drafting.", "0.25", false),
            model("ANTHROPIC_CLAUDE_3_5_HAIKU", AiProvider.ANTHROPIC, "Anthropic Claude 3.5 Haiku", "Updated Haiku model for strategy analysis.", "0.80", false),
            model("DEEPSEEK_V3_0324", AiProvider.DEEPSEEK, "DeepSeek V3 0324", "General strategy reasoning model.", "0.30", false),
            model("DEEPSEEK_V3_0324_FREE", AiProvider.DEEPSEEK, "DeepSeek V3 0324 free", "Free DeepSeek strategy generation model.", "0", true),
            model("GEMINI_FLASH_1_5_8B", AiProvider.GOOGLE, "Gemini Flash 1.5 8B", "Lightweight Gemini Flash model.", "0.10", false),
            model("GEMINI_FLASH_2_0", AiProvider.GOOGLE, "Gemini Flash 2.0", "Fast Gemini strategy design model.", "0.20", false),
            model("GEMINI_FLASH_2_0_LITE", AiProvider.GOOGLE, "Gemini Flash 2.0 Lite", "Low-cost Gemini Flash model.", "0.08", false),
            model("GEMINI_2_0_FLASH_EXPERIMENTAL_FREE", AiProvider.GOOGLE, "Gemini 2.0 Flash Experimental free", "Free experimental Gemini Flash model.", "0", true),
            model("GEMINI_2_5_FLASH", AiProvider.GOOGLE, "Gemini 2.5 Flash", "Gemini 2.5 Flash for structured strategy drafting.", "0.30", false),
            model("META_LLAMA_3_3_70B_INSTRUCT", AiProvider.META, "Meta Llama 3.3 70B Instruct", "Large Llama instruction model.", "0.25", false),
            model("META_LLAMA_4_MAVERICK", AiProvider.META, "Meta Llama 4 Maverick", "Meta Llama 4 Maverick strategy model.", "0.35", false),
            model("META_LLAMA_4_SCOUT", AiProvider.META, "Meta Llama 4 Scout", "Meta Llama 4 Scout lightweight model.", "0.18", false),
            model("META_LLAMA_3_1_8B_INSTRUCT_FREE", AiProvider.META, "Meta Llama 3.1 8B Instruct free", "Free compact Llama model.", "0", true),
            model("MICROSOFT_PHI_4", AiProvider.MICROSOFT, "Microsoft Phi 4", "Compact Microsoft reasoning model.", "0.12", false),
            model("MINIMAX_M1", AiProvider.MINIMAX, "MiniMax M1", "MiniMax model for strategy generation.", "0.25", false),
            model("MISTRAL_MEDIUM_FREE", AiProvider.MISTRAL, "Mistral Medium free", "Free Mistral strategy model.", "0", true),
            model("MISTRAL_SMALL_3_1_24B", AiProvider.MISTRAL, "Mistral Small 3.1 24B", "Mistral small model for structured outputs.", "0.15", false),
            model("OPENAI_GPT_4_1_MINI", AiProvider.OPENAI, "OpenAI GPT-4.1 Mini", "OpenAI mini strategy designer model.", "0.40", false),
            model("OPENAI_GPT_4_1_NANO", AiProvider.OPENAI, "OpenAI GPT-4.1 Nano", "OpenAI low-cost strategy designer model.", "0.10", false),
            model("QWEN3_30B_A3B_FREE", AiProvider.QWEN, "Qwen3 30B A3B free", "Free Qwen strategy designer model.", "0", true),
            model("XAI_GROK_4_FAST_FREE", AiProvider.XAI, "xAI Grok 4 Fast free", "Free xAI fast strategy designer model.", "0", true)
    );

    private AiModelCatalog() {
    }

    public static List<AiModelDefinition> availableModels() {
        boolean freeEnabled = AppConfig.getBoolean(CFG_FREE_MODELS, true);
        boolean paidEnabled = AppConfig.getBoolean(CFG_PAID_MODELS, false);
        return MODELS.stream()
                .filter(model -> model.status() == AiModelStatus.OK)
                .filter(model -> model.free() ? freeEnabled : paidEnabled)
                .sorted(Comparator.comparing(AiModelDefinition::free).reversed()
                        .thenComparing(AiModelDefinition::displayName))
                .toList();
    }

    public static List<AiModelDefinition> modelsForFeature(AiFeature feature) {
        return availableModels().stream()
                .filter(model -> model.features().contains(feature))
                .toList();
    }

    public static Optional<AiModelDefinition> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return MODELS.stream()
                .filter(model -> model.id().equalsIgnoreCase(id.trim()))
                .findFirst();
    }

    public static AiModelDefinition defaultModel() {
        String configured = AppConfig.get(CFG_DEFAULT_MODEL, "QWEN3_30B_A3B_FREE");
        return find(configured)
                .filter(model -> availableModels().contains(model))
                .orElseGet(() -> availableModels().stream()
                        .findFirst()
                        .orElse(MODELS.getFirst()));
    }

    public static List<AiModelDefinition> allSeededModels() {
        return MODELS;
    }

    private static AiModelDefinition model(
            String id,
            AiProvider provider,
            String displayName,
            String description,
            String creditsPerMillion,
            boolean free) {
        return new AiModelDefinition(
                id,
                provider,
                displayName,
                description,
                new BigDecimal(creditsPerMillion),
                free,
                false,
                STRATEGY_FEATURES,
                AiModelStatus.OK,
                Map.of("catalog", "investpro"));
    }
}
