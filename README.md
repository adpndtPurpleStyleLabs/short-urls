# PreonsURL

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Database](https://img.shields.io/badge/Database-MariaDB-blue.svg)](https://mariadb.org/)
[![Build](https://img.shields.io/badge/Build-Maven-C71A36.svg)](https://maven.apache.org/)

**PreonsURL** is a high-performance URL shortening and business link infrastructure engine. It combines a lock-free/spin-wait buffered short code generator with MariaDB persistence, directory-type categorization (e.g. `/invoice/{code}`), link access analytics logging, and API-key protected creation endpoints.

---

## Key Features

- **High-Throughput Short Code Pool**: Multi-threaded worker buckets (`com.preonsurl.core`) pre-generate obfuscated Base62 short codes using Feistel cipher encryption and epoch timestamp generation to avoid sequential guessing and database locks.
- **Directory Routing**: Supports categorized routing prefixes like `https://short.domain/invoice/k9X2b` alongside root paths `https://short.domain/k9X2b`.
- **Idempotent Link Creation**: If a short code already exists for the same URL and directory type, the existing short URL is returned immediately without duplicate records.
- **API Key Security**: The `/create` endpoint enforces authentication via the `X-API-KEY` header (or `Authorization: Bearer <key>`), configured in `application.properties`.
- **Access Analytics Logging**: Every redirection records the client's IP address (with `X-Forwarded-For` proxy support), User-Agent, Referer, and timestamp in `short_urls_access_log` while atomically incrementing the link's click count.
- **Test Isolation**: Automated tests execute using an in-memory H2 database with MariaDB/MySQL compatibility mode, allowing tests to run with zero dependencies on external database servers.

---

## Architecture Overview

```
                            +--------------------------+
                            |  Client / Application    |
                            +--------------------------+
                               |                    |
         POST /create (X-API-KEY)             GET /{dir}/{code} or /{code}
                               |                    |
                               v                    v
                  +------------------------+   +------------------------+
                  |  ApiKeyAuthInterceptor |   |   ServingController    |
                  +------------------------+   +------------------------+
                               | (Authorized)       |
                               v                    |
                  +------------------------+        |
                  |    CreateController    |        |
                  +------------------------+        |
                               |                    |
                               v                    v
                  +---------------------------------------------+
                  |               ShortUrlService               |
                  +---------------------------------------------+
                         /                  \              \
                        /                    \              \
                       v                      v              v
            +-------------------+   +------------------+   +------------------------+
            |   ShortCodePool   |   |    short_urls    |   | short_urls_access_log  |
            | (Pre-buffered ID) |   |  (MariaDB Table) |   |    (MariaDB Table)     |
            +-------------------+   +------------------+   +------------------------+
```

---

## Project Structure

```
preonsurl/
├── pom.xml                                  # Project dependencies (Spring Boot, JPA, MariaDB, H2)
├── schema-mariadb.sql                       # MariaDB DDL for database and tables
├── src/
│   ├── main/
│   │   ├── java/com/preonsurl/
│   │   │   ├── PreonsurlApplication.java   # Spring Boot entry point
│   │   │   ├── core/                       # Short code generation engine
│   │   │   │   ├── Base62.java              # Base62 encoder/decoder
│   │   │   │   ├── EpochIdGenerator.java    # Distributed epoch-based ID generator
│   │   │   │   ├── Feistel62Obfuscator.java # Cryptographic permutation obfuscator
│   │   │   │   ├── ShortCodeBucket.java     # Worker queue buffer
│   │   │   │   ├── ShortCodeGenerator.java  # Generator facade
│   │   │   │   ├── ShortCodePool.java       # Multi-worker code pool
│   │   │   │   └── UrlShortenerCore.java    # Shortener core wrapper
│   │   │   ├── apis/                       # REST & Serving APIs
│   │   │   │   ├── config/                  # Interceptor & Bean configurations
│   │   │   │   │   ├── ApiKeyAuthInterceptor.java
│   │   │   │   │   ├── ShortenerBeanConfig.java
│   │   │   │   │   └── WebMvcConfig.java
│   │   │   │   ├── dto/                     # Request and Response payloads
│   │   │   │   │   ├── CreateShortUrlRequest.java
│   │   │   │   │   └── CreateShortUrlResponse.java
│   │   │   │   ├── entity/                  # JPA Entities
│   │   │   │   │   ├── ShortUrl.java
│   │   │   │   │   └── ShortUrlAccessLog.java
│   │   │   │   ├── repository/              # Spring Data JPA Repositories
│   │   │   │   │   ├── ShortUrlRepository.java
│   │   │   │   │   └── ShortUrlAccessLogRepository.java
│   │   │   │   ├── service/                 # Business logic
│   │   │   │   │   └── ShortUrlService.java
│   │   │   │   └── web/                     # REST Controllers
│   │   │   │       ├── CreateController.java
│   │   │   │       └── ServingController.java
│   │   └── resources/
│   │       └── application.properties       # Default & MariaDB configuration
│   └── test/
│       ├── java/com/preonsurl/              # Integration and Unit tests
│       │   ├── apis/CreateControllerTest.java
│       │   └── apis/ServingControllerTest.java
│       └── resources/
│           └── application.properties       # H2 In-Memory DB configuration for tests
```

---

## Database Setup (MariaDB)

### 1. Apply MariaDB DDL Schema
Execute the provided [schema-mariadb.sql](schema-mariadb.sql) script against your MariaDB server:

```bash
mariadb -h <HOST> -P <PORT> -u <USER> -p < schema-mariadb.sql
```

The script sets up:
1. `preonsurl` database with `utf8mb4` character set.
2. `short_urls` table for storing links, directory mappings, and click counts.
3. `short_urls_access_log` table for audit/access history with foreign key cascade deletion.

---

## Configuration

Settings can be customized in `src/main/resources/application.properties` or overridden via environment variables:

| Property | Environment Variable | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `server.port` | `SERVER_PORT` | `8081` | Web server port |
| `spring.datasource.url` | `DB_HOST`, `DB_PORT`, `DB_NAME` | `jdbc:mariadb://localhost:3306/preonsurl` | MariaDB JDBC URL |
| `spring.datasource.username` | `DB_USER` | `root` | MariaDB username |
| `spring.datasource.password` | `DB_PASSWORD` | *(empty)* | MariaDB password |
| `preonsurl.api.key` | `API_KEY` | `preons-secret-api-key-2026` | API authentication key for `/create` |
| `preonsurl.shortener.domain` | `SHORTENER_DOMAIN` | `http://localhost:8081` | Public domain prefix for generated short URLs |
| `preonsurl.shortener.worker-count` | — | `4` | Number of background worker threads generating codes |
| `preonsurl.shortener.bucket-capacity` | — | `100` | Pre-buffered capacity per worker bucket |
| `preonsurl.shortener.secret` | `SHORTENER_SECRET` | *(internal key)* | Secret key for Feistel obfuscation |
| `logging.file.path` | `LOG_PATH` | `logs` | Directory for application and error log files |

---

## Production Logging Architecture

PreonsURL utilizes a production-grade Logback setup defined in [logback-spring.xml](src/main/resources/logback-spring.xml):

- **Day-Wise Rolling Files**: Logs roll over every night at midnight (`%d{yyyy-MM-dd}`).
- **1 Month Retention**: Keeps exactly **30 days** of historical day-wise log files (`<maxHistory>30</maxHistory>`).
- **Automatic Gzip Compression**: Historical files are compressed into `.gz` format (`preonsurl-2026-09-16.0.log.gz`) to optimize disk space.
- **Dedicated Error Log**: Errors (level `ERROR`) are simultaneously piped into `logs/preonsurl-error.log` (also with 30-day retention) for immediate issue detection.
- **Disk Protection Caps**: Configured with a `10GB` total size cap and `100MB` per-file rotation limit to prevent disk saturation.

---

## API Documentation

### 1. Create Short URL (`POST /create`)

Requires the `X-API-KEY` header (or `Authorization: Bearer <key>`). Accepts `application/json` or `application/x-www-form-urlencoded`.

#### Request Headers
```http
X-API-KEY: preons-secret-api-key-2026
Content-Type: application/json
```

#### Request Body
```json
{
  "url": "https://billing.example.com/statements/2026/invoice-84820",
  "dirType": "/invoice"
}
```
*Note: `dirType` is optional. Leading and trailing slashes are normalized automatically.*

#### Response (`200 OK`)
```json
{
  "shortUrl": "http://localhost:8081/invoice/3wK8s",
  "shortCode": "3wK8s",
  "originalUrl": "https://billing.example.com/statements/2026/invoice-84820",
  "dirType": "invoice",
  "existing": false
}
```
*If called again with the same URL and directory type, the existing short URL is returned with `"existing": true`.*

#### Example `curl` Commands

**With Directory Type:**
```bash
curl -X POST http://localhost:8081/create \
  -H "X-API-KEY: preons-secret-api-key-2026" \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com/long/path", "dirType": "/invoice"}'
```

**Without Directory Type (Root short link):**
```bash
curl -X POST http://localhost:8081/create \
  -H "X-API-KEY: preons-secret-api-key-2026" \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com/long/path"}'
```

---

### 2. Link Redirection (`GET /{shortCode}` and `GET /{dirType}/{shortCode}`)

Resolves the short URL, logs the access details into `short_urls_access_log`, increments the click counter, and redirects the client.

#### Endpoints
- `GET /{shortCode}` — Resolves root short URLs (e.g., `http://localhost:8081/3wK8s`)
- `GET /{dirType}/{shortCode}` — Resolves directory short URLs (e.g., `http://localhost:8081/invoice/3wK8s`)

#### Responses
- **302 Found**: Contains `Location: <original_url>` header.
- **404 Not Found**: Returned if the short code is invalid or does not exist.

#### Example
```bash
curl -i http://localhost:8081/invoice/3wK8s
```
```http
HTTP/1.1 302 Found
Location: https://billing.example.com/statements/2026/invoice-84820
```

---

## Building and Running

### Prerequisites
- **JDK 25** (or compatible modern OpenJDK)
- **MariaDB 10.5+** or **11.x**

### Compile & Build
```bash
./mvnw clean compile
```

### Run Tests
```bash
./mvnw clean test
```
*Automated tests run against an in-memory H2 database with MySQL compatibility mode, requiring no live MariaDB connection.*

### Run 100 Million Stress & Collision Benchmark
PreonsURL includes a high-throughput test runner [CoreStressAndBenchmarkRunner.java](src/test/java/com/preonsurl/core/CoreStressAndBenchmarkRunner.java) to test code generation speed and verify zero collisions across millions of codes:

```bash
# Run 100 Million benchmark across 1, 4, 8, 16 workers:
./mvnw test-compile && java -Xmx6g -cp "target/test-classes:target/classes:$(./mvnw dependency:build-classpath | grep -v '\[INFO\]' | tail -n 1)" com.preonsurl.core.CoreStressAndBenchmarkRunner 100000000 1,4,8,16
```

#### Benchmark Results (100,000,000 Codes Generated)
| Workers | Total Codes | Time Taken | Codes / Sec | Collisions | Memory Used |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1** | 10,000,000 | 19.61 s | 509,816 /s | **0** | 18 MB |
| **4** | 10,000,000 | 5.45 s | 1,835,443 /s | **0** | 33 MB |
| **8** | 100,000,000 | 37.88 s | 2,640,070 /s | **0** | 430 MB |
| **16** | 100,000,000 | 30.56 s | **3,272,569 /s** | **0** | 197 MB |

---

### Run the Application Locally
```bash
# Using default configuration
./mvnw spring-boot:run

# Or with custom MariaDB connection
DB_HOST=192.168.1.50 DB_USER=preons DB_PASSWORD=secret ./mvnw spring-boot:run
```

---

## Memory & RAM Optimization

The container has been tuned specifically for low-overhead microservice deployment:

| Parameter | Previous Default | Optimized Value | Memory Impact |
| :--- | :--- | :--- | :--- |
| **Garbage Collector** | ZGC (`-XX:+UseZGC`) | **G1GC** (`-XX:+UseG1GC`) | Eliminates massive virtual memory mapping & GC worker threads on large host servers |
| **Heap Size** | MaxRAMPercentage 75% (~70 GB on 93GB host) | **`-Xms128m -Xmx512m`** | Bounds heap strictly between 128MB and 512MB |
| **Thread Stack (`-Xss`)** | Default 1024k | **`512k`** | Halves native stack memory consumed by all active threads |
| **Metaspace Limit** | Unlimited | **`-XX:MaxMetaspaceSize=160m`** | Prevents classloader metadata leaks |
| **Tomcat Worker Pool** | 200 threads | **50 threads** (`server.tomcat.threads.max=50`) | Saves ~150MB of thread stack overhead |
| **Hikari Connection Pool**| 10 connections | **10 max, 2 min-idle** | Minimizes JDBC connection buffers when idle |
| **Docker CGroup Limits** | Unlimited | **`limits.memory: 600M`, `reservations: 256M`** | Enforces OS-level cgroup boundary |

**Result**: Startup RSS memory drops from **~1.84 GiB down to ~180MB - 300MB**.


---

## Running with Docker & Docker Compose

PreonsURL includes complete Docker and Docker Compose configurations that launch both the application and MariaDB (with automated schema execution on first startup).

### 1. Start Application & MariaDB with Docker Compose
```bash
docker compose up -d --build
```
This will:
- Spin up `mariadb:11.4` on port `3306`.
- Automatically initialize the database and tables using [schema-mariadb.sql](schema-mariadb.sql).
- Build and start the `preonsurl-app` container on port `8081` once the database healthcheck passes.

### 2. View Logs
```bash
docker compose logs -f app
```

### 3. Stop Services
```bash
docker compose down
```
*(Add `-v` if you also want to delete persistent database volumes: `docker compose down -v`)*

---

### Manual Docker Build
If you want to build and run the standalone Docker container:
```bash
# Build Docker image
docker build -t preonsurl:latest .

# Run container connected to an existing MariaDB
docker run -d \
  --name preonsurl \
  -p 8081:8081 \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=3306 \
  -e DB_USER=preons \
  -e DB_PASSWORD=preonspass \
  -e API_KEY=preons-secret-api-key-2026 \
  preonsurl:latest
```

