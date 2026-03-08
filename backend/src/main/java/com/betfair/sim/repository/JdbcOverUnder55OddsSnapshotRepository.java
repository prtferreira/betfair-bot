package com.betfair.sim.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOverUnder55OddsSnapshotRepository extends JdbcAbstractMarketOddsSnapshotRepository
    implements OverUnder55OddsSnapshotRepository {
  public JdbcOverUnder55OddsSnapshotRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate, "market_over_under_55_snapshot", "OVER_UNDER_55");
  }
}

