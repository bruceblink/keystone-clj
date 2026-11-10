# Keystone CLJ

Clojure-native rewrite of [bruceblink/Keystone](https://github.com/bruceblink/Keystone), focused on HTTP API and business behavior compatibility, without preserving Spring Boot internals or Starter support.

**Status: initial skeleton; NOT production-ready.**

## Technology

- Clojure 1.12.6, Reitit, Ring/Jetty, Integrant, Malli
- next.jdbc, HoneySQL, HikariCP, MySQL
- Flyway, Redis (Jedis), Caffeine
- tools.deps, tools.build, Docker Compose, GitHub Actions

## Local development

Requirements: Java 21+ and Clojure CLI. JDK 25 is recommended.

~~~bash
clojure -M:test
clojure -M:run
curl http://localhost:18080/health
~~~

Expected output:

~~~json
{"code":0,"msg":"操作成功","data":"is alive"}
~~~

By default, database and Redis are disabled. Protected routes deliberately return 401 until production-grade RS256 JWT validation, session revocation, and RBAC are implemented. A test-only authenticator is injected into unit tests; no production bypass exists.

## Docker

~~~bash
cp .env.example .env
# Replace all CHANGE_ME values.
docker compose up -d --build
curl http://localhost:18080/health
~~~

Compose initializes clients but DOES NOT migrate the database automatically. First import the original Keystone Flyway SQL, inspect the target database's migration history, and explicitly enable APP_MIGRATE on disposable test data.

## Implemented routes

| Method | Path | Status |
| --- | --- | --- |
| GET | /health | Implemented with unit tests |
| GET | /system/dict/types | Read-only, requires DB and authentication |
| GET | /system/dict/type/list | Alias of dictionary type list |
| GET | /system/dict/type/:dictId | Read-only dictionary type details |
| GET | /system/dict/data/type/:dictType | Read-only dictionary data by type |

Dictionary database integration has NOT been tested yet. Unimplemented routes are listed in docs/ROADMAP.md.

## Environment variables

| Name | Default | Description |
| --- | --- | --- |
| HOST / PORT | 0.0.0.0 / 18080 | HTTP listen |
| APP_DB_ENABLED | false | Start MySQL pool |
| DB_JDBC_URL | jdbc:mysql://localhost:33066/keystone?serverTimezone=UTC | JDBC URL |
| DB_USERNAME / DB_PASSWORD | empty | Database user/password |
| DB_POOL_SIZE | 10 | HikariCP max pool size |
| APP_REDIS_ENABLED | false | Start Jedis pool |
| REDIS_HOST / REDIS_PORT / REDIS_PASSWORD | localhost / 6379 / empty | Redis connection |
| APP_MIGRATE | false | Explicit Flyway migration |
| CACHE_TTL_MINUTES | 5 | Caffeine TTL |

## Tests, REPL and build

~~~bash
clojure -M:test
clojure -M:dev -r
# in REPL: (require 'user) (user/start) (user/stop)
clojure -T:build uber
java -jar target/keystone-clj.jar
~~~

## Import original migration files

~~~bash
git clone https://github.com/bruceblink/Keystone.git /tmp/Keystone
bash scripts/import-migrations.sh /tmp/Keystone
~~~

Import does NOT run SQL or change database schema. Existing Flyway file versions and checksums must remain unchanged.

Details: docs/ARCHITECTURE.md, docs/API_CONTRACT.md, docs/ROADMAP.md.

MIT License. Original Keystone copyright attribution is preserved.
