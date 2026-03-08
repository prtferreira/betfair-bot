-- Market-level snapshots captured from selected games polling.
-- Common shape for HALF_TIME, HALF_TIME_SCORE, FULL_TIME_SCORE and OVER_UNDER_* markets.

CREATE TABLE IF NOT EXISTS market_half_time_snapshot (
  id BIGSERIAL PRIMARY KEY,
  updated_at_utc TIMESTAMPTZ NOT NULL,
  match_start_at_utc TIMESTAMPTZ NULL,
  game_minute BIGINT NULL,
  betfair_event_id VARCHAR(32) NOT NULL,
  betfair_market_id VARCHAR(32) NOT NULL,
  statpal_main_id VARCHAR(64) NULL,
  home_team VARCHAR(120) NULL,
  away_team VARCHAR(120) NULL,
  market_status VARCHAR(32) NULL,
  selection_id BIGINT NULL,
  selection_name VARCHAR(128) NULL,
  back_odds NUMERIC(10,3) NULL,
  lay_odds NUMERIC(10,3) NULL,
  source VARCHAR(32) NOT NULL DEFAULT 'betfair-selected-games'
);
CREATE INDEX IF NOT EXISTS idx_market_half_time_snapshot_event_time
  ON market_half_time_snapshot (betfair_event_id, updated_at_utc DESC);
CREATE INDEX IF NOT EXISTS idx_market_half_time_snapshot_market_time
  ON market_half_time_snapshot (betfair_market_id, updated_at_utc DESC);

CREATE TABLE IF NOT EXISTS market_half_time_score_snapshot (LIKE market_half_time_snapshot INCLUDING DEFAULTS);
CREATE INDEX IF NOT EXISTS idx_market_half_time_score_snapshot_event_time
  ON market_half_time_score_snapshot (betfair_event_id, updated_at_utc DESC);
CREATE INDEX IF NOT EXISTS idx_market_half_time_score_snapshot_market_time
  ON market_half_time_score_snapshot (betfair_market_id, updated_at_utc DESC);

CREATE TABLE IF NOT EXISTS market_full_time_score_snapshot (LIKE market_half_time_snapshot INCLUDING DEFAULTS);
CREATE INDEX IF NOT EXISTS idx_market_full_time_score_snapshot_event_time
  ON market_full_time_score_snapshot (betfair_event_id, updated_at_utc DESC);
CREATE INDEX IF NOT EXISTS idx_market_full_time_score_snapshot_market_time
  ON market_full_time_score_snapshot (betfair_market_id, updated_at_utc DESC);

CREATE TABLE IF NOT EXISTS market_over_under_05_snapshot (LIKE market_half_time_snapshot INCLUDING DEFAULTS);
CREATE INDEX IF NOT EXISTS idx_market_over_under_05_snapshot_event_time
  ON market_over_under_05_snapshot (betfair_event_id, updated_at_utc DESC);
CREATE INDEX IF NOT EXISTS idx_market_over_under_05_snapshot_market_time
  ON market_over_under_05_snapshot (betfair_market_id, updated_at_utc DESC);

CREATE TABLE IF NOT EXISTS market_over_under_15_snapshot (LIKE market_half_time_snapshot INCLUDING DEFAULTS);
CREATE INDEX IF NOT EXISTS idx_market_over_under_15_snapshot_event_time
  ON market_over_under_15_snapshot (betfair_event_id, updated_at_utc DESC);
CREATE INDEX IF NOT EXISTS idx_market_over_under_15_snapshot_market_time
  ON market_over_under_15_snapshot (betfair_market_id, updated_at_utc DESC);

CREATE TABLE IF NOT EXISTS market_over_under_25_snapshot (LIKE market_half_time_snapshot INCLUDING DEFAULTS);
CREATE INDEX IF NOT EXISTS idx_market_over_under_25_snapshot_event_time
  ON market_over_under_25_snapshot (betfair_event_id, updated_at_utc DESC);
CREATE INDEX IF NOT EXISTS idx_market_over_under_25_snapshot_market_time
  ON market_over_under_25_snapshot (betfair_market_id, updated_at_utc DESC);

CREATE TABLE IF NOT EXISTS market_over_under_35_snapshot (LIKE market_half_time_snapshot INCLUDING DEFAULTS);
CREATE INDEX IF NOT EXISTS idx_market_over_under_35_snapshot_event_time
  ON market_over_under_35_snapshot (betfair_event_id, updated_at_utc DESC);
CREATE INDEX IF NOT EXISTS idx_market_over_under_35_snapshot_market_time
  ON market_over_under_35_snapshot (betfair_market_id, updated_at_utc DESC);

CREATE TABLE IF NOT EXISTS market_over_under_45_snapshot (LIKE market_half_time_snapshot INCLUDING DEFAULTS);
CREATE INDEX IF NOT EXISTS idx_market_over_under_45_snapshot_event_time
  ON market_over_under_45_snapshot (betfair_event_id, updated_at_utc DESC);
CREATE INDEX IF NOT EXISTS idx_market_over_under_45_snapshot_market_time
  ON market_over_under_45_snapshot (betfair_market_id, updated_at_utc DESC);

CREATE TABLE IF NOT EXISTS market_over_under_55_snapshot (LIKE market_half_time_snapshot INCLUDING DEFAULTS);
CREATE INDEX IF NOT EXISTS idx_market_over_under_55_snapshot_event_time
  ON market_over_under_55_snapshot (betfair_event_id, updated_at_utc DESC);
CREATE INDEX IF NOT EXISTS idx_market_over_under_55_snapshot_market_time
  ON market_over_under_55_snapshot (betfair_market_id, updated_at_utc DESC);

CREATE TABLE IF NOT EXISTS market_over_under_65_snapshot (LIKE market_half_time_snapshot INCLUDING DEFAULTS);
CREATE INDEX IF NOT EXISTS idx_market_over_under_65_snapshot_event_time
  ON market_over_under_65_snapshot (betfair_event_id, updated_at_utc DESC);
CREATE INDEX IF NOT EXISTS idx_market_over_under_65_snapshot_market_time
  ON market_over_under_65_snapshot (betfair_market_id, updated_at_utc DESC);

CREATE TABLE IF NOT EXISTS market_over_under_75_snapshot (LIKE market_half_time_snapshot INCLUDING DEFAULTS);
CREATE INDEX IF NOT EXISTS idx_market_over_under_75_snapshot_event_time
  ON market_over_under_75_snapshot (betfair_event_id, updated_at_utc DESC);
CREATE INDEX IF NOT EXISTS idx_market_over_under_75_snapshot_market_time
  ON market_over_under_75_snapshot (betfair_market_id, updated_at_utc DESC);

CREATE TABLE IF NOT EXISTS market_over_under_85_snapshot (LIKE market_half_time_snapshot INCLUDING DEFAULTS);
CREATE INDEX IF NOT EXISTS idx_market_over_under_85_snapshot_event_time
  ON market_over_under_85_snapshot (betfair_event_id, updated_at_utc DESC);
CREATE INDEX IF NOT EXISTS idx_market_over_under_85_snapshot_market_time
  ON market_over_under_85_snapshot (betfair_market_id, updated_at_utc DESC);
