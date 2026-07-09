# Wallet Service

A wallet microservice for RecargaPay's take-home assignment: create wallets, deposit, withdraw, transfer between users, and query current or historical balances.

See [DESIGN.md](DESIGN.md) for the architecture and how it meets the functional/non-functional requirements, and [TRADEOFFS.md](TRADEOFFS.md) for documented assumptions and compromises. [PLAN.md](PLAN.md) is the implementation plan used to build this.

## Prerequisites

- JDK 21
- No Docker/external database required for the default profile — it runs against an in-memory H2 database.

## Build

```bash
./gradlew build
```

## Test

```bash
./gradlew test
```

This runs domain unit tests, application-layer unit tests (Mockito-mocked ports), full-stack integration tests (`@SpringBootTest` + MockMvc + H2/Flyway), and two concurrency tests that prove the locking strategy prevents overdrafts and deadlocks.

## Run

```bash
./gradlew bootRun
```

The service starts on `http://localhost:8080` with an in-memory H2 database (schema created by Flyway on startup, reset on every restart).

### Running against Postgres

`src/main/resources/application-prod.yml` is an example production profile pointing at Postgres. To use it:

1. Add the Postgres JDBC driver dependency in `build.gradle`: `runtimeOnly 'org.postgresql:postgresql'`.
2. Provide `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` environment variables (or edit the YAML directly).
3. Run with `./gradlew bootRun --args='--spring.profiles.active=prod'`.

The schema (`src/main/resources/db/migration/V1__init_schema.sql`) uses only Postgres-compatible SQL (UUID, TIMESTAMP WITH TIME ZONE, NUMERIC, CHECK constraints), so no migration changes are needed to switch databases.

## API

Base path: `/api/v1`

| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| POST | `/wallet` | `{"userId":"<uuid>"}` | 201 + wallet | 400 invalid; 409 duplicate |
| GET | `/wallet/{id}/balance` | — | 200 + balance | 404 |
| GET | `/wallet/{id}/balance/history?asOf=<ISO-8601>` | — | 200 + balance | 404; 400 malformed `asOf` |
| POST | `/wallet/{id}/deposit` | `{"amount":"100.00","reference":"optional"}` | 201 + new balance | 404; 400 non-positive |
| POST | `/wallet/{id}/withdrawal` | `{"amount":"50.00"}` | 200 + new balance | 404; 400; 422 insufficient funds |
| POST | `/transfer` | `{"sourceWalletId":...,"destinationWalletId":...,"amount":"20.00"}` | 201 + both balances | 404; 400 same-wallet/non-positive; 422 insufficient funds |

Errors are returned as RFC 7807 `application/problem+json` bodies.

Wallets are always created in BRL (single-currency assumption — see TRADEOFFS.md), so `POST /wallet` only needs a `userId`.

## Curl walkthrough

```bash
# create two wallets
curl -s -X POST localhost:8080/api/v1/wallet -H 'Content-Type: application/json' \
  -d '{"userId":"11111111-1111-1111-1111-111111111111"}'
curl -s -X POST localhost:8080/api/v1/wallet -H 'Content-Type: application/json' \
  -d '{"userId":"22222222-2222-2222-2222-222222222222"}'

# (substitute the walletId values returned above)
WALLET1=<uuid>
WALLET2=<uuid>

# deposit and withdraw
curl -s -X POST localhost:8080/api/v1/wallet/$WALLET1/deposit -H 'Content-Type: application/json' \
  -d '{"amount":"100.00"}'
curl -s -X POST localhost:8080/api/v1/wallet/$WALLET1/withdrawal -H 'Content-Type: application/json' \
  -d '{"amount":"30.00"}'

# transfer
curl -s -X POST localhost:8080/api/v1/transfer -H 'Content-Type: application/json' \
  -d "{\"sourceWalletId\":\"$WALLET1\",\"destinationWalletId\":\"$WALLET2\",\"amount\":\"20.00\"}"

# balances
curl -s localhost:8080/api/v1/wallet/$WALLET1/balance
curl -s "localhost:8080/api/v1/wallet/$WALLET1/balance/history?asOf=2026-07-09T10:00:00Z"

# health
curl -s localhost:8080/actuator/health
```

## Project layout

Hexagonal (ports & adapters) + DDD. See `PLAN.md`/`DESIGN.md` for the full rationale.

```
src/main/java/com/recargapay/walletservice/
├── domain/          # Wallet aggregate, value objects, exceptions, repository ports — no framework imports
├── application/     # Use cases (one per functional requirement) and their command/view DTOs
├── adapters/
│   ├── in/web/      # REST controllers, request/response DTOs, global exception handler
│   └── out/persistence/  # JPA entities, Spring Data repositories, mappers, port implementations
└── config/          # Spring configuration (e.g. the injectable Clock bean)
```
