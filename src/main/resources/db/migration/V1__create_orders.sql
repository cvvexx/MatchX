CREATE TABLE orders (
    id              UUID           NOT NULL,
    user_id         UUID           NOT NULL,
    symbol          VARCHAR(20)    NOT NULL,
    side            VARCHAR(8)     NOT NULL,
    type            VARCHAR(16)    NOT NULL,
    price           NUMERIC(24, 8),
    quantity        NUMERIC(24, 8) NOT NULL,
    filled_quantity NUMERIC(24, 8) NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    created_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id)
);

CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_orders_symbol_status ON orders (symbol, status);

ALTER TABLE orders
    ADD CONSTRAINT orders_side_check CHECK (side IN ('BUY', 'SELL')),
    ADD CONSTRAINT orders_type_check CHECK (type IN ('LIMIT', 'MARKET')),
    ADD CONSTRAINT orders_status_check CHECK (status IN ('NEW', 'PARTIALLY_FILLED', 'FILLED', 'CANCELLED', 'REJECTED'));
