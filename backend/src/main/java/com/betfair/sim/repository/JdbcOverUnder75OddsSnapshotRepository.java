package com.betfair.sim.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOverUnder75OddsSnapshotRepository extends JdbcAbstractMarketOddsSnapshotRepository
    implements OverUnder75OddsSnapshotRepository {
  public JdbcOverUnder75OddsSnapshotRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate, "market_over_under_75_snapshot", "OVER_UNDER_75");
  }
}

