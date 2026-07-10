# Wallet Service

A wallet microservice for RecargaPay's take-home assignment: create wallets, deposit, withdraw, transfer between users, and query current or historical balances.

See [DESIGN.md](DESIGN.md) for the architecture and how it meets the functional/non-functional requirements, and [TRADEOFFS.md](TRADEOFFS.md) for documented assumptions and compromises.

## Prerequisites

- JDK 21
- No Docker/external database required for the default profile — it runs against an in-memory H2 database.

Alternatively, open this repo in **GitHub Codespaces** (Code → Codespaces → Create codespace). `.devcontainer/docker-compose.yml` provisions two containers: the JDK 21 dev environment and a real Postgres 16 database. The codespace sets `SPRING_PROFILES_ACTIVE=prod`, so `./gradlew bootRun` there talks to the real Postgres automatically (not H2) — production-parity out of the box. Locally, outside a codespace, nothing changes: `bootRun` still defaults to H2 unless you opt into `prod` yourself. Either way `./gradlew test` always runs against an isolated H2 instance (see "Test" below) — tests never touch Postgres, even inside the codespace.

## Build

```bash
./gradlew build
```

## Test

```bash
./gradlew test
```

This runs domain unit tests, application-layer unit tests (Mockito-mocked ports), full-stack integration tests (`@SpringBootTest` + MockMvc + H2/Flyway), and two concurrency tests that prove the locking strategy prevents overdrafts and deadlocks. Every `@SpringBootTest` is pinned to `@ActiveProfiles("test")` (`src/test/resources/application-test.yml`, an isolated H2 instance), and the Gradle `test` task additionally clears `SPRING_PROFILES_ACTIVE` for the forked test JVM — so tests always run against a fresh, isolated database, even inside the devcontainer where `SPRING_PROFILES_ACTIVE=prod` is set ambiently for `bootRun`.

## Run

```bash
./gradlew bootRun
```

The service starts on `http://localhost:8080` with an in-memory H2 database (schema created by Flyway on startup, reset on every restart).

### Running against Postgres

`src/main/resources/application-prod.yml` is the production profile pointing at Postgres (the Postgres JDBC driver is always on the classpath, so no dependency changes are needed).

- **In a codespace**: already wired up — `./gradlew bootRun` connects to the `db` container automatically (see `.devcontainer/docker-compose.yml`). Connect to it directly from your own machine with `psql -h localhost -U wallet_service -d walletdb` (password `wallet_service`) since port 5432 is forwarded.
- **Locally, against your own Postgres**: provide `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` environment variables (or edit the YAML directly), then run with `./gradlew bootRun --args='--spring.profiles.active=prod'`.

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

All four mutating endpoints (`POST /wallet`, `/deposit`, `/withdrawal`, `/transfer`) accept an optional `Idempotency-Key` header. Send the same key on a retry (e.g. after a timeout) and you'll get back the original response instead of the operation being applied twice — see DESIGN.md for how this is guaranteed atomically. Omit the header and nothing changes: every request is applied independently, as before.

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

# idempotent retry: same key twice only deposits once
curl -s -X POST localhost:8080/api/v1/wallet/$WALLET1/deposit -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: retry-demo-1' -d '{"amount":"50.00"}'
curl -s -X POST localhost:8080/api/v1/wallet/$WALLET1/deposit -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: retry-demo-1' -d '{"amount":"50.00"}'  # returns the same response, balance unchanged

# health (liveness = process alive; readiness = process alive AND db reachable)
curl -s localhost:8080/actuator/health
curl -s localhost:8080/actuator/health/liveness
curl -s localhost:8080/actuator/health/readiness
```

## Project layout

Hexagonal (ports & adapters) + DDD. See `DESIGN.md` for the full rationale.

```
src/main/java/com/recargapay/walletservice/
├── domain/          # Wallet aggregate, value objects, exceptions, repository ports — no framework imports
├── application/     # Use cases (one per functional requirement) and their command/view DTOs
├── adapters/
│   ├── in/web/      # REST controllers, request/response DTOs, global exception handler
│   └── out/persistence/  # JPA entities, Spring Data repositories, mappers, port implementations
└── config/          # Spring configuration (e.g. the injectable Clock bean)
```
