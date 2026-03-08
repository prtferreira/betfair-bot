package com.betfair.sim.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOverUnder85OddsSnapshotRepository extends JdbcAbstractMarketOddsSnapshotRepository
    implements OverUnder85OddsSnapshotRepository {
  public JdbcOverUnder85OddsSnapshotRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate, "market_over_under_85_snapshot", "OVER_UNDER_85");
  }
}
