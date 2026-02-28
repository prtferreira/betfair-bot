-- Core schema for Betfair odds time-series ingestion and analytics.
-- Designed for PostgreSQL and compatible with TimescaleDB (optional).

CREATE TABLE IF NOT EXISTS matches (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) UNIQUE,
    home_team VARCHAR(255) NOT NULL,
    away_team VARCHAR(255) NOT NULL,
    competition VARCHAR(255),
    kickoff_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS markets (
    id BIGSERIAL PRIMARY KEY,
    market_id VARCHAR(32) NOT NULL UNIQUE,
    match_id BIGINT NOT NULL REFERENCES matches (id) ON DELETE CASCADE,
    market_type VARCHAR(64) NOT NULL,
    market_name VARCHAR(255),
    status VARCHAR(16),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS runners (
    id BIGSERIAL PRIMARY KEY,
    market_id VARCHAR(32) NOT NULL REFERENCES markets (market_id) ON DELETE CASCADE,
    runner_id BIGINT NOT NULL,
    runner_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (market_id, runner_id)
);

CREATE TABLE IF NOT EXISTS source_files (
    id BIGSERIAL PRIMARY KEY,
    path TEXT NOT NULL UNIQUE,
    file_date DATE,
    checksum_sha256 CHAR(64),
    file_size_bytes BIGINT,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS odds_ticks (
    id BIGSERIAL PRIMARY KEY,
    ts TIMESTAMPTZ NOT NULL,
    game_minute SMALLINT NOT NULL CHECK (game_minute >= 0 AND game_minute <= 130),
    market_id VARCHAR(32) NOT NULL,
    runner_id BIGINT NOT NULL,
    market_status VARCHAR(16),
    back_odds NUMERIC(10, 2),
    lay_odds NUMERIC(10, 2),
    source_file_id BIGINT REFERENCES source_files (id) ON DELETE SET NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_odds_ticks_market FOREIGN KEY (market_id)
        REFERENCES markets (market_id) ON DELETE CASCADE,
    CONSTRAINT fk_odds_ticks_runner FOREIGN KEY (market_id, runner_id)
        REFERENCES runners (market_id, runner_id) ON DELETE CASCADE,
    CONSTRAINT uq_odds_tick UNIQUE (ts, market_id, runner_id)
);

-- Query-optimized indexes (most common read paths).
CREATE INDEX IF NOT EXISTS idx_markets_match_type
    ON markets (match_id, market_type);

CREATE INDEX IF NOT EXISTS idx_runners_market
    ON runners (market_id);

CREATE INDEX IF NOT EXISTS idx_odds_ticks_market_runner_ts_desc
    ON odds_ticks (market_id, runner_id, ts DESC);

CREATE INDEX IF NOT EXISTS idx_odds_ticks_market_ts_desc
    ON odds_ticks (market_id, ts DESC);

CREATE INDEX IF NOT EXISTS idx_odds_ticks_ts_desc
    ON odds_ticks (ts DESC);

CREATE INDEX IF NOT EXISTS idx_odds_ticks_source_file
    ON odds_ticks (source_file_id);

-- Optional TimescaleDB conversion:
-- If TimescaleDB is installed and create_hypertable exists, convert odds_ticks.
DO $$
BEGIN
    PERFORM create_hypertable('odds_ticks', 'ts', if_not_exists => TRUE);
EXCEPTION
    WHEN undefined_function THEN
        NULL;
END $$;
