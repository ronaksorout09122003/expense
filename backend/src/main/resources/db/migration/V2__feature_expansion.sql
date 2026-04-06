ALTER TABLE recurring_rule ADD COLUMN to_account_id UUID;
ALTER TABLE recurring_rule ADD COLUMN note VARCHAR(500);
ALTER TABLE recurring_rule ADD COLUMN reference_no VARCHAR(100);
ALTER TABLE recurring_rule ADD COLUMN location_text VARCHAR(180);
ALTER TABLE recurring_rule ADD COLUMN interval_days INTEGER NOT NULL DEFAULT 30;
ALTER TABLE recurring_rule ADD COLUMN due_date_offset_days INTEGER NOT NULL DEFAULT 0;
ALTER TABLE recurring_rule ADD COLUMN remind_days_before INTEGER NOT NULL DEFAULT 0;
ALTER TABLE recurring_rule ADD COLUMN auto_create BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE recurring_rule ADD COLUMN last_run_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE recurring_rule ADD COLUMN is_shared BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE recurring_rule ADD COLUMN shared_participant_count INTEGER NOT NULL DEFAULT 1;

CREATE TABLE budget (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(140) NOT NULL,
    category_id UUID,
    amount_limit NUMERIC(19, 2) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    alert_percent INTEGER NOT NULL DEFAULT 80,
    rollover_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_budget_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_budget_category FOREIGN KEY (category_id) REFERENCES category (id)
);

CREATE TABLE savings_goal (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(140) NOT NULL,
    target_amount NUMERIC(19, 2) NOT NULL,
    saved_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    target_date DATE,
    account_id UUID,
    notes VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_goal_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_goal_account FOREIGN KEY (account_id) REFERENCES account (id)
);

CREATE TABLE household (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL,
    name VARCHAR(140) NOT NULL,
    invite_code VARCHAR(24) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_household_owner FOREIGN KEY (owner_user_id) REFERENCES app_user (id),
    CONSTRAINT uk_household_invite_code UNIQUE (invite_code)
);

CREATE TABLE household_member (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL,
    user_id UUID NOT NULL,
    member_role VARCHAR(30) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_household_member_household FOREIGN KEY (household_id) REFERENCES household (id),
    CONSTRAINT fk_household_member_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT uk_household_member UNIQUE (household_id, user_id)
);

ALTER TABLE txn_attachment ADD COLUMN label VARCHAR(140);
ALTER TABLE txn_attachment ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE txn ADD COLUMN recurring_rule_id UUID;
ALTER TABLE txn ADD COLUMN household_id UUID;
ALTER TABLE txn ADD COLUMN is_shared BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE txn ADD COLUMN shared_participant_count INTEGER NOT NULL DEFAULT 1;

ALTER TABLE recurring_rule ADD COLUMN household_id UUID;

ALTER TABLE recurring_rule ADD CONSTRAINT fk_recurring_rule_user FOREIGN KEY (user_id) REFERENCES app_user (id);
ALTER TABLE recurring_rule ADD CONSTRAINT fk_recurring_rule_account FOREIGN KEY (account_id) REFERENCES account (id);
ALTER TABLE recurring_rule ADD CONSTRAINT fk_recurring_rule_to_account FOREIGN KEY (to_account_id) REFERENCES account (id);
ALTER TABLE recurring_rule ADD CONSTRAINT fk_recurring_rule_category FOREIGN KEY (category_id) REFERENCES category (id);
ALTER TABLE recurring_rule ADD CONSTRAINT fk_recurring_rule_counterparty FOREIGN KEY (counterparty_id) REFERENCES counterparty (id);
ALTER TABLE recurring_rule ADD CONSTRAINT fk_recurring_rule_household FOREIGN KEY (household_id) REFERENCES household (id);

ALTER TABLE txn ADD CONSTRAINT fk_txn_recurring_rule FOREIGN KEY (recurring_rule_id) REFERENCES recurring_rule (id);
ALTER TABLE txn ADD CONSTRAINT fk_txn_household FOREIGN KEY (household_id) REFERENCES household (id);

CREATE INDEX idx_budget_user_period ON budget (user_id, period_start, period_end);
CREATE INDEX idx_goal_user_active ON savings_goal (user_id, is_active);
CREATE INDEX idx_household_member_user ON household_member (user_id, is_active);
CREATE INDEX idx_household_member_household ON household_member (household_id, is_active);
CREATE INDEX idx_recurring_rule_user_active ON recurring_rule (user_id, is_active, next_run_at);
CREATE INDEX idx_txn_household_transaction_at ON txn (household_id, transaction_at DESC);
