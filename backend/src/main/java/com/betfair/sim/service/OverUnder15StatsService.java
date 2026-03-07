package com.betfair.sim.service;

import com.betfair.sim.model.EventMarket;
import com.betfair.sim.model.EventSelection;
import com.betfair.sim.model.Game;
import com.betfair.sim.model.LiveGameEntry;
import com.betfair.sim.model.OverUnder15BetRecord;
import com.betfair.sim.model.OverUnder15StatusResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OverUnder15StatsService {
  private static final String STRATEGY_ID = "overunder1_5";
  private static final double STARTING_BANK = 1000d;
  private static final double STAKE = 20d;
  private static final int ENTRY_MINUTE = 20;
  private static final double MIN_ODDS = 1.45d;
  private static final DateTimeFormatter AUDIT_FILE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

  private final GameService gameService;
  private final JdbcTemplate jdbcTemplate;
  private final Path auditDir;

  public OverUnder15StatsService(
      GameService gameService,
      JdbcTemplate jdbcTemplate,
      @Value("${betfair.followed-games.dir:backend/data}") String followedGamesDir) {
    this.gameService = gameService;
    this.jdbcTemplate = jdbcTemplate;
    this.auditDir = FollowedGamesPathResolver.resolve(followedGamesDir).resolve("api");
    initSchema();
  }

  public synchronized OverUnder15StatusResponse getStatus() {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    List<LiveGameEntry> liveGames = gameService.betfairLiveGames();
    Map<String, LiveGameEntry> liveGamesByEventId = new LinkedHashMap<>();
    for (LiveGameEntry game : liveGames) {
      String eventId = safe(game.getEventId()).trim();
      if (!eventId.isBlank()) {
        liveGamesByEventId.put(eventId, game);
      }
    }
    Map<String, Double> over15ByEventId = loadOver15OddsByEventId(today);
    backfillOver15OddsFromBetfairMarkets(liveGames, over15ByEventId);
    Map<String, Game> betfairByEventId = loadBetfairGamesByEventId(today);

    Map<String, OverUnder15BetRecord> betsByEventId = loadBetsByEventId();
    String now = Instant.now().toString();

    for (LiveGameEntry game : liveGames) {
      String eventId = safe(game.getEventId()).trim();
      if (eventId.isBlank()) {
        continue;
      }
      OverUnder15BetRecord existing = betsByEventId.get(eventId);
      int minute = parseMinute(game.getMinute());
      int goals = parseGoals(game.getScore());
      Double odds = over15ByEventId.get(eventId);

      if (existing == null) {
        boolean shouldEnter =
            Boolean.TRUE.equals(game.isInPlay())
                && minute >= ENTRY_MINUTE
                && goals == 0
                && odds != null
                && odds >= MIN_ODDS;
        if (shouldEnter) {
          OverUnder15BetRecord created = new OverUnder15BetRecord();
          created.setStrategyId(STRATEGY_ID);
          created.setEventId(eventId);
          created.setMarketId(safe(game.getMarketId()));
          created.setHomeTeam(safe(game.getHomeTeam()));
          created.setAwayTeam(safe(game.getAwayTeam()));
          created.setLeague(safe(game.getLeague()));
          created.setPlacedAtMinute(safe(game.getMinute()));
          created.setOdds(odds);
          created.setStake(STAKE);
          created.setState("OPEN");
          created.setProfit(0d);
          created.setLatestScore(safe(game.getScore()));
          created.setLatestMinute(safe(game.getMinute()));
          created.setCreatedAt(now);
          created.setUpdatedAt(now);
          insertBet(created);
          appendAuditLine(created, now);
          betsByEventId.put(eventId, created);
        }
        continue;
      }

      existing.setLatestScore(safe(game.getScore()));
      existing.setLatestMinute(safe(game.getMinute()));
      existing.setUpdatedAt(now);
      if (existing.getMarketId() == null || existing.getMarketId().isBlank()) {
        existing.setMarketId(safe(game.getMarketId()));
      }

      if ("OPEN".equalsIgnoreCase(existing.getState())) {
        if (goals >= 2) {
          existing.setState("WON");
          existing.setProfit(round2((existing.getOdds() - 1d) * existing.getStake()));
          existing.setSettledAt(now);
        } else if (isFinished(game)) {
          existing.setState("LOST");
          existing.setProfit(round2(-existing.getStake()));
          existing.setSettledAt(now);
        }
      }
      updateBet(existing);
    }
    settleStaleOpenBets(betsByEventId, liveGamesByEventId, betfairByEventId, now);

    List<OverUnder15BetRecord> bets = loadBets();
    OverUnder15StatusResponse response = buildResponse(liveGames, bets, now);
    persistStrategyBalance(response, now);
    return response;
  }

  private OverUnder15StatusResponse buildResponse(
      List<LiveGameEntry> liveGames, List<OverUnder15BetRecord> bets, String updatedAt) {
    int wins = 0;
    int losses = 0;
    int open = 0;
    int finished = 0;
    double settledProfit = 0d;
    double wonValue = 0d;
    double lostValue = 0d;
    double openExposure = 0d;

    for (OverUnder15BetRecord bet : bets) {
      String state = safe(bet.getState()).toUpperCase(Locale.ROOT);
      if ("OPEN".equals(state)) {
        open++;
        openExposure += bet.getStake();
      } else {
        finished++;
        settledProfit += bet.getProfit();
      }
      if ("WON".equals(state)) {
        wins++;
        wonValue += Math.max(0d, bet.getProfit());
      } else if ("LOST".equals(state)) {
        losses++;
        lostValue += Math.abs(Math.min(0d, bet.getProfit()));
      }
    }

    OverUnder15StatusResponse response = new OverUnder15StatusResponse();
    response.setStrategyId(STRATEGY_ID);
    response.setStake(STAKE);
    response.setUpdatedAt(updatedAt);
    response.setLiveGames(liveGames);
    response.setBets(bets);
    response.setOpenBets(open);
    response.setFinishedBets(finished);
    response.setWins(wins);
    response.setLosses(losses);
    response.setSettledProfit(round2(settledProfit));
    response.setWonValue(round2(wonValue));
    response.setLostValue(round2(lostValue));
    response.setBank(round2(STARTING_BANK + settledProfit - openExposure));
    return response;
  }

  private Map<String, Double> loadOver15OddsByEventId(LocalDate date) {
    List<Game> oddsGames = gameService.betfairMatchOddsForDate(date.toString());
    Map<String, Double> out = new LinkedHashMap<>();
    for (Game game : oddsGames) {
      String eventId = safe(game.getId()).trim();
      if (eventId.isBlank()) {
        continue;
      }
      Double over15 = game.getOver15Odds();
      if (over15 != null && !over15.isNaN()) {
        out.put(eventId, over15);
      }
    }
    return out;
  }

  private Map<String, Game> loadBetfairGamesByEventId(LocalDate date) {
    List<Game> games = gameService.betfairMatchOddsForDate(date == null ? null : date.toString());
    Map<String, Game> out = new LinkedHashMap<>();
    for (Game game : games) {
      String eventId = safe(game.getId()).trim();
      if (!eventId.isBlank()) {
        out.put(eventId, game);
      }
    }
    return out;
  }

  private void backfillOver15OddsFromBetfairMarkets(
      List<LiveGameEntry> liveGames, Map<String, Double> over15ByEventId) {
    for (LiveGameEntry game : liveGames) {
      String eventId = safe(game.getEventId()).trim();
      if (eventId.isBlank() || over15ByEventId.containsKey(eventId)) {
        continue;
      }
      List<EventMarket> markets = gameService.betfairEventMarkets(eventId, List.of("OVER_UNDER_15"));
      Double over15 = extractOver15BackOdds(markets);
      if (over15 != null) {
        over15ByEventId.put(eventId, over15);
      }
    }
  }

  private Double extractOver15BackOdds(List<EventMarket> markets) {
    if (markets == null || markets.isEmpty()) {
      return null;
    }
    for (EventMarket market : markets) {
      String marketType = safe(market.getMarketType()).toUpperCase(Locale.ROOT);
      String marketName = safe(market.getMarketName()).toLowerCase(Locale.ROOT);
      if (!"OVER_UNDER_15".equals(marketType) && !marketName.contains("over/under 1.5")) {
        continue;
      }
      List<EventSelection> selections = market.getSelections();
      if (selections == null) {
        continue;
      }
      for (EventSelection selection : selections) {
        String name = safe(selection.getSelectionName()).toLowerCase(Locale.ROOT);
        if (!name.contains("over 1.5")) {
          continue;
        }
        Double odds = selection.getBackOdds();
        if (odds != null && !odds.isNaN()) {
          return odds;
        }
      }
    }
    return null;
  }

  private boolean isFinished(LiveGameEntry game) {
    String minute = safe(game.getMinute()).trim().toUpperCase(Locale.ROOT);
    if ("FT".equals(minute) || "FINISHED".equals(minute)) {
      return true;
    }
    String marketStatus = safe(game.getMarketStatus()).trim().toUpperCase(Locale.ROOT);
    if ("CLOSED".equals(marketStatus)) {
      return true;
    }
    return Boolean.FALSE.equals(game.isInPlay());
  }

  private void settleStaleOpenBets(
      Map<String, OverUnder15BetRecord> betsByEventId,
      Map<String, LiveGameEntry> liveGamesByEventId,
      Map<String, Game> betfairByEventId,
      String now) {
    for (OverUnder15BetRecord bet : betsByEventId.values()) {
      if (!"OPEN".equalsIgnoreCase(safe(bet.getState()))) {
        continue;
      }
      String eventId = safe(bet.getEventId()).trim();
      if (eventId.isBlank()) {
        continue;
      }
      if (liveGamesByEventId.containsKey(eventId)) {
        continue;
      }

      Game betfairGame = betfairByEventId.get(eventId);
      if (betfairGame != null) {
        String marketStatus = safe(betfairGame.getMarketStatus()).trim().toUpperCase(Locale.ROOT);
        if (isTerminalMarketStatus(marketStatus) || Boolean.FALSE.equals(betfairGame.getInPlay())) {
          settleFromLatestScore(bet, now);
          updateBet(bet);
          continue;
        }
      }

      // Fallback: if feed no longer returns the match and we have a stale 90+ snapshot,
      // close the bet using latest known score to avoid permanently stuck OPEN rows.
      if (isStaleAtOrPastNinety(bet, now)) {
        settleFromLatestScore(bet, now);
        updateBet(bet);
      }
    }
  }

  private boolean isTerminalMarketStatus(String marketStatus) {
    return "CLOSED".equals(marketStatus)
        || "INACTIVE".equals(marketStatus)
        || "SETTLED".equals(marketStatus);
  }

  private boolean isStaleAtOrPastNinety(OverUnder15BetRecord bet, String now) {
    int minute = parseMinute(bet.getLatestMinute());
    if (minute < 90) {
      return false;
    }
    try {
      Instant nowInstant = Instant.parse(now);
      Instant updated = Instant.parse(safe(bet.getUpdatedAt()).trim());
      return Duration.between(updated, nowInstant).toMinutes() >= 20;
    } catch (Exception ex) {
      return false;
    }
  }

  private void settleFromLatestScore(OverUnder15BetRecord bet, String now) {
    int goals = parseGoals(bet.getLatestScore());
    if (goals >= 2) {
      bet.setState("WON");
      bet.setProfit(round2((bet.getOdds() - 1d) * bet.getStake()));
    } else {
      bet.setState("LOST");
      bet.setProfit(round2(-bet.getStake()));
    }
    bet.setSettledAt(now);
    bet.setUpdatedAt(now);
  }

  private int parseMinute(String raw) {
    String text = safe(raw).trim();
    if (text.isBlank()) {
      return 0;
    }
    String upper = text.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    if ("HT".equals(upper) || "HT'".equals(upper)) {
      return 45;
    }
    String normalized = upper.replace("’", "'").replaceAll("[^0-9+']", "");
    if (normalized.isBlank()) {
      return 0;
    }
    String base = normalized.endsWith("'") ? normalized.substring(0, normalized.length() - 1) : normalized;
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

  private int parseGoals(String rawScore) {
    String text = safe(rawScore).trim();
    if (!text.matches("\\d+\\s*-\\s*\\d+")) {
      return -1;
    }
    String[] parts = text.split("-");
    try {
      return Integer.parseInt(parts[0].trim()) + Integer.parseInt(parts[1].trim());
    } catch (Exception ex) {
      return -1;
    }
  }

  private Map<String, OverUnder15BetRecord> loadBetsByEventId() {
    Map<String, OverUnder15BetRecord> out = new LinkedHashMap<>();
    for (OverUnder15BetRecord bet : loadBets()) {
      out.put(bet.getEventId(), bet);
    }
    return out;
  }

  private List<OverUnder15BetRecord> loadBets() {
    return jdbcTemplate.query(
        "SELECT strategy_id, event_id, market_id, home_team, away_team, league, placed_at_minute, "
            + "odds, stake, state, profit, latest_score, latest_minute, created_at, updated_at, settled_at "
            + "FROM overunder_15_stats WHERE strategy_id = ? ORDER BY created_at DESC",
        (rs, rowNum) -> {
          OverUnder15BetRecord bet = new OverUnder15BetRecord();
          bet.setStrategyId(rs.getString("strategy_id"));
          bet.setEventId(rs.getString("event_id"));
          bet.setMarketId(rs.getString("market_id"));
          bet.setHomeTeam(rs.getString("home_team"));
          bet.setAwayTeam(rs.getString("away_team"));
          bet.setLeague(rs.getString("league"));
          bet.setPlacedAtMinute(rs.getString("placed_at_minute"));
          bet.setOdds(rs.getDouble("odds"));
          bet.setStake(rs.getDouble("stake"));
          bet.setState(rs.getString("state"));
          bet.setProfit(rs.getDouble("profit"));
          bet.setLatestScore(rs.getString("latest_score"));
          bet.setLatestMinute(rs.getString("latest_minute"));
          bet.setCreatedAt(
              rs.getTimestamp("created_at") == null ? "" : rs.getTimestamp("created_at").toInstant().toString());
          bet.setUpdatedAt(
              rs.getTimestamp("updated_at") == null ? "" : rs.getTimestamp("updated_at").toInstant().toString());
          bet.setSettledAt(
              rs.getTimestamp("settled_at") == null ? "" : rs.getTimestamp("settled_at").toInstant().toString());
          return bet;
        },
        STRATEGY_ID);
  }

  private void insertBet(OverUnder15BetRecord bet) {
    jdbcTemplate.update(
        "INSERT INTO overunder_15_stats ("
            + "strategy_id, event_id, market_id, home_team, away_team, league, placed_at_minute, "
            + "odds, stake, state, profit, latest_score, latest_minute, created_at, updated_at, settled_at"
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)",
        bet.getStrategyId(),
        bet.getEventId(),
        bet.getMarketId(),
        bet.getHomeTeam(),
        bet.getAwayTeam(),
        bet.getLeague(),
        bet.getPlacedAtMinute(),
        bet.getOdds(),
        bet.getStake(),
        bet.getState(),
        bet.getProfit(),
        bet.getLatestScore(),
        bet.getLatestMinute(),
        toTimestampOrNull(bet.getSettledAt()));
  }

  private void updateBet(OverUnder15BetRecord bet) {
    jdbcTemplate.update(
        "UPDATE overunder_15_stats SET market_id = ?, latest_score = ?, latest_minute = ?, state = ?, "
            + "profit = ?, updated_at = CURRENT_TIMESTAMP, settled_at = ? "
            + "WHERE strategy_id = ? AND event_id = ?",
        bet.getMarketId(),
        bet.getLatestScore(),
        bet.getLatestMinute(),
        bet.getState(),
        bet.getProfit(),
        toTimestampOrNull(bet.getSettledAt()),
        bet.getStrategyId(),
        bet.getEventId());
  }

  private void persistStrategyBalance(OverUnder15StatusResponse status, String updatedAt) {
    jdbcTemplate.update(
        "MERGE INTO overunder_strategy_balances ("
            + "strategy_id, current_balance, settled_profit, open_bets, finished_bets, wins, losses, "
            + "won_value, lost_value, stake, updated_at"
            + ") KEY(strategy_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
        status.getStrategyId(),
        status.getBank(),
        status.getSettledProfit(),
        status.getOpenBets(),
        status.getFinishedBets(),
        status.getWins(),
        status.getLosses(),
        status.getWonValue(),
        status.getLostValue(),
        status.getStake());
  }

  private Timestamp toTimestampOrNull(String instantText) {
    String text = safe(instantText).trim();
    if (text.isBlank()) {
      return null;
    }
    try {
      return Timestamp.from(Instant.parse(text));
    } catch (Exception ex) {
      return null;
    }
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private double round2(double value) {
    return Math.round(value * 100d) / 100d;
  }

  private void appendAuditLine(OverUnder15BetRecord bet, String placedAt) {
    String timestamp = safe(placedAt).isBlank() ? Instant.now().toString() : placedAt;
    LocalDate day = resolveAuditDay(timestamp);
    Path file = auditDir.resolve("overunder_15_audit_" + day.format(AUDIT_FILE_DATE));
    String line =
        String.format(
            Locale.ROOT,
            "%s vs %s | placeBetAt=%s | minute=%s | odd=%.2f%n",
            safe(bet.getHomeTeam()),
            safe(bet.getAwayTeam()),
            timestamp,
            safe(bet.getPlacedAtMinute()),
            round2(bet.getOdds()));
    try {
      Files.createDirectories(auditDir);
      Files.writeString(
          file,
          line,
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to append overunder 1.5 audit", ex);
    }
  }

  private LocalDate resolveAuditDay(String timestamp) {
    try {
      return Instant.parse(timestamp).atZone(ZoneOffset.UTC).toLocalDate();
    } catch (Exception ignored) {
      return LocalDate.now(ZoneOffset.UTC);
    }
  }

  private void initSchema() {
    jdbcTemplate.execute(
        "CREATE TABLE IF NOT EXISTS overunder_15_stats ("
            + "id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,"
            + "strategy_id VARCHAR(64) NOT NULL,"
            + "event_id VARCHAR(64) NOT NULL,"
            + "market_id VARCHAR(64),"
            + "home_team VARCHAR(255),"
            + "away_team VARCHAR(255),"
            + "league VARCHAR(255),"
            + "placed_at_minute VARCHAR(32),"
            + "odds DOUBLE NOT NULL,"
            + "stake DOUBLE NOT NULL,"
            + "state VARCHAR(16) NOT NULL,"
            + "profit DOUBLE NOT NULL DEFAULT 0,"
            + "latest_score VARCHAR(32),"
            + "latest_minute VARCHAR(32),"
            + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            + "settled_at TIMESTAMP,"
            + "UNIQUE(strategy_id, event_id)"
            + ")");

    jdbcTemplate.execute(
        "CREATE TABLE IF NOT EXISTS overunder_strategy_balances ("
            + "strategy_id VARCHAR(64) PRIMARY KEY,"
            + "current_balance DOUBLE NOT NULL,"
            + "settled_profit DOUBLE NOT NULL,"
            + "open_bets INT NOT NULL,"
            + "finished_bets INT NOT NULL,"
            + "wins INT NOT NULL,"
            + "losses INT NOT NULL,"
            + "won_value DOUBLE NOT NULL,"
            + "lost_value DOUBLE NOT NULL,"
            + "stake DOUBLE NOT NULL,"
            + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
            + ")");
  }
}
