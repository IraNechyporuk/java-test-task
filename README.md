# Redis Optional Spring Boot

Spring Boot application demonstrating two features:

1. **Logging all application properties on startup**
2. **Optional Redis connection** — application starts and runs without Redis, automatically reconnects when Redis becomes available

## Tech Stack

- Java 21
- Spring Boot 3.2.5
- Redisson 3.25.2
- Log4j2
- Lombok
- JUnit 5 + Mockito + AssertJ

## How it works

### Task 1 — Properties Logging
On every startup, all loaded properties are printed to the console in `key=value` format, sorted alphabetically. Sensitive values (password, token, secret, etc.) are masked.

### Task 2 — Optional Redis
- Application starts even if Redis is unavailable
- Local `ConcurrentHashMap` is used as in-memory cache when Redis is down
- A background scheduler checks Redis availability every 10 seconds
- Once Redis is back, local cache is cleared and the real Redis client is activated automatically
- If Redis goes down again, the application falls back to local cache seamlessly

## Running locally

**Start Redis via Docker:**
```bash
docker run -d -p 6379:6379 redis
```

**Run the application:**
```bash
mvn spring-boot:run
```

**Simulate Redis failure:**
```bash
docker stop <container_id>
```

## Configuration
```properties
redis.masterAddress=redis://localhost:6379
redis.mode=STANDALONE
iagl.tenantId=IAGL
server.port=8080
```

## Running tests
```bash
mvn test
```
