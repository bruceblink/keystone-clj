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
- [x] RS256 JWT signature, issuer and expiry verification with session hook
- [x] Redis refresh sessions, rotation, revocation and force-login service
- [ ] RBAC, menu permissions and departmental data-scope filtering
- [ ] User, role, menu, dept, post and dictionary CRUD
- [ ] Exact error and timestamp compatibility

## P1: management
- [ ] Config, notice, profile, logs and online users
- [ ] Import/export and file API
- [ ] Cache consistency and invalidation
- [ ] Frontend E2E parity

## P2: operations
- [ ] Cron jobs, execution logs, failure/retry/concurrency policies
- [ ] Rate limits, idempotency, metrics and audit
- [ ] Performance and deployment benchmarks versus Java Keystone

Exit condition: each feature passes API-contract, auth, persistence and failure tests. Internal architecture may differ.
