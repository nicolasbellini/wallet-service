# Trade-offs and Assumptions

## Assumptions

The assignment intentionally leaves several details unspecified. Where that happened, here's what was assumed and why:

1. **Single currency (BRL) application-wide.** The functional requirements never mention multi-currency support or FX conversion. Supporting multiple currencies correctly (conversion rates, exchange-rate history, currency-aware ledger entries) is a substantial feature in its own right. Wallets are always created in BRL; the currency isn't even a request parameter for `POST /wallet`, since there's nothing to choose.
2. **One wallet per user.** "The" wallet is referred to in the singular throughout the spec ("retrieve the current balance of **a** user's wallet"). Enforced both at the application layer (`DuplicateWalletException`) and at the database level (`UNIQUE` constraint on `wallet.user_id`).
3. **Transfers are wallet-to-wallet within this application only.** No external/third-party transfer target, and no `TransferStatus`/pending-confirmation workflow. A transfer is a single atomic, synchronous operation completing within one transaction. Supporting third-party transfers (e.g. to an external bank account) would require a status field, asynchronous confirmation, and reconciliation with an external system — a materially different design not implied by the spec.
4. **No authentication/authorization.** The spec doesn't mention who is allowed to call these endpoints or how callers are identified. Out of scope — any caller can act on any wallet by ID. In a real deployment this would sit behind a gateway/auth layer (e.g. verifying the caller owns the wallet they're operating on).
5. **`userId` is an opaque, pre-existing identifier.** There's no `User` entity, registration endpoint, or validation that a `userId` "really exists" — user management is assumed to be a different, pre-existing bounded context.
6. ~~No idempotency keys on mutating endpoints~~ — **implemented** (see below). All four mutating endpoints (`POST /wallet`, `/deposit`, `/withdrawal`, `/transfer`) accept an optional `Idempotency-Key` header; retries with the same key return the original response instead of re-executing.
7. **Synchronous REST only** — no event bus, no outbox pattern for publishing ledger events to other services. Every operation completes (or fails) within the HTTP request/response cycle.
8. **`occurred_at` is always server time**, never accepted from the client, to protect the integrity of the audit trail.
9. **Historical balance with no ledger entries at/before the requested instant resolves to zero** — treated as "no activity yet at that point," regardless of whether the requested instant is before or after the wallet's `createdAt`.
10. **Balances can never go negative** — enforced in the `Wallet` aggregate and again with a `CHECK` constraint at the database level (defense in depth).
11. **New wallets start at a zero balance** — there's no "initial deposit on creation" parameter.
12. **Monetary amounts are fixed at 2 decimal places**, rounded `HALF_EVEN`, matching BRL's minor unit.
13. **Idempotency scope**: a key is bound to the exact request path it was first used with (reusing a key for a different wallet/endpoint returns 422). Keys don't expire — a production version would add a TTL/cleanup job so the `idempotency_key` table doesn't grow unbounded; skipped here as a low-risk simplification for a take-home project's timeframe.

## Idempotency

`POST /wallet`, `POST /wallet/{id}/deposit`, `POST /wallet/{id}/withdrawal`, and `POST /transfer` accept an optional `Idempotency-Key` header. If present:

- The key, the request path, and the eventual response are recorded in the **same database transaction** as the wallet mutation and ledger append — they commit or roll back together, so there's never a state where the operation succeeded but the idempotency record didn't (or vice versa).
- A retry with the same key returns the **original** response verbatim (same status code, same body — e.g. the same `entryId`) without re-running the use case.
- Reusing a key for a genuinely different request (different wallet, different endpoint) is rejected with `422`.
- Two requests racing with the *same brand-new key at the same instant* is handled via the `idempotency_key` table's primary key: the loser gets `409` ("already being processed, please retry") rather than both executing.
- Omitting the header preserves the exact pre-existing behavior — every request is applied independently, no dedupe.

## Time invested

Roughly 8 hours total, including the idempotency feature added after the initial delivery (originally ~6.5–7h, within the 6–8h guideline; idempotency was a deliberate post-delivery addition, not part of the original estimate):

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

## Skipped or simplified due to time constraints

- **Pagination** on any endpoint that could return a ledger history listing (no such listing endpoint was actually required, but if one were added, it would need pagination from day one).
- **Rate limiting / abuse protection** at the API layer.
- **Authentication/authorization** (see assumption 4).
- **Multi-currency support and FX** (see assumption 1).
- **Outbox pattern / event publishing** so other services could react to wallet events without polling — currently the ledger is only queryable synchronously through this service's own API.
- **Docker Compose setup** for a one-command local Postgres environment demonstrating full production parity — the default H2 profile covers local dev/test, and `application-prod.yml` documents the Postgres path, but there's no `docker-compose.yml` wiring it all together.
- **OpenAPI/Swagger UI** — endpoints are documented in README.md by hand rather than via generated interactive API docs.
- **Structured/JSON logging and distributed tracing** (e.g. correlation IDs propagated across services) — the `GlobalExceptionHandler` attaches a correlation ID to unexpected-error responses, but there's no end-to-end tracing setup.
