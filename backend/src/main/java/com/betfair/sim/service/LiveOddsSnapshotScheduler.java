package com.betfair.sim.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class LiveOddsSnapshotScheduler {
  private static final Logger LOGGER = LoggerFactory.getLogger(LiveOddsSnapshotScheduler.class);

  private final GameService gameService;
  private final BetfairApiClient betfairApiClient;

  public LiveOddsSnapshotScheduler(GameService gameService, BetfairApiClient betfairApiClient) {
    this.gameService = gameService;
    this.betfairApiClient = betfairApiClient;
  }

  @Scheduled(fixedDelayString = "${betfair.live-odds.persist-interval-ms:30000}")
  public void captureLiveOddsSnapshots() {
    if (!betfairApiClient.isEnabled()) {
      return;
    }
    try {
      gameService.betfairLiveGames();
    } catch (Exception ex) {
      LOGGER.warn("Live odds snapshot capture failed: {}", ex.getMessage());
    }
  }
}
