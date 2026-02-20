package com.betfair.sim.service;

import com.betfair.sim.model.Game;
import com.betfair.sim.model.EventMarket;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GameService {
  private static final String[] LEAGUES = {
    "Premier League",
    "La Liga",
    "Serie A",
    "Bundesliga",
    "Ligue 1",
    "Championship"
  };
  private static final String[] TEAMS = {
    "Arsenal",
    "Chelsea",
    "Liverpool",
    "Manchester City",
    "Manchester United",
    "Tottenham",
    "Everton",
    "Aston Villa",
    "Newcastle",
    "Leeds United",
    "Brighton",
    "West Ham"
  };

  private final BetfairApiClient betfairApiClient;
  private final Path followedGamesDir;

  public GameService(
      BetfairApiClient betfairApiClient,
      @Value("${betfair.followed-games.dir:backend/data}") String followedGamesDir) {
    this.betfairApiClient = betfairApiClient;
    this.followedGamesDir = FollowedGamesPathResolver.resolve(followedGamesDir);
  }

  public List<Game> gamesForDate(String date) {
    LocalDate localDate = LocalDate.parse(date);
    if (betfairApiClient.isEnabled()) {
      List<Game> liveGames = betfairApiClient.listGames(localDate);
      if (!liveGames.isEmpty()) {
        applySyntheticOdds(liveGames, localDate);
        return liveGames;
      }
    }
    Random random = new Random(localDate.toEpochDay());
    int gameCount = 5 + random.nextInt(4);

    List<Game> games = new ArrayList<>();
    for (int i = 0; i < gameCount; i++) {
      String home = TEAMS[random.nextInt(TEAMS.length)];
      String away = TEAMS[random.nextInt(TEAMS.length)];
      while (away.equals(home)) {
        away = TEAMS[random.nextInt(TEAMS.length)];
      }
      String league = LEAGUES[random.nextInt(LEAGUES.length)];
      LocalTime time = LocalTime.of(12 + random.nextInt(8), random.nextBoolean() ? 0 : 30);
      String startTime = localDate + " " + time;
      String marketId = "1." + Math.abs(random.nextInt(900000));

      games.add(
          new Game(
              date + "-" + i,
              "Football",
              league,
              home,
              away,
              startTime,
              marketId,
              roundOdds(1.4 + random.nextDouble() * 3.2),
              roundOdds(2.6 + random.nextDouble() * 2.4),
              roundOdds(1.4 + random.nextDouble() * 3.2)));
    }

    return games;
  }

  public List<Game> gamesForDateBetfairOnly(String date) {
    if (!betfairApiClient.isEnabled()) {
      return List.of();
    }
    LocalDate localDate = LocalDate.parse(date);
    return betfairApiClient.listMatchOddsForDate(localDate);
  }

  public List<Game> betfairFootballEvents() {
    if (!betfairApiClient.isEnabled()) {
      return List.of();
    }
    List<Game> games = betfairApiClient.listEventsAllFootball();
    applySyntheticOdds(games, LocalDate.now());
    return games;
  }

  public List<Game> betfairMatchOddsForDate(String date) {
    if (!betfairApiClient.isEnabled()) {
      return List.of();
    }
    LocalDate resolvedDate = date == null ? LocalDate.now(ZoneOffset.UTC) : LocalDate.parse(date);
    return betfairApiClient.listMatchOddsForDate(resolvedDate);
  }

  public List<Game> betfairInPlayBrazilSerieA() {
    if (!betfairApiClient.isEnabled()) {
      return List.of();
    }
    return betfairApiClient.listInPlayBrazilSerieA();
  }

  public String betfairFootballEventsRaw() {
    if (!betfairApiClient.isEnabled()) {
      return "";
    }
    return betfairApiClient.fetchEventsRaw(null);
  }

  public List<EventMarket> betfairEventMarkets(String eventId, List<String> marketTypes) {
    if (!betfairApiClient.isEnabled()) {
      return List.of();
    }
    return betfairApiClient.listMarketsForEvent(eventId, marketTypes);
  }

  public Path saveFollowedMarketIds(String date, List<String> marketIds) {
    String resolvedDate = date == null || date.isBlank() ? LocalDate.now(ZoneOffset.UTC).toString() : date;
    List<String> ids = marketIds == null ? List.of() : marketIds;
    Path outputFile = followedGamesDir.resolve("followed-match-ids-" + resolvedDate + ".txt");

    try {
      Files.createDirectories(followedGamesDir);
      Files.write(outputFile, ids, StandardCharsets.UTF_8);
      return outputFile;
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to save followed match IDs", ex);
    }
  }

  public Path saveSelectedGames(String date, List<String> entries) {
    String resolvedDate = date == null || date.isBlank() ? LocalDate.now(ZoneOffset.UTC).toString() : date;
    List<String> lines = entries == null ? List.of() : entries;
    Path outputFile = followedGamesDir.resolve("selectedGames-" + resolvedDate + ".txt");
    Path gamesToFollowFile = followedGamesDir.resolve("gamesToFollow.txt");

    try {
      Files.createDirectories(followedGamesDir);
      List<String> existing = Files.exists(outputFile)
          ? Files.readAllLines(outputFile, StandardCharsets.UTF_8)
          : List.of();
      List<String> mergedLines = new ArrayList<>();
      mergedLines.addAll(existing);
      for (String line : lines) {
        if (line == null || line.isBlank()) {
          continue;
        }
        if (!mergedLines.contains(line)) {
          mergedLines.add(line);
        }
      }
      Files.write(outputFile, mergedLines, StandardCharsets.UTF_8);

      List<String> existingFollowed = Files.exists(gamesToFollowFile)
          ? Files.readAllLines(gamesToFollowFile, StandardCharsets.UTF_8)
          : List.of();
      List<String> marketIds =
          mergedLines.stream()
              .map(line -> line.split(",", 2)[0].trim())
              .filter(id -> !id.isBlank())
              .distinct()
              .toList();
      List<String> mergedMarketIds = new ArrayList<>(existingFollowed);
      for (String marketId : marketIds) {
        if (!mergedMarketIds.contains(marketId)) {
          mergedMarketIds.add(marketId);
        }
      }
      Files.write(gamesToFollowFile, mergedMarketIds, StandardCharsets.UTF_8);
      return outputFile;
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to save selected games", ex);
    }
  }

  public Path saveBalancedGames(String date, List<String> entries) {
    String resolvedDate = date == null || date.isBlank() ? LocalDate.now(ZoneOffset.UTC).toString() : date;
    List<String> lines = entries == null ? List.of() : entries;
    Path outputFile = followedGamesDir.resolve("balancedGames-" + resolvedDate + ".txt");

    try {
      Files.createDirectories(followedGamesDir);
      Files.write(outputFile, lines, StandardCharsets.UTF_8);
      return outputFile;
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to save balanced games", ex);
    }
  }

  public Path saveLayMatchesReport(String date, List<String> entries) {
    String resolvedDate =
        date == null || date.isBlank() ? LocalDate.now(ZoneOffset.UTC).toString() : date;
    List<String> lines = entries == null ? List.of() : entries;
    Path outputFile = followedGamesDir.resolve("layMatches-" + resolvedDate + ".txt");

    try {
      Files.createDirectories(followedGamesDir);
      Files.write(outputFile, lines, StandardCharsets.UTF_8);
      return outputFile;
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to save lay matches report", ex);
    }
  }

  public List<InPlayStatusEntry> loadBalancedGameStatuses(String date) {
    String resolvedDate = date == null || date.isBlank() ? LocalDate.now(ZoneOffset.UTC).toString() : date;
    Path inputFile = followedGamesDir.resolve("balancedGames-" + resolvedDate + ".txt");
    if (!Files.exists(inputFile)) {
      return List.of();
    }
    try {
      List<String> lines = Files.readAllLines(inputFile, StandardCharsets.UTF_8);
      List<InPlayStatusEntry> entries = new ArrayList<>();
      List<String> marketIds = new ArrayList<>();
      for (String line : lines) {
        if (line == null || line.isBlank()) {
          continue;
        }
        String[] parts = line.split(",", 3);
        String marketId = parts.length > 0 ? parts[0].trim() : "";
        String startTime = parts.length > 1 ? parts[1].trim() : "";
        String teams = parts.length > 2 ? parts[2].trim() : "";
        if (marketId.isBlank()) {
          continue;
        }
        entries.add(new InPlayStatusEntry(marketId, startTime, teams, "Scheduled"));
        marketIds.add(marketId);
      }
      if (marketIds.isEmpty() || !betfairApiClient.isEnabled()) {
        return entries;
      }
      Map<String, BetfairApiClient.MarketStatus> statusByMarket =
          betfairApiClient.getMarketStatuses(marketIds);
      Instant now = Instant.now();
      for (InPlayStatusEntry entry : entries) {
        BetfairApiClient.MarketStatus status = statusByMarket.get(entry.marketId);
        if (status == null) {
          continue;
        }
        entry.status = resolveStatus(status, entry.startTime, now);
      }
      return entries;
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to read balanced games", ex);
    }
  }

  private String resolveStatus(BetfairApiClient.MarketStatus status, String startTimeText, Instant now) {
    String marketStatus = status.getStatus() == null ? "" : status.getStatus().toUpperCase();
    if ("CLOSED".equals(marketStatus)) {
      return "Finished";
    }
    if ("INACTIVE".equals(marketStatus)) {
      return "Postponed";
    }
    if ("SUSPENDED".equals(marketStatus) && status.isInPlay()) {
      return "Half-time";
    }
    if (status.isInPlay()) {
      return "Started";
    }
    Instant startTime = null;
    try {
      if (startTimeText != null && !startTimeText.isBlank()) {
        startTime = Instant.parse(startTimeText);
      }
    } catch (Exception ignored) {
      startTime = status.getStartTime();
    }
    if (startTime != null) {
      long minutes = Math.max(0, (startTime.getEpochSecond() - now.getEpochSecond()) / 60);
      if (minutes <= 30) {
        return "Coming up soon";
      }
    }
    return "Scheduled";
  }

  private void applySyntheticOdds(List<Game> games, LocalDate date) {
    for (Game game : games) {
      if (game.getHomeOdds() != null || game.getDrawOdds() != null || game.getAwayOdds() != null) {
        continue;
      }
      long seed =
          Math.abs(
              Objects.hash(date, game.getId(), game.getHomeTeam(), game.getAwayTeam(), game.getLeague()));
      Random random = new Random(seed);
      double home = 1.4 + random.nextDouble() * 3.2;
      double draw = 2.6 + random.nextDouble() * 2.4;
      double away = 1.4 + random.nextDouble() * 3.2;
      game.setHomeOdds(roundOdds(home));
      game.setDrawOdds(roundOdds(draw));
      game.setAwayOdds(roundOdds(away));
    }
  }

  private double roundOdds(double odds) {
    return Math.round(odds * 100.0) / 100.0;
  }
}
