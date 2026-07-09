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
