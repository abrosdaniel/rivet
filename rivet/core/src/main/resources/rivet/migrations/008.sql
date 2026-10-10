CREATE TABLE chat_history(id BIGSERIAL PRIMARY KEY, channel TEXT NOT NULL, at BIGINT NOT NULL, body TEXT NOT NULL, recipients TEXT[] NOT NULL DEFAULT '{}');
CREATE INDEX chat_history_channel ON chat_history(channel,id DESC);
CREATE INDEX chat_history_time ON chat_history(at);
