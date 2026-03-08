package com.betfair.sim.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcHalfTimeScoreOddsSnapshotRepository extends JdbcAbstractMarketOddsSnapshotRepository
    implements HalfTimeScoreOddsSnapshotRepository {
  public JdbcHalfTimeScoreOddsSnapshotRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate, "market_half_time_score_snapshot", "HALF_TIME_SCORE");
  }
}

