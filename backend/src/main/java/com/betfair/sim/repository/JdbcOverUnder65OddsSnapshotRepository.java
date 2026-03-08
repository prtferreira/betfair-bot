package com.betfair.sim.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOverUnder65OddsSnapshotRepository extends JdbcAbstractMarketOddsSnapshotRepository
    implements OverUnder65OddsSnapshotRepository {
  public JdbcOverUnder65OddsSnapshotRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate, "market_over_under_65_snapshot", "OVER_UNDER_65");
  }
}

