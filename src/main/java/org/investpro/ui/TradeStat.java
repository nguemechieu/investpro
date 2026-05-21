package org.investpro.ui;

import java.time.Instant;

record TradeStat(double profit, double notional, Instant timestamp) {
}
