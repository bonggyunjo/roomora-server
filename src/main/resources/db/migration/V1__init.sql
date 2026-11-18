-- Roomora Initial Schema (PostgreSQL 16 + pgvector + pg_trgm)
-- Total: 18 tables

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ============================================================
-- 1. Auth & Social (users, user_follow)
-- ============================================================
CREATE TABLE users (
    id                BIGSERIAL PRIMARY KEY,
    email             VARCHAR(100) NOT NULL,
    password          VARCHAR(255),
    nickname          VARCHAR(30)  NOT NULL,
    profile_image_url VARCHAR(500),
    bio               VARCHAR(200),
    provider          VARCHAR(20)  NOT NULL,
    provider_id       VARCHAR(100),
    role              VARCHAR(20)  NOT NULL DEFAULT 'USER',
    follower_count    INTEGER      NOT NULL DEFAULT 0,
    following_count   INTEGER      NOT NULL DEFAULT 0,
    deleted_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_users_nickname_active
    ON users (nickname) WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uq_users_email_provider_active
    ON users (email, provider) WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uq_users_provider_id_active
    ON users (provider, provider_id)
    WHERE provider_id IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE user_follow (
    id           BIGSERIAL PRIMARY KEY,
    follower_id  BIGINT      NOT NULL REFERENCES users (id),
    following_id BIGINT      NOT NULL REFERENCES users (id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_follow UNIQUE (follower_id, following_id),
    CONSTRAINT chk_user_follow_not_self CHECK (follower_id <> following_id)
);

CREATE INDEX idx_user_follow_follower ON user_follow (follower_id, created_at DESC);
CREATE INDEX idx_user_follow_following ON user_follow (following_id, created_at DESC);

-- ============================================================
-- 2. Recipe Community (6 tables)
-- ============================================================
CREATE TABLE recipe_post (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users (id),
    title         VARCHAR(120) NOT NULL,
    content       TEXT         NOT NULL,
    housing_type  VARCHAR(30)  NOT NULL,
    area_pyeong   SMALLINT     NOT NULL CHECK (area_pyeong > 0),
    contract_type VARCHAR(20)  NOT NULL,
    is_no_drill   BOOLEAN      NOT NULL,
    budget        INTEGER      NOT NULL CHECK (budget >= 0),
    style_tag     VARCHAR(30)  NOT NULL,
    thumbnail_url VARCHAR(500),
    like_count    INTEGER      NOT NULL DEFAULT 0,
    scrap_count   INTEGER      NOT NULL DEFAULT 0,
    view_count    INTEGER      NOT NULL DEFAULT 0,
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recipe_post_filter
    ON recipe_post (housing_type, is_no_drill, budget, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_recipe_post_keyset
    ON recipe_post (id DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE recipe_image (
    id         BIGSERIAL PRIMARY KEY,
    post_id    BIGINT       NOT NULL REFERENCES recipe_post (id) ON DELETE CASCADE,
    image_url  VARCHAR(500) NOT NULL,
    sort_order INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX idx_recipe_image_post ON recipe_image (post_id, sort_order ASC);

CREATE TABLE recipe_item (
    id            BIGSERIAL PRIMARY KEY,
    post_id       BIGINT       NOT NULL REFERENCES recipe_post (id) ON DELETE CASCADE,
    item_name     VARCHAR(100) NOT NULL,
    category      VARCHAR(30)  NOT NULL,
    price         INTEGER      NOT NULL DEFAULT 0 CHECK (price >= 0),
    purchase_note VARCHAR(300)
);

CREATE INDEX idx_recipe_item_post ON recipe_item (post_id);

CREATE TABLE recipe_like (
    id         BIGSERIAL PRIMARY KEY,
    post_id    BIGINT      NOT NULL REFERENCES recipe_post (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recipe_like UNIQUE (post_id, user_id)
);

CREATE INDEX idx_recipe_like_user ON recipe_like (user_id, created_at DESC);

CREATE TABLE recipe_scrap (
    id         BIGSERIAL PRIMARY KEY,
    post_id    BIGINT      NOT NULL REFERENCES recipe_post (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recipe_scrap UNIQUE (post_id, user_id)
);

CREATE INDEX idx_recipe_scrap_user ON recipe_scrap (user_id, created_at DESC);

CREATE TABLE recipe_comment (
    id         BIGSERIAL PRIMARY KEY,
    post_id    BIGINT      NOT NULL REFERENCES recipe_post (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    parent_id  BIGINT      REFERENCES recipe_comment (id),
    content    TEXT        NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recipe_comment_post ON recipe_comment (post_id, parent_id, created_at ASC);

-- ============================================================
-- 3. Weekly Contest (6 tables)
-- ============================================================
CREATE TABLE contest_round (
    id                BIGSERIAL PRIMARY KEY,
    round_no          INTEGER      NOT NULL UNIQUE,
    title             VARCHAR(120) NOT NULL,
    theme_description TEXT         NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    start_at          TIMESTAMPTZ  NOT NULL,
    end_at            TIMESTAMPTZ  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE contest_post (
    id            BIGSERIAL PRIMARY KEY,
    round_id      BIGINT       NOT NULL REFERENCES contest_round (id),
    user_id       BIGINT       NOT NULL REFERENCES users (id),
    title         VARCHAR(120) NOT NULL,
    content       TEXT         NOT NULL,
    thumbnail_url VARCHAR(500),
    vote_count    INTEGER      NOT NULL DEFAULT 0,
    scrap_count   INTEGER      NOT NULL DEFAULT 0,
    final_rank    INTEGER,
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_contest_post_round_user_active
    ON contest_post (round_id, user_id) WHERE deleted_at IS NULL;

CREATE INDEX idx_contest_post_ranking
    ON contest_post (round_id, vote_count DESC) WHERE deleted_at IS NULL;

CREATE TABLE contest_image (
    id              BIGSERIAL PRIMARY KEY,
    contest_post_id BIGINT       NOT NULL REFERENCES contest_post (id) ON DELETE CASCADE,
    image_url       VARCHAR(500) NOT NULL,
    sort_order      INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX idx_contest_image_post ON contest_image (contest_post_id, sort_order ASC);

CREATE TABLE contest_vote (
    id              BIGSERIAL PRIMARY KEY,
    round_id        BIGINT      NOT NULL REFERENCES contest_round (id),
    contest_post_id BIGINT      NOT NULL REFERENCES contest_post (id) ON DELETE CASCADE,
    user_id         BIGINT      NOT NULL REFERENCES users (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_contest_vote_round_user UNIQUE (round_id, user_id)
);

CREATE INDEX idx_contest_vote_post ON contest_vote (contest_post_id);

CREATE TABLE contest_scrap (
    id              BIGSERIAL PRIMARY KEY,
    contest_post_id BIGINT      NOT NULL REFERENCES contest_post (id) ON DELETE CASCADE,
    user_id         BIGINT      NOT NULL REFERENCES users (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_contest_scrap UNIQUE (contest_post_id, user_id)
);

CREATE INDEX idx_contest_scrap_user ON contest_scrap (user_id, created_at DESC);

CREATE TABLE contest_comment (
    id              BIGSERIAL PRIMARY KEY,
    contest_post_id BIGINT      NOT NULL REFERENCES contest_post (id) ON DELETE CASCADE,
    user_id         BIGINT      NOT NULL REFERENCES users (id),
    parent_id       BIGINT      REFERENCES contest_comment (id),
    content         TEXT        NOT NULL,
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_contest_comment_post ON contest_comment (contest_post_id, parent_id, created_at ASC);

-- ============================================================
-- 4. Interior Guide & AI RAG (2 tables)
-- ============================================================
CREATE TABLE interior_guide (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(150) NOT NULL,
    category      VARCHAR(30)  NOT NULL,
    style_tag     VARCHAR(30),
    content       TEXT         NOT NULL,
    color_palette VARCHAR(200),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE rag_document_chunk (
    id          BIGSERIAL PRIMARY KEY,
    source_type VARCHAR(20)  NOT NULL,
    source_id   BIGINT       NOT NULL,
    chunk_index INTEGER      NOT NULL,
    content     TEXT         NOT NULL,
    metadata    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    embedding   vector(1536) NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rag_chunk_source UNIQUE (source_type, source_id, chunk_index)
);

CREATE INDEX idx_rag_chunk_embedding_hnsw
    ON rag_document_chunk USING hnsw (embedding vector_cosine_ops);

CREATE INDEX idx_rag_chunk_content_trgm
    ON rag_document_chunk USING gin (content gin_trgm_ops);

CREATE INDEX idx_rag_chunk_source
    ON rag_document_chunk (source_type, source_id);

CREATE INDEX idx_rag_chunk_metadata_gin
    ON rag_document_chunk USING gin (metadata);

-- ============================================================
-- 5. AI Checklist (2 tables)
-- ============================================================
CREATE TABLE ai_checklist (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users (id),
    title         VARCHAR(150) NOT NULL,
    query_summary VARCHAR(300),
    target_budget INTEGER,
    housing_type  VARCHAR(30),
    is_no_drill   BOOLEAN,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_checklist_user ON ai_checklist (user_id, created_at DESC);

CREATE TABLE ai_checklist_item (
    id             BIGSERIAL PRIMARY KEY,
    checklist_id   BIGINT       NOT NULL REFERENCES ai_checklist (id) ON DELETE CASCADE,
    step_order     INTEGER      NOT NULL,
    category       VARCHAR(30)  NOT NULL,
    content        VARCHAR(300) NOT NULL,
    estimated_cost INTEGER      NOT NULL DEFAULT 0 CHECK (estimated_cost >= 0),
    is_checked     BOOLEAN      NOT NULL DEFAULT FALSE,
    memo           VARCHAR(300)
);

CREATE INDEX idx_ai_checklist_item_order ON ai_checklist_item (checklist_id, step_order ASC);
