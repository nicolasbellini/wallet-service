# Design

## Architecture: Hexagonal (ports & adapters) + DDD

```
                    ┌─────────────────────────────┐
  HTTP request ───▶ │  adapters/in/web             │  controllers, DTOs, GlobalExceptionHandler
                    └───────────────┬──────────────┘
                                    │  application/dto (commands, views)
                    ┌───────────────▼──────────────┐
                    │  application/usecase          │  orchestration, @Transactional boundary
                    └───────────────┬──────────────┘
                                    │  domain/port (interfaces)
                    ┌───────────────▼──────────────┐
                    │  domain/model + exception     │  Wallet, Money, LedgerEntry — no framework imports
                    └───────────────┬──────────────┘
                                    │  implemented by
                    ┌───────────────▼──────────────┐
  H2 / Postgres ◀── │  adapters/out/persistence      │  JPA entities, repositories, mappers
                    └────────────────────────────────┘
```

The boundary that matters and is strictly enforced: **`domain` has zero Spring/JPA imports.** Domain logic (`Wallet.deposit`/`withdraw`, `Money` arithmetic, invariants) is plain Java, unit-testable without a Spring context. `application` depends only on the domain ports (`WalletRepository`, `LedgerRepository`), not on their JPA implementations — the persistence technology could be swapped without touching a single use case.

**Wiring**: plain `@Service`/`@Repository`/`@RestController` stereotypes rather than manual `@Bean` wiring in a separate configuration class. Full hexagonal purity (zero framework annotations even in adapters) buys little for a project this size and costs real time; the boundary that actually matters — the domain package's independence — is preserved regardless. `@Transactional` lives on use-case `execute()` methods, which is where the transaction boundary conceptually belongs (one use case = one business transaction).

**Lombok** was deliberately avoided project-wide: `@Data`-style generated `equals`/`hashCode`/`toString` on JPA entities is a well-known source of bugs with lazy-loaded associations and Hibernate proxies, it can produce surprising results when combined with Jackson serialization, and generated code that isn't visible in source makes debugging harder.

**ID generation: UUID, assigned by the domain, not the database.** `WalletId.generate()`, ledger entry IDs, `transferId`, and idempotency keys are all UUIDs minted in application code before anything is persisted. Three reasons:

1. **The domain doesn't depend on persistence to exist.** `Wallet.createNew(...)` produces a fully-formed aggregate with its ID already set — no round-trip to the database to ask "what's my new row's ID" before the object is usable. That fits the hexagonal design, where the domain shouldn't need persistence just to construct a valid instance.
2. **No enumeration risk.** There's no auth layer (see TRADEOFFS.md), and wallet IDs go straight into URLs (`/wallet/{id}/balance`). Sequential integers would let anyone iterate `?id=1,2,3...` and probe other users' balances; UUIDs are unguessable.
3. **Coordination-free correlation IDs.** `transferId` links two ledger entries and idempotency keys are opaque client-or-server-generated tokens — UUID is the natural fit for both, since any component can mint one without a shared counter.

Trade-off: random UUIDs (v4, used here) scatter new rows randomly across a B-tree index instead of appending at the end, which is worse for index locality than sequential IDs at high insert volume. Not a real concern at this project's scale; if it ever became one, UUIDv7 (time-ordered but still random/unguessable) would fix it while keeping the enumeration resistance — same type, no application-level rework.

## Functional requirements → implementation

| Requirement | Endpoint | Use case |
|---|---|---|
| Create Wallet | `POST /api/v1/wallet` | `CreateWalletUseCase` |
| Retrieve Balance | `GET /api/v1/wallet/{id}/balance` | `GetCurrentBalanceUseCase` |
| Retrieve Historical Balance | `GET /api/v1/wallet/{id}/balance/history?asOf=` | `GetHistoricalBalanceUseCase` |
| Deposit Funds | `POST /api/v1/wallet/{id}/deposit` | `DepositUseCase` |
| Withdraw Funds | `POST /api/v1/wallet/{id}/withdrawal` | `WithdrawUseCase` |
| Transfer Funds | `POST /api/v1/transfer` | `TransferFundsUseCase` |

## Non-functional requirement: full traceability / auditability

The ledger (`ledger_entry` table) is an **immutable, append-only** record of every operation:

- Every deposit, withdrawal, and transfer leg is one row — nothing is ever updated or deleted.
- Each row snapshots `balance_after`, the wallet's balance immediately following that entry. This means:
  - **Current balance** is just the wallet's cached `balance` column (kept in sync transactionally with every ledger append — see below).
  - **Historical balance** at any instant is a single indexed lookup: the latest entry for that wallet at or before the requested timestamp (`idx_ledger_entry_wallet_occurred`), no replay from genesis needed.
- Transfers write **two** linked entries (`TRANSFER_OUT` on the source, `TRANSFER_IN` on the destination) sharing one `transfer_id`, so both legs of a transfer can be reconstructed and cross-checked from either wallet's history — a full audit trail for cross-wallet money movement.
- `occurred_at` is always server-clock time (an injected `Clock` bean, never client-supplied), so the audit timeline can't be manipulated by a caller.
- The wallet's cached `balance` and its ledger entries are written in the **same database transaction** as part of the same use case, so they can never drift apart — the cached balance is always provably equal to `SELECT balance_after FROM ledger_entry WHERE wallet_id = ? ORDER BY occurred_at DESC, id DESC LIMIT 1`.

## Non-functional requirement: mission-critical / high availability

- **Statelessness**: the service holds no in-memory session/request state; any instance can serve any request, so it scales horizontally behind a load balancer and survives individual instance restarts.
- **ACID transactions**: every mutating use case (deposit/withdraw/transfer) is a single `@Transactional` method — either the balance update and the ledger append(s) both commit, or neither does. There is no window where a balance change exists without its audit entry, or vice versa.
- **Concurrency correctness without downtime**: see the locking strategy below — correctness under concurrent load is guaranteed outright rather than detected-and-retried, which avoids retry storms under contention.
- **Schema evolution safety**: Flyway versions the schema (`V1__init_schema.sql`); `ddl-auto=validate` ensures the running application never silently auto-migrates the schema — deployments and schema changes are decoupled and reviewable.
- **Health checks, with liveness and readiness genuinely separated**: `/actuator/health/liveness` reflects only whether the process itself is alive (`livenessState`) — an orchestrator should restart the instance if and only if this fails. `/actuator/health/readiness` additionally includes the `db` indicator, so it fails the moment the database is unreachable even though the process is perfectly healthy — an orchestrator should stop routing traffic to the instance (not restart it) when this fails, since restarting a process that's fine but whose database is down doesn't help and just causes unnecessary churn. Configured via `management.endpoint.health.group.*` in `application.yml`; `/actuator/health` still reports everything for manual inspection.
- **Graceful shutdown**: `server.shutdown=graceful` (with a 20s cap via `spring.lifecycle.timeout-per-shutdown-phase`) lets in-flight requests finish before the instance stops accepting new ones on `SIGTERM`, instead of dropping them mid-transaction. Matters most during rolling deploys and autoscaling scale-down, where instances are routinely terminated while still serving traffic.
- **Virtual threads for request handling** (`spring.threads.virtual.enabled=true`, available since this is already Java 21): every mutating request blocks its serving thread while holding a pessimistic DB lock (see below). Under real contention on a hot wallet, that can exhaust a small platform-thread pool and make an *entire instance* unresponsive to unrelated requests — a local lock-contention problem turning into an instance-wide availability problem. Virtual threads make that blocking cheap: a request waiting on a lock no longer pins a scarce OS thread, so the instance keeps serving everything else. (Caveat worth knowing: connection-pool internals that still use `synchronized` blocks can pin a virtual thread to its carrier while blocked, shrinking — but not eliminating — the benefit; not something addressed here, since the current HikariCP version already minimizes this.)
- **Postgres-compatible by design**: the default profile runs on H2 for a zero-setup local/test experience, but the schema and all SQL are Postgres-compatible (standard `UUID`, `TIMESTAMP WITH TIME ZONE`, `NUMERIC`, `CHECK` constraints — no H2-only syntax), so moving to a real, replicated Postgres instance for production is a configuration change, not a rewrite (see `application-prod.yml`).
- **Not addressed here — infrastructure, not application code**: the database itself is the actual single point of failure (every instance depends on the same one), which needs a multi-AZ/replicated Postgres setup with automatic failover at the infrastructure layer. Out of scope for this project (see TRADEOFFS.md). The GitHub Codespaces devcontainer (`.devcontainer/docker-compose.yml`) does run the app against a real, single Postgres container for development-time production parity — that's a dev convenience, not an HA setup; it's one ephemeral instance with no replication or failover.

## Caching: deliberately not used

There is no cache in front of `GET /balance` (or anywhere else) — a conscious choice, not an oversight:

- **Staleness is the wrong trade-off for a balance endpoint.** The entire design (pessimistic locking, single source of truth in the ledger, ACID transactions per use case) exists to always serve the *true current* balance. A cache introduces a window where a user deposits or withdraws, immediately checks their balance, and sees a stale number — a correctness/trust problem that matters more here than in most services.
- **No measured need.** `GetCurrentBalanceUseCase` is a single indexed primary-key lookup; `GetHistoricalBalanceUseCase` is a single indexed range lookup (`idx_ledger_entry_wallet_occurred`). Neither is a demonstrated bottleneck, and adding a cache without one is premature optimization that only adds invalidation complexity (every deposit/withdraw/transfer would need to invalidate the affected wallet's cached entry).
- **Conflicts with the statelessness goal.** An in-process cache (e.g. Caffeine) would make instances diverge in what they report, undermining the "any instance can serve any request" property called out above. Making it consistent would require an external distributed cache (Redis) — new infrastructure this project has deliberately avoided (the `.devcontainer` docker-compose setup exists for Postgres production-parity, not as a general excuse to add more services).

The one exception, if this ever became worth doing: `GET /balance/history?asOf=<past timestamp>` is provably immutable once computed — a fixed past instant's balance never changes — so it's the one read that could be cached indefinitely with zero staleness risk. Not built speculatively without an actual read-heavy workload to justify it.

## Concurrency strategy

**Pessimistic row locking is primary.** Every mutating use case starts by calling `WalletRepository.findByIdForUpdate`, which issues `SELECT ... FOR UPDATE` (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) and holds that lock until the transaction commits. This serializes all mutations to a given wallet — two concurrent withdrawals against the same wallet cannot both read the same starting balance and both succeed when only one should.

This was chosen over optimistic locking (`@Version` + retry) as the *primary* mechanism because for a monetary ledger, correctness needs to be guaranteed outright, not merely detected after the fact and retried — a naive optimistic retry loop under contention risks retry storms or requires careful backoff logic that's disproportionate for this project's scope. Per-wallet contention is expected to be low in practice (one wallet is rarely hit by many simultaneous requests), so the throughput cost of pessimistic locking is acceptable. `@Version` is still kept on `WalletJpaEntity` as a defense-in-depth fast-fail signal, mapped to `409 Conflict` if it's ever triggered.

**Deadlock prevention for transfers**: `TransferFundsUseCase` never locks "source, then destination" — it always locks both wallets **in ascending `WalletId` (UUID) order**, independent of which one is the source or destination. This creates one total lock order across all wallets, so two concurrent opposite-direction transfers (A→B and B→A) can never form a circular wait. This is proven by `ConcurrentTransferDeadlockIT`, which fires simultaneous A→B and B→A transfers and asserts they all complete without a lock timeout. `ConcurrentWithdrawalIT` proves the overdraft-prevention side: 20 concurrent withdrawals against a wallet that can only satisfy 10 of them result in exactly 10 successes, 10 rejections, and a final balance of exactly zero — never negative.

## Idempotency

Deposit, withdraw, transfer, and create-wallet are all retry-unsafe by nature — a client that times out waiting for a response has no way to know whether the operation actually happened, and a naive retry can double-deposit or double-transfer. All four mutating endpoints accept an optional `Idempotency-Key` request header to make retries safe:

- `IdempotencyService.executeIdempotent(...)` wraps the controller's use-case call inside its own `@Transactional` method. Because Spring's default transaction propagation is `REQUIRED`, the use case's own `@Transactional` method joins this same physical transaction rather than starting a new one — so the idempotency bookkeeping and the actual wallet mutation commit or roll back **together, atomically**. There's no window where one succeeded and the other didn't.
- The dedupe store (`idempotency_key` table: key, request path, response status, response body) follows the same port/adapter pattern as `WalletRepository`/`LedgerRepository` (`IdempotencyKeyRepository` port in `application/idempotency`, JPA adapter in `adapters/out/persistence`), and uses the same pessimistic-locking technique as wallet mutations: `findByKeyForUpdate` locks the row for the duration of the transaction, so a *sequential* retry (the common case — client times out, then retries) blocks until the original request's transaction resolves, then sees the completed response.
- A *simultaneous* race on a brand-new key (two requests reserving the same never-before-seen key at the same instant) can't be caught by row locking, since there's no row yet to lock. It's caught instead by the table's primary key constraint: the losing transaction's `INSERT` fails with a `DataIntegrityViolationException`, translated to `409 Conflict` ("already being processed, please retry") — the losing request never runs the use case.
- No header means no behavior change at all — every request executes independently, exactly as before this feature existed. This keeps the feature purely additive and backward-compatible.

## Status code rationale

- **422 Unprocessable Entity** for `InsufficientFundsException` and `IdempotencyKeyReusedException` — the request is syntactically valid but violates a business invariant, distinct from 400 (malformed input).
- **409 Conflict** for `DuplicateWalletException`, `IdempotencyKeyInFlightException` (resource-state conflicts) and, as defense-in-depth, for `ObjectOptimisticLockingFailureException`.
- **404 Not Found** for `WalletNotFoundException`.
- **400 Bad Request** for `SameWalletTransferException`, `InvalidAmountException`, `CurrencyMismatchException`, bean-validation failures, and malformed request parameters (e.g. an unparseable `asOf`).
- Errors are returned as RFC 7807 `application/problem+json` via Spring 6's built-in `ProblemDetail` — standard, and less boilerplate than a hand-rolled error DTO.

## Transfers are wallet-to-wallet within this application only

Both `sourceWalletId` and `destinationWalletId` in a transfer must already exist as wallets managed by this service. There is no external/third-party transfer target and no pending/confirmation `TransferStatus` field — a transfer is a single atomic, synchronous operation across two wallets this service controls, not a multi-party transaction requiring asynchronous confirmation. Third-party transfers (e.g. to an external bank account) would need a status workflow (`PENDING` → `CONFIRMED`/`FAILED`) and reconciliation with an external system, which is out of scope here — see TRADEOFFS.md.
