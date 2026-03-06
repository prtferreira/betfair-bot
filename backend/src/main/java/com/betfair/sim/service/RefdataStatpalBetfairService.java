package com.betfair.sim.service;

import com.betfair.sim.model.Game;
import com.betfair.sim.model.RefdataStatpalBetfairApiMatch;
import com.betfair.sim.model.RefdataStatpalBetfairBetfairMatch;
import com.betfair.sim.model.RefdataStatpalBetfairCandidatesResponse;
import com.betfair.sim.model.RefdataStatpalBetfairMappingEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RefdataStatpalBetfairService {
  private static final DateTimeFormatter API_FILE_DATE = DateTimeFormatter.BASIC_ISO_DATE;
  private static final DateTimeFormatter API_MATCH_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final Set<String> STOP_WORDS =
      Set.of("fc", "cf", "sc", "fk", "ac", "afc", "club", "de", "cd");

  private final ObjectMapper objectMapper;
  private final GameService gameService;
  private final JdbcTemplate jdbcTemplate;
  private final Path apiDir;

  public RefdataStatpalBetfairService(
      ObjectMapper objectMapper,
      GameService gameService,
      JdbcTemplate jdbcTemplate,
      @Value("${betfair.followed-games.dir:backend/data}") String baseDir) {
    this.objectMapper = objectMapper;
    this.gameService = gameService;
    this.jdbcTemplate = jdbcTemplate;
    this.apiDir = FollowedGamesPathResolver.resolve(baseDir).resolve("api");
    initSchema();
  }

  public RefdataStatpalBetfairCandidatesResponse candidates(String date) {
    LocalDate resolvedDate =
        date == null || date.isBlank() ? LocalDate.now(ZoneOffset.UTC) : LocalDate.parse(date);
    List<RefdataStatpalBetfairApiMatch> apiMatches = loadApiMatches(resolvedDate);
    List<RefdataStatpalBetfairBetfairMatch> betfairMatches = loadBetfairMatches(resolvedDate);
    List<RefdataStatpalBetfairMappingEntry> mappings = loadMappings(resolvedDate);
    return new RefdataStatpalBetfairCandidatesResponse(
        resolvedDate.toString(), apiMatches, betfairMatches, mappings);
  }

  public int autoMapByHomeAwayNames(String date) {
    LocalDate resolvedDate =
        date == null || date.isBlank() ? LocalDate.now(ZoneOffset.UTC) : LocalDate.parse(date);
    List<RefdataStatpalBetfairApiMatch> apiMatches = loadApiMatches(resolvedDate);
    List<RefdataStatpalBetfairBetfairMatch> betfairMatches = loadBetfairMatches(resolvedDate);
    List<RefdataStatpalBetfairMappingEntry> mappings = loadMappings(resolvedDate);

    Set<String> mappedApiIds =
        mappings.stream().map(RefdataStatpalBetfairMappingEntry::getApiMatchId).collect(Collectors.toSet());
    Set<String> mappedBetfairIds =
        mappings.stream()
            .map(RefdataStatpalBetfairMappingEntry::getBetfairEventId)
            .collect(Collectors.toSet());

    List<RefdataStatpalBetfairApiMatch> apiPool =
        apiMatches.stream().filter(a -> !mappedApiIds.contains(a.getApiMatchId())).toList();
    List<RefdataStatpalBetfairBetfairMatch> betfairPool =
        betfairMatches.stream().filter(b -> !mappedBetfairIds.contains(b.getBetfairEventId())).toList();

    Map<String, List<RefdataStatpalBetfairBetfairMatch>> betfairByExactPair = new HashMap<>();
    for (RefdataStatpalBetfairBetfairMatch betfair : betfairPool) {
      String key = normalize(betfair.getHomeTeam()) + "|" + normalize(betfair.getAwayTeam());
      betfairByExactPair.computeIfAbsent(key, k -> new ArrayList<>()).add(betfair);
    }

    int mappedCount = 0;
    Set<String> usedBetfairIds = new HashSet<>();
    Set<String> usedApiIds = new HashSet<>();

    // 1) Strict exact pair auto-map first (home+away) so perfect matches never lose to ambiguity.
    for (RefdataStatpalBetfairApiMatch api : apiPool) {
      String key = normalize(api.getHomeTeam()) + "|" + normalize(api.getAwayTeam());
      List<RefdataStatpalBetfairBetfairMatch> exactMatches = betfairByExactPair.get(key);
      if (exactMatches == null || exactMatches.size() != 1) {
        continue;
      }
      RefdataStatpalBetfairBetfairMatch chosen = exactMatches.get(0);
      if (usedBetfairIds.contains(chosen.getBetfairEventId())) {
        continue;
      }
      saveMapping(
          resolvedDate.toString(),
          api.getApiMatchId(),
          chosen.getBetfairEventId(),
          "auto-exact-pair",
          1.0d);
      usedBetfairIds.add(chosen.getBetfairEventId());
      usedApiIds.add(api.getApiMatchId());
      mappedCount++;
    }

    // 2) Fallback auto-map by one-side name similarity and long-token overlaps (>4 chars).
    for (RefdataStatpalBetfairApiMatch api : apiPool) {
      if (usedApiIds.contains(api.getApiMatchId())) {
        continue;
      }
      MatchCandidate best = null;
      MatchCandidate second = null;
      for (RefdataStatpalBetfairBetfairMatch betfair : betfairPool) {
        if (usedBetfairIds.contains(betfair.getBetfairEventId())) {
          continue;
        }
        double homeScore = nameSimilarity(api.getHomeTeam(), betfair.getHomeTeam());
        double awayScore = nameSimilarity(api.getAwayTeam(), betfair.getAwayTeam());
        double homeLongWordScore = longWordMatchScore(api.getHomeTeam(), betfair.getHomeTeam());
        double awayLongWordScore = longWordMatchScore(api.getAwayTeam(), betfair.getAwayTeam());
        int homeLongOverlap = longWordOverlapCount(api.getHomeTeam(), betfair.getHomeTeam());
        int awayLongOverlap = longWordOverlapCount(api.getAwayTeam(), betfair.getAwayTeam());
        int longOverlapTotal = homeLongOverlap + awayLongOverlap;
        double score = Math.max(Math.max(homeScore, awayScore), Math.max(homeLongWordScore, awayLongWordScore));
        if (longOverlapTotal > 0) {
          // Shared long words (>4 chars) are a strong signal for near-identical team names.
          score = Math.max(score, Math.min(0.98d, 0.78d + (0.07d * longOverlapTotal)));
        }
        boolean hasLongWordAnchor = homeLongWordScore >= 0.80d || awayLongWordScore >= 0.80d;
        if (score < 0.70d && !hasLongWordAnchor) {
          continue;
        }
        String matchedSide = homeScore >= awayScore ? "home" : "away";
        if (homeLongWordScore > awayLongWordScore && homeLongWordScore > homeScore) {
          matchedSide = "home-longword";
        } else if (awayLongWordScore > homeLongWordScore && awayLongWordScore > awayScore) {
          matchedSide = "away-longword";
        }
        MatchCandidate candidate =
            new MatchCandidate(betfair, score, matchedSide, longOverlapTotal);
        if (best == null || candidate.score() > best.score()) {
          second = best;
          best = candidate;
        } else if (second == null || candidate.score() > second.score()) {
          second = candidate;
        }
      }
      if (best == null) {
        continue;
      }
      if (second != null
          && (best.score() - second.score()) < 0.08d
          && best.longOverlapTotal() <= second.longOverlapTotal()) {
        continue;
      }
      RefdataStatpalBetfairBetfairMatch chosen = best.betfairMatch();
      String source = best.longOverlapTotal() > 0 ? "auto-longword-token" : "auto-single-team";
      saveMapping(
          resolvedDate.toString(),
          api.getApiMatchId(),
          chosen.getBetfairEventId(),
          source,
          round2(best.score()));
      usedBetfairIds.add(chosen.getBetfairEventId());
      usedApiIds.add(api.getApiMatchId());
      mappedCount++;
    }
    return mappedCount;
  }

  public int deleteAllMappings(String date) {
    LocalDate resolvedDate =
        date == null || date.isBlank() ? LocalDate.now(ZoneOffset.UTC) : LocalDate.parse(date);
    return jdbcTemplate.update(
        "DELETE FROM refdata_statpal_betfair WHERE match_date = ?",
        resolvedDate);
  }

  public void saveMapping(
      String date,
      String apiMatchId,
      String betfairEventId,
      String source,
      Double confidenceScore) {
    LocalDate resolvedDate =
        date == null || date.isBlank() ? LocalDate.now(ZoneOffset.UTC) : LocalDate.parse(date);
    List<RefdataStatpalBetfairApiMatch> apiMatches = loadApiMatches(resolvedDate);
    List<RefdataStatpalBetfairBetfairMatch> betfairMatches = loadBetfairMatches(resolvedDate);
    RefdataStatpalBetfairApiMatch apiMatch =
        apiMatches.stream().filter(m -> apiMatchId.equals(m.getApiMatchId())).findFirst().orElse(null);
    RefdataStatpalBetfairBetfairMatch betfairMatch =
        betfairMatches.stream()
            .filter(m -> betfairEventId.equals(m.getBetfairEventId()))
            .findFirst()
            .orElse(null);
    if (apiMatch == null || betfairMatch == null) {
      throw new IllegalArgumentException("Could not resolve api or betfair match for mapping");
    }

    jdbcTemplate.update(
        "DELETE FROM refdata_statpal_betfair "
            + "WHERE match_date = ? AND (statpal_main_id = ? OR betfair_event_id = ?)",
        resolvedDate,
        apiMatchId,
        betfairEventId);

    jdbcTemplate.update(
        "INSERT INTO refdata_statpal_betfair ("
            + "match_date, statpal_main_id, statpal_home_team, statpal_away_team, "
            + "betfair_event_id, betfair_home_team, betfair_away_team, confidence_score, "
            + "mapping_source, created_at, updated_at"
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        resolvedDate,
        apiMatchId,
        apiMatch.getHomeTeam(),
        apiMatch.getAwayTeam(),
        betfairEventId,
        betfairMatch.getHomeTeam(),
        betfairMatch.getAwayTeam(),
        confidenceScore,
        source == null || source.isBlank() ? "manual" : source);

    // Maintain reusable team-name reference mappings.
    upsertTeamNameMapping(apiMatch.getHomeTeam(), betfairMatch.getHomeTeam(), source, confidenceScore);
    upsertTeamNameMapping(apiMatch.getAwayTeam(), betfairMatch.getAwayTeam(), source, confidenceScore);
  }

  public void deleteMapping(String date, String apiMatchId, String betfairEventId) {
    LocalDate resolvedDate =
        date == null || date.isBlank() ? LocalDate.now(ZoneOffset.UTC) : LocalDate.parse(date);
    if (apiMatchId != null && !apiMatchId.isBlank()) {
      jdbcTemplate.update(
          "DELETE FROM refdata_statpal_betfair WHERE match_date = ? AND statpal_main_id = ?",
          resolvedDate,
          apiMatchId);
      return;
    }
    if (betfairEventId != null && !betfairEventId.isBlank()) {
      jdbcTemplate.update(
          "DELETE FROM refdata_statpal_betfair WHERE match_date = ? AND betfair_event_id = ?",
          resolvedDate,
          betfairEventId);
    }
  }

  private void initSchema() {
    jdbcTemplate.execute(
        "CREATE TABLE IF NOT EXISTS refdata_statpal_betfair ("
            + "id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,"
            + "match_date DATE NOT NULL,"
            + "statpal_main_id VARCHAR(64) NOT NULL,"
            + "statpal_home_team VARCHAR(255),"
            + "statpal_away_team VARCHAR(255),"
            + "betfair_event_id VARCHAR(64) NOT NULL,"
            + "betfair_home_team VARCHAR(255),"
            + "betfair_away_team VARCHAR(255),"
            + "confidence_score DOUBLE,"
            + "mapping_source VARCHAR(32) NOT NULL DEFAULT 'manual',"
            + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            + "UNIQUE(match_date, statpal_main_id),"
            + "UNIQUE(match_date, betfair_event_id)"
            + ")");

    jdbcTemplate.execute(
        "CREATE TABLE IF NOT EXISTS refdata_statpal_betfair_team ("
            + "id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,"
            + "statpal_team_name VARCHAR(255) NOT NULL,"
            + "betfair_team_name VARCHAR(255) NOT NULL,"
            + "normalized_statpal_team VARCHAR(255) NOT NULL,"
            + "normalized_betfair_team VARCHAR(255) NOT NULL,"
            + "mapping_source VARCHAR(32) NOT NULL DEFAULT 'manual',"
            + "confidence_score DOUBLE,"
            + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            + "UNIQUE(normalized_statpal_team, normalized_betfair_team)"
            + ")");
  }

  private void upsertTeamNameMapping(
      String statpalTeamName, String betfairTeamName, String source, Double confidenceScore) {
    String statpal = value(statpalTeamName).trim();
    String betfair = value(betfairTeamName).trim();
    if (statpal.isBlank() || betfair.isBlank()) {
      return;
    }
    String normalizedStatpal = normalize(statpal);
    String normalizedBetfair = normalize(betfair);
    if (normalizedStatpal.isBlank() || normalizedBetfair.isBlank()) {
      return;
    }
    jdbcTemplate.update(
        "MERGE INTO refdata_statpal_betfair_team ("
            + "statpal_team_name, betfair_team_name, normalized_statpal_team, normalized_betfair_team, "
            + "mapping_source, confidence_score, updated_at"
            + ") KEY(normalized_statpal_team, normalized_betfair_team) "
            + "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
        statpal,
        betfair,
        normalizedStatpal,
        normalizedBetfair,
        source == null || source.isBlank() ? "manual" : source,
        confidenceScore);
  }

  private List<RefdataStatpalBetfairApiMatch> loadApiMatches(LocalDate date) {
    Path file = apiDir.resolve(date.format(API_FILE_DATE));
    if (!Files.exists(file)) {
      return List.of();
    }
    JsonNode root;
    try {
      root = objectMapper.readTree(Files.newBufferedReader(file));
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to parse api feed file: " + file, ex);
    }

    List<ApiRawMatch> rawMatches = new ArrayList<>();
    JsonNode leagues = root.path("live_matches").path("league");
    if (!leagues.isArray()) {
      return List.of();
    }
    for (JsonNode leagueNode : leagues) {
      String leagueName = text(leagueNode.path("name"));
      JsonNode matchNode = leagueNode.path("match");
      if (matchNode.isArray()) {
        for (JsonNode m : matchNode) {
          collectApiMatch(rawMatches, m, leagueName, date);
        }
      } else if (matchNode.isObject()) {
        collectApiMatch(rawMatches, matchNode, leagueName, date);
      }
    }

    Map<String, Integer> duplicateCountByMainId = new HashMap<>();
    for (ApiRawMatch raw : rawMatches) {
      duplicateCountByMainId.merge(raw.mainId(), 1, Integer::sum);
    }

    Map<String, RefdataStatpalBetfairApiMatch> byId = new LinkedHashMap<>();
    for (ApiRawMatch raw : rawMatches) {
      String resolvedApiId = raw.mainId();
      if (duplicateCountByMainId.getOrDefault(raw.mainId(), 0) > 1) {
        String fallbackId = raw.fallbackId1();
        resolvedApiId =
            (fallbackId == null || fallbackId.isBlank())
                ? raw.mainId() + "_dup"
                : raw.mainId() + "_" + fallbackId;
      }
      if (byId.containsKey(resolvedApiId)) {
        int suffix = 2;
        String candidate = resolvedApiId + "_" + suffix;
        while (byId.containsKey(candidate)) {
          suffix++;
          candidate = resolvedApiId + "_" + suffix;
        }
        resolvedApiId = candidate;
      }
      byId.put(
          resolvedApiId,
          new RefdataStatpalBetfairApiMatch(
              resolvedApiId,
              raw.matchDate().toString(),
              raw.leagueName(),
              raw.homeTeam(),
              raw.awayTeam(),
              raw.displayName()));
    }

    return byId.values().stream()
        .sorted(Comparator.comparing(RefdataStatpalBetfairApiMatch::getDisplayName))
        .toList();
  }

  private void collectApiMatch(
      List<ApiRawMatch> out,
      JsonNode matchNode,
      String leagueName,
      LocalDate fallbackDate) {
    String mainId = text(matchNode.path("main_id"));
    if (mainId.isBlank()) {
      return;
    }
    String fallbackId1 = text(matchNode.path("fallback_id_1"));
    String home = text(matchNode.path("home").path("name"));
    String away = text(matchNode.path("away").path("name"));
    LocalDate matchDate = parseMatchDate(text(matchNode.path("date")), fallbackDate);
    out.add(
        new ApiRawMatch(
            mainId,
            fallbackId1,
            matchDate,
            leagueName,
            home,
            away,
            (home + " vs " + away).trim()));
  }

  private List<RefdataStatpalBetfairBetfairMatch> loadBetfairMatches(LocalDate date) {
    List<Game> games = gameService.gamesForDateBetfairOnly(date.toString());
    Map<String, RefdataStatpalBetfairBetfairMatch> byId = new LinkedHashMap<>();
    for (Game game : games) {
      if (game == null || game.getId() == null || game.getId().isBlank()) {
        continue;
      }
      String home = value(game.getHomeTeam());
      String away = value(game.getAwayTeam());
      byId.put(
          game.getId(),
          new RefdataStatpalBetfairBetfairMatch(
              game.getId(),
              value(game.getLeague()),
              home,
              away,
              value(game.getStartTime()),
              (home + " vs " + away).trim()));
    }
    return byId.values().stream()
        .sorted(Comparator.comparing(RefdataStatpalBetfairBetfairMatch::getDisplayName))
        .toList();
  }

  private List<RefdataStatpalBetfairMappingEntry> loadMappings(LocalDate date) {
    return jdbcTemplate.query(
        "SELECT match_date, statpal_main_id, statpal_home_team, statpal_away_team, "
            + "betfair_event_id, betfair_home_team, betfair_away_team, confidence_score, "
            + "mapping_source, updated_at "
            + "FROM refdata_statpal_betfair WHERE match_date = ? "
            + "ORDER BY updated_at DESC",
        (rs, rowNum) ->
            new RefdataStatpalBetfairMappingEntry(
                rs.getObject("match_date", LocalDate.class).toString(),
                rs.getString("statpal_main_id"),
                rs.getString("statpal_home_team"),
                rs.getString("statpal_away_team"),
                rs.getString("betfair_event_id"),
                rs.getString("betfair_home_team"),
                rs.getString("betfair_away_team"),
                rs.getString("mapping_source"),
                rs.getObject("confidence_score") == null ? null : rs.getDouble("confidence_score"),
                rs.getTimestamp("updated_at") == null ? "" : rs.getTimestamp("updated_at").toInstant().toString()),
        date);
  }

  private static String normalize(String value) {
    if (value == null) {
      return "";
    }
    String cleaned = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    cleaned = cleaned.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    return cleaned;
  }

  private static double nameSimilarity(String a, String b) {
    String left = normalize(a);
    String right = normalize(b);
    if (left.isBlank() || right.isBlank()) {
      return 0d;
    }
    if (left.equals(right)) {
      return 1d;
    }
    Set<String> leftTokens = tokens(left);
    Set<String> rightTokens = tokens(right);
    if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
      return 0d;
    }
    Set<String> intersection = new HashSet<>(leftTokens);
    intersection.retainAll(rightTokens);
    Set<String> union = new HashSet<>(leftTokens);
    union.addAll(rightTokens);
    double jaccard = union.isEmpty() ? 0d : ((double) intersection.size()) / union.size();
    double contains = (left.contains(right) || right.contains(left)) ? 1d : 0d;
    return Math.min(1d, (0.75d * jaccard) + (0.25d * contains));
  }

  private static Set<String> tokens(String normalized) {
    Set<String> out = new HashSet<>();
    for (String token : normalized.split("\\s+")) {
      if (token.isBlank() || STOP_WORDS.contains(token)) {
        continue;
      }
      out.add(token);
    }
    return out;
  }

  private static double longWordMatchScore(String a, String b) {
    String left = normalize(a);
    String right = normalize(b);
    if (left.isBlank() || right.isBlank()) {
      return 0d;
    }
    Set<String> leftTokens = longTokens(left);
    Set<String> rightTokens = longTokens(right);
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
    double jaccard = union.isEmpty() ? 0d : ((double) intersection.size()) / union.size();
    return Math.min(1d, 0.85d + (0.15d * jaccard));
  }

  private static int longWordOverlapCount(String a, String b) {
    String left = normalize(a);
    String right = normalize(b);
    if (left.isBlank() || right.isBlank()) {
      return 0;
    }
    Set<String> leftTokens = longTokens(left);
    Set<String> rightTokens = longTokens(right);
    if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
      return 0;
    }
    Set<String> intersection = new HashSet<>(leftTokens);
    intersection.retainAll(rightTokens);
    return intersection.size();
  }

  private static Set<String> longTokens(String normalized) {
    Set<String> out = new HashSet<>();
    for (String token : normalized.split("\\s+")) {
      if (token.isBlank() || STOP_WORDS.contains(token) || token.length() <= 4) {
        continue;
      }
      out.add(token);
    }
    return out;
  }

  private static double round2(double value) {
    return Math.round(value * 100d) / 100d;
  }


  private static LocalDate parseMatchDate(String value, LocalDate fallbackDate) {
    if (value == null || value.isBlank()) {
      return fallbackDate;
    }
    try {
      return LocalDate.parse(value, API_MATCH_DATE);
    } catch (DateTimeParseException ex) {
      return fallbackDate;
    }
  }

  private static String text(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return "";
    }
    return node.asText("");
  }

  private static String value(String v) {
    return v == null ? "" : v;
  }

  private record ApiRawMatch(
      String mainId,
      String fallbackId1,
      LocalDate matchDate,
      String leagueName,
      String homeTeam,
      String awayTeam,
      String displayName) {}

  private record MatchCandidate(
      RefdataStatpalBetfairBetfairMatch betfairMatch,
      double score,
      String matchedSide,
      int longOverlapTotal) {}
}
