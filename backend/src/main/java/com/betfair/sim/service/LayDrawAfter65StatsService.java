package com.betfair.sim.service;

import com.betfair.sim.model.Game;
import com.betfair.sim.model.LayDrawAfterHt00BetRecord;
import com.betfair.sim.model.LayDrawAfterHt00StatusResponse;
import com.betfair.sim.model.LiveGameEntry;
import com.betfair.sim.model.MappedLiveGameEntry;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class LayDrawAfter65StatsService {
  private static final String STRATEGY_ID = "laydraw_after_65_tie_lt2";
  private static final double STARTING_BANK = 1000d;
  private static final double STAKE = 20d;
  private static final int ENTRY_MINUTE = 65;
  private static final int ENTRY_MAX_MINUTE = 76;
  private static final double MAX_DRAW_LAY_ODDS = 2.0d;

  private final GameService gameService;
  private final JdbcTemplate jdbcTemplate;

  public LayDrawAfter65StatsService(GameService gameService, JdbcTemplate jdbcTemplate) {
    this.gameService = gameService;
    this.jdbcTemplate = jdbcTemplate;
    initSchema();
  }

  public synchronized LayDrawAfterHt00StatusResponse getStatus() {
    List<LiveGameEntry> liveGames = runWithTimeout(gameService::betfairLiveGames, List.of(), 10000);
    Map<String, LiveGameEntry> liveGamesByEventId = new LinkedHashMap<>();
    for (LiveGameEntry game : liveGames) {
      String eventId = safe(game.getEventId()).trim();
      if (!eventId.isBlank()) {
        liveGamesByEventId.put(eventId, game);
      }
    }
    Map<String, Game> betfairByEventId =
        runWithTimeout(() -> loadBetfairGamesByEventId(LocalDate.now(ZoneOffset.UTC)), Map.of(), 1500);
    Map<String, MappedLiveGameEntry> statpalByEventId = runWithTimeout(this::loadStatpalMappedByEventId, Map.of(), 3500);
    Map<String, LayDrawAfterHt00BetRecord> betsByEventId = loadBetsByEventId();
    pruneInvalidOpenBetsByEntryWindow(betsByEventId);
    String now = Instant.now().toString();

    for (LiveGameEntry game : liveGames) {
      String eventId = safe(game.getEventId()).trim();
      if (eventId.isBlank()) {
        continue;
      }
      int minute = parseMinute(game.getMinute());
      Integer homeGoals = parseHomeGoals(game.getScore());
      Integer awayGoals = parseAwayGoals(game.getScore());
      boolean tied = homeGoals != null && awayGoals != null && homeGoals.intValue() == awayGoals.intValue();
      Double drawBack = game.getDrawOdds();
      Double drawLay = game.getDrawLayOdds();

      LayDrawAfterHt00BetRecord existing = betsByEventId.get(eventId);
      if (existing == null) {
        if (shouldOpen(game, minute, tied, drawLay)) {
          LayDrawAfterHt00BetRecord created = new LayDrawAfterHt00BetRecord();
          created.setStrategyId(STRATEGY_ID);
          created.setEventId(eventId);
          created.setMarketId(safe(game.getMarketId()));
          created.setHomeTeam(safe(game.getHomeTeam()));
          created.setAwayTeam(safe(game.getAwayTeam()));
          created.setLeague(safe(game.getLeague()));
          created.setPlacedAtMinute(safe(game.getMinute()));
          created.setEntryScore(safe(game.getScore()));
          created.setEntryBackOdds(round2(drawBack == null ? 0d : drawBack));
          created.setEntryLayOdds(round2(drawLay));
          created.setStake(STAKE);
          created.setState("OPEN");
          created.setProfit(0d);
          created.setExitAtMinute("");
          created.setExitBackOdds(0d);
          created.setCloseReason("");
          created.setLatestScore(safe(game.getScore()));
          created.setLatestMinute(safe(game.getMinute()));
          created.setCreatedAt(now);
          created.setUpdatedAt(now);
          insertBet(created);
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

      if ("OPEN".equalsIgnoreCase(existing.getState()) && isFinished(game)) {
        settleByFinalScore(existing, tied, game.getMinute(), now);
      }
      updateBet(existing);
    }

    settleStaleOpenBets(betsByEventId, liveGamesByEventId, betfairByEventId, statpalByEventId, now);

    List<LayDrawAfterHt00BetRecord> bets = loadBets();
    LayDrawAfterHt00StatusResponse response = buildResponse(liveGames, bets, now);
    persistStrategyBalance(response);
    return response;
  }

  private boolean shouldOpen(LiveGameEntry game, int minute, boolean tied, Double drawLay) {
    if (!Boolean.TRUE.equals(game.isInPlay())) {
      return false;
    }
    if (minute < ENTRY_MINUTE || minute > ENTRY_MAX_MINUTE) {
      return false;
    }
    if (!tied) {
      return false;
    }
    return drawLay != null && drawLay > 1.01d && drawLay < MAX_DRAW_LAY_ODDS;
  }

  private void settleByFinalScore(
      LayDrawAfterHt00BetRecord bet, boolean tied, String exitMinute, String now) {
    double layOdds = Math.max(1.01d, bet.getEntryLayOdds());
    double layStake = Math.max(0d, bet.getStake());
    if (tied) {
      bet.setProfit(round2(-((layOdds - 1d) * layStake)));
      bet.setState("LOST");
      bet.setCloseReason("FINISHED_DRAW");
    } else {
      bet.setProfit(round2(layStake));
      bet.setState("WON");
      bet.setCloseReason("FINISHED_NOT_DRAW");
    }
    bet.setExitBackOdds(0d);
    bet.setExitAtMinute(safe(exitMinute));
    bet.setSettledAt(now);
    bet.setUpdatedAt(now);
  }

  private LayDrawAfterHt00StatusResponse buildResponse(
      List<LiveGameEntry> liveGames, List<LayDrawAfterHt00BetRecord> bets, String updatedAt) {
    int wins = 0;
    int losses = 0;
    int open = 0;
    int finished = 0;
    double settledProfit = 0d;
    double wonValue = 0d;
    double lostValue = 0d;
    double openExposure = 0d;

    for (LayDrawAfterHt00BetRecord bet : bets) {
      String state = safe(bet.getState()).toUpperCase(Locale.ROOT);
      if ("OPEN".equals(state)) {
        open++;
        openExposure += (Math.max(1.01d, bet.getEntryLayOdds()) - 1d) * Math.max(0d, bet.getStake());
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

    LayDrawAfterHt00StatusResponse response = new LayDrawAfterHt00StatusResponse();
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

  private Map<String, LayDrawAfterHt00BetRecord> loadBetsByEventId() {
    Map<String, LayDrawAfterHt00BetRecord> out = new LinkedHashMap<>();
    for (LayDrawAfterHt00BetRecord bet : loadBets()) {
      out.put(bet.getEventId(), bet);
    }
    return out;
  }

  private List<LayDrawAfterHt00BetRecord> loadBets() {
    return jdbcTemplate.query(
        "SELECT strategy_id, event_id, market_id, home_team, away_team, league, placed_at_minute, entry_score, "
            + "entry_back_odds, entry_lay_odds, stake, state, profit, exit_at_minute, exit_back_odds, close_reason, "
            + "latest_score, latest_minute, created_at, updated_at, settled_at "
            + "FROM laydraw_after_65_stats WHERE strategy_id = ? ORDER BY created_at DESC",
        (rs, rowNum) -> {
          LayDrawAfterHt00BetRecord bet = new LayDrawAfterHt00BetRecord();
          bet.setStrategyId(rs.getString("strategy_id"));
          bet.setEventId(rs.getString("event_id"));
          bet.setMarketId(rs.getString("market_id"));
          bet.setHomeTeam(rs.getString("home_team"));
          bet.setAwayTeam(rs.getString("away_team"));
          bet.setLeague(rs.getString("league"));
          bet.setPlacedAtMinute(rs.getString("placed_at_minute"));
          bet.setEntryScore(rs.getString("entry_score"));
          bet.setEntryBackOdds(rs.getDouble("entry_back_odds"));
          bet.setEntryLayOdds(rs.getDouble("entry_lay_odds"));
          bet.setStake(rs.getDouble("stake"));
          bet.setState(rs.getString("state"));
          bet.setProfit(rs.getDouble("profit"));
          bet.setExitAtMinute(rs.getString("exit_at_minute"));
          bet.setExitBackOdds(rs.getDouble("exit_back_odds"));
          bet.setCloseReason(rs.getString("close_reason"));
          bet.setLatestScore(rs.getString("latest_score"));
          bet.setLatestMinute(rs.getString("latest_minute"));
          bet.setCreatedAt(
              rs.getTimestamp("created_at") == null
                  ? ""
                  : rs.getTimestamp("created_at").toInstant().toString());
          bet.setUpdatedAt(
              rs.getTimestamp("updated_at") == null
                  ? ""
                  : rs.getTimestamp("updated_at").toInstant().toString());
          bet.setSettledAt(
              rs.getTimestamp("settled_at") == null
                  ? ""
                  : rs.getTimestamp("settled_at").toInstant().toString());
          return bet;
        },
        STRATEGY_ID);
  }

  private void insertBet(LayDrawAfterHt00BetRecord bet) {
    jdbcTemplate.update(
        "INSERT INTO laydraw_after_65_stats ("
            + "strategy_id, event_id, market_id, home_team, away_team, league, placed_at_minute, "
            + "entry_score, entry_back_odds, entry_lay_odds, stake, state, profit, exit_at_minute, exit_back_odds, close_reason, "
            + "latest_score, latest_minute, created_at, updated_at, settled_at"
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)",
        bet.getStrategyId(),
        bet.getEventId(),
        bet.getMarketId(),
        bet.getHomeTeam(),
        bet.getAwayTeam(),
        bet.getLeague(),
        bet.getPlacedAtMinute(),
        bet.getEntryScore(),
        bet.getEntryBackOdds(),
        bet.getEntryLayOdds(),
        bet.getStake(),
        bet.getState(),
        bet.getProfit(),
        bet.getExitAtMinute(),
        bet.getExitBackOdds(),
        bet.getCloseReason(),
        bet.getLatestScore(),
        bet.getLatestMinute(),
        toTimestampOrNull(bet.getSettledAt()));
  }

  private void updateBet(LayDrawAfterHt00BetRecord bet) {
    jdbcTemplate.update(
        "UPDATE laydraw_after_65_stats SET market_id = ?, latest_score = ?, latest_minute = ?, state = ?, "
            + "profit = ?, exit_at_minute = ?, exit_back_odds = ?, close_reason = ?, updated_at = CURRENT_TIMESTAMP, settled_at = ? "
            + "WHERE strategy_id = ? AND event_id = ?",
        bet.getMarketId(),
        bet.getLatestScore(),
        bet.getLatestMinute(),
        bet.getState(),
        bet.getProfit(),
        bet.getExitAtMinute(),
        bet.getExitBackOdds(),
        bet.getCloseReason(),
        toTimestampOrNull(bet.getSettledAt()),
        bet.getStrategyId(),
        bet.getEventId());
  }

  private void persistStrategyBalance(LayDrawAfterHt00StatusResponse status) {
    jdbcTemplate.update(
        "MERGE INTO laydraw_after_65_strategy_balances ("
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

  private void pruneInvalidOpenBetsByEntryWindow(Map<String, LayDrawAfterHt00BetRecord> betsByEventId) {
    if (betsByEventId == null || betsByEventId.isEmpty()) {
      return;
    }
    List<String> toDelete = new java.util.ArrayList<>();
    for (Map.Entry<String, LayDrawAfterHt00BetRecord> entry : betsByEventId.entrySet()) {
      LayDrawAfterHt00BetRecord bet = entry.getValue();
      if (bet == null || !"OPEN".equalsIgnoreCase(safe(bet.getState()))) {
        continue;
      }
      int placedMinute = parseMinute(bet.getPlacedAtMinute());
      if (placedMinute > ENTRY_MAX_MINUTE) {
        toDelete.add(entry.getKey());
      }
    }
    for (String eventId : toDelete) {
      jdbcTemplate.update(
          "DELETE FROM laydraw_after_65_stats WHERE strategy_id = ? AND event_id = ? AND state = 'OPEN'",
          STRATEGY_ID,
          eventId);
      betsByEventId.remove(eventId);
    }
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

  private void settleStaleOpenBets(
      Map<String, LayDrawAfterHt00BetRecord> betsByEventId,
      Map<String, LiveGameEntry> liveGamesByEventId,
      Map<String, Game> betfairByEventId,
      Map<String, MappedLiveGameEntry> statpalByEventId,
      String now) {
    for (LayDrawAfterHt00BetRecord bet : betsByEventId.values()) {
      if (!"OPEN".equalsIgnoreCase(safe(bet.getState()))) {
        continue;
      }
      String eventId = safe(bet.getEventId()).trim();
      if (eventId.isBlank() || liveGamesByEventId.containsKey(eventId)) {
        continue;
      }

      // Prefer mapped Statpal snapshot for finish detection and final score reconciliation.
      MappedLiveGameEntry statpal = statpalByEventId.get(eventId);
      if (statpal != null) {
        String statpalScore = safe(statpal.getScore()).trim();
        String statpalMinute = safe(statpal.getMinute()).trim();
        if (!statpalScore.isBlank()) {
          bet.setLatestScore(statpalScore);
        }
        if (!statpalMinute.isBlank()) {
          bet.setLatestMinute(statpalMinute);
        }
        if (isStatpalFinished(statpal)) {
          Integer home = parseHomeGoals(bet.getLatestScore());
          Integer away = parseAwayGoals(bet.getLatestScore());
          if (home != null && away != null) {
            settleByFinalScore(
                bet,
                home.intValue() == away.intValue(),
                bet.getLatestMinute(),
                now);
            updateBet(bet);
            continue;
          }
        }
      }

      Game betfairGame = betfairByEventId.get(eventId);
      if (betfairGame != null) {
        String marketStatus = safe(betfairGame.getMarketStatus()).trim().toUpperCase(Locale.ROOT);
        if (isTerminalMarketStatus(marketStatus) || Boolean.FALSE.equals(betfairGame.getInPlay())) {
          Integer home = parseHomeGoals(bet.getLatestScore());
          Integer away = parseAwayGoals(bet.getLatestScore());
          settleByFinalScore(
              bet,
              home != null && away != null && home.intValue() == away.intValue(),
              bet.getLatestMinute(),
              now);
          updateBet(bet);
          continue;
        }
      }

      // Fallback: if the feed lost this match and snapshot is old enough, force settlement
      // from last known score to avoid permanently OPEN bets.
      if (parseMinute(bet.getLatestMinute()) >= 90 || isStaleByLastUpdate(bet.getUpdatedAt(), now, 45)) {
        Integer home = parseHomeGoals(bet.getLatestScore());
        Integer away = parseAwayGoals(bet.getLatestScore());
        if (home != null && away != null) {
          settleByFinalScore(
              bet,
              home.intValue() == away.intValue(),
              bet.getLatestMinute(),
              now);
          updateBet(bet);
        }
      }
    }
  }

  private boolean isTerminalMarketStatus(String marketStatus) {
    return "CLOSED".equals(marketStatus)
        || "INACTIVE".equals(marketStatus)
        || "SETTLED".equals(marketStatus);
  }

  private boolean isFinished(LiveGameEntry game) {
    String minute = safe(game.getMinute()).trim().toUpperCase(Locale.ROOT);
    if ("FT".equals(minute) || "FINISHED".equals(minute) || parseMinute(minute) >= 90) {
      return true;
    }
    String marketStatus = safe(game.getMarketStatus()).trim().toUpperCase(Locale.ROOT);
    if ("CLOSED".equals(marketStatus)) {
      return true;
    }
    return Boolean.FALSE.equals(game.isInPlay());
  }

  private boolean isStatpalFinished(MappedLiveGameEntry entry) {
    if (entry == null) {
      return false;
    }
    String status = safe(entry.getStatus()).trim().toUpperCase(Locale.ROOT);
    if ("FT".equals(status)
        || "FINISHED".equals(status)
        || "AET".equals(status)
        || "PEN".equals(status)
        || "AFTER PEN.".equals(status)
        || "ENDED".equals(status)
        || "POSTP".equals(status)
        || "CANC".equals(status)) {
      return true;
    }
    return parseMinute(entry.getMinute()) >= 90;
  }

  private boolean isStaleByLastUpdate(String updatedAt, String now, long minMinutes) {
    try {
      Instant nowInstant = Instant.parse(now);
      Instant updated = Instant.parse(safe(updatedAt).trim());
      return Duration.between(updated, nowInstant).toMinutes() >= minMinutes;
    } catch (Exception ex) {
      return false;
    }
  }

  private Map<String, MappedLiveGameEntry> loadStatpalMappedByEventId() {
    Map<String, MappedLiveGameEntry> out = new LinkedHashMap<>();
    List<MappedLiveGameEntry> mapped = gameService.betfairMappedLiveGames();
    for (MappedLiveGameEntry entry : mapped) {
      String eventId = safe(entry == null ? null : entry.getBetfairEventId()).trim();
      if (!eventId.isBlank()) {
        out.putIfAbsent(eventId, entry);
      }
    }
    return out;
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
    if ("FT".equals(upper) || "FINISHED".equals(upper)) {
      return 90;
    }
    String normalized = upper.replaceAll("[^0-9+']", "");
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

  private Integer parseHomeGoals(String rawScore) {
    String text = safe(rawScore).trim();
    if (!text.matches("\\d+\\s*-\\s*\\d+")) {
      return null;
    }
    String[] parts = text.split("-");
    try {
      return Integer.parseInt(parts[0].trim());
    } catch (Exception ex) {
      return null;
    }
  }

  private Integer parseAwayGoals(String rawScore) {
    String text = safe(rawScore).trim();
    if (!text.matches("\\d+\\s*-\\s*\\d+")) {
      return null;
    }
    String[] parts = text.split("-");
    try {
      return Integer.parseInt(parts[1].trim());
    } catch (Exception ex) {
      return null;
    }
  }

  private void initSchema() {
    jdbcTemplate.execute(
        "CREATE TABLE IF NOT EXISTS laydraw_after_65_stats ("
            + "strategy_id VARCHAR(64) NOT NULL,"
            + "event_id VARCHAR(64) NOT NULL,"
            + "market_id VARCHAR(64),"
            + "home_team VARCHAR(255),"
            + "away_team VARCHAR(255),"
            + "league VARCHAR(255),"
            + "placed_at_minute VARCHAR(32),"
            + "entry_score VARCHAR(32),"
            + "entry_back_odds DOUBLE,"
            + "entry_lay_odds DOUBLE,"
            + "stake DOUBLE,"
            + "state VARCHAR(16),"
            + "profit DOUBLE,"
            + "exit_at_minute VARCHAR(32),"
            + "exit_back_odds DOUBLE,"
            + "close_reason VARCHAR(64),"
            + "latest_score VARCHAR(32),"
            + "latest_minute VARCHAR(32),"
            + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
            + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
            + "settled_at TIMESTAMP NULL,"
            + "PRIMARY KEY(strategy_id, event_id)"
            + ")");

    jdbcTemplate.execute("ALTER TABLE laydraw_after_65_stats ADD COLUMN IF NOT EXISTS entry_score VARCHAR(32)");

    jdbcTemplate.execute(
        "CREATE TABLE IF NOT EXISTS laydraw_after_65_strategy_balances ("
            + "strategy_id VARCHAR(64) PRIMARY KEY,"
            + "current_balance DOUBLE,"
            + "settled_profit DOUBLE,"
            + "open_bets INT,"
            + "finished_bets INT,"
            + "wins INT,"
            + "losses INT,"
            + "won_value DOUBLE,"
            + "lost_value DOUBLE,"
            + "stake DOUBLE,"
            + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
            + ")");
  }

  private double round2(double value) {
    return Math.round(value * 100d) / 100d;
  }

  private String safe(String value) {
    return value == null ? "" : value;
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

  private <T> T runWithTimeout(Supplier<T> supplier, T fallback, long timeoutMs) {
    try {
      return CompletableFuture.supplyAsync(supplier).get(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (Exception ex) {
      return fallback;
    }
  }
}
