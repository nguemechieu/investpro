# IBKR Professional Architecture Flows

This document describes the production-oriented IBKR architecture that now exists in InvestPro under `org.investpro.broker.ibkr`.

## Components

- `IBKRBroker`: orchestrator implementing broker-level workflow.
- `IBKRConnectionManager`: session lifecycle, heartbeat, and auto-reconnect loop.
- `IBKRAccountService`: account retrieval bridge.
- `IBKRPositionService`: position retrieval bridge.
- `IBKROrderService`: order mapping and execution bridge.
- `IBKRMarketDataService`: streaming adapter for ticker updates.
- `IBKRContractService`: contract normalization and mapping.
- `IBKREventBridge`: JavaFX `ObservableList` and EventBus callback fanout.
- `IBKROfficialApiGateway`: integration point for official `EClientSocket`, `EWrapper`, and `EReader` callback model.

## Connection Flow

```mermaid
sequenceDiagram
    participant UI as IBKRConnectionDialog
    participant Broker as IBKRBroker
    participant Conn as IBKRConnectionManager
    participant Ex as IbkrExchange
    participant API as IBKROfficialApiGateway

    UI->>Broker: connect()
    Broker->>Conn: connect()
    Conn->>Ex: connect(mode)
    Conn->>API: connect(host, port, clientId)
    Conn->>API: ensureReaderLoopRunning()
    Conn-->>Broker: connected
    Broker->>Broker: refresh account/positions/orders
```

## Login and Session Health Flow

```mermaid
flowchart TD
    A[Connect Request] --> B[IBKRConnectionManager.connect]
    B --> C[IbkrExchange Connection]
    B --> D[Official API Gateway Connect]
    C --> E{Connected?}
    D --> E
    E -- Yes --> F[Heartbeat Marked]
    F --> G[Scheduled Health Tick]
    G --> H{Connection Stale?}
    H -- No --> F
    H -- Yes and autoReconnect --> B
```

## Account Synchronization Flow

```mermaid
sequenceDiagram
    participant Broker as IBKRBroker
    participant AccountSvc as IBKRAccountService
    participant Exchange as IbkrExchange
    participant Bridge as IBKREventBridge
    participant Bus as EventBusManager

    Broker->>AccountSvc: getAccount()
    AccountSvc->>Exchange: fetchAccount().join()
    Exchange-->>AccountSvc: Account
    AccountSvc-->>Broker: Account
    Broker->>Bridge: onAccount(account)
    Bridge->>Bus: publish(ACCOUNT_UPDATE)
```

## Order Execution Flow

```mermaid
sequenceDiagram
    participant UI as Trading UI
    participant Broker as IBKRBroker
    participant OrderSvc as IBKROrderService
    participant Exchange as IbkrExchange
    participant Bridge as IBKREventBridge

    UI->>Broker: placeOrder(order)
    Broker->>OrderSvc: placeOrder(order)
    OrderSvc->>Exchange: createMarketOrder/createLimitOrder/createStopOrder
    Exchange-->>OrderSvc: orderId
    OrderSvc-->>Broker: orderId
    Broker->>Bridge: onOrders(getOrders())
```

## Market Data Flow

```mermaid
sequenceDiagram
    participant UI as Trading UI
    participant Broker as IBKRBroker
    participant MDSvc as IBKRMarketDataService
    participant Exchange as IbkrExchange
    participant Bridge as IBKREventBridge

    UI->>Broker: subscribeMarketData(pair)
    Broker->>MDSvc: subscribe(pair)
    loop Every 1 second
        MDSvc->>Exchange: fetchTicker(pair)
        Exchange-->>MDSvc: Ticker
        MDSvc->>Bridge: onTicker(ticker)
    end
```
