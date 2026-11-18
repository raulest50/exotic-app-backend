CREATE TABLE agent_assistant_usage (
    response_id VARCHAR(128) PRIMARY KEY,
    quota_id VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    input_tokens BIGINT NOT NULL CHECK (input_tokens >= 0),
    cached_input_tokens BIGINT NOT NULL CHECK (cached_input_tokens >= 0),
    output_tokens BIGINT NOT NULL CHECK (output_tokens >= 0),
    estimated_cost NUMERIC(20,10) NOT NULL CHECK (estimated_cost >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_assistant_cached_tokens CHECK (cached_input_tokens <= input_tokens)
);

CREATE INDEX idx_agent_assistant_usage_quota ON agent_assistant_usage (quota_id);
