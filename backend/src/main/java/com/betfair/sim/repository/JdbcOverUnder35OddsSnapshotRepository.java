package com.betfair.sim.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOverUnder35OddsSnapshotRepository extends JdbcAbstractMarketOddsSnapshotRepository
    implements OverUnder35OddsSnapshotRepository {
  public JdbcOverUnder35OddsSnapshotRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate, "market_over_under_35_snapshot", "OVER_UNDER_35");
  }
}

