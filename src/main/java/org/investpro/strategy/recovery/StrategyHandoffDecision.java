package org.investpro.strategy.recovery;

/**
 * Safety decision when considering assignment replacement while positions may
 * still be open.
 */
public enum StrategyHandoffDecision {
    ALLOW_IMMEDIATE_REPLACEMENT,
    BLOCK_UNTIL_POSITION_CLOSED,
    REQUIRE_MANUAL_REVIEW,
    ARCHIVE_ONLY
}
