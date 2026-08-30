-- ═════════════════════════════════════════════════════════════════════════════
-- V1 — initial schema.
--
-- Ported from the seven Firestore collections the single-file site used
-- (members, committees, events, gallery, achievements, messages, settings).
-- Three things change on the way across, on purpose:
--
--   1. member.committee_id is a real foreign key. Firestore stored the committee
--      NAME on each member, so renaming a committee silently orphaned its members.
--   2. Dates are DATE columns with a separate display label. Firestore stored
--      'Oct 10-12, 2024' as a string, so "is this event past?" was a regex over
--      prose. Ordering and the upcoming/past split are now the database's job.
--   3. Responsibilities are rows, not a JSON array, so they can be ordered and
--      edited individually.
-- ═════════════════════════════════════════════════════════════════════════════

CREATE TABLE admin_user (
    id              UUID         PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    password_hash   VARCHAR(120) NOT NULL,
    failed_attempts INT          NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ── Committees ───────────────────────────────────────────────────────────────
-- The id is the human-readable slug the old site used ('advisory', 'technical'),
-- because the public site deep-links to #committee-<id> and those URLs are already
-- shared. A surrogate UUID here would break every existing link for no gain.
CREATE TABLE committee (
    id                     VARCHAR(64)  PRIMARY KEY,
    display_order          INT          NOT NULL,
    type                   VARCHAR(16)  NOT NULL,
    name                   VARCHAR(160) NOT NULL,
    icon                   VARCHAR(16),
    gradient               VARCHAR(160),
    size_label             VARCHAR(64),
    badge                  VARCHAR(64),
    coord_label            VARCHAR(96),
    coordinator            VARCHAR(160),
    coordinator_sub        VARCHAR(160),
    coordinator_photo      TEXT,
    coordinator_photo_id   VARCHAR(255),
    coord2_label           VARCHAR(96),
    coordinator2           VARCHAR(160),
    coordinator2_photo     TEXT,
    coordinator2_photo_id  VARCHAR(255),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT committee_type_valid CHECK (type IN ('advisory', 'executive', 'functional'))
);
CREATE INDEX idx_committee_order ON committee (display_order);

CREATE TABLE committee_responsibility (
    committee_id VARCHAR(64) NOT NULL REFERENCES committee (id) ON DELETE CASCADE,
    position     INT         NOT NULL,
    description  TEXT        NOT NULL,
    PRIMARY KEY (committee_id, position)
);

-- ── Members ──────────────────────────────────────────────────────────────────
CREATE TABLE member (
    id              UUID         PRIMARY KEY,
    name            VARCHAR(160) NOT NULL,
    role            VARCHAR(120) NOT NULL,
    -- ON DELETE SET NULL, not CASCADE: deleting a committee must not silently
    -- delete the students on it. They surface as unassigned in the dashboard.
    committee_id    VARCHAR(64)  REFERENCES committee (id) ON DELETE SET NULL,
    academic_year   VARCHAR(32),
    linkedin_url    VARCHAR(500),
    github_url      VARCHAR(500),
    email           VARCHAR(255),
    photo_url       TEXT,
    photo_public_id VARCHAR(255),
    display_order   INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_member_committee ON member (committee_id, display_order);

-- ── Events ───────────────────────────────────────────────────────────────────
-- No status column: upcoming vs past is derived from starts_on against today.
-- Storing it would mean a nightly job to keep it honest, and the old site's
-- equivalent (autoSortEvents) had to re-run on every page load to compensate.
CREATE TABLE event (
    id               UUID         PRIMARY KEY,
    title            VARCHAR(200) NOT NULL,
    starts_on        DATE         NOT NULL,
    ends_on          DATE,
    date_label       VARCHAR(120),
    tag              VARCHAR(64),
    emoji            VARCHAR(16),
    description      TEXT,
    link_url         VARCHAR(500),
    banner_url       TEXT,
    banner_public_id VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT event_dates_ordered CHECK (ends_on IS NULL OR ends_on >= starts_on)
);
CREATE INDEX idx_event_starts_on ON event (starts_on DESC);

-- ── Gallery ──────────────────────────────────────────────────────────────────
-- album_id groups a multi-file upload into one tile, exactly as the old
-- grp_<timestamp> field did — kept because the album lightbox depends on it.
CREATE TABLE gallery_item (
    id          UUID         PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    description TEXT,
    category    VARCHAR(64),
    taken_on    DATE,
    url         TEXT         NOT NULL,
    public_id   VARCHAR(255),
    album_id    VARCHAR(64),
    album_title VARCHAR(200),
    album_index INT,
    album_total INT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_gallery_category ON gallery_item (category);
CREATE INDEX idx_gallery_album    ON gallery_item (album_id, album_index);
CREATE INDEX idx_gallery_created  ON gallery_item (created_at DESC);

-- ── Achievements ─────────────────────────────────────────────────────────────
CREATE TABLE achievement (
    id              UUID         PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,
    student         VARCHAR(160) NOT NULL,
    category        VARCHAR(64),
    achieved_on     DATE,
    description     TEXT,
    photo_url       TEXT,
    photo_public_id VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_achievement_category ON achievement (category);
CREATE INDEX idx_achievement_date     ON achievement (achieved_on DESC NULLS LAST);

-- ── Contact messages ─────────────────────────────────────────────────────────
CREATE TABLE contact_message (
    id             UUID         PRIMARY KEY,
    name           VARCHAR(160) NOT NULL,
    email          VARCHAR(255) NOT NULL,
    subject        VARCHAR(200),
    body           TEXT         NOT NULL,
    is_read        BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Hashed, not raw: enough to rate-limit a sender, not enough to be a log of
    -- who visited the site.
    sender_ip_hash VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Present because marking a message read is an update, and because every entity
    -- extending BaseEntity carries both timestamps.
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_message_created ON contact_message (created_at DESC);
CREATE INDEX idx_message_unread  ON contact_message (is_read) WHERE is_read = FALSE;

-- ── Site settings ────────────────────────────────────────────────────────────
-- One row, enforced by the check constraint. The old site had settings/site and
-- settings/announcement as two documents and therefore two round trips on every
-- page load; the public site needs both, always, so they are one row and one GET.
CREATE TABLE site_settings (
    id                      SMALLINT     PRIMARY KEY DEFAULT 1,
    phone                   VARCHAR(64),
    email                   VARCHAR(255),
    address                 TEXT,
    website                 VARCHAR(255),
    linkedin                VARCHAR(500),
    instagram               VARCHAR(500),
    notification_email      VARCHAR(255),
    about_title             VARCHAR(200),
    about_description       TEXT,
    feature1_title          VARCHAR(120),
    feature1_description    TEXT,
    feature2_title          VARCHAR(120),
    feature2_description    TEXT,
    feature3_title          VARCHAR(120),
    feature3_description    TEXT,
    feature4_title          VARCHAR(120),
    feature4_description    TEXT,
    announcement_text       TEXT,
    announcement_expires_at TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT site_settings_singleton CHECK (id = 1)
);
