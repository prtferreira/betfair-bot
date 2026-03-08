package com.betfair.sim.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOverUnder05OddsSnapshotRepository extends JdbcAbstractMarketOddsSnapshotRepository
    implements OverUnder05OddsSnapshotRepository {
  public JdbcOverUnder05OddsSnapshotRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate, "market_over_under_05_snapshot", "OVER_UNDER_05");
  }
}

