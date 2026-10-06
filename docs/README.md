# InvestPro documentation index

Updated: 2026-10-05. Source of truth: current source, POM and deployment files.
Desktop baseline: JDK 27, JavaFX 27 and Maven 3.8.5+. Status: active development.

## Start here

- [Project overview and installation](../README.md)
- [Trading Desk user guide](trading-desk.md)
- [Telegram remote desk and OpenAI questions](telegram-remote-desk.md)
- [Python AI service setup and limitations](../ai-service/README.md)
- [Developer guide](../DEVELOPER_GUIDE.md)
- [Docker setup](../DOCKER_SETUP.md)
- [Release readiness](../PRODUCTION_READY.md) and [release checklist](../PRODUCTION_RELEASE_CHECKLIST.md)
- [Current status](../STATUS.md), [quick reference](../QUICK_REFERENCE.md) and [security](../SECURITY.md)

PAPER uses a session-local simulator; broker authentication does not enable live
execution in paper mode. Telegram confirmations and authorized private chats are
required for remote actions. Local Python AI uses rule-based advisory RPCs and is
separate from OpenAI conversation support. See the guides for exact limitations.

## Current guides and design references

Design/API examples in specialist guides describe their intended subsystem; they
are not proof that every adapter or deployment has been verified. Prefer the
startup and behavior guides above where an older example conflicts with current code.

| Document | Repository path |
|---|---|
| [InvestPro local AI service](../ai-service/README.md) | `ai-service/README.md` |
| [InvestPro Clean Architecture Refactoring Guide](../ARCHITECTURE.md) | `ARCHITECTURE.md` |
| [InvestPro Backtesting Module - Implementation Guide](../BACKTESTING_GUIDE.md) | `BACKTESTING_GUIDE.md` |
| [Controller Integration Guide - How to Use](../CONTROLLER_INTEGRATION_GUIDE.md) | `CONTROLLER_INTEGRATION_GUIDE.md` |
| [InvestPro - Developer Guide](../DEVELOPER_GUIDE.md) | `DEVELOPER_GUIDE.md` |
| [Docker setup](../DOCKER_SETUP.md) | `DOCKER_SETUP.md` |
| [Docker usage guide](../DOCKER_USAGE_GUIDE.md) | `DOCKER_USAGE_GUIDE.md` |
| [Auto Strategy Lab](AUTO_STRATEGY_LAB.md) | `docs/AUTO_STRATEGY_LAB.md` |
| [Currency Registry In InvestPro](CURRENCY_REGISTRY.md) | `docs/CURRENCY_REGISTRY.md` |
| [Exchange stream access](exchange-stream-access.md) | `docs/exchange-stream-access.md` |
| [IBKR Professional Architecture Flows](ibkr/IBKR_ARCHITECTURE_FLOWS.md) | `docs/ibkr/IBKR_ARCHITECTURE_FLOWS.md` |
| [Market Instruments](MARKET_INSTRUMENTS.md) | `docs/MARKET_INSTRUMENTS.md` |
| [InvestPro Plugin Architecture](PLUGIN_ARCHITECTURE.md) | `docs/PLUGIN_ARCHITECTURE.md` |
| [Multi-Strategy Framework Integration Guide](strategy/strategy-integration-guide.md) | `docs/strategy/strategy-integration-guide.md` |
| [Telegram remote desk](telegram-remote-desk.md) | `docs/telegram-remote-desk.md` |
| [InvestPro Terminal Stage 1 Architecture](TERMINAL_STAGE1_ARCHITECTURE.md) | `docs/TERMINAL_STAGE1_ARCHITECTURE.md` |
| [InvestPro Terminal Stage 2: Provider Adapter Bridges](TERMINAL_STAGE2_ADAPTER_BRIDGES.md) | `docs/TERMINAL_STAGE2_ADAPTER_BRIDGES.md` |
| [Trading desk user guide](trading-desk.md) | `docs/trading-desk.md` |
| [Trading Decision Pipeline](trading-pipeline.md) | `docs/trading-pipeline.md` |
| [Transfer Funds Architecture](transfer/TRANSFER_ARCHITECTURE.md) | `docs/transfer/TRANSFER_ARCHITECTURE.md` |
| [InvestPro User Strategy Guide](USER_STRATEGY_GUIDE.md) | `docs/USER_STRATEGY_GUIDE.md` |
| [InvestPro User Strategy Development Guide](user-strategies.md) | `docs/user-strategies.md` |
| [Simple EMA Crossover User Strategy](../examples/user-strategy-simple-ema/README.md) | `examples/user-strategy-simple-ema/README.md` |
| [Local AI integration](../LOCAL_AI_RUNTIME.md) | `LOCAL_AI_RUNTIME.md` |
| [Comprehensive Menu System for InvestPro](../MENU_SYSTEM_GUIDE.md) | `MENU_SYSTEM_GUIDE.md` |
| [DefaultMoneyFormatter - Code Reference](../MONEYFORMATTER_CODE_REFERENCE.md) | `MONEYFORMATTER_CODE_REFERENCE.md` |
| [InvestPro System Monitor - Modernization Guide](../MONITORING_SYSTEM.md) | `MONITORING_SYSTEM.md` |
| [InvestPro release readiness](../PRODUCTION_READY.md) | `PRODUCTION_READY.md` |
| [Production Release Checklist](../PRODUCTION_RELEASE_CHECKLIST.md) | `PRODUCTION_RELEASE_CHECKLIST.md` |
| [InvestPro quick reference](../QUICK_REFERENCE.md) | `QUICK_REFERENCE.md` |
| [README](../README.md) | `README.md` |
| [Security guidance](../SECURITY.md) | `SECURITY.md` |
| [InvestPro - Sequence Diagrams & Workflows](../SEQUENCE_DIAGRAMS.md) | `SEQUENCE_DIAGRAMS.md` |
| [CSS Improvements Summary](../src/main/resources/CSS_IMPROVEMENTS.md) | `src/main/resources/CSS_IMPROVEMENTS.md` |
| [CSS_VARIABLES_REFERENCE](../src/main/resources/CSS_VARIABLES_REFERENCE.md) | `src/main/resources/CSS_VARIABLES_REFERENCE.md` |
| [InvestPro current status](../STATUS.md) | `STATUS.md` |
| [Java Strategy Architecture - Implementation Guide](../STRATEGY_ARCHITECTURE.md) | `STRATEGY_ARCHITECTURE.md` |
| [STRATEGY_INTEGRATION_GUIDE](../STRATEGY_INTEGRATION_GUIDE.md) | `STRATEGY_INTEGRATION_GUIDE.md` |
| [Symbol-Level Status Tracking System - Implementation Summary](../SYMBOL_STATUS_TRACKING.md) | `SYMBOL_STATUS_TRACKING.md` |
| [InvestPro System Architecture](../SYSTEM_ARCHITECTURE.md) | `SYSTEM_ARCHITECTURE.md` |
| [Telegram bot guide](../TELEGRAM_BOT_GUIDE.md) | `TELEGRAM_BOT_GUIDE.md` |
| [Theme Customization System - User Guide](../THEME_CUSTOMIZATION_GUIDE.md) | `THEME_CUSTOMIZATION_GUIDE.md` |
| [InvestPro - UML Class Diagrams](../UML_CLASS_DIAGRAMS.md) | `UML_CLASS_DIAGRAMS.md` |

## Historical reports

These reports retain original dates, Java versions, test counts and completion
claims as historical evidence only. They must not be used as current installation
instructions or release certification.

| Document | Repository path |
|---|---|
| [InvestPro Architecture Refactoring Guide](../ARCHITECTURE_REFACTORING.md) | `ARCHITECTURE_REFACTORING.md` |
| [Architecture Refactoring - Completion Summary](../ARCHITECTURE_REFACTORING_COMPLETE.md) | `ARCHITECTURE_REFACTORING_COMPLETE.md` |
| [Coinbase 401 Unauthorized - Troubleshooting Guide](../COINBASE_401_FIX.md) | `COINBASE_401_FIX.md` |
| [Coinbase Adapter Refactoring - Status Report](../COINBASE_REFACTORING.md) | `COINBASE_REFACTORING.md` |
| [Docker Reconfiguration Summary - Phase 2 Complete ✅](../DOCKER_IMPLEMENTATION_SUMMARY.md) | `DOCKER_IMPLEMENTATION_SUMMARY.md` |
| [Exchange Refactoring Complete ✅](../EXCHANGE_REFACTORING_SUMMARY.md) | `EXCHANGE_REFACTORING_SUMMARY.md` |
| [Method Usage Analysis - InvestPro Exchange Interface](../METHOD_USAGE_ANALYSIS.md) | `METHOD_USAGE_ANALYSIS.md` |
| [DefaultMoneyFormatter Refactoring Summary](../MONEYFORMATTER_REFACTORING.md) | `MONEYFORMATTER_REFACTORING.md` |
| [MySQL to PostgreSQL Migration Guide for InvestPro](../MYSQL_TO_POSTGRESQL_MIGRATION.md) | `MYSQL_TO_POSTGRESQL_MIGRATION.md` |
| [Critical Refactoring Checklist - Priority Order](../REFACTORING_CHECKLIST.md) | `REFACTORING_CHECKLIST.md` |
| [Session 8 Summary - Code Quality & Java 21 Compliance](../SESSION_8_SUMMARY.md) | `SESSION_8_SUMMARY.md` |
| [Session 9 Summary - Theme Customization System](../SESSION_9_SUMMARY.md) | `SESSION_9_SUMMARY.md` |
| [Signal/Strategy Integration System - Complete Documentation](../SIGNAL_INTEGRATION_COMPLETE.md) | `SIGNAL_INTEGRATION_COMPLETE.md` |
| [InvestPro Stabilization & Architecture Consolidation - COMPLETION SUMMARY](../STABILIZATION_COMPLETE_SESSION6.md) | `STABILIZATION_COMPLETE_SESSION6.md` |
| [Tiered Strategy Startup Optimization](../TIERED_STARTUP_OPTIMIZATION.md) | `TIERED_STARTUP_OPTIMIZATION.md` |
| [User Strategy System - Implementation Complete](../USER_STRATEGY_IMPLEMENTATION.md) | `USER_STRATEGY_IMPLEMENTATION.md` |
