-- Persist in-play match odds snapshots every polling cycle.
CREATE TABLE IF NOT EXISTS live_match_odds_snapshot (
  id BIGSERIAL PRIMARY KEY,
  updated_at_utc TIMESTAMPTZ NOT NULL,
  match_start_at_utc TIMESTAMPTZ NULL,
  betfair_event_id VARCHAR(32) NOT NULL,
  betfair_market_id VARCHAR(32) NULL,
  statpal_main_id VARCHAR(64) NULL,
  home_team VARCHAR(120) NULL,
  away_team VARCHAR(120) NULL,
  score_text VARCHAR(16) NULL,
  minute_text VARCHAR(16) NULL,
  minute_number SMALLINT NULL,
  game_status VARCHAR(32) NOT NULL,
  in_play BOOLEAN NOT NULL DEFAULT FALSE,
  main_back_odd_home NUMERIC(10,3) NULL,
  main_lay_odd_home NUMERIC(10,3) NULL,
  main_back_odd_draw NUMERIC(10,3) NULL,
  main_lay_odd_draw NUMERIC(10,3) NULL,
  main_back_odd_away NUMERIC(10,3) NULL,
  main_lay_odd_away NUMERIC(10,3) NULL,
  matched_volume_gbp NUMERIC(14,2) NULL,
  source VARCHAR(32) NOT NULL DEFAULT 'betfair-live'
);

CREATE INDEX IF NOT EXISTS idx_lmos_event_time
  ON live_match_odds_snapshot (betfair_event_id, updated_at_utc DESC);

CREATE INDEX IF NOT EXISTS idx_lmos_statpal_time
  ON live_match_odds_snapshot (statpal_main_id, updated_at_utc DESC);

CREATE INDEX IF NOT EXISTS idx_lmos_status_time
  ON live_match_odds_snapshot (game_status, updated_at_utc DESC);
