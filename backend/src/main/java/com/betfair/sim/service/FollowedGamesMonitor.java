package com.betfair.sim.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    name = "betfair.followed-games.monitor.enabled",
    havingValue = "true",
    matchIfMissing = false)
public class FollowedGamesMonitor {
  private static final Logger LOGGER = LoggerFactory.getLogger(FollowedGamesMonitor.class);

  private final BetfairApiClient betfairApiClient;
  private final Path gamesToFollowFile;
  private final Path inPlayGamesFile;

  public FollowedGamesMonitor(
      BetfairApiClient betfairApiClient,
      @Value("${betfair.followed-games.dir:backend/data}") String followedGamesDir) {
    this.betfairApiClient = betfairApiClient;
    Path dir = FollowedGamesPathResolver.resolve(followedGamesDir);
    this.gamesToFollowFile = dir.resolve("gamesToFollow.txt");
    this.inPlayGamesFile = dir.resolve("inPlayGames.txt");
  }

  @Scheduled(fixedDelay = 30000)
  public void refreshInPlayGames() {
    if (!betfairApiClient.isEnabled()) {
      return;
    }

    List<String> marketIds = readMarketIds();
    if (marketIds.isEmpty()) {
      writeInPlayIds(List.of());
      return;
    }

    Map<String, BetfairApiClient.MarketStatus> statusByMarket =
        betfairApiClient.getMarketStatuses(marketIds);
    if (statusByMarket.isEmpty()) {
      return;
    }

    Instant now = Instant.now();
    List<String> started = new ArrayList<>();
    for (String marketId : marketIds) {
      BetfairApiClient.MarketStatus status = statusByMarket.get(marketId);
      if (status == null) {
        continue;
      }
      boolean hasStarted =
          status.isInPlay()
              || (status.getStartTime() != null && !status.getStartTime().isAfter(now));
      if (hasStarted) {
        started.add(marketId);
      }
    }

    writeInPlayIds(started);
  }

  private List<String> readMarketIds() {
    if (!Files.exists(gamesToFollowFile)) {
      try {
        Files.createDirectories(gamesToFollowFile.getParent());
        Files.write(gamesToFollowFile, List.of(), StandardCharsets.UTF_8);
      } catch (IOException ex) {
        LOGGER.warn("Failed to create gamesToFollow file", ex);
      }
      return List.of();
    }
    try {
      List<String> lines = Files.readAllLines(gamesToFollowFile, StandardCharsets.UTF_8);
      Set<String> ids = new LinkedHashSet<>();
      for (String line : lines) {
        if (line == null || line.isBlank()) {
          continue;
        }
        String trimmed = line.trim();
        String marketId = trimmed.split("[,\\s]+")[0];
        if (!marketId.isBlank()) {
          ids.add(marketId);
        }
      }
      return new ArrayList<>(ids);
    } catch (IOException ex) {
      LOGGER.warn("Failed to read gamesToFollow file", ex);
      return List.of();
    }
  }

  private void writeInPlayIds(List<String> marketIds) {
    try {
      Files.createDirectories(inPlayGamesFile.getParent());
      Files.write(inPlayGamesFile, marketIds, StandardCharsets.UTF_8);
    } catch (IOException ex) {
      LOGGER.warn("Failed to write inPlayGames file", ex);
    }
  }
}
