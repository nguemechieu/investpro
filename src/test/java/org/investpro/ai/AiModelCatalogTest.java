package org.investpro.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AiModelCatalogTest {

    @AfterEach
    void clearProperties() {
        System.clearProperty("ai.freeModelsEnabled");
        System.clearProperty("ai.paidModelsEnabled");
        System.clearProperty("ai.defaultModel");
    }

    @Test
    void seedsRequestedAiModelsAndUsesFreeDefault() {
        System.setProperty("ai.freeModelsEnabled", "true");
        System.setProperty("ai.paidModelsEnabled", "false");
        System.setProperty("ai.defaultModel", "QWEN3_30B_A3B_FREE");

        assertThat(AiModelCatalog.allSeededModels()).hasSize(21);
        assertThat(AiModelCatalog.defaultModel().id()).isEqualTo("QWEN3_30B_A3B_FREE");
        assertThat(AiModelCatalog.availableModels()).allMatch(AiModelDefinition::free);
    }

    @Test
    void paidModelCostRequiresCreditsButFreeModelDoesNot() {
        AiModelDefinition paid = AiModelCatalog.find("OPENAI_GPT_4_1_MINI").orElseThrow();
        AiModelDefinition free = AiModelCatalog.find("QWEN3_30B_A3B_FREE").orElseThrow();
        AiCreditAccount account = new AiCreditAccount(BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(AiCostEstimator.estimateTotalCredits(free, "build an RSI strategy")).isZero();
        assertThat(AiCostEstimator.estimateTotalCredits(paid, "build an RSI strategy")).isPositive();
        assertThat(account.hasEnoughCredits(AiCostEstimator.estimateTotalCredits(paid, "build an RSI strategy"))).isFalse();
    }
}
