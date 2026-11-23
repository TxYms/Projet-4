-- DataShare : schéma initial (MVP)
-- Appliqué automatiquement par Flyway au démarrage de l'API.

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE stored_files (
    id             UUID         PRIMARY KEY,
    owner_id       UUID         NOT NULL,
    original_name  VARCHAR(255) NOT NULL,
    content_type   VARCHAR(255) NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    storage_key    VARCHAR(100) NOT NULL,
    download_token VARCHAR(64)  NOT NULL,
    password_hash  VARCHAR(100),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    content_purged BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_stored_files_token UNIQUE (download_token),
    CONSTRAINT uk_stored_files_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_stored_files_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_stored_files_size CHECK (size_bytes >= 0),
    CONSTRAINT ck_stored_files_expiry CHECK (expires_at > created_at)
);

-- Historique d'un utilisateur, trié par date d'envoi
CREATE INDEX idx_stored_files_owner_created ON stored_files (owner_id, created_at DESC);
-- Tâche de purge des fichiers expirés
CREATE INDEX idx_stored_files_expires_at ON stored_files (expires_at);
