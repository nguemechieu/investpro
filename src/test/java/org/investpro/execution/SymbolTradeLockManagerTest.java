package org.investpro.execution;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymbolTradeLockManagerTest {

    @Test
    void firstLockSucceeds() {
        SymbolTradeLockManager locks = new SymbolTradeLockManager();

        assertTrue(locks.tryLock("OANDA", "EUR/USD", "first"));
        assertTrue(locks.isLocked("OANDA", "EUR/USD"));
        assertEquals("first", locks.getLockReason("OANDA", "EUR/USD"));
    }

    @Test
    void secondLockForSameSymbolFails() {
        SymbolTradeLockManager locks = new SymbolTradeLockManager();

        assertTrue(locks.tryLock("OANDA", "EUR/USD", "first"));
        assertFalse(locks.tryLock("OANDA", "EUR/USD", "second"));
    }

    @Test
    void unlockAllowsLockAgain() {
        SymbolTradeLockManager locks = new SymbolTradeLockManager();

        assertTrue(locks.tryLock("OANDA", "EUR/USD", "first"));
        locks.unlock("OANDA", "EUR/USD");

        assertTrue(locks.tryLock("OANDA", "EUR/USD", "second"));
        assertEquals("second", locks.getLockReason("OANDA", "EUR/USD"));
    }

    @Test
    void expiredLockAllowsNewLock() throws InterruptedException {
        SymbolTradeLockManager locks = new SymbolTradeLockManager(Duration.ofMillis(25));

        assertTrue(locks.tryLock("OANDA", "EUR/USD", "first"));
        Thread.sleep(40);

        assertTrue(locks.tryLock("OANDA", "EUR/USD", "second"));
        assertEquals("second", locks.getLockReason("OANDA", "EUR/USD"));
    }
}
