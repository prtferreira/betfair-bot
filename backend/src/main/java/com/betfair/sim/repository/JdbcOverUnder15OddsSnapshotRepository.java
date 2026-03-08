package com.betfair.sim.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOverUnder15OddsSnapshotRepository extends JdbcAbstractMarketOddsSnapshotRepository
    implements OverUnder15OddsSnapshotRepository {
  public JdbcOverUnder15OddsSnapshotRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate, "market_over_under_15_snapshot", "OVER_UNDER_15");
  }
}

