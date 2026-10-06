# Security guidance

Updated: 2026-10-05. InvestPro is in active development; there is no established
security-supported release matrix or promised vulnerability-response SLA here.

Do not commit API keys, private keys, bot tokens, account exports or credential
files. Use the exchange credential dialog or recognized configuration sources.
Coinbase requires a CDP key name and a matching PEM private key. Credential
normalization repairs supported input formats; it cannot recover missing key data.

Telegram remote commands require configured allowed user IDs and private chats.
All allowed users share the desktop account. Trading actions require short-lived,
one-use confirmations, and PAPER routes locally. AI text is not executed as commands.
Configure OpenAI keys in the desktop environment, not through Telegram messages.

The optional local AI service uses plaintext, unauthenticated gRPC. Keep it on
loopback or a trusted private network. Root Compose publishes ports and contains
development database/VNC passwords; change deployment access and credentials
before exposing services beyond your development machine.

Report suspected vulnerabilities to a repository maintainer through an available
private contact or repository security reporting channel. Do not include secrets
or exploitable account details in public issues. If private reporting is unavailable,
request a private contact without posting the exploit or credentials.

Static analysis is currently skipped on JDK 27 because of the configured SpotBugs
parser. A passing build is not a vulnerability audit. See
[release readiness](PRODUCTION_READY.md) and [remote desk](docs/telegram-remote-desk.md).
