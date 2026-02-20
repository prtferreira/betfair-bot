package com.betfair.sim.service;

import com.betfair.sim.model.EventMarket;
import com.betfair.sim.model.EventSelection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
public class SelectedGamesOddsCaptureService {
  private static final Logger LOGGER = LoggerFactory.getLogger(SelectedGamesOddsCaptureService.class);
  private static final DateTimeFormatter DATE_FOLDER_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final List<String> ALLOWED_MARKET_TYPES =
      List.of(
          "CORRECT_SCORE",
          "HALF_TIME",
          "HALF_TIME_SCORE",
          "MATCH_ODDS",
          "OVER_UNDER_05",
          "OVER_UNDER_15",
          "OVER_UNDER_25",
          "OVER_UNDER_35",
          "OVER_UNDER_45",
          "OVER_UNDER_55",
          "OVER_UNDER_65",
          "OVER_UNDER_75",
          "OVER_UNDER_85");

  private final BetfairApiClient betfairApiClient;
  private final Path followedGamesDir;

  public SelectedGamesOddsCaptureService(
      BetfairApiClient betfairApiClient,
      @Value("${betfair.followed-games.dir:backend/data}") String followedGamesDir) {
    this.betfairApiClient = betfairApiClient;
    this.followedGamesDir = FollowedGamesPathResolver.resolve(followedGamesDir);
  }

  @Scheduled(fixedDelay = 30000)
  public void captureSelectedGamesOdds() {
    if (!betfairApiClient.isEnabled()) {
      return;
    }

    Instant now = Instant.now();
    List<SelectedGameRef> selectedGames = readSelectedGames();
    if (selectedGames.isEmpty()) {
      return;
    }

    List<SelectedGameRef> startedGames =
        selectedGames.stream()
            .filter(game -> hasStarted(game.startTime(), now))
            .toList();
    if (startedGames.isEmpty()) {
      return;
    }

    Map<String, BetfairApiClient.EventIdentity> identityByMarketId =
        betfairApiClient.resolveEventIdentityForMarketIds(
            startedGames.stream().map(SelectedGameRef::marketId).toList());

    for (SelectedGameRef game : startedGames) {
      BetfairApiClient.EventIdentity identity = identityByMarketId.get(game.marketId());
      if (identity == null || identity.getEventId() == null || identity.getEventId().isBlank()) {
        LOGGER.debug("Skipping selected market {} because event identity could not be resolved", game.marketId());
        continue;
      }

      String[] teams = resolveTeams(game, identity.getEventName());
      List<EventMarket> markets =
          betfairApiClient.listMarketsForEvent(identity.getEventId(), ALLOWED_MARKET_TYPES);
      if (markets.isEmpty()) {
        LOGGER.debug(
            "No allowed markets found for selected market {} eventId={} ({})",
            game.marketId(),
            identity.getEventId(),
            identity.getEventName());
        continue;
      }

      for (EventMarket market : markets) {
        appendMarketSnapshot(game, teams[0], teams[1], market, now);
      }
    }
  }

  private List<SelectedGameRef> readSelectedGames() {
    if (!Files.exists(followedGamesDir)) {
      return List.of();
    }

    Map<String, SelectedGameRef> byMarketId = new LinkedHashMap<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(followedGamesDir, "selectedGames-*.txt")) {
      for (Path file : stream) {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (String line : lines) {
          SelectedGameRef ref = parseSelectedGameLine(line);
          if (ref == null) {
            continue;
          }
          byMarketId.put(ref.marketId(), ref);
        }
      }
    } catch (IOException ex) {
      LOGGER.warn("Failed to read selected games files", ex);
    }
    return new ArrayList<>(byMarketId.values());
  }

  private SelectedGameRef parseSelectedGameLine(String line) {
    if (line == null || line.isBlank()) {
      return null;
    }
    String[] parts = line.split(",", 3);
    String marketId = parts.length > 0 ? parts[0].trim() : "";
    String startTime = parts.length > 1 ? parts[1].trim() : "";
    String teams = parts.length > 2 ? parts[2].trim() : "";
    if (marketId.isBlank()) {
      return null;
    }
    String[] split = splitTeams(teams);
    return new SelectedGameRef(marketId, startTime, split[0], split[1]);
  }

  private boolean hasStarted(String startTime, Instant now) {
    Instant kickoff = parseInstant(startTime);
    if (kickoff == null) {
      return true;
    }
    return !kickoff.isAfter(now);
  }

  private String[] resolveTeams(SelectedGameRef game, String fallbackEventName) {
    if (!game.homeTeam().isBlank() && !game.awayTeam().isBlank()) {
      return new String[] {game.homeTeam(), game.awayTeam()};
    }
    return splitTeams(fallbackEventName);
  }

  private String[] splitTeams(String value) {
    if (value == null || value.isBlank()) {
      return new String[] {"home", "away"};
    }
    String normalized = value.trim();
    String delimiter = normalized.contains(" vs ") ? " vs " : (normalized.contains(" v ") ? " v " : null);
    if (delimiter == null) {
      return new String[] {normalized, "away"};
    }
    String[] parts = normalized.split(java.util.regex.Pattern.quote(delimiter), 2);
    String home = parts.length > 0 ? parts[0].trim() : "home";
    String away = parts.length > 1 ? parts[1].trim() : "away";
    return new String[] {home.isBlank() ? "home" : home, away.isBlank() ? "away" : away};
  }

  private void appendMarketSnapshot(
      SelectedGameRef selectedGame,
      String homeTeam,
      String awayTeam,
      EventMarket market,
      Instant now) {
    String marketType = normalizeMarketType(market.getMarketType());
    if (marketType.isBlank()) {
      return;
    }

    Instant kickoff = parseInstant(selectedGame.startTime());
    long gameMinute = kickoff == null ? 0L : Math.max(0L, (now.getEpochSecond() - kickoff.getEpochSecond()) / 60L);
    LocalDate folderDate = (kickoff == null ? now : kickoff).atOffset(ZoneOffset.UTC).toLocalDate();
    String day = DATE_FOLDER_FORMAT.format(folderDate);
    Path outputDir = followedGamesDir.resolve(day);
    Path outputFile =
        outputDir.resolve(
            sanitizeFileName(homeTeam)
                + "_"
                + sanitizeFileName(awayTeam)
                + "_"
                + day
                + "_"
                + marketType
                + ".txt");

    try {
      Files.createDirectories(outputDir);
      if (!Files.exists(outputFile)) {
        Files.write(
            outputFile,
            List.of(
                "timestamp,game_minute,market_id,market_type,market_status,runner_id,runner_name,back_odds,lay_odds"),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
      }

      Set<String> seenRunnerKeys = new LinkedHashSet<>();
      List<String> lines = new ArrayList<>();
      List<EventSelection> selections =
          market.getSelections() == null ? List.of() : market.getSelections();
      for (EventSelection selection : selections) {
        if (selection == null || selection.getSelectionId() == null) {
          continue;
        }
        String runnerKey = selection.getSelectionId() + "|" + sanitizeCsv(selection.getSelectionName());
        if (!seenRunnerKeys.add(runnerKey)) {
          continue;
        }
        lines.add(
            String.join(
                ",",
                now.toString(),
                String.valueOf(gameMinute),
                sanitizeCsv(market.getMarketId()),
                sanitizeCsv(marketType),
                sanitizeCsv(market.getMarketStatus()),
                String.valueOf(selection.getSelectionId()),
                sanitizeCsv(selection.getSelectionName()),
                formatOdds(selection.getBackOdds()),
                formatOdds(selection.getLayOdds())));
      }

      if (!lines.isEmpty()) {
        Files.write(
            outputFile,
            lines,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
      }
    } catch (IOException ex) {
      LOGGER.warn("Failed to write odds snapshot for market {}", market.getMarketId(), ex);
    }
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

  private String normalizeMarketType(String value) {
    return value == null ? "" : value.trim().toUpperCase();
  }

  private String sanitizeCsv(String value) {
    if (value == null) {
      return "";
    }
    return value.replace(",", " ").replace("\r", " ").replace("\n", " ").trim();
  }

  private String sanitizeFileName(String value) {
    String sanitized = sanitizeCsv(value).replaceAll("[^A-Za-z0-9._-]", "_");
    if (sanitized.isBlank()) {
      return "unknown";
    }
    return sanitized;
  }

  private String formatOdds(Double value) {
    if (value == null) {
      return "";
    }
    return String.format("%.2f", value);
  }

  private static final class SelectedGameRef {
    private final String marketId;
    private final String startTime;
    private final String homeTeam;
    private final String awayTeam;

    private SelectedGameRef(String marketId, String startTime, String homeTeam, String awayTeam) {
      this.marketId = Objects.requireNonNull(marketId);
      this.startTime = startTime == null ? "" : startTime;
      this.homeTeam = homeTeam == null ? "" : homeTeam;
      this.awayTeam = awayTeam == null ? "" : awayTeam;
    }

    private String marketId() {
      return marketId;
    }

    private String startTime() {
      return startTime;
    }

    private String homeTeam() {
      return homeTeam;
    }

    private String awayTeam() {
      return awayTeam;
    }
  }
}
