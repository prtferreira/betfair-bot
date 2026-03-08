package com.betfair.sim.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;

@Service
public class StatpalLiveClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(StatpalLiveClient.class);

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final String baseUrl;
  private final String accessKey;
  private final Duration cacheTtl;
  private volatile Instant cacheExpiresAt = Instant.EPOCH;
  private volatile List<LiveMatch> cachedLiveMatches = List.of();

  public StatpalLiveClient(
      RestTemplateBuilder restTemplateBuilder,
      ObjectMapper objectMapper,
      @Value("${statpal.live.base-url:https://statpal.io/api/v2/soccer/matches/live}") String baseUrl,
      @Value("${statpal.access-key:c6a0d160-93fa-467e-9f5b-0d59e78a14ca}") String accessKey,
      @Value("${statpal.live.cache-ttl-seconds:30}") long cacheTtlSeconds,
      @Value("${statpal.http.connect-timeout-ms:2500}") long connectTimeoutMs,
      @Value("${statpal.http.read-timeout-ms:4000}") long readTimeoutMs) {
    this.restTemplate =
        restTemplateBuilder
            .setConnectTimeout(Duration.ofMillis(Math.max(500L, connectTimeoutMs)))
            .setReadTimeout(Duration.ofMillis(Math.max(1000L, readTimeoutMs)))
            .build();
    this.objectMapper = objectMapper;
    this.baseUrl = baseUrl;
    this.accessKey = accessKey;
    this.cacheTtl = Duration.ofSeconds(Math.max(1L, cacheTtlSeconds));
  }

  public List<LiveMatch> fetchLiveMatches() {
    Instant now = Instant.now();
    List<LiveMatch> cachedSnapshot = cachedLiveMatches;
    if (now.isBefore(cacheExpiresAt)) {
      return cachedSnapshot;
    }
    synchronized (this) {
      now = Instant.now();
      cachedSnapshot = cachedLiveMatches;
      if (now.isBefore(cacheExpiresAt)) {
        return cachedSnapshot;
      }
      List<LiveMatch> fetched = fetchLiveMatchesInternal();
      cachedLiveMatches = List.copyOf(fetched);
      cacheExpiresAt = now.plus(cacheTtl);
      return cachedLiveMatches;
    }
  }

  private List<LiveMatch> fetchLiveMatchesInternal() {
    if (accessKey == null || accessKey.isBlank()) {
      LOGGER.warn("[STATPAL_DEBUG] access key is missing/blank");
      return List.of();
    }
    try {
      String url = baseUrl + "?access_key=" + accessKey;
      LOGGER.debug(
          "[STATPAL_DEBUG] calling statpal live endpoint baseUrl={} keyLength={}",
          baseUrl,
          accessKey.length());
      HttpHeaders headers = new HttpHeaders();
      headers.setAccept(List.of(MediaType.APPLICATION_JSON));
      HttpEntity<Void> request = new HttpEntity<>(headers);
      ResponseEntity<String> entity = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, request, String.class);
      String response = entity.getBody();
      if (response == null || response.isBlank()) {
        LOGGER.info("[STATPAL_DEBUG] empty live matches response");
        return List.of();
      }
      JsonNode root = objectMapper.readTree(response);
      List<LiveMatch> matches = new ArrayList<>();
      JsonNode leagues = root.path("live_matches").path("league");
      if (leagues.isArray()) {
        for (JsonNode leagueNode : leagues) {
          String leagueName = leagueNode.path("name").asText("").trim();
          JsonNode matchNode = leagueNode.path("match");
          collectMatches(matchNode, leagueName, matches);
        }
      } else {
        collectMatches(root.path("live_matches"), "", matches);
      }
      LOGGER.info("[STATPAL_DEBUG] fetched live matches count={} httpStatus={}", matches.size(), entity.getStatusCode().value());
      if (!matches.isEmpty()) {
        LiveMatch sample = matches.get(0);
        LOGGER.info(
            "[STATPAL_DEBUG] sample match: {} vs {} score={}{}{} status={} minute={}",
            sample.getHomeTeam(),
            sample.getAwayTeam(),
            sample.getHomeGoals() == null ? "?" : sample.getHomeGoals(),
            "-",
            sample.getAwayGoals() == null ? "?" : sample.getAwayGoals(),
            sample.getStatus(),
            sample.getMinute());
      }
      return matches;
    } catch (HttpStatusCodeException ex) {
      String body = ex.getResponseBodyAsString();
      LOGGER.warn(
          "[STATPAL_DEBUG] statpal http error status={} body={}",
          ex.getStatusCode().value(),
          body == null ? "" : body);
      return List.of();
    } catch (Exception ex) {
      LOGGER.warn("[STATPAL_DEBUG] failed to fetch live matches: {}", ex.getMessage());
      return List.of();
    }
  }

  private void collectMatches(JsonNode node, String leagueName, List<LiveMatch> target) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return;
    }
    if (node.isArray()) {
      for (JsonNode child : node) {
        collectMatches(child, leagueName, target);
      }
      return;
    }
    if (!node.isObject()) {
      return;
    }
    LiveMatch parsed = parseMatch(node, leagueName);
    if (parsed != null) {
      target.add(parsed);
    }
  }

  private LiveMatch parseMatch(JsonNode match, String leagueName) {
    if (match == null || !match.isObject()) {
      return null;
    }
    String mainId = match.path("main_id").asText("").trim();
    String homeTeam = match.path("home").path("name").asText("").trim();
    String awayTeam = match.path("away").path("name").asText("").trim();
    if (homeTeam.isBlank() || awayTeam.isBlank()) {
      return null;
    }

    Integer homeGoals = parseInt(match.path("home").path("goals").asText(""));
    Integer awayGoals = parseInt(match.path("away").path("goals").asText(""));
    String status = match.path("status").asText("").trim();
    String minute = extractMinute(match);
    if (minute.isBlank() && "HT".equalsIgnoreCase(status)) {
      minute = "HT";
    }
    boolean hasGoalEvent = hasGoalEvent(match.path("events").path("event"));
    boolean goalScored =
        (homeGoals != null && homeGoals > 0)
            || (awayGoals != null && awayGoals > 0)
            || hasGoalEvent;

    return new LiveMatch(
        mainId, leagueName, homeTeam, awayTeam, homeGoals, awayGoals, status, minute, goalScored);
  }

  private String extractMinute(JsonNode match) {
    String minute = match.path("minute").asText("").trim();
    if (!minute.isBlank()) {
      String inj = match.path("inj_minute").asText("").trim();
      return inj.isBlank() ? minute + "'" : minute + "+" + inj + "'";
    }
    String status = match.path("status").asText("").trim();
    if (isLiveMinuteStatus(status)) {
      return status + "'";
    }
    String inj = match.path("inj_minute").asText("").trim();
    if (!inj.isBlank()) {
      return inj + "'";
    }
    return "";
  }

  private boolean hasGoalEvent(JsonNode events) {
    if (events == null || events.isNull() || events.isMissingNode()) {
      return false;
    }
    if (events.isArray()) {
      for (JsonNode event : events) {
        if (isGoalType(event.path("type").asText(""))) {
          return true;
        }
      }
      return false;
    }
    return isGoalType(events.path("type").asText(""));
  }

  private boolean isGoalType(String value) {
    String type = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    return "goal".equals(type) || "owngoal".equals(type);
  }

  private boolean isLiveMinuteStatus(String status) {
    if (status == null || status.isBlank()) {
      return false;
    }
    return status.trim().matches("^\\d{1,3}(\\+\\d{1,2})?$");
  }

  private Integer parseInt(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (Exception ignored) {
      return null;
    }
  }

  private int parseInt(String value, int fallback) {
    Integer parsed = parseInt(value);
    return parsed == null ? fallback : parsed;
  }

  public static String normalizeName(String input) {
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

  public static final class LiveMatch {
    private final String mainId;
    private final String league;
    private final String homeTeam;
    private final String awayTeam;
    private final Integer homeGoals;
    private final Integer awayGoals;
    private final String status;
    private final String minute;
    private final boolean goalScored;

    private LiveMatch(
        String mainId,
        String league,
        String homeTeam,
        String awayTeam,
        Integer homeGoals,
        Integer awayGoals,
        String status,
        String minute,
        boolean goalScored) {
      this.mainId = mainId;
      this.league = league;
      this.homeTeam = homeTeam;
      this.awayTeam = awayTeam;
      this.homeGoals = homeGoals;
      this.awayGoals = awayGoals;
      this.status = status;
      this.minute = minute;
      this.goalScored = goalScored;
    }

    public String getMainId() {
      return mainId;
    }

    public String getLeague() {
      return league;
    }

    public String getHomeTeam() {
      return homeTeam;
    }

    public String getAwayTeam() {
      return awayTeam;
    }

    public Integer getHomeGoals() {
      return homeGoals;
    }

    public Integer getAwayGoals() {
      return awayGoals;
    }

    public String getStatus() {
      return status;
    }

    public String getMinute() {
      return minute;
    }

    public boolean isGoalScored() {
      return goalScored;
    }
  }
}
