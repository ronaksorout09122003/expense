CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    mobile VARCHAR(30),
    email VARCHAR(190) NOT NULL,
    currency_code VARCHAR(8) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    pin_hash VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_user_email UNIQUE (email)
);

CREATE TABLE user_setting (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    default_account_id UUID,
    default_currency VARCHAR(8) NOT NULL,
    date_format VARCHAR(30) NOT NULL,
    biometric_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    reminder_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    session_timeout_minutes INTEGER NOT NULL DEFAULT 30,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_setting_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT uk_user_setting_user UNIQUE (user_id)
);

CREATE TABLE account (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    account_type VARCHAR(30) NOT NULL,
    opening_balance NUMERIC(19, 2) NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    accent_color VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_account_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE TABLE category (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    parent_category_id UUID,
    category_kind VARCHAR(30) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_category_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_category_id) REFERENCES category (id)
);

CREATE TABLE counterparty (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    counterparty_type VARCHAR(30) NOT NULL,
    phone VARCHAR(30),
    notes VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_counterparty_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE TABLE txn (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    entry_source VARCHAR(30) NOT NULL,
    from_account_id UUID,
    to_account_id UUID,
    category_id UUID,
    counterparty_id UUID,
    amount NUMERIC(19, 2) NOT NULL,
    transaction_at TIMESTAMP WITH TIME ZONE NOT NULL,
    note VARCHAR(500),
    location_text VARCHAR(180),
    reference_no VARCHAR(100),
    due_date DATE,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_txn_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_txn_from_account FOREIGN KEY (from_account_id) REFERENCES account (id),
    CONSTRAINT fk_txn_to_account FOREIGN KEY (to_account_id) REFERENCES account (id),
    CONSTRAINT fk_txn_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT fk_txn_counterparty FOREIGN KEY (counterparty_id) REFERENCES counterparty (id)
);

CREATE TABLE txn_settlement (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    base_txn_id UUID NOT NULL,
    settlement_txn_id UUID NOT NULL,
    settled_amount NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_txn_settlement_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_txn_settlement_base FOREIGN KEY (base_txn_id) REFERENCES txn (id),
    CONSTRAINT fk_txn_settlement_settlement FOREIGN KEY (settlement_txn_id) REFERENCES txn (id)
);

CREATE TABLE recurring_rule (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(140) NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    account_id UUID,
    category_id UUID,
    counterparty_id UUID,
    frequency_type VARCHAR(30) NOT NULL,
    next_run_at TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE txn_attachment (
    id UUID PRIMARY KEY,
    txn_id UUID NOT NULL,
    file_url VARCHAR(500) NOT NULL,
    mime_type VARCHAR(120),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_txn_attachment_txn FOREIGN KEY (txn_id) REFERENCES txn (id)
);

CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    entity_name VARCHAR(80) NOT NULL,
    entity_id UUID NOT NULL,
    action_name VARCHAR(80) NOT NULL,
    old_value CLOB,
    new_value CLOB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_log_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE INDEX idx_account_user_active ON account (user_id, is_active);
CREATE INDEX idx_category_user_parent ON category (user_id, parent_category_id);
CREATE INDEX idx_counterparty_user_type ON counterparty (user_id, counterparty_type);
CREATE INDEX idx_txn_user_transaction_at ON txn (user_id, transaction_at DESC);
CREATE INDEX idx_txn_user_type ON txn (user_id, transaction_type);
CREATE INDEX idx_txn_settlement_base ON txn_settlement (base_txn_id);
CREATE INDEX idx_txn_settlement_settlement ON txn_settlement (settlement_txn_id);
