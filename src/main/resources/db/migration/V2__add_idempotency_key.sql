CREATE TABLE idempotency_key (
    idempotency_key  VARCHAR(255) PRIMARY KEY,
    request_path     VARCHAR(255) NOT NULL,
    response_status  INT,
    response_body    TEXT,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL
);
