package com.betfair.sim.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcLiveMatchOddsSnapshotRepository implements LiveMatchOddsSnapshotRepository {
  private static final String TABLE_NAME = "live_match_odds_snapshot";

  private final JdbcTemplate jdbcTemplate;

  public JdbcLiveMatchOddsSnapshotRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void initSchema() {
    jdbcTemplate.execute(
        "CREATE TABLE IF NOT EXISTS "
            + TABLE_NAME
            + " ("
            + "id BIGSERIAL PRIMARY KEY,"
            + "updated_at_utc TIMESTAMP WITH TIME ZONE NOT NULL,"
            + "match_start_at_utc TIMESTAMP WITH TIME ZONE NULL,"
            + "betfair_event_id VARCHAR(32) NOT NULL,"
            + "betfair_market_id VARCHAR(32) NULL,"
            + "statpal_main_id VARCHAR(64) NULL,"
            + "home_team VARCHAR(120) NULL,"
            + "away_team VARCHAR(120) NULL,"
            + "score_text VARCHAR(16) NULL,"
            + "minute_text VARCHAR(16) NULL,"
            + "minute_number SMALLINT NULL,"
            + "game_status VARCHAR(32) NOT NULL,"
            + "in_play BOOLEAN NOT NULL DEFAULT FALSE,"
            + "main_back_odd_home NUMERIC(10,3) NULL,"
            + "main_lay_odd_home NUMERIC(10,3) NULL,"
            + "main_back_odd_draw NUMERIC(10,3) NULL,"
            + "main_lay_odd_draw NUMERIC(10,3) NULL,"
            + "main_back_odd_away NUMERIC(10,3) NULL,"
            + "main_lay_odd_away NUMERIC(10,3) NULL,"
            + "matched_volume_gbp NUMERIC(14,2) NULL,"
            + "source VARCHAR(32) NOT NULL DEFAULT 'betfair-live'"
            + ")");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS idx_lmos_event_time ON "
            + TABLE_NAME
            + " (betfair_event_id, updated_at_utc DESC)");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS idx_lmos_statpal_time ON "
            + TABLE_NAME
            + " (statpal_main_id, updated_at_utc DESC)");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS idx_lmos_status_time ON "
            + TABLE_NAME
            + " (game_status, updated_at_utc DESC)");
  }

  @Override
  public void insert(LiveMatchOddsSnapshotRow row) {
    jdbcTemplate.update(
        "INSERT INTO "
            + TABLE_NAME
            + " ("
            + "updated_at_utc, match_start_at_utc, betfair_event_id, betfair_market_id, statpal_main_id, "
            + "home_team, away_team, score_text, minute_text, minute_number, game_status, in_play, "
            + "main_back_odd_home, main_lay_odd_home, main_back_odd_draw, main_lay_odd_draw, "
            + "main_back_odd_away, main_lay_odd_away, matched_volume_gbp, source"
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        toTimestamp(row.updatedAtUtc()),
        toTimestamp(row.matchStartAtUtc()),
        row.betfairEventId(),
        row.betfairMarketId(),
        row.statpalMainId(),
        row.homeTeam(),
        row.awayTeam(),
        row.scoreText(),
        row.minuteText(),
        row.minuteNumber(),
        row.gameStatus(),
        row.inPlay(),
        row.mainBackOddHome(),
        row.mainLayOddHome(),
        row.mainBackOddDraw(),
        row.mainLayOddDraw(),
        row.mainBackOddAway(),
        row.mainLayOddAway(),
        row.matchedVolumeGbp(),
        row.source());
  }

  @Override
  public List<LiveMatchOddsSnapshotRow> findRecentByEventId(String betfairEventId, int limit) {
    int safeLimit = Math.max(1, Math.min(500, limit));
    return jdbcTemplate.query(
        "SELECT updated_at_utc, match_start_at_utc, betfair_event_id, betfair_market_id, statpal_main_id, "
            + "home_team, away_team, score_text, minute_text, minute_number, game_status, in_play, "
            + "main_back_odd_home, main_lay_odd_home, main_back_odd_draw, main_lay_odd_draw, "
            + "main_back_odd_away, main_lay_odd_away, matched_volume_gbp, source "
            + "FROM "
            + TABLE_NAME
            + " WHERE betfair_event_id = ? "
            + "ORDER BY updated_at_utc DESC LIMIT ?",
        rowMapper(),
        betfairEventId,
        safeLimit);
  }

  @Override
  public int updateGameStatusByEventId(String betfairEventId, String gameStatus, Instant updatedAtUtc) {
    if (betfairEventId == null || betfairEventId.isBlank()) {
      return 0;
    }
    return jdbcTemplate.update(
        "UPDATE "
            + TABLE_NAME
            + " SET game_status = ?, updated_at_utc = ? "
            + "WHERE betfair_event_id = ?",
        gameStatus,
        toTimestamp(updatedAtUtc),
        betfairEventId);
  }

  private RowMapper<LiveMatchOddsSnapshotRow> rowMapper() {
    return (rs, rowNum) -> toRow(rs);
  }

  private LiveMatchOddsSnapshotRow toRow(ResultSet rs) throws SQLException {
    return new LiveMatchOddsSnapshotRow(
        toInstant(rs.getTimestamp("updated_at_utc")),
        toInstant(rs.getTimestamp("match_start_at_utc")),
        rs.getString("betfair_event_id"),
        rs.getString("betfair_market_id"),
        rs.getString("statpal_main_id"),
        rs.getString("home_team"),
        rs.getString("away_team"),
        rs.getString("score_text"),
        rs.getString("minute_text"),
        (Integer) rs.getObject("minute_number"),
        rs.getString("game_status"),
        rs.getBoolean("in_play"),
        toDouble(rs.getObject("main_back_odd_home")),
        toDouble(rs.getObject("main_lay_odd_home")),
        toDouble(rs.getObject("main_back_odd_draw")),
        toDouble(rs.getObject("main_lay_odd_draw")),
        toDouble(rs.getObject("main_back_odd_away")),
        toDouble(rs.getObject("main_lay_odd_away")),
        toDouble(rs.getObject("matched_volume_gbp")),
        rs.getString("source"));
  }

  private Timestamp toTimestamp(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }

  private Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }

  private Double toDouble(Object value) {
    if (!(value instanceof Number number)) {
      return null;
    }
    return number.doubleValue();
  }
}

