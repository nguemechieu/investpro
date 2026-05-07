# MySQL to PostgreSQL Migration Guide for InvestPro

## Overview

This guide helps you migrate from MySQL to PostgreSQL for the InvestPro application running in Docker.

## Pre-Migration Checklist

- [ ] Backup existing MySQL database
- [ ] PostgreSQL Docker container is running
- [ ] psql client installed locally or accessible via Docker
- [ ] Sufficient disk space for the migrated data
- [ ] Application backed up and tested

## Step 1: Backup Existing MySQL Database

### If MySQL is running on host:
```bash
# Export MySQL database to file
mysqldump -u root -p investpro > mysql_investpro_backup.sql
# Enter password when prompted
```

### If MySQL is in Docker:
```bash
# Find MySQL container name
docker ps | grep mysql

# Export from container
docker exec mysql-container mysqldump -u root -proot investpro > mysql_investpro_backup.sql
```

## Step 2: Prepare PostgreSQL Database

### Ensure PostgreSQL is running:
```bash
docker-compose up -d postgres
docker-compose ps postgres  # Should show 'Up'
```

### Connect to PostgreSQL and create necessary schemas:
```bash
# Connect to PostgreSQL from host
docker exec -it investpro-postgres psql -U investpro -d investpro

# Or use psql on host if installed
psql -h localhost -U investpro -d investpro -W
# Password: investpro123
```

## Step 3: Convert MySQL Dump to PostgreSQL

MySQL and PostgreSQL have some syntax differences. The dump file needs conversion.

### Option A: Using pgloader (Recommended for complex schemas)

```bash
# Install pgloader
# On Windows: download from https://pgloader.io
# On macOS: brew install pgloader
# On Linux: apt-get install pgloader

# Create pgloader migration script
cat > migrate.load << 'EOF'
LOAD DATABASE
  FROM mysql://root:root@localhost:3306/investpro
  INTO postgresql://investpro:investpro123@localhost:5432/investpro
  WITH include drop, create tables, create indexes, reset sequences
  SET migration_parameters to '
    include-drop-if-exists = yes,
    create-indexes = yes,
    reset-sequences = yes
  ';
EOF

# Run pgloader
pgloader migrate.load
```

### Option B: Manual SQL Conversion

If you have a small database, manually convert the SQL:

```bash
# Common differences to fix in mysql_investpro_backup.sql:

# 1. Replace AUTO_INCREMENT with SERIAL
# MySQL: AUTO_INCREMENT
# PostgreSQL: SERIAL or BIGSERIAL

# 2. Replace backticks with double quotes
# MySQL: `column_name`
# PostgreSQL: "column_name"

# 3. Replace UNSIGNED with CHECK constraints
# MySQL: INT UNSIGNED
# PostgreSQL: INT CHECK (column >= 0)

# 4. Replace CHARSET/COLLATE
# MySQL: CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
# PostgreSQL: ENCODING 'UTF8' (handled at DB level)

# Quick conversion using sed:
sed -i 's/AUTO_INCREMENT/SERIAL/g' mysql_investpro_backup.sql
sed -i 's/`/"/g' mysql_investpro_backup.sql
sed -i 's/ CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci//g' mysql_investpro_backup.sql
```

## Step 4: Import Data into PostgreSQL

### Method 1: Direct import from Docker
```bash
# Create converted SQL file
docker exec -i investpro-postgres psql -U investpro -d investpro < mysql_investpro_backup.sql
```

### Method 2: Import from psql prompt
```bash
# Connect to PostgreSQL
docker exec -it investpro-postgres psql -U investpro -d investpro

# Inside psql:
\i mysql_investpro_backup.sql

# Or copy and paste SQL content
```

## Step 5: Verify Migration

### Check tables were created:
```bash
docker exec -it investpro-postgres psql -U investpro -d investpro

# List all tables
\dt

# Count rows in each table
SELECT tablename, 
       (SELECT count(*) FROM "{tablename}") as row_count 
FROM pg_tables 
WHERE schemaname = 'public';

# Exit psql
\q
```

### Verify data integrity:
```bash
-- Check for any NULL values where not expected
SELECT * FROM your_table WHERE column_name IS NULL;

-- Verify sequence values
SELECT sequencename FROM pg_sequences;

-- Reset sequence if needed
SELECT setval('table_id_seq', COALESCE((SELECT MAX(id) FROM table), 1) + 1);
```

## Step 6: Update Application Configuration

### Update InvestPro connection settings:

**Option A: Via docker-compose.yml**
```yaml
environment:
  DB_URL: jdbc:postgresql://postgres:5432/investpro
  DB_USER: investpro
  DB_PASSWORD: investpro123
  DB_DRIVER: org.postgresql.Driver
```

**Option B: Via application config files**
Update any hardcoded JDBC URLs in:
- `src/main/resources/application.properties`
- `src/main/resources/application.yml`
- `config.properties`

Old: `jdbc:mysql://localhost:3306/investpro`
New: `jdbc:postgresql://postgres:5432/investpro`

**Option C: Via environment variables**
```bash
export DB_URL=jdbc:postgresql://postgres:5432/investpro
export DB_USER=investpro
export DB_PASSWORD=investpro123
```

## Step 7: Add PostgreSQL JDBC Driver

Ensure `pom.xml` includes PostgreSQL driver:

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.1</version>
</dependency>
```

Then rebuild:
```bash
docker-compose build --no-cache investpro-app
```

## Step 8: Testing

### Test Docker deployment:
```bash
# Start services
docker-compose down
docker-compose up --build

# Wait for all services to be healthy
docker-compose ps

# Check application logs
docker-compose logs investpro-app

# Verify database connection
docker exec investpro-app bash -c 'java -version'
```

### Verify application functionality:
1. Access GUI: `http://localhost:6080`
2. Verify data is visible
3. Test CRUD operations
4. Check logs for errors: `docker-compose logs investpro-app`

## Step 9: Cleanup (After Successful Migration)

```bash
# Stop MySQL container if no longer needed
docker-compose down mysql

# Remove old MySQL volumes (if safe)
docker volume rm mysql_data

# Archive backup files
mkdir -p backups
mv mysql_investpro_backup.sql backups/
```

## Troubleshooting

### Import fails with encoding errors
```bash
# Force UTF-8 encoding
LANG=en_US.UTF-8 psql -U investpro -d investpro < backup.sql

# Or in PostgreSQL:
SET client_encoding = 'UTF8';
```

### Data types incompatibility
Common MySQL to PostgreSQL type mappings:
```
MySQL          → PostgreSQL
TEXT           → TEXT
BLOB           → BYTEA
JSON           → JSONB (recommended) or JSON
DATETIME       → TIMESTAMP
BOOLEAN        → BOOLEAN or SMALLINT
INT UNSIGNED   → BIGINT or INT with CHECK
DOUBLE         → DOUBLE PRECISION
DECIMAL(10,2)  → NUMERIC(10,2)
```

### Sequence/Auto-increment issues
```bash
# In PostgreSQL, after import:
SELECT setval('users_id_seq', (SELECT MAX(id) FROM users) + 1);

# Or for all sequences:
DO $$
DECLARE
  seq record;
BEGIN
  FOR seq IN SELECT sequencename FROM pg_sequences WHERE schemaname = 'public'
  LOOP
    EXECUTE 'SELECT setval(' || quote_literal(seq.sequencename) || 
            ', COALESCE((SELECT MAX(id) FROM ' || 
            regexp_replace(seq.sequencename, '_id_seq$', '') || 
            '), 1) + 1)';
  END LOOP;
END $$;
```

### Foreign key constraint violations
```bash
# Disable and re-enable constraints during import:
SET CONSTRAINTS ALL DEFERRED;

-- Import your data

SET CONSTRAINTS ALL IMMEDIATE;
```

### Application can't connect
```bash
# Test connection from app container:
docker exec investpro-app bash -c \
  'psql -h postgres -U investpro -d investpro -c "SELECT 1"'

# Should return:
#  ?column?
# ----------
#        1
```

## Rollback Plan

If migration fails:

```bash
# Restore from backup
docker-compose down
docker-compose up -d postgres

# Wait for postgres to be ready
sleep 10

# Restore from backup
docker exec -i investpro-postgres psql -U investpro -d investpro < mysql_investpro_backup.sql

# Or go back to MySQL configuration
```

## Performance Optimization (Post-Migration)

```sql
-- Analyze table statistics for query optimization
ANALYZE;

-- Create indexes for commonly queried columns
CREATE INDEX idx_column_name ON table_name(column_name);

-- Vacuum to clean up and reclaim space
VACUUM FULL ANALYZE;
```

## PostgreSQL Specific Features to Leverage

Now that you're on PostgreSQL, consider using:
- **JSONB**: Better JSON support than MySQL
- **Arrays**: Native array types
- **Full-text search**: Built-in FTS capabilities
- **Window functions**: Advanced analytical queries
- **Common Table Expressions (CTEs)**: WITH clauses for complex queries
- **Triggers**: More powerful than MySQL

## Support and Additional Resources

- PostgreSQL documentation: https://www.postgresql.org/docs/
- pgloader: https://pgloader.io/
- MySQL to PostgreSQL guide: https://wiki.postgresql.org/wiki/Converting_from_MySQL_to_PostgreSQL
