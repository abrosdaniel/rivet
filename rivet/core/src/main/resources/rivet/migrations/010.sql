ALTER TABLE notices ADD COLUMN expires_at BIGINT NOT NULL DEFAULT 0 CHECK (expires_at >= 0);
CREATE INDEX notices_expiry ON notices(expires_at,id) WHERE expires_at > 0;
