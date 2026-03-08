package com.betfair.sim.repository;

import java.time.Instant;

public record LiveMatchOddsSnapshotRow(
    Instant updatedAtUtc,
    Instant matchStartAtUtc,
    String betfairEventId,
    String betfairMarketId,
    String statpalMainId,
    String homeTeam,
    String awayTeam,
    String scoreText,
    String minuteText,
    Integer minuteNumber,
    String gameStatus,
    boolean inPlay,
    Double mainBackOddHome,
    Double mainLayOddHome,
    Double mainBackOddDraw,
    Double mainLayOddDraw,
    Double mainBackOddAway,
    Double mainLayOddAway,
    Double matchedVolumeGbp,
    String source) {}

