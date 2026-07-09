# Wallet Service — Implementation Plan

## Progress

- [x] 1. Gradle/project setup
- [ ] 2. Domain layer + unit tests
- [ ] 3. Persistence layer
- [ ] 4. Application use cases + unit tests
- [ ] 5. Web adapters
- [ ] 6. Integration + concurrency tests
- [ ] 7. Deliverable docs (README.md, DESIGN.md, TRADEOFFS.md)
- [ ] 8. Verification pass

## Context

This is a take-home assignment (RecargaPay "Wallet Service Assignment") to build a Java microservice managing users' money: create wallets, deposit, withdraw, transfer between users, retrieve current balance, and retrieve historical balance at a point in time. It's explicitly framed as mission-critical (HA expectations) and must provide full traceability/auditability of every operation for balance auditing. Deliverables are a repo with the implementation, install/test/run instructions, a design-choices write-up, and a trade-offs write-up, with all ambiguous requirements resolved via documented assumptions.

The project is greenfield — only an empty scaffold exists (`src/main/java/com/recargapay/walletservice`, `src/main/resources/db/migration`, `src/test/java/com/recargapay/walletservice`), no build file yet.

Decisions already confirmed with the user:
1. **Stack**: Spring Boot 3 + Gradle, Java 21.
2. **Persistence style**: event-sourced, immutable append-only ledger; current/historical balance derived from it. Directly satisfies auditability.
3. **Database**: H2 in-memory for dev/tests, Postgres-compatible schema/SQL (Flyway migrations).
4. **Architecture**: Hexagonal (ports & adapters) + DDD, explicitly requested by the user.
5. **Naming**: singular "wallet" used consistently everywhere — REST paths, DB table names, and any other plural references (`ledger_entries` → `ledger_entry`).
6. **No Lombok** anywhere in the project — avoids known Jackson serialization pitfalls, lazy-init/proxy issues with Hibernate entities, and makes debugging harder. Immutable data-only classes use Java records instead; JPA entities are plain classes with explicit accessors.
7. **Transfers are wallet-to-wallet within this application only** — no third-party/external-account transfers. This is why the design has no `TransferStatus`/pending-confirmation workflow: a transfer is a single atomic, synchronous operation across two wallets we control, not a multi-party transaction requiring async confirmation. Documented as an explicit assumption.
8. A **`PLAN.md`** is committed at the repo root (alongside README/DESIGN/TRADEOFFS) tracking implementation steps with progress checkboxes, mirroring this plan.

## Package / directory layout

Base package: `com.recargapay.walletservice` (matches existing scaffold).

```
wallet-service/
├── build.gradle, settings.gradle, gradlew(.bat), gradle/wrapper/*
├── .gitignore
├── README.md / DESIGN.md / TRADEOFFS.md / PLAN.md
└── src
    ├── main/java/com/recargapay/walletservice/
    │   ├── WalletServiceApplication.java
    │   ├── domain/
    │   │   ├── model/        Wallet, LedgerEntry, EntryType, Money, WalletId, UserId
    │   │   ├── exception/     WalletNotFoundException, InsufficientFundsException, InvalidAmountException,
    │   │   │                  DuplicateWalletException, SameWalletTransferException
    │   │   └── port/          WalletRepository, LedgerRepository   (interfaces, zero Spring/JPA imports)
    │   ├── application/
    │   │   ├── usecase/       CreateWalletUseCase, DepositUseCase, WithdrawUseCase, TransferFundsUseCase,
    │   │   │                  GetCurrentBalanceUseCase, GetHistoricalBalanceUseCase
    │   │   └── dto/           command/view objects passed between web adapter and use cases
    │   ├── adapters/
    │   │   ├── in/web/        WalletController, TransferController, GlobalExceptionHandler, dto/* (records)
    │   │   └── out/persistence/
    │   │       ├── entity/    WalletJpaEntity, LedgerEntryJpaEntity   (plain classes, no Lombok, explicit
    │   │       │              getters/setters, equals/hashCode on id only — avoids Lombok+Hibernate-proxy pitfalls)
    │   │       ├── repository/ WalletJpaRepository (incl. pessimistic-lock query), LedgerEntryJpaRepository
    │   │       ├── WalletRepositoryAdapter, LedgerRepositoryAdapter  (implement domain ports)
    │   │       └── mapper/    WalletMapper, LedgerEntryMapper
    │   └── config/            ClockConfig (injectable Clock bean), OpenApiConfig (optional, springdoc)
    ├── main/resources/
    │   ├── application.yml            (default profile: H2 + Flyway, ddl-auto=validate)
    │   ├── application-prod.yml       (Postgres placeholder, demonstrates portability)
    │   └── db/migration/V1__init_schema.sql
    └── test/java/com/recargapay/walletservice/
        ├── domain/model/MoneyTest.java, WalletTest.java
        ├── application/usecase/*UseCaseTest.java     (Mockito-mocked ports)
        ├── adapters/in/web/WalletControllerIT.java   (@SpringBootTest + MockMvc, H2)
        └── concurrency/ConcurrentWithdrawalIT.java, ConcurrentTransferDeadlockIT.java
```

**Wiring**: plain `@Service`/`@Repository`/`@RestController` stereotypes rather than manual `@Bean` wiring — full hexagonal purity (zero framework annotations in adapters) isn't worth the time cost here. The boundary that matters and is preserved: **the `domain` package has zero Spring/JPA imports**. `@Transactional` lives on use-case `execute()` methods.

## Gradle dependencies

Spring Boot Web, Spring Data JPA, Validation, Actuator, Flyway (`flyway-core`, Postgres driver dependency commented out for later), H2 (`runtimeOnly`), springdoc-openapi (optional), Spring Boot Test (JUnit 5, Mockito, AssertJ, MockMvc) for tests. **No Lombok** — per user preference (Jackson serialization pitfalls, lazy-init/proxy issues with Hibernate entities, harder debugging). Immutable data-only types (domain VOs, web DTOs, application command/view objects) are Java records; JPA entities and the `Wallet` aggregate are plain classes with hand-written accessors.

## Domain layer

- **`Money`** — record-like VO: `BigDecimal amount` (scale 2, `HALF_EVEN`), `Currency currency`. Rejects null/negative at construction. Currency fixed to `BRL` app-wide (documented assumption).
- **`WalletId` / `UserId`** — UUID-wrapping records for type safety.
- **`Wallet`** — aggregate root: `id`, `userId`, `balance`, `createdAt`. `deposit(Money)` / `withdraw(Money)` enforce amount > 0 and non-negative resulting balance (`InsufficientFundsException` on violation). No framework imports.
- **`LedgerEntry`** — immutable record: `id`, `walletId`, `entryType`, `amount` (positive magnitude), `balanceAfter` (snapshot enabling O(1) historical lookups), `occurredAt`, `transferId` (nullable, correlates transfer legs), `relatedWalletId` (nullable), `reference` (nullable).
- **`EntryType`**: `DEPOSIT`, `WITHDRAWAL`, `TRANSFER_OUT`, `TRANSFER_IN`.
- **Ports**: `WalletRepository{save, findById, findByIdForUpdate, findByUserId}`, `LedgerRepository{append, findLatestAsOf(walletId, asOf)}`.

`occurredAt` is always server-clock time (injected `Clock` bean), never client-supplied — protects ledger integrity.

## Application layer (use cases)

- **CreateWalletUseCase** — rejects if a wallet already exists for the user (`DuplicateWalletException`, one-wallet-per-user assumption); creates zero-balance wallet.
- **GetCurrentBalanceUseCase** — reads `wallet.balance` directly (kept in sync transactionally with every ledger append).
- **GetHistoricalBalanceUseCase** — `ledgerRepository.findLatestAsOf(walletId, asOf)`; returns that entry's `balanceAfter`, or zero if none exists at/before `asOf` (documented assumption).
- **DepositUseCase / WithdrawUseCase** — `findByIdForUpdate` (row lock) → mutate aggregate → save → append one `LedgerEntry`. Single `@Transactional` method, lock held to commit.
- **TransferFundsUseCase** — rejects same-wallet transfers; locks both wallets **in ascending WalletId order** (not source-then-destination) to prevent deadlocks; withdraws from source, deposits to destination; appends two linked `LedgerEntry` rows (`TRANSFER_OUT`/`TRANSFER_IN`) sharing one `transferId`.

## REST API

| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| POST | `/api/v1/wallet` | `{userId, currency}` | 201 + `WalletResponse` | 400 invalid; 409 duplicate |
| GET | `/api/v1/wallet/{id}/balance` | — | 200 `BalanceResponse` | 404 |
| GET | `/api/v1/wallet/{id}/balance/history?asOf=<ISO-8601>` | — | 200 `BalanceResponse` | 404; 400 malformed `asOf` |
| POST | `/api/v1/wallet/{id}/deposit` | `{amount, reference?}` | 201 `{walletId, entryId, newBalance}` | 404; 400 non-positive |
| POST | `/api/v1/wallet/{id}/withdrawal` | `{amount}` | 200/201 | 404; 400; **422** insufficient funds |
| POST | `/api/v1/transfer` | `{sourceWalletId, destinationWalletId, amount}` | 201 `TransferResponse` | 404; 400 same-wallet/non-positive; 422 insufficient funds |

Transfers are **wallet-to-wallet within this application only** — both `sourceWalletId` and `destinationWalletId` must already exist as wallets managed by this service (validated with 404 otherwise). No external/third-party transfer target and no pending/confirmation `TransferStatus` — see assumption in Context.

`GlobalExceptionHandler` (`@RestControllerAdvice`) uses Spring's `ProblemDetail` (RFC 7807). Mapping: `WalletNotFoundException`→404, `InsufficientFundsException`→422, `DuplicateWalletException`→409, `SameWalletTransferException`/`InvalidAmountException`/bean-validation→400, `ObjectOptimisticLockingFailureException`→409 (defense-in-depth), generic `Exception`→500 (logged with correlation id, generic body).

## Flyway migration — `V1__init_schema.sql`

Postgres-compatible types (`UUID`, `TIMESTAMP WITH TIME ZONE`, `NUMERIC(19,2)`, `CHECK` constraints):

```sql
CREATE TABLE wallet (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL UNIQUE,
    currency    VARCHAR(3) NOT NULL,
    balance     NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE ledger_entry (
    id                 UUID PRIMARY KEY,
    wallet_id          UUID NOT NULL REFERENCES wallet(id),
    entry_type         VARCHAR(20) NOT NULL,
    amount             NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    balance_after      NUMERIC(19,2) NOT NULL CHECK (balance_after >= 0),
    occurred_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    transfer_id        UUID,
    related_wallet_id  UUID,
    reference          VARCHAR(255)
);

CREATE INDEX idx_ledger_entry_wallet_occurred ON ledger_entry (wallet_id, occurred_at DESC, id DESC);
CREATE INDEX idx_ledger_entry_transfer_id ON ledger_entry (transfer_id);
```

`user_id UNIQUE` enforces one-wallet-per-user at the DB level too. `spring.jpa.hibernate.ddl-auto=validate` keeps Flyway as the single source of schema truth.

## Concurrency strategy

**Pessimistic row locking is primary.** `WalletJpaRepository.findByIdForUpdate` uses `@Lock(LockModeType.PESSIMISTIC_WRITE)`, called at the start of every mutating use case's single `@Transactional` method — serializes mutations per wallet, making overdraft races and lost updates impossible. Justification: correctness must be guaranteed outright for a monetary ledger, not merely detected-and-retried; per-wallet contention is expected to be low so the throughput cost is acceptable. `@Version` stays on `WalletJpaEntity` as a defense-in-depth fast-fail signal.

**Deadlock prevention**: transfers always lock wallets in ascending `WalletId` order, independent of source/destination role, giving a total lock order so opposite-direction concurrent transfers (A→B and B→A) can't circular-wait.

## Test plan

- **Domain unit tests** (pure JUnit 5): `MoneyTest`, `WalletTest` (deposit/withdraw math, insufficient funds, invalid amounts).
- **Application unit tests** (JUnit 5 + Mockito, mocked ports): one per use case, including transfer's lock-order and shared-`transferId` behavior, and historical-balance's no-entry-found → zero case.
- **Integration tests** (`@SpringBootTest` + MockMvc + H2, Flyway auto-applied): `WalletControllerIT` — full happy path (create → deposit → withdraw → transfer → balance → history) plus 404/400/422 error paths.
- **Concurrency tests** (highest-value tests, prove the correctness/NFR claims):
  - `ConcurrentWithdrawalIT` — wallet starts at 100.00; 20 concurrent withdraw-10.00 requests via `ExecutorService`; assert exactly 10 succeed, 10 fail with 422, final balance is exactly 0.00.
  - `ConcurrentTransferDeadlockIT` — simultaneous A→B and B→A transfers in a loop; assert both complete within a bounded timeout, no lock timeout/deadlock.

## Deliverable docs

- **README.md** — prerequisites (JDK 21), build/run/test commands, how to switch to a Postgres profile, endpoint table, curl walkthrough, Actuator health mention.
- **DESIGN.md** — hexagonal layer diagram; functional-requirement → endpoint/use-case mapping; how the ledger (snapshot balances + immutability + `transferId` correlation) satisfies auditability; how locking + transaction boundaries + statelessness + Actuator + Flyway-versioned schema satisfy the mission-critical/HA requirement; wiring rationale; records-vs-plain-classes rationale (no Lombok); status-code rationale.
- **TRADEOFFS.md** — **Assumptions**: single currency (BRL), one wallet per user, transfers are wallet-to-wallet within this application only (no third-party/external transfer target, no `TransferStatus`/pending-confirmation workflow), no auth/authz (out of scope), no idempotency keys (with a sketch of the `Idempotency-Key` + dedupe-table approach not implemented), synchronous REST only (no event bus/outbox), `userId` is an opaque pre-existing identifier (no `User` entity), server-side `occurredAt` only, historical balance with no prior entries = zero, no negative balances, wallets start at zero, fixed 2-decimal scale. Plus a time-tracking table and an explicit "skipped due to time" list (ledger-history pagination, rate limiting, auth, multi-currency, idempotency keys, outbox/event publishing, docker-compose Postgres parity demo).
- **PLAN.md** — this implementation plan, committed to the repo with progress checkboxes updated as steps complete.

## Ordered implementation steps

1. Gradle/project setup (wrapper, build.gradle, settings.gradle, `.gitignore`, app skeleton, `application.yml`) + commit `PLAN.md`.
2. Domain layer + its unit tests.
3. Persistence: migration, JPA entities, Spring Data repos (incl. locked-read query), mappers, repository adapters.
4. Application use cases + Mockito unit tests.
5. Web adapters: controllers, DTOs + Bean Validation, `GlobalExceptionHandler`.
6. Integration + concurrency tests.
7. README.md, DESIGN.md, TRADEOFFS.md.
8. Verification pass (below) and fix gaps.

## Verification

```
./gradlew build
./gradlew test        # unit + integration + concurrency
./gradlew bootRun

curl -X POST localhost:8080/api/v1/wallet -H 'Content-Type: application/json' -d '{"userId":"<uuid-1>","currency":"BRL"}'
curl -X POST localhost:8080/api/v1/wallet -H 'Content-Type: application/json' -d '{"userId":"<uuid-2>","currency":"BRL"}'
curl -X POST localhost:8080/api/v1/wallet/<walletId1>/deposit -d '{"amount":"100.00"}'
curl -X POST localhost:8080/api/v1/wallet/<walletId1>/withdrawal -d '{"amount":"30.00"}'
curl -X POST localhost:8080/api/v1/transfer -d '{"sourceWalletId":"<walletId1>","destinationWalletId":"<walletId2>","amount":"20.00"}'
curl localhost:8080/api/v1/wallet/<walletId1>/balance
curl "localhost:8080/api/v1/wallet/<walletId1>/balance/history?asOf=2026-07-09T10:00:00Z"
curl localhost:8080/actuator/health

./gradlew test --tests "*ConcurrentWithdrawalIT*"
./gradlew test --tests "*ConcurrentTransferDeadlockIT*"
```

Success criteria: all Gradle tasks green; concurrent-withdrawal test ends at exactly 0.00 balance (never negative); transfer deadlock test completes without timeout; historical balance query between the deposit and withdrawal returns the post-deposit, pre-withdrawal balance.
