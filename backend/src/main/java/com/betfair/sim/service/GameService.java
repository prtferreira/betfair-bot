package com.betfair.sim.service;

import com.betfair.sim.model.Game;
import com.betfair.sim.model.EventMarket;
import com.betfair.sim.model.AnalyticsGameEntry;
import com.betfair.sim.model.AnalyticsGoalsEstimate;
import com.betfair.sim.model.LiveGameEntry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.text.Normalizer;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
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
  private final StatpalLiveClient statpalLiveClient;
  private final Path followedGamesDir;
  private final int domScrapeMaxPerRequest;
  private final Map<String, LiveTracker> liveTrackers = new ConcurrentHashMap<>();

  public GameService(
      BetfairApiClient betfairApiClient,
      StatpalLiveClient statpalLiveClient,
      @Value("${betfair.dom-score.max-scrapes-per-request:20}") int domScrapeMaxPerRequest,
      @Value("${betfair.followed-games.dir:backend/data}") String followedGamesDir) {
    this.betfairApiClient = betfairApiClient;
    this.statpalLiveClient = statpalLiveClient;
    this.domScrapeMaxPerRequest = Math.max(1, domScrapeMaxPerRequest);
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

  public List<LiveGameEntry> betfairLiveGames() {
    if (!betfairApiClient.isEnabled()) {
      return List.of();
    }

    Map<String, Game> candidatesByMarketId = new LinkedHashMap<>();
    for (Game game : betfairApiClient.listInPlayFootballMatchOdds()) {
      if (game == null) {
        continue;
      }
      String marketId = valueOrEmpty(game.getMarketId());
      if (!marketId.isBlank()) {
        candidatesByMarketId.put(marketId, game);
      }
    }
    List<String> trackedMarketIds =
        liveTrackers.keySet().stream()
            .map(key -> key.split("\\|", 2)[0])
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(id -> !id.isBlank())
            .distinct()
            .toList();
    if (!trackedMarketIds.isEmpty()) {
      for (Game game : betfairApiClient.listMatchOddsByMarketIds(trackedMarketIds)) {
        if (game == null) {
          continue;
        }
        String marketId = valueOrEmpty(game.getMarketId());
        if (!marketId.isBlank()) {
          candidatesByMarketId.put(marketId, game);
        }
      }
    }
    List<Game> candidates = new ArrayList<>(candidatesByMarketId.values());
    if (candidates.isEmpty()) {
      return List.of();
    }
    List<String> candidateMarketIds =
        candidates.stream()
            .map(Game::getMarketId)
            .filter(Objects::nonNull)
            .filter(id -> !id.isBlank())
            .distinct()
            .toList();
    Map<String, BetfairApiClient.MarketOutcome> outcomesByMarket =
        candidateMarketIds.isEmpty()
            ? Map.of()
            : betfairApiClient.getMarketOutcomes(candidateMarketIds);
    Map<String, BetfairApiClient.InferredScore> inferredByMarket =
        candidateMarketIds.isEmpty()
            ? Map.of()
            : betfairApiClient.inferScoresFromCorrectScoreMarkets(candidateMarketIds);

    Instant now = Instant.now();
    List<LiveGameEntry> entries = new ArrayList<>();
    for (Game game : candidates) {
      if (game == null) {
        continue;
      }
      String trackerKey = (valueOrEmpty(game.getMarketId()) + "|" + valueOrEmpty(game.getId())).trim();
      boolean inPlay = Boolean.TRUE.equals(game.getInPlay());
      boolean trackedBefore = liveTrackers.containsKey(trackerKey);
      if (!inPlay && !trackedBefore) {
        continue;
      }
      LiveGameEntry entry = new LiveGameEntry();
      entry.setEventId(valueOrEmpty(game.getId()));
      entry.setMarketId(valueOrEmpty(game.getMarketId()));
      entry.setLeague(valueOrEmpty(game.getLeague()));
      entry.setHomeTeam(valueOrEmpty(game.getHomeTeam()));
      entry.setAwayTeam(valueOrEmpty(game.getAwayTeam()));
      entry.setStartTime(valueOrEmpty(game.getStartTime()));
      entry.setMarketStatus(valueOrEmpty(game.getMarketStatus()));
      entry.setInPlay(inPlay);
      entry.setHomeOdds(game.getHomeOdds());
      entry.setDrawOdds(game.getDrawOdds());
      entry.setAwayOdds(game.getAwayOdds());
      entry.setScore("-");
      entry.setHighlight("");
      entry.setZeroZeroAfterHt(false);

      BetfairApiClient.MarketOutcome outcome = outcomesByMarket.get(entry.getMarketId());
      if (outcome != null && outcome.getHomeScore() != null && outcome.getAwayScore() != null) {
        entry.setScore(outcome.getHomeScore() + "-" + outcome.getAwayScore());
      }
      if ("-".equals(entry.getScore())) {
        BetfairApiClient.InferredScore inferred = inferredByMarket.get(entry.getMarketId());
        if (inferred != null
            && inferred.getHomeScore() != null
            && inferred.getAwayScore() != null) {
          entry.setScore(inferred.getHomeScore() + "-" + inferred.getAwayScore());
        }
      }

      if (entry.getMinute() == null || entry.getMinute().isBlank()) {
        String inferred = inferMinuteFromKickoff(game.getStartTime(), now);
        entry.setMinute(inferred);
        entry.setMinuteSource("kickoff-estimate");
      }
      applyLiveClassification(entry);
      entries.add(entry);
    }

    List<LiveGameEntry> missingScoreCandidates =
        entries.stream().filter(entry -> "-".equals(entry.getScore())).toList();
    List<LiveGameEntry> minuteOnlyCandidates =
        entries.stream()
            .filter(
                entry ->
                    !"-".equals(entry.getScore())
                        && "kickoff-estimate".equals(entry.getMinuteSource()))
            .toList();
    List<LiveGameEntry> domCandidates = new ArrayList<>();
    for (LiveGameEntry entry : missingScoreCandidates) {
      if (domCandidates.size() >= domScrapeMaxPerRequest) {
        break;
      }
      domCandidates.add(entry);
    }
    for (LiveGameEntry entry : minuteOnlyCandidates) {
      if (domCandidates.size() >= domScrapeMaxPerRequest) {
        break;
      }
      domCandidates.add(entry);
    }
    Map<String, BetfairApiClient.ExchangeLiveSnapshot> htmlSnapshotCache = new ConcurrentHashMap<>();
    domCandidates.parallelStream()
        .forEach(
            entry ->
                htmlSnapshotCache.computeIfAbsent(
                    entry.getEventId(),
                    key ->
                        betfairApiClient.fetchExchangeLiveSnapshot(
                            entry.getEventId(),
                            entry.getLeague(),
                            entry.getHomeTeam(),
                            entry.getAwayTeam())));

    for (LiveGameEntry entry : domCandidates) {
      BetfairApiClient.ExchangeLiveSnapshot snapshot = htmlSnapshotCache.get(entry.getEventId());
      if (snapshot != null && snapshot.score() != null && !snapshot.score().isBlank()) {
        entry.setScore(snapshot.score());
      }
      if ("kickoff-estimate".equals(entry.getMinuteSource())
          && snapshot != null
          && snapshot.minute() != null
          && !snapshot.minute().isBlank()) {
        entry.setMinute(snapshot.minute());
        entry.setMinuteSource("betfair-dom");
      }
      applyLiveClassification(entry);
    }

    entries.sort(Comparator.comparing(LiveGameEntry::getStartTime, String::compareTo));
    return entries;
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

  public List<AnalyticsGameEntry> analyticsGamesByDate(String date) {
    String resolvedDate = date == null || date.isBlank() ? LocalDate.now(ZoneOffset.UTC).toString() : date;
    LocalDate localDate = LocalDate.parse(resolvedDate);
    String folder = localDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
    Path inputDir = followedGamesDir.resolve(folder);
    if (!Files.exists(inputDir) || !Files.isDirectory(inputDir)) {
      return List.of();
    }

    Map<String, LinkedHashSet<String>> marketsByGame = new LinkedHashMap<>();
    Map<String, List<Path>> filesByGame = new HashMap<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputDir, "*.txt")) {
      for (Path file : stream) {
        String fileName = file.getFileName().toString();
        ParsedAnalyticsFile parsed = parseAnalyticsFileName(fileName, folder);
        if (parsed == null) {
          continue;
        }
        marketsByGame.computeIfAbsent(parsed.gameKey(), key -> new LinkedHashSet<>()).add(parsed.marketType());
        filesByGame.computeIfAbsent(parsed.gameKey(), key -> new ArrayList<>()).add(file);
      }
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to read analytics games", ex);
    }

    List<AnalyticsGameEntry> entries = new ArrayList<>();
    for (Map.Entry<String, LinkedHashSet<String>> entry : marketsByGame.entrySet()) {
      String gameKey = entry.getKey();
      List<String> markets = new ArrayList<>(entry.getValue());
      markets.sort(String::compareTo);
      entries.add(
          new AnalyticsGameEntry(
              gameKey,
              gameKey.replace('_', ' ').trim(),
              markets));
      AnalyticsGoalsEstimate estimate = estimateGoalsFromFiles(gameKey, filesByGame.get(gameKey));
      entries.get(entries.size() - 1).setGuessedGoals(estimate.getGuessedGoals());
    }
    entries.sort(Comparator.comparing(AnalyticsGameEntry::getDisplayName, String::compareToIgnoreCase));
    return entries;
  }

  public AnalyticsGoalsEstimate analyticsGoalsForGame(String date, String gameKey) {
    String resolvedDate = date == null || date.isBlank() ? LocalDate.now(ZoneOffset.UTC).toString() : date;
    LocalDate localDate = LocalDate.parse(resolvedDate);
    String folder = localDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
    Path inputDir = followedGamesDir.resolve(folder);
    if (!Files.exists(inputDir) || !Files.isDirectory(inputDir) || gameKey == null || gameKey.isBlank()) {
      return new AnalyticsGoalsEstimate(gameKey == null ? "" : gameKey, "", 0, List.of());
    }

    String prefix = gameKey + "_" + folder + "_OVER_UNDER_";
    List<Path> files = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputDir, "*.txt")) {
      for (Path file : stream) {
        String fileName = file.getFileName().toString();
        if (fileName.startsWith(prefix)) {
          files.add(file);
        }
      }
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to read analytics goal files", ex);
    }
    return estimateGoalsFromFiles(gameKey, files);
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

  private ParsedAnalyticsFile parseAnalyticsFileName(String fileName, String folderDate) {
    if (fileName == null || !fileName.endsWith(".txt")) {
      return null;
    }
    String stem = fileName.substring(0, fileName.length() - 4);
    String marker = "_" + folderDate + "_";
    int markerIndex = stem.indexOf(marker);
    if (markerIndex <= 0) {
      return null;
    }
    String gameKey = stem.substring(0, markerIndex);
    String marketType = stem.substring(markerIndex + marker.length());
    if (gameKey.isBlank() || marketType.isBlank()) {
      return null;
    }
    return new ParsedAnalyticsFile(gameKey, marketType);
  }

  private AnalyticsGoalsEstimate estimateGoalsFromFiles(String gameKey, List<Path> files) {
    if (files == null || files.isEmpty()) {
      return new AnalyticsGoalsEstimate(gameKey, gameKey.replace('_', ' ').trim(), 0, List.of());
    }

    List<GoalLineResult> lineResults = new ArrayList<>();
    for (Path file : files) {
      String fileName = file.getFileName().toString();
      int extension = fileName.lastIndexOf('.');
      String stem = extension > 0 ? fileName.substring(0, extension) : fileName;
      int suffixStart = stem.lastIndexOf("_OVER_UNDER_");
      if (suffixStart < 0) {
        continue;
      }
      String marketType = stem.substring(suffixStart + 1);
      Double threshold = parseOverUnderThreshold(marketType);
      if (threshold == null) {
        continue;
      }
      boolean closedBeforeEnd = isMarketClosedBeforeEnd(file);
      boolean overFavouredAtLatestSnapshot = isOverFavouredAtLatestSnapshot(file);
      lineResults.add(
          new GoalLineResult(
              threshold, marketType, closedBeforeEnd, overFavouredAtLatestSnapshot));
    }

    lineResults.sort(Comparator.comparingDouble(GoalLineResult::threshold));
    List<String> closedLines = new ArrayList<>();
    int goals = 0;
    for (GoalLineResult line : lineResults) {
      if (line.reached()) {
        goals++;
        if (line.closedBeforeEnd()) {
          closedLines.add(line.marketType() + " (CLOSED)");
        } else if (line.overFavouredAtLatestSnapshot()) {
          closedLines.add(line.marketType() + " (OVER favoured)");
        }
      }
    }
    return new AnalyticsGoalsEstimate(gameKey, gameKey.replace('_', ' ').trim(), goals, closedLines);
  }

  private Double parseOverUnderThreshold(String marketType) {
    if (marketType == null || !marketType.startsWith("OVER_UNDER_")) {
      return null;
    }
    String raw = marketType.substring("OVER_UNDER_".length());
    try {
      return Integer.parseInt(raw) / 10.0;
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private boolean isMarketClosedBeforeEnd(Path file) {
    try {
      List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
      for (String line : lines) {
        if (line == null || line.isBlank() || line.startsWith("timestamp")) {
          continue;
        }
        String[] parts = line.split(",");
        if (parts.length < 5) {
          continue;
        }
        String status = parts[4].trim().toUpperCase();
        if (!"CLOSED".equals(status)) {
          continue;
        }
        long minute = parseMinute(parts[1]);
        if (minute < 120) {
          return true;
        }
      }
      return false;
    } catch (IOException ex) {
      return false;
    }
  }

  private boolean isOverFavouredAtLatestSnapshot(Path file) {
    try {
      List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
      Quote overQuote = null;
      Quote underQuote = null;
      for (String line : lines) {
        if (line == null || line.isBlank() || line.startsWith("timestamp")) {
          continue;
        }
        String[] parts = line.split(",");
        if (parts.length < 9) {
          continue;
        }
        String runnerName = parts[6].trim().toUpperCase();
        double backOdds = parseOdds(parts[7]);
        if (backOdds <= 0) {
          continue;
        }
        long minute = parseMinute(parts[1]);
        String timestamp = parts[0].trim();
        Quote quote = new Quote(minute, timestamp, backOdds);
        if (runnerName.startsWith("OVER ")) {
          if (overQuote == null || quote.isAfter(overQuote)) {
            overQuote = quote;
          }
        } else if (runnerName.startsWith("UNDER ")) {
          if (underQuote == null || quote.isAfter(underQuote)) {
            underQuote = quote;
          }
        }
      }
      if (overQuote == null || underQuote == null) {
        return false;
      }
      return overQuote.backOdds() < underQuote.backOdds();
    } catch (IOException ex) {
      return false;
    }
  }

  private double parseOdds(String value) {
    try {
      return Double.parseDouble(value == null ? "0" : value.trim());
    } catch (Exception ex) {
      return 0.0;
    }
  }

  private long parseMinute(String value) {
    try {
      return Long.parseLong(value == null ? "0" : value.trim());
    } catch (Exception ex) {
      return 0L;
    }
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

  private String inferMinuteFromKickoff(String kickoffIso, Instant now) {
    if (kickoffIso == null || kickoffIso.isBlank()) {
      return "Live";
    }
    try {
      Instant kickoff = Instant.parse(kickoffIso);
      long elapsed = Math.max(0L, (now.getEpochSecond() - kickoff.getEpochSecond()) / 60L);
      if (elapsed <= 0L) {
        return "1'";
      }
      if (elapsed <= 45L) {
        return elapsed + "'";
      }
      if (elapsed <= 60L) {
        return "HT";
      }
      long secondHalf = Math.min(90L, elapsed - 15L);
      return Math.max(46L, secondHalf) + "'";
    } catch (Exception ex) {
      return "Live";
    }
  }

  private StatpalLiveClient.LiveMatch findBestStatpalMatch(
      Game game, List<StatpalLiveClient.LiveMatch> matches) {
    if (matches == null || matches.isEmpty() || game == null) {
      return null;
    }
    String home = normalizeTeam(game.getHomeTeam());
    String away = normalizeTeam(game.getAwayTeam());
    if (home.isBlank() || away.isBlank()) {
      return null;
    }
    StatpalLiveClient.LiveMatch best = null;
    double bestScore = -1d;
    for (StatpalLiveClient.LiveMatch match : matches) {
      String statHome = normalizeTeam(match.getHomeTeam());
      String statAway = normalizeTeam(match.getAwayTeam());
      double direct = teamSimilarity(home, statHome) + teamSimilarity(away, statAway);
      double swapped = teamSimilarity(home, statAway) + teamSimilarity(away, statHome);
      double score = Math.max(direct, swapped);
      if (score > bestScore) {
        bestScore = score;
        best = match;
      }
    }
    return bestScore >= 1.15d ? best : null;
  }

  private String normalizeTeam(String input) {
    if (input == null) {
      return "";
    }
    String noAccents =
        Normalizer.normalize(input, Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    String normalized = noAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    normalized = normalized.replaceAll("\\b(fc|cf|sc|ac|cd|fk|ud|sv|us|de|the)\\b", " ");
    return normalized.replaceAll("\\s{2,}", " ").trim();
  }

  private double teamSimilarity(String left, String right) {
    if (left == null || right == null || left.isBlank() || right.isBlank()) {
      return 0d;
    }
    if (left.equals(right)) {
      return 1.5d;
    }
    if (left.contains(right) || right.contains(left)) {
      return 1.2d;
    }
    Set<String> leftTokens = tokenize(left);
    Set<String> rightTokens = tokenize(right);
    if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
      return 0d;
    }
    Set<String> intersection = new HashSet<>(leftTokens);
    intersection.retainAll(rightTokens);
    if (intersection.isEmpty()) {
      return 0d;
    }
    Set<String> union = new HashSet<>(leftTokens);
    union.addAll(rightTokens);
    return (double) intersection.size() / (double) union.size();
  }

  private Set<String> tokenize(String value) {
    return Arrays.stream(value.split("\\s+"))
        .map(String::trim)
        .filter(token -> !token.isBlank())
        .collect(Collectors.toSet());
  }

  private String normalizeMinuteText(String minute, String status) {
    String minuteText = minute == null ? "" : minute.trim();
    if (!minuteText.isBlank()) {
      return minuteText.endsWith("'") ? minuteText : minuteText + "'";
    }
    String statusText = status == null ? "" : status.trim().toUpperCase();
    if ("HT".equals(statusText)) {
      return "HT";
    }
    if ("FT".equals(statusText) || "AET".equals(statusText) || "PEN".equals(statusText)) {
      return "Finished";
    }
    return "";
  }

  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }

  private void applyLiveClassification(LiveGameEntry entry) {
    if (entry == null) {
      return;
    }
    String key = (valueOrEmpty(entry.getMarketId()) + "|" + valueOrEmpty(entry.getEventId())).trim();
    if (key.isBlank()) {
      return;
    }
    LiveTracker tracker = liveTrackers.computeIfAbsent(key, ignored -> new LiveTracker());
    int[] score = parseScore(entry.getScore());
    int minute = parseLiveMinute(entry.getMinute());
    boolean finished = isFinished(entry);
    boolean knownScore = score[0] >= 0 && score[1] >= 0;
    int totalGoals = knownScore ? score[0] + score[1] : -1;

    if (tracker.firstGoalMinute == null && knownScore && totalGoals > 0) {
      tracker.firstGoalMinute = minute > 0 ? minute : 1;
    }
    if (tracker.firstGoalMinute != null && tracker.firstGoalMinute <= 60) {
      tracker.highlight = "orange";
    }

    if (!"orange".equals(tracker.highlight)) {
      if (minute >= 60 && !tracker.sixtySnapshotSaved) {
        tracker.sixtySnapshotSaved = true;
        saveSixtyMinuteOdds(entry, minute);
      }
      if (minute >= 60 && knownScore && totalGoals == 0) {
        tracker.zeroZeroAt60 = true;
        tracker.highlight = "yellow";
      }
      if (tracker.zeroZeroAt60 && knownScore && totalGoals > 0) {
        tracker.highlight = "green";
      }
    }

    if (finished && knownScore && totalGoals == 0) {
      tracker.highlight = "red";
      if (!tracker.finishedZeroZeroSaved) {
        tracker.finishedZeroZeroSaved = true;
        saveFinishedZeroZero(entry);
      }
    }

    entry.setHighlight(valueOrEmpty(tracker.highlight));
    entry.setZeroZeroAfterHt(!finished && knownScore && totalGoals == 0 && minute >= 46);
  }

  private int[] parseScore(String score) {
    if (score == null) {
      return new int[] {-1, -1};
    }
    String text = score.trim();
    if (!text.matches("\\d+\\s*-\\s*\\d+")) {
      return new int[] {-1, -1};
    }
    String[] parts = text.split("-");
    try {
      return new int[] {Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
    } catch (Exception ex) {
      return new int[] {-1, -1};
    }
  }

  private int parseLiveMinute(String minuteText) {
    if (minuteText == null || minuteText.isBlank()) {
      return 0;
    }
    String text = minuteText.trim().toUpperCase();
    if ("HT".equals(text)) {
      return 45;
    }
    if ("FT".equals(text) || "FINISHED".equals(text)) {
      return 90;
    }
    String normalized = text.replace("’", "'").replaceAll("[^0-9+']", "");
    if (normalized.isBlank()) {
      return 0;
    }
    String base = normalized;
    if (base.endsWith("'")) {
      base = base.substring(0, base.length() - 1);
    }
    try {
      if (base.contains("+")) {
        String[] parts = base.split("\\+");
        return Integer.parseInt(parts[0]) + Integer.parseInt(parts[1]);
      }
      return Integer.parseInt(base);
    } catch (Exception ex) {
      return 0;
    }
  }

  private boolean isFinished(LiveGameEntry entry) {
    String marketStatus = valueOrEmpty(entry.getMarketStatus()).toUpperCase();
    if ("CLOSED".equals(marketStatus)) {
      return true;
    }
    String minute = valueOrEmpty(entry.getMinute()).toUpperCase();
    return "FT".equals(minute) || "FINISHED".equals(minute);
  }

  private void saveSixtyMinuteOdds(LiveGameEntry entry, int minute) {
    LocalDate day = resolveEntryDay(entry);
    Path file = followedGamesDir.resolve("live-odds-at-60-" + day + ".txt");
    String line =
        day
            + " | "
            + valueOrEmpty(entry.getHomeTeam())
            + " vs "
            + valueOrEmpty(entry.getAwayTeam())
            + " | minute="
            + minute
            + " | "
            + valueOrEmpty(entry.getHomeTeam())
            + "="
            + valueOrDash(entry.getHomeOdds())
            + ", Draw="
            + valueOrDash(entry.getDrawOdds())
            + ", "
            + valueOrEmpty(entry.getAwayTeam())
            + "="
            + valueOrDash(entry.getAwayOdds());
    appendLine(file, line);
  }

  private void saveFinishedZeroZero(LiveGameEntry entry) {
    LocalDate day = resolveEntryDay(entry);
    Path file = followedGamesDir.resolve("live-finished-0-0-" + day + ".txt");
    String line =
        day
            + " | "
            + valueOrEmpty(entry.getHomeTeam())
            + " vs "
            + valueOrEmpty(entry.getAwayTeam())
            + " | score=0-0";
    appendLine(file, line);
  }

  private LocalDate resolveEntryDay(LiveGameEntry entry) {
    try {
      String start = valueOrEmpty(entry.getStartTime());
      if (!start.isBlank()) {
        return Instant.parse(start).atZone(ZoneOffset.UTC).toLocalDate();
      }
    } catch (Exception ignored) {
      // ignore parsing issues
    }
    return LocalDate.now(ZoneOffset.UTC);
  }

  private String valueOrDash(Double value) {
    return value == null ? "-" : String.valueOf(value);
  }

  private synchronized void appendLine(Path file, String line) {
    try {
      Files.createDirectories(file.getParent());
      List<String> existing =
          Files.exists(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : List.of();
      if (existing.contains(line)) {
        return;
      }
      List<String> output = new ArrayList<>(existing);
      output.add(line);
      Files.write(file, output, StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to write live game tracking file", ex);
    }
  }

  private double roundOdds(double odds) {
    return Math.round(odds * 100.0) / 100.0;
  }

  private static final class LiveTracker {
    private Integer firstGoalMinute;
    private boolean zeroZeroAt60;
    private boolean sixtySnapshotSaved;
    private boolean finishedZeroZeroSaved;
    private String highlight = "";
  }

  private static final class ParsedAnalyticsFile {
    private final String gameKey;
    private final String marketType;

    private ParsedAnalyticsFile(String gameKey, String marketType) {
      this.gameKey = gameKey;
      this.marketType = marketType;
    }

    private String gameKey() {
      return gameKey;
    }

    private String marketType() {
      return marketType;
    }
  }

  private static final class GoalLineResult {
    private final double threshold;
    private final String marketType;
    private final boolean closedBeforeEnd;
    private final boolean overFavouredAtLatestSnapshot;

    private GoalLineResult(
        double threshold,
        String marketType,
        boolean closedBeforeEnd,
        boolean overFavouredAtLatestSnapshot) {
      this.threshold = threshold;
      this.marketType = marketType;
      this.closedBeforeEnd = closedBeforeEnd;
      this.overFavouredAtLatestSnapshot = overFavouredAtLatestSnapshot;
    }

    private double threshold() {
      return threshold;
    }

    private String marketType() {
      return marketType;
    }

    private boolean closedBeforeEnd() {
      return closedBeforeEnd;
    }

    private boolean overFavouredAtLatestSnapshot() {
      return overFavouredAtLatestSnapshot;
    }

    private boolean reached() {
      return closedBeforeEnd || overFavouredAtLatestSnapshot;
    }
  }

  private static final class Quote {
    private final long minute;
    private final String timestamp;
    private final double backOdds;

    private Quote(long minute, String timestamp, double backOdds) {
      this.minute = minute;
      this.timestamp = timestamp;
      this.backOdds = backOdds;
    }

    private double backOdds() {
      return backOdds;
    }

    private boolean isAfter(Quote other) {
      if (minute != other.minute) {
        return minute > other.minute;
      }
      return timestamp.compareTo(other.timestamp) > 0;
    }
  }
}
