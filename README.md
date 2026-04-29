# InvestPro

![Sopotek, inc](src/main/resources/investpro_icon.png)
[![Build Status](https://github.com/nguemechieu/investpro/actions/workflows/maven.yml/badge.svg)](https://github.com/nguemechieu/actions/workflows/maven-publish.yml/badge.svg)
[![Build Status](https://github.com/nguemechieu/investpro/actions/workflows/docker-image.yml/badge.svg)](https://github.com/nguemechieu/actions/workflows/docker-image.yml)
[![license](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://opensource.org/licenses/Apache-2.0)
[![Upload coverage reports to Codecov](https://github.com/nguemechieu/investpro/actions/workflows/codecov.yaml/badge.svg)](https://github.com/nguemechieu/investpro/actions/workflows/codecov.yaml)

## What is InvestPro ?
InvestPro is an AI-assisted investment research, portfolio monitoring, and trade decision-support platform designed for disciplined investors and active traders. It combines market data, portfolio analytics, risk controls, broker integrations, and intelligent agents into one professional command center.
## Supported Platforms

- **BINANCE US**
- **COINBASE PRO**
- **OANDA**
- **TELEGRAM BOT**
- **FOREX NEWS FACTORY**

## Features

- Portfolio Management
- Trade Alerts
- Trade Signals
- Mini Web Browser
- Screenshot Feature

## Supported Operating Systems

- **Windows** ![Windows](./src/docs/windows.ico)
- **Linux** ![Linux](./src/docs/linux.ico)
- **macOS** ![MacOS](./src/docs/macos.ico)

📈 InvestPro – Intelligent Financial Market Analysis and Trading Platform
InvestPro is a modern, full-featured Java-based desktop application designed for real-time financial market analysis,
intelligent trading, and predictive insights using AI. Built with JavaFX, JPA/Hibernate, gRPC, and Spring-inspired
architecture, InvestPro is both a trader’s assistant and a powerful research tool.

💡 Key Features
Real-Time Market Data: Connects to leading crypto and stock exchanges (Coinbase, Binance US, Oanda) to stream live
candlestick data.

Modular Data Suppliers: Abstracted CandleDataSupplier implementations provide flexible multi-exchange support.

AI-Powered Predictions: Seamlessly integrates with a Python-based gRPC prediction server using machine learning (DNNs)
to forecast market direction with confidence scoring.

Interactive Candlestick Charting: Custom JavaFX candlestick chart component with overlays for indicators (RSI, ATR,
MACD, etc.).

Integrated Database (JPA/Hibernate): Stores historical candles, currencies, and user data using MySQL and standard
entity mappings.

Detachable PopOver UI: Intuitive UX using custom-styled popovers for tooltips, configuration panels, and data overlays.

Modular Architecture: Leverages Java Platform Module System (JPMS) for high maintainability, security, and future
scalability.

⚙️ Tech Stack
Frontend: JavaFX (with CSS styling), ControlsFX, JFoenix

Backend/Data: Java 23, JPA (Hibernate), MySQL

AI Integration: Python ML server via gRPC

Messaging: Protocol Buffers (Protobuf)

Security & Logging: SLF4J + Logback, modular package encapsulation

Build Tool: Maven with module path awareness (module-info.java)

🧠 Use Cases
Live market data monitoring

AI-powered trading strategy backtesting

Candle pattern and indicator visualization

Trader decision support system

🧠 AI Integration
InvestPro leverages a companion project called investpro_ai_server — a Python-based gRPC microservice powered by machine
learning models (e.g. TensorFlow/Keras). This AI server performs real-time market predictions (e.g. up/down
classification with confidence) using indicators like RSI, MACD, Bollinger Bands, and OHLCV data.

Prediction Features:

✅ Realtime predictions via gRPC with PredictorGrpc.Predict endpoint

✅ Confidence scoring and decision visualization

✅ Integrated with InvestPro’s charting UI

