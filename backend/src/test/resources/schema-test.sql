DROP TABLE IF EXISTS audits;
DROP TABLE IF EXISTS guest_sessions;
DROP TABLE IF EXISTS guest_users;

CREATE TABLE guest_users (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE guest_sessions (
    id UUID PRIMARY KEY,
    guest_id UUID NOT NULL REFERENCES guest_users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE audits (
    id UUID PRIMARY KEY,
    guest_id UUID NOT NULL REFERENCES guest_users(id) ON DELETE CASCADE,
    target_url VARCHAR(2048) NOT NULL,
    audited_at TIMESTAMP WITH TIME ZONE NOT NULL,
    scanned_pages INTEGER NOT NULL,
    overall_score INTEGER NOT NULL,
    aeo_score INTEGER NOT NULL,
    a11y_score INTEGER NOT NULL,
    crawl_mode VARCHAR(16) NOT NULL,
    csr_likely BOOLEAN NOT NULL,
    issue_count INTEGER NOT NULL,
    result_json CLOB NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
