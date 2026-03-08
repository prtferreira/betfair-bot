package com.betfair.sim.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcFullTimeScoreOddsSnapshotRepository extends JdbcAbstractMarketOddsSnapshotRepository
    implements FullTimeScoreOddsSnapshotRepository {
  public JdbcFullTimeScoreOddsSnapshotRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate, "market_full_time_score_snapshot", "FULL_TIME_SCORE");
  }
}

