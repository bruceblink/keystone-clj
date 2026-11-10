# Rewrite roadmap

## P0: API and security parity
- [x] Clojure native project, Reitit/Ring, Integrant, Malli
- [x] next.jdbc/HoneySQL read-only dictionary vertical slice
- [x] MySQL, Redis, Caffeine lifecycle wiring
- [x] Fail-closed authentication boundary and isolated unit tests
- [x] Flyway migration import helper, no automatic schema mutation
- [x] Docker/Compose, CI, uberjar build configuration
- [x] Verify dependencies, build, tests and Docker startup in a networked runtime
- [x] Snapshot all upstream API routes, fixtures and migration checksums
- [x] RSA public key/password decryption and bcrypt login service
- [x] Captcha, login and refresh HTTP flow
- [x] RS256 JWT signature, issuer and expiry verification with session hook
- [x] Redis refresh sessions, rotation, revocation and force-login service
- [x] JWT RBAC permissions and database-backed dynamic router lookup
- [ ] Departmental data-scope filtering
- [x] User, role, menu, dept and post read APIs
- [x] User, role, menu, dept and post write APIs
- [x] Dictionary type/data write APIs and cache invalidation
- [ ] Exact error and timestamp compatibility

## P1: management
- [x] Config and notice list/detail/create/update/delete APIs
- [x] Profile read/update/password APIs
- [ ] Avatar upload
- [x] Online users, forced logout and server/cache monitor summaries
- [x] Login and operation log query/delete APIs
- [ ] Import/export and file API
- [ ] Cache consistency and invalidation
- [ ] Frontend E2E parity

## P2: operations
- [ ] Cron jobs, execution logs, failure/retry/concurrency policies
- [ ] Rate limits, idempotency, metrics and audit
- [ ] Performance and deployment benchmarks versus Java Keystone

Exit condition: each feature passes API-contract, auth, persistence and failure tests. Internal architecture may differ.
