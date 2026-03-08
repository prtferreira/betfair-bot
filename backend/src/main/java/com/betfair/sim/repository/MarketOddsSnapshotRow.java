package com.betfair.sim.repository;

import java.time.Instant;

public record MarketOddsSnapshotRow(
    Instant updatedAtUtc,
    Instant matchStartAtUtc,
    Long gameMinute,
    String betfairEventId,
    String betfairMarketId,
    String statpalMainId,
    String homeTeam,
    String awayTeam,
    String marketStatus,
    Long selectionId,
    String selectionName,
    Double backOdds,
    Double layOdds,
    String source) {}

