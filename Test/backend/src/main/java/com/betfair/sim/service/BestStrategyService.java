package com.betfair.sim.service;

import com.betfair.sim.model.BestStrategyMonitorEntry;
import com.betfair.sim.model.Game;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class BestStrategyService {
  private static final Logger LOGGER = LoggerFactory.getLogger(BestStrategyService.class);

  private final BetfairApiClient betfairApiClient;
  private final Path followedGamesDir;
  private final Path oddsSnapshotsDir;

  public BestStrategyService(
      BetfairApiClient betfairApiClient,
      @Value("${betfair.followed-games.dir:backend/data}") String followedGamesDir) {
    this.betfairApiClient = betfairApiClient;
    this.followedGamesDir = Paths.get(followedGamesDir);
    this.oddsSnapshotsDir = this.followedGamesDir.resolve("best-strategy-odds");
  }

  public Path saveSelectedGames(String date, List<String> entries) {
    String resolvedDate =
        date == null || date.isBlank() ? LocalDate.now(ZoneOffset.UTC).toString() : date;
    List<String> lines = entries == null ? List.of() : entries;
    Path outputFile = followedGamesDir.resolve("bestStrategyGames-" + resolvedDate + ".txt");

    try {
      Files.createDirectories(followedGamesDir);
      Files.write(outputFile, lines, StandardCharsets.UTF_8);
      return outputFile;
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to save best strategy games", ex);
    }
  }

  public List<BestStrategyMonitorEntry> loadMonitorEntries(String date) {
    String resolvedDate =
        date == null || date.isBlank() ? LocalDate.now(ZoneOffset.UTC).toString() : date;
    List<SelectedGameRef> selected = readSelectedGames(resolvedDate);
    if (selected.isEmpty()) {
      return List.of();
    }

    List<String> marketIds = selected.stream().map(SelectedGameRef::marketId).toList();
    Map<String, BetfairApiClient.MarketStatus> statusByMarket = betfairApiClient.getMarketStatuses(marketIds);
    Map<String, Game> oddsByMarket = new LinkedHashMap<>();
    for (Game game : betfairApiClient.listMatchOddsByMarketIds(marketIds)) {
      oddsByMarket.put(game.getMarketId(), game);
    }

    Instant now = Instant.now();
    List<BestStrategyMonitorEntry> entries = new ArrayList<>();
    for (SelectedGameRef selectedGame : selected) {
      BetfairApiClient.MarketStatus marketStatus = statusByMarket.get(selectedGame.marketId());
      boolean started = hasStarted(selectedGame.startTime(), marketStatus, now);
      String status = resolveStatus(selectedGame.startTime(), marketStatus, started, now);
      Game odds = oddsByMarket.get(selectedGame.marketId());

      BestStrategyMonitorEntry entry = new BestStrategyMonitorEntry();
      entry.setMarketId(selectedGame.marketId());
      entry.setStartTime(selectedGame.startTime());
      entry.setTeams(selectedGame.teams());
      entry.setStarted(started);
      entry.setStatus(status);
      if (odds != null) {
        entry.setHomeOdds(odds.getHomeOdds());
        entry.setDrawOdds(odds.getDrawOdds());
        entry.setAwayOdds(odds.getAwayOdds());
        entry.setFtMarketStatus(odds.getMarketStatus());
        entry.setOver05Odds(odds.getOver05Odds());
        entry.setUnder05Odds(odds.getUnder05Odds());
        entry.setOu05MarketStatus(odds.getOu05MarketStatus());
        entry.setHtHomeOdds(odds.getHtHomeOdds());
        entry.setHtDrawOdds(odds.getHtDrawOdds());
        entry.setHtAwayOdds(odds.getHtAwayOdds());
        entry.setHtMarketStatus(odds.getHtMarketStatus());
      }
      entry.setOddsFile(snapshotFile(resolvedDate, selectedGame.marketId()).toString());
      entries.add(entry);
    }

    entries.sort(
        Comparator.comparing(BestStrategyMonitorEntry::getStartTime, Comparator.nullsLast(String::compareTo))
            .thenComparing(BestStrategyMonitorEntry::getMarketId, Comparator.nullsLast(String::compareTo)));
    return entries;
  }

  @Scheduled(fixedDelay = 30000)
  public void captureStartedGamesOdds() {
    if (!betfairApiClient.isEnabled()) {
      return;
    }
    String today = LocalDate.now(ZoneOffset.UTC).toString();
    List<BestStrategyMonitorEntry> entries = loadMonitorEntries(today);
    if (entries.isEmpty()) {
      return;
    }
    Instant now = Instant.now();
    for (BestStrategyMonitorEntry entry : entries) {
      if (!entry.isStarted()) {
        continue;
      }
      appendSnapshot(today, entry, now);
    }
  }

  private List<SelectedGameRef> readSelectedGames(String resolvedDate) {
    Path inputFile = followedGamesDir.resolve("bestStrategyGames-" + resolvedDate + ".txt");
    if (!Files.exists(inputFile)) {
      return List.of();
    }
    try {
      List<String> lines = Files.readAllLines(inputFile, StandardCharsets.UTF_8);
      Set<String> seen = new LinkedHashSet<>();
      List<SelectedGameRef> entries = new ArrayList<>();
      for (String line : lines) {
        if (line == null || line.isBlank()) {
          continue;
        }
        String[] parts = line.split(",", 3);
        String marketId = parts.length > 0 ? parts[0].trim() : "";
        String startTime = parts.length > 1 ? parts[1].trim() : "";
        String teams = parts.length > 2 ? parts[2].trim() : "";
        if (marketId.isBlank() || !seen.add(marketId)) {
          continue;
        }
        entries.add(new SelectedGameRef(marketId, startTime, teams));
      }
      return entries;
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to read best strategy games", ex);
    }
  }

  private boolean hasStarted(
      String selectedStartTime, BetfairApiClient.MarketStatus marketStatus, Instant now) {
    if (marketStatus != null) {
      if (marketStatus.isInPlay()) {
        return true;
      }
      Instant start = marketStatus.getStartTime();
      if (start != null && !start.isAfter(now)) {
        return true;
      }
    }
    Instant selectedStart = parseInstant(selectedStartTime);
    return selectedStart != null && !selectedStart.isAfter(now);
  }

  private String resolveStatus(
      String selectedStartTime,
      BetfairApiClient.MarketStatus marketStatus,
      boolean started,
      Instant now) {
    String marketStatusText =
        marketStatus == null || marketStatus.getStatus() == null
            ? ""
            : marketStatus.getStatus().toUpperCase();

    if ("CLOSED".equals(marketStatusText)) {
      return "Finished";
    }
    if ("INACTIVE".equals(marketStatusText)) {
      return "Postponed";
    }
    if ("SUSPENDED".equals(marketStatusText) && marketStatus != null && marketStatus.isInPlay()) {
      return "Half-time";
    }
    if (marketStatus != null && marketStatus.isInPlay()) {
      return "Started";
    }
    if (started) {
      return "Started";
    }

    Instant start = marketStatus == null ? null : marketStatus.getStartTime();
    if (start == null) {
      start = parseInstant(selectedStartTime);
    }
    if (start != null) {
      long minutes = Math.max(0L, (start.getEpochSecond() - now.getEpochSecond()) / 60);
      if (minutes <= 30) {
        return "Coming up soon";
      }
    }
    return "Scheduled";
  }

  private Instant parseInstant(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (Exception ex) {
      return null;
    }
  }

  private void appendSnapshot(String date, BestStrategyMonitorEntry entry, Instant now) {
    try {
      Files.createDirectories(oddsSnapshotsDir);
      Path file = snapshotFile(date, entry.getMarketId());
      if (!Files.exists(file)) {
        Files.write(
            file,
            List.of(
                "timestamp,ft_status,ou05_status,ht_status,home_ft,draw_ft,away_ft,over05_ft,under05_ft,home_ht,draw_ht,away_ht"),
            StandardCharsets.UTF_8);
      }
      String line =
          String.join(
              ",",
              now.toString(),
              sanitize(entry.getFtMarketStatus()),
              sanitize(entry.getOu05MarketStatus()),
              sanitize(entry.getHtMarketStatus()),
              formatOdds(entry.getHomeOdds()),
              formatOdds(entry.getDrawOdds()),
              formatOdds(entry.getAwayOdds()),
              formatOdds(entry.getOver05Odds()),
              formatOdds(entry.getUnder05Odds()),
              formatOdds(entry.getHtHomeOdds()),
              formatOdds(entry.getHtDrawOdds()),
              formatOdds(entry.getHtAwayOdds()));
      Files.write(
          file,
          List.of(line),
          StandardCharsets.UTF_8,
          StandardOpenOption.APPEND);
    } catch (IOException ex) {
      LOGGER.warn("Failed to append best strategy odds snapshot for {}", entry.getMarketId(), ex);
    }
  }

  private Path snapshotFile(String date, String marketId) {
    String safeMarketId =
        marketId == null ? "unknown" : marketId.replaceAll("[^A-Za-z0-9._-]", "_");
    return oddsSnapshotsDir.resolve("bestStrategyOdds-" + date + "-" + safeMarketId + ".txt");
  }

  private String formatOdds(Double value) {
    if (value == null) {
      return "";
    }
    return String.format("%.2f", value);
  }

  private String sanitize(String value) {
    if (value == null) {
      return "";
    }
    return value.replace(",", " ").trim();
  }

  private static final class SelectedGameRef {
    private final String marketId;
    private final String startTime;
    private final String teams;

    private SelectedGameRef(String marketId, String startTime, String teams) {
      this.marketId = Objects.requireNonNull(marketId);
      this.startTime = startTime == null ? "" : startTime;
      this.teams = teams == null ? "" : teams;
    }

    public String marketId() {
      return marketId;
    }

    public String startTime() {
      return startTime;
    }

    public String teams() {
      return teams;
    }
  }
}
