package com.betfair.sim.service;

import com.betfair.sim.model.LayDrawAfterHt00BetRecord;
import com.betfair.sim.model.LayDrawAfterHt00StatusResponse;
import com.betfair.sim.model.LiveGameEntry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class LayDrawAfterHt00StatsService {
  private static final String STRATEGY_ID = "laydraw_after_ht_0_0";
  private static final double STARTING_BANK = 1000d;
  private static final double STAKE = 20d;
  private static final int ENTRY_START_MINUTE = 46;
  private static final int ENTRY_END_MINUTE = 50;
  private static final int EXIT_MINUTE = 70;
  private static final double MAX_DRAW_LAY_ODDS = 3.0d;
  private static final double MAX_HOME_AWAY_DIFF = 1.5d;
  private static final double MAX_ENTRY_SPREAD = 0.04d;
  private static final DateTimeFormatter AUDIT_FILE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

  private final GameService gameService;
  private final JdbcTemplate jdbcTemplate;
  private final Path auditDir;

  public LayDrawAfterHt00StatsService(
      GameService gameService,
      JdbcTemplate jdbcTemplate,
      @Value("${betfair.followed-games.dir:backend/data}") String followedGamesDir) {
    this.gameService = gameService;
    this.jdbcTemplate = jdbcTemplate;
    this.auditDir = FollowedGamesPathResolver.resolve(followedGamesDir).resolve("api");
    initSchema();
  }

  public synchronized LayDrawAfterHt00StatusResponse getStatus() {
    List<LiveGameEntry> liveGames = gameService.betfairLiveGames();
    Map<String, LayDrawAfterHt00BetRecord> betsByEventId = loadBetsByEventId();
    String now = Instant.now().toString();

    for (LiveGameEntry game : liveGames) {
      String eventId = safe(game.getEventId()).trim();
      if (eventId.isBlank()) {
        continue;
      }
      int minute = parseMinute(game.getMinute());
      int goals = parseGoals(game.getScore());
      Double drawBack = game.getDrawOdds();
      Double drawLay = game.getDrawLayOdds();
      LayDrawAfterHt00BetRecord existing = betsByEventId.get(eventId);

      if (existing == null) {
        if (shouldOpen(game, minute, goals, drawBack, drawLay)) {
          LayDrawAfterHt00BetRecord created = new LayDrawAfterHt00BetRecord();
          created.setStrategyId(STRATEGY_ID);
          created.setEventId(eventId);
          created.setMarketId(safe(game.getMarketId()));
          created.setHomeTeam(safe(game.getHomeTeam()));
          created.setAwayTeam(safe(game.getAwayTeam()));
          created.setLeague(safe(game.getLeague()));
          created.setPlacedAtMinute(safe(game.getMinute()));
          created.setEntryBackOdds(round2(drawBack));
          created.setEntryLayOdds(round2(drawLay));
          created.setStake(STAKE);
          created.setState("OPEN");
          created.setProfit(0d);
          created.setExitBackOdds(0d);
          created.setExitAtMinute("");
          created.setCloseReason("");
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
        String closeReason = resolveCloseReason(game, minute, goals);
        if (!closeReason.isBlank() && drawBack != null && drawBack > 1.01d) {
          closePosition(existing, drawBack, game.getMinute(), closeReason, now);
        } else if (!closeReason.isBlank() && isFinished(game)) {
          closePosition(existing, Math.max(1.01d, existing.getEntryLayOdds()), game.getMinute(), closeReason, now);
        }
      }
      updateBet(existing);
    }

    List<LayDrawAfterHt00BetRecord> bets = loadBets();
    LayDrawAfterHt00StatusResponse response = buildResponse(liveGames, bets, now);
    persistStrategyBalance(response);
    return response;
  }

  private boolean shouldOpen(
      LiveGameEntry game, int minute, int goals, Double drawBack, Double drawLay) {
    if (!Boolean.TRUE.equals(game.isInPlay())) {
      return false;
    }
    if (minute < ENTRY_START_MINUTE || minute > ENTRY_END_MINUTE) {
      return false;
    }
    if (goals != 0) {
      return false;
    }
    if (drawBack == null || drawLay == null || drawLay <= 1.01d || drawLay > MAX_DRAW_LAY_ODDS) {
      return false;
    }
    if (!isAcceptedSpread(drawBack, drawLay)) {
      return false;
    }
    return isBalancedGame(game.getHomeOdds(), game.getAwayOdds());
  }

  private boolean isAcceptedSpread(double back, double lay) {
    if (back <= 1.01d || lay <= 1.01d) {
      return false;
    }
    return (lay - back) <= MAX_ENTRY_SPREAD;
  }

  private boolean isBalancedGame(Double homeBack, Double awayBack) {
    if (homeBack == null || awayBack == null || homeBack <= 1.01d || awayBack <= 1.01d) {
      return false;
    }
    double favorite = Math.min(homeBack, awayBack);
    double underdog = Math.max(homeBack, awayBack);
    return (underdog - favorite) <= MAX_HOME_AWAY_DIFF;
  }

  private String resolveCloseReason(LiveGameEntry game, int minute, int goals) {
    if (goals > 0) {
      return "GOAL";
    }
    if (minute >= EXIT_MINUTE) {
      return "TIME_70";
    }
    if (isFinished(game)) {
      return "FINISHED";
    }
    return "";
  }

  private void closePosition(
      LayDrawAfterHt00BetRecord bet, double exitBackOdds, String exitMinute, String closeReason, String now) {
    double layOdds = Math.max(1.01d, bet.getEntryLayOdds());
    double layStake = Math.max(0d, bet.getStake());
    double backOdds = Math.max(1.01d, exitBackOdds);
    double backStake = (layOdds * layStake) / backOdds;
    double equalizedProfit = layStake - backStake;

    bet.setExitBackOdds(round2(backOdds));
    bet.setExitAtMinute(safe(exitMinute));
    bet.setCloseReason(closeReason);
    bet.setProfit(round2(equalizedProfit));
    bet.setState(equalizedProfit >= 0d ? "WON" : "LOST");
    bet.setSettledAt(now);
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
        "SELECT strategy_id, event_id, market_id, home_team, away_team, league, placed_at_minute, "
            + "entry_back_odds, entry_lay_odds, stake, state, profit, exit_at_minute, exit_back_odds, close_reason, "
            + "latest_score, latest_minute, created_at, updated_at, settled_at "
            + "FROM laydraw_after_ht_0_0_stats WHERE strategy_id = ? ORDER BY created_at DESC",
        (rs, rowNum) -> {
          LayDrawAfterHt00BetRecord bet = new LayDrawAfterHt00BetRecord();
          bet.setStrategyId(rs.getString("strategy_id"));
          bet.setEventId(rs.getString("event_id"));
          bet.setMarketId(rs.getString("market_id"));
          bet.setHomeTeam(rs.getString("home_team"));
          bet.setAwayTeam(rs.getString("away_team"));
          bet.setLeague(rs.getString("league"));
          bet.setPlacedAtMinute(rs.getString("placed_at_minute"));
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
              rs.getTimestamp("created_at") == null ? "" : rs.getTimestamp("created_at").toInstant().toString());
          bet.setUpdatedAt(
              rs.getTimestamp("updated_at") == null ? "" : rs.getTimestamp("updated_at").toInstant().toString());
          bet.setSettledAt(
              rs.getTimestamp("settled_at") == null ? "" : rs.getTimestamp("settled_at").toInstant().toString());
          return bet;
        },
        STRATEGY_ID);
  }

  private void insertBet(LayDrawAfterHt00BetRecord bet) {
    jdbcTemplate.update(
        "INSERT INTO laydraw_after_ht_0_0_stats ("
            + "strategy_id, event_id, market_id, home_team, away_team, league, placed_at_minute, "
            + "entry_back_odds, entry_lay_odds, stake, state, profit, exit_at_minute, exit_back_odds, close_reason, "
            + "latest_score, latest_minute, created_at, updated_at, settled_at"
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)",
        bet.getStrategyId(),
        bet.getEventId(),
        bet.getMarketId(),
        bet.getHomeTeam(),
        bet.getAwayTeam(),
        bet.getLeague(),
        bet.getPlacedAtMinute(),
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
        "UPDATE laydraw_after_ht_0_0_stats SET market_id = ?, latest_score = ?, latest_minute = ?, state = ?, "
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
        "MERGE INTO laydraw_after_ht_0_0_strategy_balances ("
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
    String normalized = upper.replace("Ã¢â‚¬â„¢", "'").replaceAll("[^0-9+']", "");
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

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private double round2(double value) {
    return Math.round(value * 100d) / 100d;
  }

  private void appendAuditLine(LayDrawAfterHt00BetRecord bet, String placedAt) {
    String timestamp = safe(placedAt).isBlank() ? Instant.now().toString() : placedAt;
    LocalDate day = resolveAuditDay(timestamp);
    Path file = auditDir.resolve("laydraw_after_ht_0_0_audit_" + day.format(AUDIT_FILE_DATE));
    String line =
        String.format(
            Locale.ROOT,
            "%s vs %s | placeBetAt=%s | minute=%s | drawBack=%.2f | drawLay=%.2f%n",
            safe(bet.getHomeTeam()),
            safe(bet.getAwayTeam()),
            timestamp,
            safe(bet.getPlacedAtMinute()),
            round2(bet.getEntryBackOdds()),
            round2(bet.getEntryLayOdds()));
    try {
      Files.createDirectories(auditDir);
      Files.writeString(
          file,
          line,
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to append LayDrawAfterHT0_0 audit", ex);
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
        "CREATE TABLE IF NOT EXISTS laydraw_after_ht_0_0_stats ("
            + "id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,"
            + "strategy_id VARCHAR(64) NOT NULL,"
            + "event_id VARCHAR(64) NOT NULL,"
            + "market_id VARCHAR(64),"
            + "home_team VARCHAR(255),"
            + "away_team VARCHAR(255),"
            + "league VARCHAR(255),"
            + "placed_at_minute VARCHAR(32),"
            + "entry_back_odds DOUBLE NOT NULL,"
            + "entry_lay_odds DOUBLE NOT NULL,"
            + "stake DOUBLE NOT NULL,"
            + "state VARCHAR(16) NOT NULL,"
            + "profit DOUBLE NOT NULL DEFAULT 0,"
            + "exit_at_minute VARCHAR(32),"
            + "exit_back_odds DOUBLE,"
            + "close_reason VARCHAR(32),"
            + "latest_score VARCHAR(32),"
            + "latest_minute VARCHAR(32),"
            + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            + "settled_at TIMESTAMP,"
            + "UNIQUE(strategy_id, event_id)"
            + ")");

    jdbcTemplate.execute(
        "CREATE TABLE IF NOT EXISTS laydraw_after_ht_0_0_strategy_balances ("
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
