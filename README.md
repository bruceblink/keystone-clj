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

By default, database and Redis are disabled. Login and protected routes remain unavailable until MySQL, Redis and `KEYSTONE_RSA_PRIVATE_KEY` are configured. There is no production authentication bypass.

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
| GET | /getConfig | Captcha flag and dictionary configuration |
| GET | /captchaImage | Configurable one-time SVG captcha |
| GET | /login/rsa-public-key | Configured RSA public key |
| POST | /login | RSA password + BCrypt login, requires DB and Redis |
| POST | /refresh-token | Rotating refresh session |
| POST | /logout-refresh-token | Refresh session revocation |
| POST | /logout | Authenticated access-token logout |
| GET | /getLoginUserInfo | Authenticated user and permissions |
| GET | /getRouters | Authenticated database-backed menu tree |
| GET | /system/users | User list, filters and pagination |
| GET | /system/users/:userId | User detail |
| GET | /system/role/list | Role list, filters and pagination |
| GET | /system/role/:roleId | Role detail |
| GET | /system/menus | Menu list |
| GET | /system/menus/:menuId | Menu detail |
| GET | /system/menus/dropdown | Menu dropdown tree |
| GET | /system/depts | Department list |
| GET | /system/dept/:deptId | Department detail |
| GET | /system/depts/dropdown | Department dropdown tree |
| GET | /system/post/list | Post list, filters and pagination |
| GET | /system/post/:postId | Post detail |

The user, role, menu, department and post resources also expose the matching
POST/PUT/DELETE management operations from the frontend contract. Write paths
require their corresponding `system:*` permission and run in database transactions.
Dictionary type and data create/update/delete endpoints are also available and
invalidate the Caffeine dictionary cache after writes.
System configuration and notice list/detail/create/update/delete endpoints are
available under `/system/config*` and `/system/notices*`.
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
| KEYSTONE_RSA_PRIVATE_KEY | empty | Base64 PKCS#8 RSA private key; required for login |
| KEYSTONE_RSA_PUBLIC_KEY | empty | Optional Base64 X.509 RSA public key |
| JWT_ISSUER | keystone | JWT issuer claim |
| TOKEN_EXPIRATION_SECONDS | 1800 | Access-token lifetime |
| TOKEN_REFRESH_EXPIRATION_SECONDS | 604800 | Refresh-session lifetime |
| APP_CAPTCHA_ENABLED | false | Enable one-time captcha validation |
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
