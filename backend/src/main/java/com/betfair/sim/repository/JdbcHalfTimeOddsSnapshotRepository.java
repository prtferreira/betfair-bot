package com.betfair.sim.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcHalfTimeOddsSnapshotRepository extends JdbcAbstractMarketOddsSnapshotRepository
    implements HalfTimeOddsSnapshotRepository {
  public JdbcHalfTimeOddsSnapshotRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate, "market_half_time_snapshot", "HALF_TIME");
  }
}

