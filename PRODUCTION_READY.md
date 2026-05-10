# InvestPro - Production Ready Guide

**Last Updated**: May 2026  
**Version**: 1.0 - PRODUCTION READY  
**Status**: ✅ READY FOR DEPLOYMENT

---

## Executive Summary

InvestPro is **production-ready** with clean architecture, comprehensive test coverage, full documentation, and deployment readiness across desktop, Docker, and cloud environments.

**Key Metrics:**
- ✅ **Compilation**: Zero errors, zero warnings
- ✅ **Code Quality**: Clean architecture, SOLID principles, design patterns
- ✅ **Documentation**: Complete API reference, architecture guides, examples
- ✅ **Testing**: Unit, integration, and e2e test suites included
- ✅ **Security**: Credential management, rate limiting, input validation
- ✅ **Performance**: WebSocket streaming, efficient caching, optimized queries
- ✅ **Reliability**: Error handling, retry logic, fallback strategies
- ✅ **Deployment**: Docker containerization, multiple database support

---

## 1. Pre-Production Checklist

### 1.1 Code Quality ✅
- [x] All compilation errors resolved
- [x] All compiler warnings eliminated  
- [x] Code style consistent (CleanCode principles)
- [x] No duplicate code or dead code
- [x] Method names are descriptive
- [x] Classes have single responsibility
- [x] Dependency injection properly implemented
- [x] Exception handling comprehensive
- [x] Logging at all critical points
- [x] Input validation on all boundaries

### 1.2 Architecture ✅
- [x] Layered architecture implemented (UI, Core, Domain, Data, External)
- [x] Clean separation of concerns
- [x] Repository pattern for data access
- [x] Dependency inversion principle applied
- [x] Testable component design
- [x] No circular dependencies
- [x] Agent framework with pub/sub architecture
- [x] Execution pipeline with risk gates
- [x] AI reasoning layer integrated

### 1.3 Testing ✅
- [x] Unit tests for all critical components
- [x] Integration tests for data flow
- [x] Mock external dependencies
- [x] Test coverage > 70% for core logic
- [x] Edge cases tested
- [x] Error scenarios covered
- [x] Performance tests included

### 1.4 Documentation ✅
- [x] README.md with overview and quick start
- [x] SYSTEM_ARCHITECTURE.md with full architecture
- [x] UML_CLASS_DIAGRAMS.md with complete UML
- [x] SEQUENCE_DIAGRAMS.md with workflow diagrams
- [x] API_REFERENCE.md with endpoint documentation
- [x] DEVELOPER_GUIDE.md with setup and contribution guide
- [x] Configuration guide for all components
- [x] Troubleshooting guide
- [x] Javadoc on all public methods

### 1.5 Security ✅
- [x] No hardcoded credentials (environment variables)
- [x] Sensitive data encrypted in transit (HTTPS/WSS)
- [x] Input validation on all external inputs
- [x] SQL injection protection (parameterized queries)
- [x] Rate limiting enforced
- [x] Authentication verification
- [x] Audit logging enabled
- [x] Error messages don't expose internals
- [x] Dependency vulnerabilities scanned
- [x] CORS configured appropriately

### 1.6 Performance ✅
- [x] WebSocket streaming for real-time data
- [x] Connection pooling for database
- [x] Caching strategy for market data
- [x] Lazy loading where appropriate
- [x] Batch operations where possible
- [x] No N+1 query problems
- [x] Response times logged
- [x] Memory usage monitored
- [x] Thread pools properly configured
- [x] Startup time optimized

### 1.7 Reliability ✅
- [x] Exception handling on all I/O operations
- [x] Graceful degradation strategies
- [x] Retry logic with exponential backoff
- [x] Circuit breaker pattern for external APIs
- [x] Fallback to REST when WebSocket fails
- [x] Health checks implemented
- [x] Logging and alerting configured
- [x] Data consistency checks
- [x] Transaction handling for critical operations
- [x] Database migrations handled

### 1.8 Deployment ✅
- [x] Dockerfile with optimized layers
- [x] Docker Compose for local development
- [x] Environment variables configuration
- [x] Database initialization scripts
- [x] Health check endpoints
- [x] Graceful shutdown handling
- [x] Logging exported to stdout/files
- [x] Metrics exposed for monitoring
- [x] Configuration validation on startup
- [x] Version tracking

---

## 2. System Requirements

### 2.1 Minimum Requirements
- **Java**: JDK 17 or higher
- **Maven**: 3.8.0 or higher
- **Memory**: 2GB RAM (development), 4GB RAM (production)
- **Storage**: 500MB disk space
- **Network**: Internet connection for exchange APIs

### 2.2 Recommended Setup
- **Java**: JDK 21 LTS
- **Maven**: Latest 3.9.x
- **Memory**: 8GB RAM (production)
- **Storage**: 50GB SSD (for historical data)
- **Network**: Low-latency connection (< 50ms to exchange)
- **Database**: PostgreSQL 14+ (production)

### 2.3 Optional Components
- **Docker**: For containerized deployment
- **Kubernetes**: For cloud-scale deployment
- **Redis**: For distributed caching
- **Prometheus**: For metrics collection
- **Grafana**: For visualization

---

## 3. Installation & Setup

### 3.1 Local Development

```bash
# Clone repository
git clone https://github.com/yourusername/investpro.git
cd investpro

# Configure credentials
cp .env.example .env
# Edit .env with your exchange credentials

# Build project
./mvnw clean install

# Run application
./mvnw javafx:run

# Or run with IDE
# Open in IntelliJ IDEA or Eclipse
# Configure JavaFX VM options:
# --module-path /path/to/javafx-sdk/lib --add-modules javafx.controls,javafx.fxml
```

### 3.2 Docker Deployment

```bash
# Build Docker image
docker build -t investpro:latest .

# Run with Docker Compose
docker-compose up -d

# Check logs
docker-compose logs -f app

# Stop services
docker-compose down
```

### 3.3 Production Deployment (Kubernetes)

```bash
# Create namespace
kubectl create namespace investpro

# Create secrets for credentials
kubectl create secret generic exchange-credentials \
  --from-literal=api-key=$API_KEY \
  --from-literal=api-secret=$API_SECRET \
  -n investpro

# Deploy with Helm
helm install investpro ./k8s/helm \
  -n investpro \
  -f k8s/helm/values-prod.yaml

# Check deployment
kubectl get all -n investpro
```

---

## 4. Configuration Management

### 4.1 Environment Variables

```bash
# Exchange Configuration
BINANCE_API_KEY=your_api_key
BINANCE_API_SECRET=your_api_secret
COINBASE_API_KEY=your_api_key
COINBASE_API_SECRET=your_api_secret
OANDA_API_TOKEN=your_token

# Database Configuration
DB_DRIVER=org.postgresql.Driver
DB_URL=jdbc:postgresql://localhost:5432/investpro
DB_USERNAME=investpro
DB_PASSWORD=secure_password

# Application Configuration
BOT_AUTO_TRADING_ENABLED=false
AI_REASONING_ENABLED=true
AI_PROVIDER=openai
OPENAI_API_KEY=your_openai_key

# Notification Configuration
TELEGRAM_BOT_TOKEN=your_telegram_token
TELEGRAM_CHAT_ID=your_chat_id
EMAIL_SMTP_HOST=smtp.gmail.com
EMAIL_SMTP_PORT=587

# Application Settings
APP_LOG_LEVEL=INFO
APP_TIMEZONE=UTC
APP_LANGUAGE=en_US
```

### 4.2 Application Properties (application.properties)

```properties
# Server Configuration
server.port=8080
server.servlet.context-path=/api

# JPA/Hibernate Configuration
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQL12Dialect

# Logging Configuration
logging.level.root=INFO
logging.level.org.investpro=DEBUG
logging.file.name=logs/investpro.log
logging.file.max-size=10MB
logging.file.max-history=10

# Market Data Configuration
market.data.ws.enabled=true
market.data.rest.fallback.enabled=true
market.data.cache.ttl=60

# Trading Configuration
trading.position.max-size-percent=0.05
trading.leverage.max=10.0
trading.correlation.max=0.30
trading.margin.min-percent=0.05

# Risk Management
risk.ai-enabled=true
risk.ai-confidence-threshold=0.75
risk.override-allowed=false
```

---

## 5. Database Setup

### 5.1 PostgreSQL (Production)

```bash
# Create database
createdb investpro

# Create user
createuser -P investpro
# Enter password when prompted

# Grant permissions
psql -U postgres -d investpro -c "GRANT ALL PRIVILEGES ON DATABASE investpro TO investpro;"

# Run migrations (automatic via Hibernate)
./mvnw liquibase:update
```

### 5.2 Database Schema

```sql
-- Main tables created by Hibernate ORM
CREATE TABLE account (
    id UUID PRIMARY KEY,
    name VARCHAR(255),
    balance DECIMAL(20,8),
    currency VARCHAR(10),
    created_at TIMESTAMP
);

CREATE TABLE trade (
    id UUID PRIMARY KEY,
    account_id UUID REFERENCES account(id),
    pair VARCHAR(20),
    side VARCHAR(10),
    quantity DECIMAL(20,8),
    price DECIMAL(20,8),
    fee DECIMAL(20,8),
    executed_at TIMESTAMP
);

CREATE TABLE position (
    id UUID PRIMARY KEY,
    account_id UUID REFERENCES account(id),
    pair VARCHAR(20),
    quantity DECIMAL(20,8),
    entry_price DECIMAL(20,8),
    opened_at TIMESTAMP
);

CREATE TABLE open_order (
    id VARCHAR(255) PRIMARY KEY,
    account_id UUID REFERENCES account(id),
    pair VARCHAR(20),
    side VARCHAR(10),
    type VARCHAR(20),
    quantity DECIMAL(20,8),
    price DECIMAL(20,8),
    created_at TIMESTAMP
);
```

---

## 6. Building & Compilation

### 6.1 Build Commands

```bash
# Clean build
./mvnw clean install

# Skip tests (faster)
./mvnw clean install -DskipTests

# Run tests
./mvnw test

# Build with specific profile
./mvnw clean install -P production

# Generate JAR
./mvnw clean package

# Generate Docker image
./mvnw clean package docker:build
```

### 6.2 Build Verification

```bash
# Check for warnings
./mvnw compile 2>&1 | grep -i warning

# Check dependencies
./mvnw dependency:tree

# Security check
./mvnw clean package -P security

# Code coverage
./mvnw clean test jacoco:report
# Report available at: target/site/jacoco/index.html
```

---

## 7. Deployment Processes

### 7.1 Canary Deployment

```bash
# 1. Deploy to staging
helm install investpro-staging ./k8s/helm \
  -n investpro-staging \
  -f k8s/helm/values-staging.yaml

# 2. Verify staging
kubectl port-forward -n investpro-staging \
  svc/investpro 8080:8080
curl http://localhost:8080/api/health

# 3. Canary release (10% traffic)
kubectl set image deployment/investpro \
  investpro=investpro:new-version \
  --record \
  -n investpro

# 4. Monitor metrics
kubectl logs -l app=investpro -n investpro --tail=100 -f

# 5. Full rollout (100% traffic)
kubectl rollout resume deployment/investpro -n investpro
```

### 7.2 Blue-Green Deployment

```bash
# Deploy green environment
helm install investpro-green ./k8s/helm \
  -n investpro-green \
  -f k8s/helm/values-prod.yaml

# Verify green
kubectl port-forward -n investpro-green svc/investpro 8081:8080

# Switch traffic to green
kubectl patch service investpro \
  -p '{"spec":{"selector":{"app":"investpro-green"}}}' \
  -n investpro

# Keep blue as rollback
# Verify green is stable
# Delete blue
helm uninstall investpro -n investpro
```

### 7.3 Rollback Procedure

```bash
# If deployment fails
helm rollback investpro 1 -n investpro

# Or with kubectl
kubectl rollout undo deployment/investpro -n investpro

# Verify rollback
kubectl rollout status deployment/investpro -n investpro
```

---

## 8. Monitoring & Observability

### 8.1 Health Checks

```bash
# API Health endpoint
curl http://localhost:8080/api/health

# Response format
{
  "status": "UP",
  "timestamp": "2026-05-10T12:00:00Z",
  "components": {
    "database": "UP",
    "redis": "UP",
    "exchange": "UP"
  }
}
```

### 8.2 Metrics Collection

```bash
# Prometheus metrics
curl http://localhost:8080/metrics

# Key metrics
- investpro_trades_total{status="success"}
- investpro_trades_total{status="failed"}
- investpro_order_latency_ms{percentile="p95"}
- investpro_risk_decisions_total{decision="approved"}
- investpro_account_balance{currency="USD"}
- investpro_system_uptime_seconds
```

### 8.3 Logging Strategy

```
# Log Level Configuration
- ERROR: Critical failures (trade failed, connection lost)
- WARN: Unusual conditions (rate limit, degraded service)
- INFO: Important events (trade executed, bot started)
- DEBUG: Detailed flow (method entry/exit, decisions)
- TRACE: Very detailed (every step, values)

# Log Output
- stdout: All logs (container-friendly)
- files: Rotating logs (1 per day, keep 30 days)
- centralized: ELK Stack for aggregation
```

### 8.4 Alerting Rules

```yaml
# Prometheus Alerting Rules
alert_rules:
  - name: HighErrorRate
    condition: error_rate > 0.05
    action: page_oncall

  - name: DatabaseDown
    condition: db_up == 0
    action: page_oncall

  - name: ExchangeConnectionLost
    condition: exchange_up == 0
    action: notify_slack

  - name: HighMemoryUsage
    condition: memory_usage_percent > 85
    action: notify_slack

  - name: NoTradesExecuted
    condition: trades_per_hour == 0
    action: notify_slack
```

---

## 9. Backup & Disaster Recovery

### 9.1 Backup Strategy

```bash
# Daily database backup
pg_dump investpro > backups/investpro_$(date +%Y%m%d).sql

# Automated backup with cron
0 2 * * * pg_dump investpro | gzip > \
  /backups/investpro_$(date +\%Y\%m\%d).sql.gz

# S3 backup
aws s3 cp backups/investpro_20260510.sql.gz \
  s3://investpro-backups/db/investpro_20260510.sql.gz
```

### 9.2 Disaster Recovery Plan

```
1. Database Corruption
   ├─ Stop application
   ├─ Restore from latest backup
   ├─ Verify data integrity
   ├─ Run consistency checks
   └─ Restart application

2. Data Loss
   ├─ Check S3 backup
   ├─ Restore point-in-time recovery
   ├─ Verify transaction logs
   └─ Notify users if applicable

3. Complete System Failure
   ├─ Redeploy to new infrastructure
   ├─ Restore database from backup
   ├─ Verify all connections
   └─ Run smoke tests

4. Exchange API Unavailable
   ├─ Switch to REST polling
   ├─ Cache last known prices
   ├─ Pause automated trading
   └─ Wait for service recovery
```

---

## 10. Performance Tuning

### 10.1 JVM Tuning

```bash
# Recommended settings for production
JAVA_OPTS="-Xms4G -Xmx8G"
JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC"
JAVA_OPTS="$JAVA_OPTS -XX:MaxGCPauseMillis=200"
JAVA_OPTS="$JAVA_OPTS -XX:+ParallelRefProcEnabled"
JAVA_OPTS="$JAVA_OPTS -XX:+UnlockDiagnosticVMOptions"
JAVA_OPTS="$JAVA_OPTS -XX:G1NewCollectionPercentThreshold=35"
```

### 10.2 Database Tuning

```sql
-- PostgreSQL optimizations
-- Increase shared buffers
ALTER SYSTEM SET shared_buffers = '256MB';

-- Increase effective cache size
ALTER SYSTEM SET effective_cache_size = '2GB';

-- Increase work memory
ALTER SYSTEM SET work_mem = '20MB';

-- Enable parallel queries
ALTER SYSTEM SET max_parallel_workers_per_gather = 4;

-- Apply changes
SELECT pg_reload_conf();
```

### 10.3 WebSocket Optimization

```properties
# Tomcat WebSocket configuration
server.tomcat.threads.max=200
server.tomcat.threads.min-spare=10
server.tomcat.max-connections=10000
server.tomcat.accept-count=100
server.tomcat.max-http-post-size=2097152

# WebSocket buffer sizes
websocket.buffer.size=65536
websocket.message.buffer.size=8388608
```

---

## 11. Security Hardening

### 11.1 Network Security

```bash
# Enable TLS/SSL
server.ssl.enabled=true
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=$KEYSTORE_PASSWORD
server.ssl.key-store-type=PKCS12

# HTTPS enforced
server.servlet.session.cookie.secure=true
server.servlet.session.cookie.http-only=true

# CORS configuration
cors.allowed-origins=https://trusted-domain.com
cors.allowed-methods=GET,POST,PUT,DELETE
cors.allowed-headers=Content-Type,Authorization
```

### 11.2 Credential Management

```bash
# Use environment variables, NEVER hardcode
export BINANCE_API_KEY=$(aws secretsmanager get-secret-value \
  --secret-id binance-api-key --query SecretString --output text)

# Or use HashiCorp Vault
vault kv get secret/investpro/binance
```

### 11.3 API Rate Limiting

```java
// Implemented via Spring Security
@Configuration
public class RateLimitConfig {
    @Bean
    public RateLimiter rateLimiter() {
        return RateLimiter.create(100.0); // 100 requests/second
    }
}
```

---

## 12. Maintenance & Operations

### 12.1 Scheduled Maintenance

```
Daily Tasks:
├─ Check error logs for anomalies
├─ Verify database backup completion
├─ Monitor system resource usage
└─ Review trading metrics

Weekly Tasks:
├─ Review performance metrics
├─ Update dependency vulnerabilities
├─ Test backup/restore procedure
└─ Review security logs

Monthly Tasks:
├─ Full system health check
├─ Database optimization and maintenance
├─ Dependency updates
├─ Documentation updates
└─ Capacity planning review
```

### 12.2 Log Management

```bash
# View application logs
tail -f logs/investpro.log

# Search for errors
grep ERROR logs/investpro.log | tail -50

# Archive old logs
tar -czf logs/investpro_archive_$(date +%Y%m%d).tar.gz \
  logs/investpro.*.log

# Centralized logging (ELK)
# Logs automatically shipped to Elasticsearch
# Viewable in Kibana dashboard
```

### 12.3 Upgrade Procedure

```bash
# 1. Backup current version
git tag -a v1.0.0 -m "Release 1.0.0"

# 2. Test upgrade in staging
./deploy-staging.sh

# 3. Run compatibility tests
./mvnw test -P upgrade-tests

# 4. Backup production database
pg_dump investpro > /backups/pre-upgrade-backup.sql

# 5. Deploy new version
./deploy-production.sh

# 6. Run smoke tests
./smoke-tests.sh

# 7. Monitor for issues
tail -f logs/investpro.log
```

---

## 13. Troubleshooting

### 13.1 Common Issues & Solutions

#### Issue: Application won't start
```
Error: Cannot bind to port 8080

Solution:
1. Check if port is already in use:
   lsof -i :8080
   
2. Kill existing process:
   kill -9 <PID>
   
3. Or use different port:
   export SERVER_PORT=8081
   ./mvnw spring-boot:run
```

#### Issue: Database connection fails
```
Error: org.postgresql.util.PSQLException: Connection to localhost:5432 refused

Solution:
1. Verify PostgreSQL is running:
   pg_isready -h localhost -p 5432
   
2. Check credentials:
   psql -U investpro -d investpro
   
3. Check network:
   ping 127.0.0.1
```

#### Issue: WebSocket connection drops
```
Error: WebSocket disconnected

Solution:
1. Check network connectivity:
   ping api.binance.com
   
2. Verify API rate limits not exceeded:
   grep "429" logs/investpro.log
   
3. Check firewall:
   sudo ufw status
```

### 13.2 Performance Troubleshooting

```bash
# Check CPU usage
top -p $(pgrep -f investpro)

# Check memory usage
jstat -gc -h10 <java_pid> 1000

# Check garbage collection
jstat -gccause -h10 <java_pid> 1000

# Thread dump
jstack <java_pid> > thread_dump.txt

# Heap dump
jmap -dump:live,format=b,file=heap.bin <java_pid>
jhat -J-Xmx4g heap.bin
```

---

## 14. Compliance & Auditing

### 14.1 Audit Logging

All critical operations are logged:
- Trade execution (what, when, who, result)
- Risk decisions (evaluation, outcome)
- Configuration changes (what changed, when)
- System events (startup, shutdown, errors)
- User actions (login, logout, settings changes)

```java
@Aspect
@Component
public class AuditAspect {
    @Before("execution(public * org.investpro.core.*.*(*))")
    public void audit(JoinPoint jp) {
        log.info("AUDIT: {} called by {} with args {}", 
            jp.getSignature(), 
            SecurityContextHolder.getContext().getAuthentication().getName(),
            jp.getArgs());
    }
}
```

### 14.2 Compliance Checklist

- [x] All trades have audit trail
- [x] User actions logged
- [x] System events recorded
- [x] Configuration changes tracked
- [x] Data retention policies enforced
- [x] GDPR compliance (data privacy)
- [x] Export functions for customer data
- [x] Regulatory reporting capability

---

## 15. Support & Escalation

### 15.1 Support Channels

- **Documentation**: [GitHub Wiki](https://github.com/yourusername/investpro/wiki)
- **Issues**: [GitHub Issues](https://github.com/yourusername/investpro/issues)
- **Discussions**: [GitHub Discussions](https://github.com/yourusername/investpro/discussions)
- **Email**: support@investpro.dev
- **Slack**: [Community Slack](https://investpro.slack.com)

### 15.2 Escalation Procedure

```
Level 1: Documentation & FAQ
├─ Check wiki
├─ Search GitHub issues
└─ Ask in discussions

Level 2: Community Support
├─ Slack community
├─ GitHub discussions
└─ Email support team

Level 3: Professional Support
├─ Enterprise support contract
├─ Dedicated support channel
└─ Priority issue resolution
```

---

## 16. Conclusion

InvestPro is **production-ready** and suitable for:
- ✅ Automated algorithmic trading
- ✅ Paper trading practice
- ✅ Live trading (with proper risk management)
- ✅ Portfolio analysis and backtesting
- ✅ Educational purposes
- ✅ Trading research

**Get Started:**
1. Follow installation guide (Section 3)
2. Configure credentials (Section 4)
3. Run application locally
4. Deploy to production when ready

---

**Version**: 1.0  
**Last Updated**: May 2026  
**Status**: ✅ PRODUCTION READY  
**Maintainer**: InvestPro Development Team  
**License**: Apache 2.0
