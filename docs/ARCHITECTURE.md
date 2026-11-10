# Architecture

The goal is functional and HTTP API compatibility with Java Keystone, not a Java-to-Clojure class translation.

## Integrant components

- :keystone/datasource: HikariCP/MySQL; disabled by default
- :keystone/migrations: Flyway; opt-in only
- :keystone/redis: Jedis client; disabled by default
- :keystone/cache: Caffeine
- :keystone/handler: Ring/Reitit API
- :keystone/server: embedded Jetty

Namespaces:
- keystone.config: environment -> data
- keystone.response: ResponseDTO-compatible envelope
- keystone.security: fail-closed auth/permission middleware
- keystone.dict.schema: Malli data validation
- keystone.dict.repository: next.jdbc + parameterized HoneySQL
- keystone.http: route composition
- keystone.system: resource lifecycle
- keystone.main: app entry point

## Important security/compatibility boundaries

- Access token verification must include RS256, issuer, expiration AND server-side Redis session validity; JWT signature verification alone is insufficient.
- Reimplement refresh-token rotation, single-account session takeover, forced logout, and locking with concurrency tests.
- Preserve RBAC permission strings, departmental data scopes and audit semantics.
- Normalize JSON dates, errors, HTTP status, pagination behavior against the Java reference. Current skeleton is not yet exact in these areas.
- SQL migrations are NOT bundled. Explicitly copy original source migrations and compare Flyway history on a disposable database.
- A successful /health check does not imply the unimplemented business functions are operational.

## Release gates

1. Unit tests and uberjar build.
2. MySQL/Redis integration and migration smoke tests.
3. Contract tests against original Java API with equivalent fixtures.
4. Security concurrency tests: JWT expiry, revocation, refresh replay, forced login.
5. Existing frontend E2E tests.
6. Latency, RSS and throughput benchmarks under identical conditions.
