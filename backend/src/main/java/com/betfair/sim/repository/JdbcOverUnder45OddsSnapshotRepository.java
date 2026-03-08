package com.betfair.sim.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOverUnder45OddsSnapshotRepository extends JdbcAbstractMarketOddsSnapshotRepository
    implements OverUnder45OddsSnapshotRepository {
  public JdbcOverUnder45OddsSnapshotRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate, "market_over_under_45_snapshot", "OVER_UNDER_45");
  }
}

