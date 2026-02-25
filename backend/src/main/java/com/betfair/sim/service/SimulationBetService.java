package com.betfair.sim.service;

import com.betfair.sim.model.SimulationBetEntry;
import com.betfair.sim.model.SimulationBetRecord;
import com.betfair.sim.model.SimulationBetStatusResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SimulationBetService {
  private static final Logger LOGGER = LoggerFactory.getLogger(SimulationBetService.class);
  private final BetfairApiClient betfairApiClient;
  private final StatpalLiveClient statpalLiveClient;
  private final ObjectMapper objectMapper;
  private final Path dataDir;
  private final Path betsFile;
  private final double startBalance;

  public SimulationBetService(
      BetfairApiClient betfairApiClient,
      StatpalLiveClient statpalLiveClient,
      ObjectMapper objectMapper,
      @Value("${betfair.simulation.dir:backend/data}") String dataDir,
      @Value("${betfair.simulation.start-balance:1000}") double startBalance) {
    this.betfairApiClient = betfairApiClient;
    this.statpalLiveClient = statpalLiveClient;
    this.objectMapper = objectMapper;
    this.dataDir = Paths.get(dataDir);
    this.betsFile = this.dataDir.resolve("simulation-bets.jsonl");
    this.startBalance = startBalance;
  }

  public synchronized Path appendBets(
      String strategyId, String strategyName, List<SimulationBetEntry> bets) {
    List<SimulationBetRecord> records = new ArrayList<>();
    String createdAt = Instant.now().toString();
    for (SimulationBetEntry entry : bets) {
      if (entry == null) {
        continue;
      }
      if (entry.getMarketId() == null || entry.getMarketId().isBlank()) {
        continue;
      }
      SimulationBetRecord record = new SimulationBetRecord();
      record.setId(UUID.randomUUID().toString());
      record.setCreatedAt(createdAt);
      record.setStrategyId(strategyId);
      record.setStrategyName(strategyName);
      record.setMarketId(entry.getMarketId());
      record.setSelectionId(entry.getSelectionId());
      record.setSelectionName(entry.getSelectionName());
      record.setHomeTeam(entry.getHomeTeam());
      record.setAwayTeam(entry.getAwayTeam());
      record.setSide(entry.getSide());
      record.setOdds(entry.getOdds());
      record.setStake(entry.getStake());
      record.setStatus("OPEN");
      record.setMarketStatus("");
      record.setInPlay(false);
      record.setProfit(null);
      records.add(record);
    }
    if (records.isEmpty()) {
      return betsFile;
    }
    try {
      Files.createDirectories(dataDir);
      List<String> lines = new ArrayList<>();
      for (SimulationBetRecord record : records) {
        lines.add(objectMapper.writeValueAsString(record));
      }
      Files.write(
          betsFile,
          lines,
          StandardCharsets.UTF_8,
          Files.exists(betsFile)
              ? java.nio.file.StandardOpenOption.APPEND
              : java.nio.file.StandardOpenOption.CREATE);
      return betsFile;
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to save simulation bets", ex);
    }
  }

  public synchronized SimulationBetStatusResponse getStatus() {
    List<SimulationBetRecord> bets = loadBets();
    if (bets.isEmpty() || !betfairApiClient.isEnabled()) {
      return buildStatusResponse(bets, Instant.now().toString());
    }

    List<String> marketIds =
        bets.stream()
            .map(SimulationBetRecord::getMarketId)
            .filter(Objects::nonNull)
            .filter(id -> !id.isBlank())
            .distinct()
            .toList();
    if (marketIds.isEmpty()) {
      return buildStatusResponse(bets, Instant.now().toString());
    }

    Map<String, BetfairApiClient.MarketOutcome> outcomes =
        betfairApiClient.getMarketOutcomes(marketIds);
    Map<String, BetfairApiClient.MarketStatus> marketStatuses =
        betfairApiClient.getMarketStatuses(marketIds);
    Map<String, BetfairApiClient.InferredScore> correctScoreInferred =
        betfairApiClient.inferScoresFromCorrectScoreMarkets(marketIds);
    Map<String, BetfairApiClient.InferredScore> inferredScores =
        betfairApiClient.inferScoresFromClosedGoalMarkets(marketIds);
    boolean updated = false;
    boolean trackingUpdated = false;
    Instant nowInstant = Instant.now();
    String now = nowInstant.toString();
    for (SimulationBetRecord bet : bets) {
      boolean wasInPlay = bet.isInPlay();
      BetfairApiClient.MarketOutcome outcome = outcomes.get(bet.getMarketId());
      BetfairApiClient.MarketStatus marketStatus = marketStatuses.get(bet.getMarketId());

      if (marketStatus != null && marketStatus.getStartTime() != null) {
        bet.setMarketStartTime(marketStatus.getStartTime().toString());
      }

      String resolvedMarketStatus =
          outcome != null
              ? outcome.getStatus()
              : marketStatus == null ? bet.getMarketStatus() : marketStatus.getStatus();
      boolean resolvedInPlay =
          outcome != null
              ? outcome.isInPlay()
              : marketStatus != null && marketStatus.isInPlay();

      if (resolvedMarketStatus != null) {
        bet.setMarketStatus(resolvedMarketStatus);
      }
      bet.setInPlay(resolvedInPlay);
      if (outcome != null) {
        if (outcome.getHomeScore() != null) {
          bet.setHomeScore(outcome.getHomeScore());
        }
        if (outcome.getAwayScore() != null) {
          bet.setAwayScore(outcome.getAwayScore());
        }
      }
      BetfairApiClient.InferredScore inferredScore = correctScoreInferred.get(bet.getMarketId());
      if (inferredScore == null) {
        inferredScore = inferredScores.get(bet.getMarketId());
      }
      if (inferredScore != null) {
        if (bet.getHomeScore() == null && inferredScore.getHomeScore() != null) {
          bet.setHomeScore(inferredScore.getHomeScore());
        }
        if (bet.getAwayScore() == null && inferredScore.getAwayScore() != null) {
          bet.setAwayScore(inferredScore.getAwayScore());
        }
        bet.setInferredScore(inferredScore.getLabel());
      }
      if (updateInPlayTracking(bet, wasInPlay, resolvedInPlay, marketStatus, nowInstant)) {
        trackingUpdated = true;
      }
      bet.setMatchClock(resolveMatchClock(bet, marketStatus, nowInstant));

      if (outcome == null) {
        continue;
      }
      if (!"SETTLED".equalsIgnoreCase(bet.getStatus())
          && "CLOSED".equalsIgnoreCase(outcome.getStatus())
          && outcome.getWinnerSelectionId() != null) {
        double profit = calculateProfit(bet, outcome.getWinnerSelectionId());
        bet.setProfit(profit);
        bet.setStatus("SETTLED");
        bet.setSettledAt(now);
        updated = true;
      }
    }

    if (updated || trackingUpdated) {
      saveAll(bets);
    }

    return buildStatusResponse(bets, now);
  }

  private String resolveMatchClock(
      SimulationBetRecord bet,
      BetfairApiClient.MarketStatus marketStatus,
      Instant now) {
    String status = bet.getMarketStatus();
    if (status != null && "CLOSED".equalsIgnoreCase(status)) {
      return "Finished";
    }
    if (!bet.isInPlay()) {
      long pausedAt = calculateElapsedInPlaySeconds(bet, now);
      if (pausedAt > 0L) {
        return "Interrupted " + Math.min(90L, Math.max(1L, pausedAt / 60L)) + "'";
      }
      return "Not started";
    }

    long elapsedSeconds = calculateElapsedInPlaySeconds(bet, now);
    if (elapsedSeconds <= 0L) {
      return "Live";
    }

    long elapsedMinutes = Math.max(1L, elapsedSeconds / 60L);
    if (elapsedMinutes >= 90L) {
      return "90'";
    }
    return elapsedMinutes + "'";
  }

  private void applyStatpalLiveData(
      SimulationBetRecord bet, List<StatpalLiveClient.LiveMatch> statpalLiveMatches) {
    if (statpalLiveMatches == null || statpalLiveMatches.isEmpty()) {
      LOGGER.debug(
          "[STATPAL_DEBUG] no live matches available for bet {} vs {}",
          bet.getHomeTeam(),
          bet.getAwayTeam());
      return;
    }
    StatpalLiveClient.LiveMatch match = findBestStatpalMatch(bet, statpalLiveMatches);
    if (match == null) {
      LOGGER.debug(
          "[STATPAL_DEBUG] no Statpal match found for bet {} vs {}",
          bet.getHomeTeam(),
          bet.getAwayTeam());
      return;
    }
    LOGGER.debug(
        "[STATPAL_DEBUG] matched bet {} vs {} -> {} vs {} (status={} minute={} score={}{}{})",
        bet.getHomeTeam(),
        bet.getAwayTeam(),
        match.getHomeTeam(),
        match.getAwayTeam(),
        match.getStatus(),
        match.getMinute(),
        match.getHomeGoals() == null ? "?" : match.getHomeGoals(),
        "-",
        match.getAwayGoals() == null ? "?" : match.getAwayGoals());
    if (match.getHomeGoals() != null && match.getAwayGoals() != null) {
      bet.setHomeScore(match.getHomeGoals());
      bet.setAwayScore(match.getAwayGoals());
      bet.setInferredScore(match.getHomeGoals() + "-" + match.getAwayGoals());
    }
    String statpalClock = buildClockFromStatpal(match);
    if (!statpalClock.isBlank()) {
      bet.setMatchClock(statpalClock);
    }
  }

  private StatpalLiveClient.LiveMatch findBestStatpalMatch(
      SimulationBetRecord bet, List<StatpalLiveClient.LiveMatch> matches) {
    String home = StatpalLiveClient.normalizeName(bet.getHomeTeam());
    String away = StatpalLiveClient.normalizeName(bet.getAwayTeam());
    if (home.isBlank() || away.isBlank()) {
      return null;
    }
    StatpalLiveClient.LiveMatch best = null;
    double bestScore = -1d;
    for (StatpalLiveClient.LiveMatch match : matches) {
      String statHome = StatpalLiveClient.normalizeName(match.getHomeTeam());
      String statAway = StatpalLiveClient.normalizeName(match.getAwayTeam());
      double score = matchScore(home, away, statHome, statAway);
      if (score > bestScore) {
        bestScore = score;
        best = match;
      }
    }
    return bestScore >= 1.15d ? best : null;
  }

  private double matchScore(String home, String away, String statHome, String statAway) {
    double direct = teamSimilarity(home, statHome) + teamSimilarity(away, statAway);
    double swapped = teamSimilarity(home, statAway) + teamSimilarity(away, statHome);
    return Math.max(direct, swapped);
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

  private String buildClockFromStatpal(StatpalLiveClient.LiveMatch match) {
    String status = match.getStatus() == null ? "" : match.getStatus().trim().toUpperCase();
    if ("HT".equals(status)) {
      return "HT";
    }
    if ("FT".equals(status) || "AET".equals(status) || "PEN".equals(status)) {
      return "Finished";
    }
    String minute = match.getMinute() == null ? "" : match.getMinute().trim();
    if (!minute.isBlank()) {
      return minute.endsWith("'") ? minute : minute + "'";
    }
    if (!status.isBlank()) {
      return status;
    }
    return "";
  }

  private boolean updateInPlayTracking(
      SimulationBetRecord bet,
      boolean wasInPlay,
      boolean isInPlay,
      BetfairApiClient.MarketStatus marketStatus,
      Instant now) {
    boolean changed = false;
    String liveStartedAt = bet.getLiveStartedAt();

    if (isInPlay) {
      if (!wasInPlay || liveStartedAt == null || liveStartedAt.isBlank()) {
        bet.setLiveStartedAt(resolveLiveAnchor(now, marketStatus));
        changed = true;
      }
      return changed;
    }

    if (wasInPlay) {
      long additional = secondsSince(liveStartedAt, now);
      if (additional > 0L) {
        bet.setAccumulatedInPlaySeconds(
            Math.max(0L, bet.getAccumulatedInPlaySeconds()) + additional);
      }
      bet.setLiveStartedAt(null);
      changed = true;
    }
    return changed;
  }

  private String resolveLiveAnchor(Instant now, BetfairApiClient.MarketStatus marketStatus) {
    if (marketStatus == null || marketStatus.getStartTime() == null) {
      return now.toString();
    }
    Instant start = marketStatus.getStartTime();
    if (start.isAfter(now)) {
      return now.toString();
    }
    long elapsedMinutes = (now.getEpochSecond() - start.getEpochSecond()) / 60L;
    if (elapsedMinutes <= 3L) {
      return now.toString();
    }
    return start.toString();
  }

  private long calculateElapsedInPlaySeconds(SimulationBetRecord bet, Instant now) {
    long total = Math.max(0L, bet.getAccumulatedInPlaySeconds());
    if (!bet.isInPlay()) {
      return total;
    }
    return total + secondsSince(bet.getLiveStartedAt(), now);
  }

  private long secondsSince(String startIso, Instant now) {
    if (startIso == null || startIso.isBlank()) {
      return 0L;
    }
    try {
      Instant start = Instant.parse(startIso);
      long delta = now.getEpochSecond() - start.getEpochSecond();
      return Math.max(0L, delta);
    } catch (Exception ignored) {
      return 0L;
    }
  }

  private double calculateProfit(SimulationBetRecord bet, long winnerSelectionId) {
    boolean isWinner = bet.getSelectionId() == winnerSelectionId;
    double odds = bet.getOdds();
    double stake = bet.getStake();
    if ("lay".equalsIgnoreCase(bet.getSide())) {
      return isWinner ? -(odds - 1.0) * stake : stake;
    }
    return isWinner ? (odds - 1.0) * stake : -stake;
  }

  private SimulationBetStatusResponse buildStatusResponse(
      List<SimulationBetRecord> bets, String timestamp) {
    double globalBalance =
        startBalance
            + bets.stream()
                .filter(bet -> bet.getProfit() != null)
                .mapToDouble(SimulationBetRecord::getProfit)
                .sum();
    Map<String, Double> byStrategy = new LinkedHashMap<>();
    for (SimulationBetRecord bet : bets) {
      if (bet.getProfit() == null) {
        continue;
      }
      String key = bet.getStrategyName();
      if (key == null || key.isBlank()) {
        key = bet.getStrategyId() == null ? "Unknown" : bet.getStrategyId();
      }
      byStrategy.put(key, byStrategy.getOrDefault(key, 0.0) + bet.getProfit());
    }

    int valueGlobalWins = 0;
    int valueGlobalLosses = 0;
    Map<String, SimulationBetStatusResponse.WinLossCount> valueDailyWinLosses = new LinkedHashMap<>();
    for (SimulationBetRecord bet : bets) {
      if (!"SETTLED".equalsIgnoreCase(bet.getStatus())) {
        continue;
      }
      boolean isValueStrategy =
          "value".equalsIgnoreCase(bet.getStrategyId())
              || "value".equalsIgnoreCase(bet.getStrategyName());
      if (!isValueStrategy) {
        continue;
      }
      if (bet.getProfit() == null) {
        continue;
      }
      double profit = bet.getProfit();
      if (profit == 0.0d) {
        continue;
      }

      String dayKey = resolveDayKey(bet);
      SimulationBetStatusResponse.WinLossCount daily =
          valueDailyWinLosses.computeIfAbsent(dayKey, key -> new SimulationBetStatusResponse.WinLossCount());
      if (profit > 0) {
        valueGlobalWins++;
        daily.setWins(daily.getWins() + 1);
      } else {
        valueGlobalLosses++;
        daily.setLosses(daily.getLosses() + 1);
      }
    }

    return new SimulationBetStatusResponse(
        globalBalance,
        byStrategy,
        valueGlobalWins,
        valueGlobalLosses,
        valueDailyWinLosses,
        bets,
        timestamp);
  }

  private String resolveDayKey(SimulationBetRecord bet) {
    String source = bet.getSettledAt();
    if (source == null || source.isBlank()) {
      source = bet.getCreatedAt();
    }
    if (source == null || source.isBlank()) {
      return "Unknown";
    }
    try {
      LocalDate date = Instant.parse(source).atZone(ZoneOffset.UTC).toLocalDate();
      return date.toString();
    } catch (Exception ignored) {
      return "Unknown";
    }
  }

  private List<SimulationBetRecord> loadBets() {
    if (!Files.exists(betsFile)) {
      return new ArrayList<>();
    }
    try {
      List<String> lines = Files.readAllLines(betsFile, StandardCharsets.UTF_8);
      List<SimulationBetRecord> bets = new ArrayList<>();
      for (String line : lines) {
        if (line == null || line.isBlank()) {
          continue;
        }
        bets.add(objectMapper.readValue(line, SimulationBetRecord.class));
      }
      return bets;
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to read simulation bets", ex);
    }
  }

  private void saveAll(List<SimulationBetRecord> bets) {
    try {
      Files.createDirectories(dataDir);
      List<String> lines =
          bets.stream()
              .map(bet -> {
                try {
                  return objectMapper.writeValueAsString(bet);
                } catch (Exception ex) {
                  return null;
                }
              })
              .filter(Objects::nonNull)
              .collect(Collectors.toList());
      Files.write(betsFile, lines, StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to update simulation bets", ex);
    }
  }
}
