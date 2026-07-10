# Trade-offs and Assumptions

## Assumptions

The assignment intentionally leaves several details unspecified. Where that happened, here's what was assumed and why:

1. **Single currency (BRL) application-wide.** The functional requirements never mention multi-currency support or FX conversion. Supporting multiple currencies correctly (conversion rates, exchange-rate history, currency-aware ledger entries) is a substantial feature in its own right. Wallets are always created in BRL; the currency isn't even a request parameter for `POST /wallet`, since there's nothing to choose.
2. **One wallet per user.** "The" wallet is referred to in the singular throughout the spec ("retrieve the current balance of **a** user's wallet"). Enforced both at the application layer (`DuplicateWalletException`) and at the database level (`UNIQUE` constraint on `wallet.user_id`).
3. **Transfers are wallet-to-wallet within this application only.** No external/third-party transfer target, and no `TransferStatus`/pending-confirmation workflow. A transfer is a single atomic, synchronous operation completing within one transaction. Supporting third-party transfers (e.g. to an external bank account) would require a status field, asynchronous confirmation, and reconciliation with an external system — a materially different design not implied by the spec.
4. **No authentication/authorization.** The spec doesn't mention who is allowed to call these endpoints or how callers are identified. Out of scope — any caller can act on any wallet by ID. In a real deployment this would sit behind a gateway/auth layer (e.g. verifying the caller owns the wallet they're operating on).
5. **`userId` is an opaque, pre-existing identifier.** There's no `User` entity, registration endpoint, or validation that a `userId` "really exists" — user management is assumed to be a different, pre-existing bounded context.
6. **Synchronous REST only** — no event bus, no outbox pattern for publishing ledger events to other services. Every operation completes (or fails) within the HTTP request/response cycle.
7. **`occurred_at` is always server time**, never accepted from the client, to protect the integrity of the audit trail.
8. **Historical balance with no ledger entries at/before the requested instant resolves to zero** — treated as "no activity yet at that point," regardless of whether the requested instant is before or after the wallet's `createdAt`.
9. **Balances can never go negative** — enforced in the `Wallet` aggregate and again with a `CHECK` constraint at the database level (defense in depth).
10. **New wallets start at a zero balance** — there's no "initial deposit on creation" parameter.
11. **Monetary amounts are fixed at 2 decimal places**, rounded `HALF_EVEN`, matching BRL's minor unit.
12. **Idempotency scope**: a key is bound to the exact request path it was first used with (reusing a key for a different wallet/endpoint returns 422). Keys don't expire — a production version would add a TTL/cleanup job so the `idempotency_key` table doesn't grow unbounded; skipped here as a low-risk simplification for a take-home project's timeframe.

## Idempotency

`POST /wallet`, `POST /wallet/{id}/deposit`, `POST /wallet/{id}/withdrawal`, and `POST /transfer` accept an optional `Idempotency-Key` header. If present:

- The key, the request path, and the eventual response are recorded in the **same database transaction** as the wallet mutation and ledger append — they commit or roll back together, so there's never a state where the operation succeeded but the idempotency record didn't (or vice versa).
- A retry with the same key returns the **original** response verbatim (same status code, same body — e.g. the same `entryId`) without re-running the use case.
- Reusing a key for a genuinely different request (different wallet, different endpoint) is rejected with `422`.
- Two requests racing with the *same brand-new key at the same instant* is handled via the `idempotency_key` table's primary key: the loser gets `409` ("already being processed, please retry") rather than both executing.
- Omitting the header preserves the exact pre-existing behavior — every request is applied independently, no dedupe.

## GitHub Codespaces / Postgres devcontainer

`.devcontainer/docker-compose.yml` runs two containers: the JDK 21 dev environment and a real Postgres 16 instance. `SPRING_PROFILES_ACTIVE=prod` is set at the container level, so `./gradlew bootRun` inside a codespace connects to the real Postgres automatically — genuine production-parity development, not just documentation of what *would* happen against Postgres. Two things worth calling out:

- **Tests are explicitly isolated from this.** Setting `SPRING_PROFILES_ACTIVE=prod` at the container level would otherwise leak into `./gradlew test` too (Spring Boot reads that env var the same way regardless of which process sets it), silently pointing tests at the shared, persistent Postgres container instead of a fresh H2 instance — breaking test isolation (e.g. the second test run would fail on the `wallet.user_id` unique constraint from data left over by the first). Fixed at two independent layers: every `@SpringBootTest` is pinned to `@ActiveProfiles("test")` (`application-test.yml`, its own isolated H2 database), and the Gradle `test` task additionally clears `SPRING_PROFILES_ACTIVE` for the forked test JVM as a second line of defense.
- **Local (non-codespace) behavior is unchanged** — outside a codespace there's no `SPRING_PROFILES_ACTIVE` set, so `bootRun` still defaults to H2 exactly as before; `prod` remains opt-in via `--spring.profiles.active=prod`.

This still isn't the infrastructure-level HA setup discussed in DESIGN.md's mission-critical section (multi-AZ, automatic failover) — it's a single, ephemeral, local Postgres container, good for realistic development against the real database engine, not a substitute for a production deployment topology.

## Time invested

Roughly 8.5 hours total, including two features added after the initial delivery (originally ~6.5–7h, within the 6–8h guideline; idempotency and the Postgres devcontainer were both deliberate post-delivery additions, not part of the original estimate):

| Phase | Approx. time |
|---|---|
| Project setup (Gradle wrapper, Spring Boot skeleton, plan) | 0.5h |
| Domain layer + unit tests | 1h |
| Persistence layer (Flyway, JPA entities/repositories/adapters) | 0.75h |
| Application use cases + unit tests | 1h |
| Web adapters (controllers, DTOs, error handling) | 1h |
| Integration + concurrency tests | 1.25h |
| Documentation (README, DESIGN, TRADEOFFS) | 0.75h |
| Verification pass | 0.5h |
| Idempotency keys (port/adapter, service, wiring, tests, docs) | 1h |
| HA config (liveness/readiness, graceful shutdown, virtual threads) + Postgres devcontainer | 0.75h |

## Skipped or simplified due to time constraints

- **Pagination** on any endpoint that could return a ledger history listing (no such listing endpoint was actually required, but if one were added, it would need pagination from day one).
- **Rate limiting / abuse protection** at the API layer.
- **Authentication/authorization** (see assumption 4).
- **Multi-currency support and FX** (see assumption 1).
- **Outbox pattern / event publishing** so other services could react to wallet events without polling — currently the ledger is only queryable synchronously through this service's own API.
- **OpenAPI/Swagger UI** — endpoints are documented in README.md by hand rather than via generated interactive API docs.
- **Structured/JSON logging and distributed tracing** (e.g. correlation IDs propagated across services) — the `GlobalExceptionHandler` attaches a correlation ID to unexpected-error responses, but there's no end-to-end tracing setup.
