ALTER TABLE coffee_chat ADD COLUMN accept_memo VARCHAR(255) NULL AFTER responded_at;

CREATE INDEX idx_coffee_chat_state_created ON coffee_chat (state, created_at);
