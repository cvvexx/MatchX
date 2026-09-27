CREATE TABLE users
(
    id         UUID                        NOT NULL,
    email      VARCHAR(255)                NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE accounts
(
    id         UUID                        NOT NULL,
    user_id    UUID                        NOT NULL,
    asset      VARCHAR(20)                 NOT NULL,
    available  NUMERIC(24, 8)              NOT NULL DEFAULT 0,
    locked     NUMERIC(24, 8)              NOT NULL DEFAULT 0,
    version    BIGINT                      NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT fk_accounts_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_accounts_user_asset ON accounts (user_id, asset);

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_user_id FOREIGN KEY (user_id) REFERENCES users (id);