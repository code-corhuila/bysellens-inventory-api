CREATE TABLE products (
    id UUID PRIMARY KEY,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    category TEXT NOT NULL,
    shade TEXT,
    purchase_price NUMERIC NOT NULL CHECK (purchase_price >= 0),
    sale_price NUMERIC NOT NULL CHECK (sale_price > 0),
    initial_stock INTEGER NOT NULL CHECK (initial_stock >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uk_products_code UNIQUE (code)
);

CREATE INDEX idx_products_active_code_id
    ON products (code ASC, id ASC)
    WHERE deleted_at IS NULL;

CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    routing_key VARCHAR(128) NOT NULL,
    event_payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_pending_created
    ON outbox_events (created_at ASC)
    WHERE published_at IS NULL;