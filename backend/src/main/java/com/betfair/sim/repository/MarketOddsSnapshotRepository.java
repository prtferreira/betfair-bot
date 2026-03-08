package com.betfair.sim.repository;

import java.time.Instant;
import java.util.List;

public interface MarketOddsSnapshotRepository {
  String marketTypeCode();

  void initSchema();

  void insert(MarketOddsSnapshotRow row);

  List<MarketOddsSnapshotRow> findRecentByEventId(String betfairEventId, int limit);

  int updateMarketStatusByEventId(String betfairEventId, String marketStatus, Instant updatedAtUtc);
}

