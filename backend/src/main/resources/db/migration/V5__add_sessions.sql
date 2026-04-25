-- セッション (= 1 端末のログイン状態) を表すテーブル。
-- AT のクレーム sid に session.id を埋め込み、ログアウトやパスワード変更時に
-- セッション単位での失効を可能にする。
CREATE TABLE sessions (
    id            VARCHAR(36) PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sessions_user_id ON sessions(user_id);

-- refresh_tokens を session_id 参照に組み替える。
-- 既存データはサンプル運用のみで保持価値が無いため DROP + CREATE する。
DROP TABLE refresh_tokens;

CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    session_id  VARCHAR(36) NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_session_id ON refresh_tokens(session_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
