package com.betfair.sim.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public abstract class JdbcAbstractMarketOddsSnapshotRepository
    implements MarketOddsSnapshotRepository {
  private final JdbcTemplate jdbcTemplate;
  private final String tableName;
  private final String marketTypeCode;

  protected JdbcAbstractMarketOddsSnapshotRepository(
      JdbcTemplate jdbcTemplate, String tableName, String marketTypeCode) {
    this.jdbcTemplate = jdbcTemplate;
    this.tableName = tableName;
    this.marketTypeCode = marketTypeCode;
  }

  @Override
  public String marketTypeCode() {
    return marketTypeCode;
  }

  @Override
  public void initSchema() {
    jdbcTemplate.execute(
        "CREATE TABLE IF NOT EXISTS "
            + tableName
            + " ("
            + "id BIGSERIAL PRIMARY KEY,"
            + "updated_at_utc TIMESTAMP WITH TIME ZONE NOT NULL,"
            + "match_start_at_utc TIMESTAMP WITH TIME ZONE NULL,"
            + "game_minute BIGINT NULL,"
            + "betfair_event_id VARCHAR(32) NOT NULL,"
            + "betfair_market_id VARCHAR(32) NOT NULL,"
            + "statpal_main_id VARCHAR(64) NULL,"
            + "home_team VARCHAR(120) NULL,"
            + "away_team VARCHAR(120) NULL,"
            + "market_status VARCHAR(32) NULL,"
            + "selection_id BIGINT NULL,"
            + "selection_name VARCHAR(128) NULL,"
            + "back_odds NUMERIC(10,3) NULL,"
            + "lay_odds NUMERIC(10,3) NULL,"
            + "source VARCHAR(32) NOT NULL DEFAULT 'betfair-selected-games'"
            + ")");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS idx_"
            + tableName
            + "_event_time ON "
            + tableName
            + " (betfair_event_id, updated_at_utc DESC)");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS idx_"
            + tableName
            + "_market_time ON "
            + tableName
            + " (betfair_market_id, updated_at_utc DESC)");
  }

  @Override
  public void insert(MarketOddsSnapshotRow row) {
    jdbcTemplate.update(
        "INSERT INTO "
            + tableName
            + " ("
            + "updated_at_utc, match_start_at_utc, game_minute, betfair_event_id, betfair_market_id, "
            + "statpal_main_id, home_team, away_team, market_status, selection_id, selection_name, "
            + "back_odds, lay_odds, source"
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        toTimestamp(row.updatedAtUtc()),
        toTimestamp(row.matchStartAtUtc()),
        row.gameMinute(),
        row.betfairEventId(),
        row.betfairMarketId(),
        row.statpalMainId(),
        row.homeTeam(),
        row.awayTeam(),
        row.marketStatus(),
        row.selectionId(),
        row.selectionName(),
        row.backOdds(),
        row.layOdds(),
        row.source());
  }

  @Override
  public List<MarketOddsSnapshotRow> findRecentByEventId(String betfairEventId, int limit) {
    int safeLimit = Math.max(1, Math.min(1000, limit));
    return jdbcTemplate.query(
        "SELECT updated_at_utc, match_start_at_utc, game_minute, betfair_event_id, betfair_market_id, "
            + "statpal_main_id, home_team, away_team, market_status, selection_id, selection_name, "
            + "back_odds, lay_odds, source FROM "
            + tableName
            + " WHERE betfair_event_id = ? ORDER BY updated_at_utc DESC LIMIT ?",
        rowMapper(),
        betfairEventId,
        safeLimit);
  }

  @Override
  public int updateMarketStatusByEventId(
      String betfairEventId, String marketStatus, Instant updatedAtUtc) {
    if (betfairEventId == null || betfairEventId.isBlank()) {
      return 0;
    }
    return jdbcTemplate.update(
        "UPDATE "
            + tableName
            + " SET market_status = ?, updated_at_utc = ? WHERE betfair_event_id = ?",
        marketStatus,
        toTimestamp(updatedAtUtc),
        betfairEventId);
  }

  private RowMapper<MarketOddsSnapshotRow> rowMapper() {
    return (rs, rowNum) -> toRow(rs);
  }

  private MarketOddsSnapshotRow toRow(ResultSet rs) throws SQLException {
    return new MarketOddsSnapshotRow(
        toInstant(rs.getTimestamp("updated_at_utc")),
        toInstant(rs.getTimestamp("match_start_at_utc")),
        (Long) rs.getObject("game_minute"),
        rs.getString("betfair_event_id"),
        rs.getString("betfair_market_id"),
        rs.getString("statpal_main_id"),
        rs.getString("home_team"),
        rs.getString("away_team"),
        rs.getString("market_status"),
        (Long) rs.getObject("selection_id"),
        rs.getString("selection_name"),
        toDouble(rs.getObject("back_odds")),
        toDouble(rs.getObject("lay_odds")),
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

