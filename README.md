# InvestPro

![InvestPro, LLC](src/main/resources/img/investpro_icon.png)

[![Build Status](https://github.com/nguemechieu/investpro/actions/workflows/maven.yml/badge.svg)](https://github.com/nguemechieu/actions/workflows/maven-publish.yml/badge.svg)
[![Build Status](https://github.com/nguemechieu/investpro/actions/workflows/docker-image.yml/badge.svg)](https://github.com/nguemechieu/actions/workflows/docker-image.yml)
[![license](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://opensource.org/licenses/Apache-2.0)
[![Upload coverage reports to Codecov](https://github.com/nguemechieu/investpro/actions/workflows/codecov.yaml/badge.svg)](https://github.com/nguemechieu/investpro/actions/workflows/codecov.yaml)

## Welcome to InvestPro

**InvestPro** is a powerful investment and utility software offering a range of features for managing investments, trading, and accessing financial data. It supports platforms like BINANCE US, COINBASE PRO, OANDA, TELEGRAM BOT, FOREX NEWS FACTORY, and provides features such as portfolio management, trade alerts, and trade signals.

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

## Getting Started

### Prerequisites

1. **Install Java Development Kit (JDK) version 11 or higher**.
2. **Install Apache Maven version 3.6 or higher**.
3. **Clone the InvestPro repository** from GitHub.

### Setup Process

1. Open a terminal in the project directory.
2. Run the following command to compile and package the application:

    ```bash
    mvn clean package
    ```

3. Run the following command to start the application:

    ```bash
    java -jar target/investpro-1.0-SNAPSHOT.jar
    ```

This will start the application on port 8080. Modify the port number if another application is using port 8080.

### Build Docker Image

You can build the Docker image by running the following command:

```bash
docker build -t investpro .
