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

**Records vs. plain classes (no Lombok)**: immutable, data-only types (`Money`, `WalletId`, `UserId`, `LedgerEntry`, all application DTOs, all web request/response DTOs) are Java `record`s — concise, correct `equals`/`hashCode`, and Jackson serializes/deserializes them natively without extra configuration. `Wallet` is a plain class, not a record, because an aggregate root has **identity-based** equality (two balance snapshots of the same wallet are still "the same" wallet), which is the opposite of a record's value-based equality contract. JPA entities (`WalletJpaEntity`, `LedgerEntryJpaEntity`) are also plain classes with hand-written accessors — Lombok was deliberately avoided project-wide: `@Data`-style generated `equals`/`hashCode`/`toString` on JPA entities is a well-known source of bugs with lazy-loaded associations and Hibernate proxies, it can produce surprising results when combined with Jackson serialization, and generated code that isn't visible in source makes debugging harder.

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
- **Health checks**: Spring Boot Actuator exposes `/actuator/health` (and `/actuator/info`), suitable for load balancer / orchestrator liveness and readiness probes.
- **Postgres-compatible by design**: the default profile runs on H2 for a zero-setup local/test experience, but the schema and all SQL are Postgres-compatible (standard `UUID`, `TIMESTAMP WITH TIME ZONE`, `NUMERIC`, `CHECK` constraints — no H2-only syntax), so moving to a real, replicated Postgres instance for production is a configuration change, not a rewrite (see `application-prod.yml`).

## Concurrency strategy

**Pessimistic row locking is primary.** Every mutating use case starts by calling `WalletRepository.findByIdForUpdate`, which issues `SELECT ... FOR UPDATE` (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) and holds that lock until the transaction commits. This serializes all mutations to a given wallet — two concurrent withdrawals against the same wallet cannot both read the same starting balance and both succeed when only one should.

This was chosen over optimistic locking (`@Version` + retry) as the *primary* mechanism because for a monetary ledger, correctness needs to be guaranteed outright, not merely detected after the fact and retried — a naive optimistic retry loop under contention risks retry storms or requires careful backoff logic that's disproportionate for this project's scope. Per-wallet contention is expected to be low in practice (one wallet is rarely hit by many simultaneous requests), so the throughput cost of pessimistic locking is acceptable. `@Version` is still kept on `WalletJpaEntity` as a defense-in-depth fast-fail signal, mapped to `409 Conflict` if it's ever triggered.

**Deadlock prevention for transfers**: `TransferFundsUseCase` never locks "source, then destination" — it always locks both wallets **in ascending `WalletId` (UUID) order**, independent of which one is the source or destination. This creates one total lock order across all wallets, so two concurrent opposite-direction transfers (A→B and B→A) can never form a circular wait. This is proven by `ConcurrentTransferDeadlockIT`, which fires simultaneous A→B and B→A transfers and asserts they all complete without a lock timeout. `ConcurrentWithdrawalIT` proves the overdraft-prevention side: 20 concurrent withdrawals against a wallet that can only satisfy 10 of them result in exactly 10 successes, 10 rejections, and a final balance of exactly zero — never negative.

## Status code rationale

- **422 Unprocessable Entity** for `InsufficientFundsException` — the request is syntactically valid but violates a business invariant, distinct from 400 (malformed input).
- **409 Conflict** for `DuplicateWalletException` (resource-state conflict) and, as defense-in-depth, for `ObjectOptimisticLockingFailureException`.
- **404 Not Found** for `WalletNotFoundException`.
- **400 Bad Request** for `SameWalletTransferException`, `InvalidAmountException`, `CurrencyMismatchException`, bean-validation failures, and malformed request parameters (e.g. an unparseable `asOf`).
- Errors are returned as RFC 7807 `application/problem+json` via Spring 6's built-in `ProblemDetail` — standard, and less boilerplate than a hand-rolled error DTO.

## Transfers are wallet-to-wallet within this application only

Both `sourceWalletId` and `destinationWalletId` in a transfer must already exist as wallets managed by this service. There is no external/third-party transfer target and no pending/confirmation `TransferStatus` field — a transfer is a single atomic, synchronous operation across two wallets this service controls, not a multi-party transaction requiring asynchronous confirmation. Third-party transfers (e.g. to an external bank account) would need a status workflow (`PENDING` → `CONFIRMED`/`FAILED`) and reconciliation with an external system, which is out of scope here — see TRADEOFFS.md.
