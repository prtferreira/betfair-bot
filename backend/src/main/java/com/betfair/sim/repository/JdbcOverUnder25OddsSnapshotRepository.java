package com.betfair.sim.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOverUnder25OddsSnapshotRepository extends JdbcAbstractMarketOddsSnapshotRepository
    implements OverUnder25OddsSnapshotRepository {
  public JdbcOverUnder25OddsSnapshotRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate, "market_over_under_25_snapshot", "OVER_UNDER_25");
  }
}

