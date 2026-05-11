package org.investpro.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppConfigKeys {

    // ============================================================
    // Application
    // ============================================================

    public static final String APP_NAME = "APP_NAME";
    public static final String APP_ENV = "APP_ENV";
    public static final String APP_DEBUG = "APP_DEBUG";
    public static final String APP_TIMEZONE = "APP_TIMEZONE";

    // ============================================================
    // Local SQLite
    // ============================================================

    public static final String SQLITE_DB_FILE = "SQLITE_DB_FILE";

    // ============================================================
    // MySQL Database
    // ============================================================

    public static final String DB_DIALECT = "DB_DIALECT";
    public static final String DB_HOST = "DB_HOST";
    public static final String DB_PORT = "DB_PORT";
    public static final String DB_NAME = "DB_NAME";
    public static final String DB_USERNAME = "DB_USERNAME";
    public static final String DB_PASSWORD = "DB_PASSWORD";
    public static final String DB_CHARSET = "DB_CHARSET";

    // ============================================================
    // Exchange / Broker Defaults
    // ============================================================

    public static final String DEFAULT_EXCHANGE = "DEFAULT_EXCHANGE";
    public static final String DEFAULT_MARKET_TYPE = "DEFAULT_MARKET_TYPE";
    public static final String DEFAULT_ACCOUNT_MODE = "DEFAULT_ACCOUNT_MODE";
    public static final String DEFAULT_TRADE_PAIR = "DEFAULT_TRADE_PAIR";

    // ============================================================
    // OANDA
    // ============================================================

    public static final String OANDA_ENABLED = "OANDA_ENABLED";
    public static final String OANDA_ENVIRONMENT = "OANDA_ENVIRONMENT";
    public static final String OANDA_BASE_URL = "OANDA_BASE_URL";
    public static final String OANDA_STREAM_URL = "OANDA_STREAM_URL";
    public static final String OANDA_API_KEY = "OANDA_API_KEY";
    public static final String OANDA_ACCOUNT_ID = "OANDA_ACCOUNT_ID";
    public static final String OANDA_EMAIL_NOTIFICATION = "OANDA_EMAIL_NOTIFICATION";
    public static final String OANDA_DEFAULT_LEVERAGE = "OANDA_DEFAULT_LEVERAGE";
    public static final String OANDA_DEFAULT_UNITS = "OANDA_DEFAULT_UNITS";
    public static final String OANDA_RATE_LIMIT_PER_SECOND = "OANDA_RATE_LIMIT_PER_SECOND";

    // ============================================================
    // Coinbase
    // ============================================================

    public static final String COINBASE_ENABLED = "COINBASE_ENABLED";
    public static final String COINBASE_ENVIRONMENT = "COINBASE_ENVIRONMENT";
    public static final String COINBASE_BASE_URL = "COINBASE_BASE_URL";
    public static final String COINBASE_ADVANCED_TRADE_URL = "COINBASE_ADVANCED_TRADE_URL";
    public static final String COINBASE_DERIVATIVES_URL = "COINBASE_DERIVATIVES_URL";
    public static final String COINBASE_API_KEY = "COINBASE_API_KEY";
    public static final String COINBASE_API_SECRET = "COINBASE_API_SECRET";
    public static final String COINBASE_API_PASSPHRASE = "COINBASE_API_PASSPHRASE";
    public static final String COINBASE_ORGANIZATION_ID = "COINBASE_ORGANIZATION_ID";
    public static final String COINBASE_KEY_NAME = "COINBASE_KEY_NAME";
    public static final String COINBASE_PRIVATE_KEY = "COINBASE_PRIVATE_KEY";
    public static final String COINBASE_PRIVATE_KEY_FILE = "COINBASE_PRIVATE_KEY_FILE";
    public static final String COINBASE_DEFAULT_MARKET_TYPE = "COINBASE_DEFAULT_MARKET_TYPE";

    // ============================================================
    // Binance US
    // ============================================================

    public static final String BINANCEUS_ENABLED = "BINANCEUS_ENABLED";
    public static final String BINANCEUS_ENVIRONMENT = "BINANCEUS_ENVIRONMENT";
    public static final String BINANCEUS_BASE_URL = "BINANCEUS_BASE_URL";
    public static final String BINANCEUS_STREAM_URL = "BINANCEUS_STREAM_URL";
    public static final String BINANCEUS_API_KEY = "BINANCEUS_API_KEY";
    public static final String BINANCEUS_API_SECRET = "BINANCEUS_API_SECRET";
    public static final String BINANCEUS_DEFAULT_MARKET_TYPE = "BINANCEUS_DEFAULT_MARKET_TYPE";
    public static final String BINANCEUS_USE_IPV4_ONLY = "BINANCEUS_USE_IPV4_ONLY";

    // ============================================================
    // Binance Global
    // ============================================================

    public static final String BINANCE_ENABLED = "BINANCE_ENABLED";
    public static final String BINANCE_ENVIRONMENT = "BINANCE_ENVIRONMENT";
    public static final String BINANCE_BASE_URL = "BINANCE_BASE_URL";
    public static final String BINANCE_FUTURES_BASE_URL = "BINANCE_FUTURES_BASE_URL";
    public static final String BINANCE_STREAM_URL = "BINANCE_STREAM_URL";
    public static final String BINANCE_API_KEY = "BINANCE_API_KEY";
    public static final String BINANCE_API_SECRET = "BINANCE_API_SECRET";
    public static final String BINANCE_DEFAULT_MARKET_TYPE = "BINANCE_DEFAULT_MARKET_TYPE";
    public static final String BINANCE_USE_IPV4_ONLY = "BINANCE_USE_IPV4_ONLY";

    // ============================================================
    // Alpaca
    // ============================================================

    public static final String ALPACA_ENABLED = "ALPACA_ENABLED";
    public static final String ALPACA_ENVIRONMENT = "ALPACA_ENVIRONMENT";
    public static final String ALPACA_BASE_URL = "ALPACA_BASE_URL";
    public static final String ALPACA_DATA_URL = "ALPACA_DATA_URL";
    public static final String ALPACA_API_KEY = "ALPACA_API_KEY";
    public static final String ALPACA_API_SECRET = "ALPACA_API_SECRET";
    public static final String ALPACA_DEFAULT_MARKET_TYPE = "ALPACA_DEFAULT_MARKET_TYPE";
    public static final String ALPACA_DEFAULT_TIME_IN_FORCE = "ALPACA_DEFAULT_TIME_IN_FORCE";

    // ============================================================
    // Interactive Brokers
    // ============================================================

    public static final String IBKR_ENABLED = "IBKR_ENABLED";
    public static final String IBKR_ENVIRONMENT = "IBKR_ENVIRONMENT";
    public static final String IBKR_HOST = "IBKR_HOST";
    public static final String IBKR_PORT = "IBKR_PORT";
    public static final String IBKR_CLIENT_ID = "IBKR_CLIENT_ID";
    public static final String IBKR_ACCOUNT_ID = "IBKR_ACCOUNT_ID";
    public static final String IBKR_DEFAULT_MARKET_TYPE = "IBKR_DEFAULT_MARKET_TYPE";

    // ============================================================
    // Schwab
    // ============================================================

    public static final String SCHWAB_ENABLED = "SCHWAB_ENABLED";
    public static final String SCHWAB_ENVIRONMENT = "SCHWAB_ENVIRONMENT";
    public static final String SCHWAB_BASE_URL = "SCHWAB_BASE_URL";
    public static final String SCHWAB_CLIENT_ID = "SCHWAB_CLIENT_ID";
    public static final String SCHWAB_CLIENT_SECRET = "SCHWAB_CLIENT_SECRET";
    public static final String SCHWAB_REDIRECT_URI = "SCHWAB_REDIRECT_URI";
    public static final String SCHWAB_REFRESH_TOKEN = "SCHWAB_REFRESH_TOKEN";
    public static final String SCHWAB_ACCOUNT_ID = "SCHWAB_ACCOUNT_ID";
    public static final String SCHWAB_DEFAULT_MARKET_TYPE = "SCHWAB_DEFAULT_MARKET_TYPE";

    // ============================================================
    // Kraken
    // ============================================================

    public static final String KRAKEN_ENABLED = "KRAKEN_ENABLED";
    public static final String KRAKEN_ENVIRONMENT = "KRAKEN_ENVIRONMENT";
    public static final String KRAKEN_BASE_URL = "KRAKEN_BASE_URL";
    public static final String KRAKEN_FUTURES_BASE_URL = "KRAKEN_FUTURES_BASE_URL";
    public static final String KRAKEN_API_KEY = "KRAKEN_API_KEY";
    public static final String KRAKEN_API_SECRET = "KRAKEN_API_SECRET";
    public static final String KRAKEN_DEFAULT_MARKET_TYPE = "KRAKEN_DEFAULT_MARKET_TYPE";

    // ============================================================
    // Bybit
    // ============================================================

    public static final String BYBIT_ENABLED = "BYBIT_ENABLED";
    public static final String BYBIT_ENVIRONMENT = "BYBIT_ENVIRONMENT";
    public static final String BYBIT_BASE_URL = "BYBIT_BASE_URL";
    public static final String BYBIT_API_KEY = "BYBIT_API_KEY";
    public static final String BYBIT_API_SECRET = "BYBIT_API_SECRET";
    public static final String BYBIT_DEFAULT_MARKET_TYPE = "BYBIT_DEFAULT_MARKET_TYPE";

    // ============================================================
    // OKX
    // ============================================================

    public static final String OKX_ENABLED = "OKX_ENABLED";
    public static final String OKX_ENVIRONMENT = "OKX_ENVIRONMENT";
    public static final String OKX_BASE_URL = "OKX_BASE_URL";
    public static final String OKX_API_KEY = "OKX_API_KEY";
    public static final String OKX_API_SECRET = "OKX_API_SECRET";
    public static final String OKX_API_PASSPHRASE = "OKX_API_PASSPHRASE";
    public static final String OKX_DEFAULT_MARKET_TYPE = "OKX_DEFAULT_MARKET_TYPE";

    // ============================================================
    // KuCoin
    // ============================================================

    public static final String KUCOIN_ENABLED = "KUCOIN_ENABLED";
    public static final String KUCOIN_ENVIRONMENT = "KUCOIN_ENVIRONMENT";
    public static final String KUCOIN_BASE_URL = "KUCOIN_BASE_URL";
    public static final String KUCOIN_FUTURES_BASE_URL = "KUCOIN_FUTURES_BASE_URL";
    public static final String KUCOIN_API_KEY = "KUCOIN_API_KEY";
    public static final String KUCOIN_API_SECRET = "KUCOIN_API_SECRET";
    public static final String KUCOIN_API_PASSPHRASE = "KUCOIN_API_PASSPHRASE";
    public static final String KUCOIN_DEFAULT_MARKET_TYPE = "KUCOIN_DEFAULT_MARKET_TYPE";

    // ============================================================
    // Stellar
    // ============================================================

    public static final String STELLAR_ENABLED = "STELLAR_ENABLED";
    public static final String STELLAR_NETWORK = "STELLAR_NETWORK";
    public static final String STELLAR_HORIZON_URL = "STELLAR_HORIZON_URL";
    public static final String STELLAR_PUBLIC_KEY = "STELLAR_PUBLIC_KEY";
    public static final String STELLAR_SECRET_KEY = "STELLAR_SECRET_KEY";
    public static final String STELLAR_DEFAULT_MARKET_TYPE = "STELLAR_DEFAULT_MARKET_TYPE";

    // ============================================================
    // MetaTrader Bridge
    // ============================================================

    public static final String METATRADER_ENABLED = "METATRADER_ENABLED";
    public static final String METATRADER_VERSION = "METATRADER_VERSION";
    public static final String METATRADER_BRIDGE_MODE = "METATRADER_BRIDGE_MODE";
    public static final String METATRADER_WS_HOST = "METATRADER_WS_HOST";
    public static final String METATRADER_WS_PORT = "METATRADER_WS_PORT";
    public static final String METATRADER_PIPE_NAME = "METATRADER_PIPE_NAME";
    public static final String METATRADER_DEFAULT_MARKET_TYPE = "METATRADER_DEFAULT_MARKET_TYPE";

    // ============================================================
    // AI / OpenAI
    // ============================================================

    public static final String OPENAI_ENABLED = "OPENAI_ENABLED";
    public static final String OPENAI_API_KEY = "OPENAI_API_KEY";
    public static final String OPENAI_MODEL = "OPENAI_MODEL";
    public static final String OPENAI_REASONING_ENABLED = "OPENAI_REASONING_ENABLED";

    // ============================================================
    // Local Python AI Engine
    // ============================================================

    public static final String PYTHON_AI_ENABLED = "PYTHON_AI_ENABLED";
    public static final String PYTHON_AI_HOST = "PYTHON_AI_HOST";
    public static final String PYTHON_AI_PORT = "PYTHON_AI_PORT";
    public static final String PYTHON_AI_BASE_URL = "PYTHON_AI_BASE_URL";
    public static final String PYTHON_AI_START_AUTOMATICALLY = "PYTHON_AI_START_AUTOMATICALLY";
    public static final String PYTHON_AI_COMMAND = "PYTHON_AI_COMMAND";

    // ============================================================
    // Notifications
    // ============================================================

    public static final String TELEGRAM_ENABLED = "TELEGRAM_ENABLED";
    public static final String TELEGRAM_TOKEN = "TELEGRAM_TOKEN";
    public static final String TELEGRAM_CHAT_ID = "TELEGRAM_CHAT_ID";

    public static final String EMAIL_NOTIFICATIONS_ENABLED = "EMAIL_NOTIFICATIONS_ENABLED";
    public static final String EMAIL_SMTP_HOST = "EMAIL_SMTP_HOST";
    public static final String EMAIL_SMTP_PORT = "EMAIL_SMTP_PORT";
    public static final String EMAIL_SMTP_USERNAME = "EMAIL_SMTP_USERNAME";
    public static final String EMAIL_SMTP_PASSWORD = "EMAIL_SMTP_PASSWORD";
    public static final String EMAIL_FROM = "EMAIL_FROM";
    public static final String EMAIL_TO = "EMAIL_TO";

    // ============================================================
    // Risk Defaults
    // ============================================================

    public static final String AUTO_TRADING_ENABLED = "AUTO_TRADING_ENABLED";
    public static final String AI_REASONING_ENABLED = "AI_REASONING_ENABLED";
    public static final String MAX_RISK_PER_TRADE = "MAX_RISK_PER_TRADE";
    public static final String MAX_DAILY_LOSS = "MAX_DAILY_LOSS";
    public static final String MAX_OPEN_POSITIONS = "MAX_OPEN_POSITIONS";
    public static final String MAX_SYMBOL_EXPOSURE = "MAX_SYMBOL_EXPOSURE";
    public static final String MAX_PORTFOLIO_EXPOSURE = "MAX_PORTFOLIO_EXPOSURE";
    public static final String DEFAULT_SIGNAL_AMOUNT = "DEFAULT_SIGNAL_AMOUNT";
    public static final String DEFAULT_MIN_CONFIDENCE = "DEFAULT_MIN_CONFIDENCE";

    // ============================================================
    // Strategy / Backtest / Paper Trading
    // ============================================================

    public static final String STRATEGY_MAX_DEFINITIONS = "STRATEGY_MAX_DEFINITIONS";
    public static final String STRATEGY_DEFAULT = "STRATEGY_DEFAULT";

    public static final String BACKTEST_ENABLED = "BACKTEST_ENABLED";
    public static final String BACKTEST_INITIAL_BALANCE = "BACKTEST_INITIAL_BALANCE";
    public static final String BACKTEST_MIN_CANDLES = "BACKTEST_MIN_CANDLES";
    public static final String BACKTEST_REQUIRED_CANDLES = "BACKTEST_REQUIRED_CANDLES";

    public static final String PAPER_TRADING_ENABLED = "PAPER_TRADING_ENABLED";
    public static final String PAPER_TRADING_INITIAL_BALANCE = "PAPER_TRADING_INITIAL_BALANCE";
    public static final String PAPER_TRADING_LEARNING_ENABLED = "PAPER_TRADING_LEARNING_ENABLED";

    // ============================================================
    // User / Login Settings
    // ============================================================

    public static final String LOGIN_USERNAME = "LOGIN_USERNAME";
    public static final String LOGIN_PASSWORD = "LOGIN_PASSWORD";

    // ============================================================
    // Technical Indicators Configuration
    // ============================================================

    public static final String INDICATOR_SMA_SHORT = "INDICATOR_SMA_SHORT";
    public static final String INDICATOR_SMA_MID = "INDICATOR_SMA_MID";
    public static final String INDICATOR_SMA_LONG = "INDICATOR_SMA_LONG";
    public static final String INDICATOR_RSI_PERIOD = "INDICATOR_RSI_PERIOD";
    public static final String INDICATOR_STOCHASTIC_PERIOD = "INDICATOR_STOCHASTIC_PERIOD";
    public static final String INDICATOR_STOCHASTIC_K_PERIOD = "INDICATOR_STOCHASTIC_K_PERIOD";
    public static final String INDICATOR_STOCHASTIC_D_PERIOD = "INDICATOR_STOCHASTIC_D_PERIOD";
    public static final String INDICATOR_BB_PERIOD = "INDICATOR_BB_PERIOD";
    public static final String INDICATOR_BB_STD_DEV = "INDICATOR_BB_STD_DEV";
    public static final String INDICATOR_ATR_PERIOD = "INDICATOR_ATR_PERIOD";
    public static final String INDICATOR_VOLATILITY_PERIOD = "INDICATOR_VOLATILITY_PERIOD";
    public static final String INDICATOR_VOLUME_PERIOD = "INDICATOR_VOLUME_PERIOD";

    // ============================================================
    // UI Configuration
    // ============================================================

    public static final String UI_INDICATORS_PANEL_WIDTH = "UI_INDICATORS_PANEL_WIDTH";

    private AppConfigKeys() {
    }
}