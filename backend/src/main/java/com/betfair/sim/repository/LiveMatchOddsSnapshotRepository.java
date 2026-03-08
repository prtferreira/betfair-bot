package com.betfair.sim.repository;

import java.time.Instant;
import java.util.List;

public interface LiveMatchOddsSnapshotRepository {
  void initSchema();

  void insert(LiveMatchOddsSnapshotRow row);

  List<LiveMatchOddsSnapshotRow> findRecentByEventId(String betfairEventId, int limit);

  int updateGameStatusByEventId(String betfairEventId, String gameStatus, Instant updatedAtUtc);
}

