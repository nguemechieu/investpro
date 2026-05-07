# Telegram Bot Integration Guide

## Overview

InvestPro now supports interactive Telegram bot integration, allowing users to:
- Execute trading commands (/buy, /sell, /cancel)
- Query account information (/balance, /positions, /orders, /pnl)
- Monitor bot status and strategy performance
- Ask questions and receive AI-powered responses
- Receive trade notifications

## Setup

### 1. Create a Telegram Bot

1. Open Telegram and search for **@BotFather**
2. Send `/newbot` command
3. Choose a name for your bot (e.g., "InvestPro Trading Bot")
4. BotFather will give you a **Bot Token** (save this)
5. Note the bot username you chose

### 2. Configure InvestPro

Add the following to your `conf.properties` or `.env` file:

```properties
telegram_token=YOUR_BOT_TOKEN_HERE
```

Or set environment variable:
```bash
export TELEGRAM_TOKEN=YOUR_BOT_TOKEN_HERE
```

### 3. Start the Bot

When you start InvestPro and the Telegram token is configured:
- Telegram message polling automatically starts
- The bot begins listening for commands and questions

## Available Commands

### Bot Control Commands

| Command | Description |
|---------|-------------|
| `/start` | Initialize the bot |
| `/help` | Show all available commands |
| `/status` | Show bot and market status |
| `/toggleauto` | Toggle auto trading on/off |

### Account & Balance Commands

| Command | Description |
|---------|-------------|
| `/balance` | Show account balance |
| `/positions` | List all open positions |
| `/orders` | Show active orders |
| `/pnl` | Show profit/loss |
| `/risk` | Show risk management metrics |

### Trading Commands

| Command | Example | Description |
|---------|---------|-------------|
| `/buy SYMBOL AMOUNT [PRICE]` | `/buy BTC 0.5` | Market buy order |
| | `/buy BTC 0.5 45000` | Limit buy order at $45,000 |
| `/sell SYMBOL AMOUNT [PRICE]` | `/sell BTC 0.5` | Market sell order |
| | `/sell ETH 2 2500` | Limit sell order at $2,500 |
| `/cancel ORDERID` | `/cancel abc123def` | Cancel order by ID |

### Information Commands

| Command | Example | Description |
|---------|---------|-------------|
| `/market SYMBOL` | `/market BTC` | Get market info for symbol |
| `/strategy` | - | Show active strategy and stats |

## Query Examples

Beyond commands, you can ask questions and the bot will respond with AI-powered answers:

### Market Analysis
- "What's the current Bitcoin trend?"
- "Analyze the EURUSD pair"
- "Is now a good time to buy?"

### Risk Management
- "What's my current risk exposure?"
- "Calculate optimal position size for 100 pips"
- "What's my maximum drawdown?"

### Trading Strategy
- "What's the win rate of my strategy?"
- "How profitable was last month?"
- "What's my Sharpe ratio?"

### General Questions
- "What cryptocurrencies are trending?"
- "News about Ethereum?"
- "Economic calendar for today?"

## Command Usage Examples

### Simple Buy Order
```
User: /buy BTC 0.1
Bot: ✅ Buy Order Submitted
Symbol: BTC
Amount: 0.1
Type: MARKET
```

### Limit Sell Order
```
User: /sell ETH 1 2500
Bot: ✅ Sell Order Submitted
Symbol: ETH
Amount: 1.0
Type: LIMIT @ $2500.00
```

### Check Account
```
User: /balance
Bot: 💰 Account Balance
Currency: USD
Balance: $15,234.50
```

### Monitor Strategy
```
User: /strategy
Bot: 📈 Active Strategy
Strategy: Mean Reversion
Win Rate: 61.3%
Total Return: +189.5%
```

## Features

### Real-Time Notifications
The bot automatically sends notifications for:
- ✅ Successful trade executions
- 🔴 Trade cancellations
- 📈 Significant price movements
- ⚠️ Risk alerts
- 📊 Daily performance summaries

### Multi-User Support
- Each Telegram user has their own context
- Commands are user-specific and secure
- User chat history is tracked separately

### ChatGPT Integration (Optional)
For intelligent responses to trading questions:

1. Get OpenAI API key from https://platform.openai.com/api-keys
2. Add to configuration:
   ```properties
   openai_api_key=YOUR_OPENAI_KEY
   ```

The bot will then:
- Provide market analysis
- Give trading advice
- Answer general finance questions
- Explain concepts and strategies

### Smart Command Parsing
- Case-insensitive commands
- Flexible amount and price formats
- Validation and error handling
- Helpful error messages

## Architecture

### Components

1. **TelegramNotifier** - Handles all Telegram API communication
   - Sends messages and files
   - Detects chat IDs automatically
   - Manages user contexts

2. **TelegramCommandHandler** - Processes trading commands
   - Parses command syntax
   - Validates parameters
   - Executes trade operations
   - Returns formatted responses

3. **Message Polling** - Background thread for message retrieval
   - Polls Telegram API every 2 seconds
   - Non-blocking asynchronous processing
   - Automatic error recovery

4. **SystemCore Integration** - Central bot coordination
   - Manages command handler
   - Controls polling lifecycle
   - Provides trading operations

## API Reference

### Starting/Stopping Polling

```java
// Start listening for messages (automatic on bot start)
systemCore.startTelegramPolling();

// Stop listening for messages
systemCore.stopTelegramPolling();

// Check polling status
boolean active = systemCore.isTelegramPollingActive();
```

### Sending Messages

```java
// Send simple message
telegramNotifier.sendMessage("Price alert: BTC reached $50,000");

// Send formatted message (Markdown)
telegramNotifier.sendMarkdown("*Bold text* and _italic_");

// Send to specific chat
telegramNotifier.sendMessageToChat(chatId, "Direct message");

// Send file
telegramNotifier.sendPhoto(Path.of("chart.png"), "Bitcoin Chart");
```

### Command Handling

```java
// Handle a command
String response = commandHandler.handleCommand("/balance", chatId);

// Set up command handler
systemCore.setCommandHandler(handler);
```

## Troubleshooting

### Bot Not Responding

1. Check bot token is correct in configuration
2. Verify you've added the bot to Telegram
3. Send `/start` to initialize
4. Check SystemCore is running with polling enabled

### Message Polling Issues

1. Check internet connection
2. Verify Telegram API is accessible
3. Check bot token hasn't expired
4. Look at logs for specific errors

### Order Execution Fails

1. Check account has sufficient balance
2. Verify symbol is tradable on exchange
3. Check amount format (decimal numbers)
4. Ensure exchange is connected

### No Response from ChatGPT

1. Verify OpenAI API key is set
2. Check OpenAI account has credits
3. Look for rate limiting (wait a minute)
4. Check API key permissions

## Security Considerations

### Best Practices

1. **Token Security**
   - Never share your bot token
   - Treat it like a password
   - Regenerate if compromised

2. **Chat ID Privacy**
   - Bot auto-detects chat IDs from messages
   - Explicitly set if you prefer specific chat

3. **API Key Management**
   - Use environment variables for keys
   - Don't commit keys to version control
   - Rotate keys regularly

4. **Command Validation**
   - Bot validates all command parameters
   - Rejects invalid amounts or prices
   - Shows helpful error messages

## Performance

- **Polling Interval**: 2 seconds (configurable)
- **Message Processing**: Asynchronous, non-blocking
- **Command Response Time**: < 1 second typically
- **API Call Timeout**: 30 seconds

## Examples

### Full Trading Session via Telegram

```
User: /start
Bot: 👋 InvestPro Trading Bot Started
Connected Exchange: Coinbase Pro
Status: 🟢 Online

User: /balance
Bot: 💰 Account Balance
Currency: USD
Balance: $10,000.00

User: /market BTC
Bot: 📊 Market Info - BTC
Bid: $45,234.50
Ask: $45,234.75
Spread: $0.25 (0.00055%)

User: /buy BTC 0.1
Bot: ✅ Buy Order Submitted
Symbol: BTC
Amount: 0.1
Type: MARKET

User: /positions
Bot: 📊 Open Positions
• Position 1: BTC +0.1 @ $45,234.50

User: /pnl
Bot: 📈 Profit
Total P&L: $234.50
Return: 2.35%

User: What's the next economic event?
Bot: 👤 User: The next major economic event is the Federal Reserve's interest rate decision on June 18th. This typically impacts currency pairs and broader market sentiment.
```

## Contributing & Support

For issues or feature requests related to Telegram integration:
1. Check logs for error messages
2. Verify configuration is correct
3. Test with simple commands first
4. Report issues with detailed context

## Future Enhancements

Planned features:
- [ ] Webhook instead of polling for faster response
- [ ] Advanced portfolio analysis commands
- [ ] Price alerts and custom notifications
- [ ] Advanced trading strategies via commands
- [ ] Performance analytics and reporting
- [ ] Multi-exchange support
- [ ] Custom command creation
