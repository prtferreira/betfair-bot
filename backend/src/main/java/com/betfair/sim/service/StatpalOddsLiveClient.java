package com.betfair.sim.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Service
public class StatpalOddsLiveClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(StatpalOddsLiveClient.class);
  private static final Duration CACHE_TTL = Duration.ofSeconds(60);

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final String baseUrl;
  private final String accessKey;
  private final String fallbackAccessKey;
  private final Map<String, CachedSnapshot> cache = new ConcurrentHashMap<>();

  public StatpalOddsLiveClient(
      RestTemplateBuilder restTemplateBuilder,
      ObjectMapper objectMapper,
      @Value("${statpal.odds-live.base-url:https://statpal.io/api/v2/soccer/odds/live}") String baseUrl,
      @Value("${statpal.odds-live.access-key:c6a0d160-93fa-467e-9f5b-0d59e78a14ca}") String accessKey,
      @Value("${statpal.access-key:}") String fallbackAccessKey) {
    this.restTemplate = restTemplateBuilder.build();
    this.objectMapper = objectMapper;
    this.baseUrl = baseUrl;
    this.accessKey = accessKey;
    this.fallbackAccessKey = fallbackAccessKey;
  }

  public MatchSnapshot fetchByMatchId(String matchId) {
    if (matchId == null || matchId.isBlank()) {
      return MatchSnapshot.empty();
    }
    Instant now = Instant.now();
    CachedSnapshot cached = cache.get(matchId);
    if (cached != null && now.isBefore(cached.expiresAt())) {
      return cached.snapshot();
    }

    String keyToUse = resolvePrimaryKey();
    if (keyToUse.isBlank()) {
      return MatchSnapshot.empty();
    }
    try {
      ResponseEntity<String> response = callEndpoint(matchId.trim(), keyToUse);
      String body = response.getBody();
      MatchSnapshot parsed = parseSnapshot(body);
      MatchSnapshot safe = parsed == null ? MatchSnapshot.empty() : parsed;
      cache.put(matchId, new CachedSnapshot(safe, now.plus(CACHE_TTL)));
      return safe;
    } catch (HttpStatusCodeException ex) {
      if (ex.getStatusCode().value() == 401) {
        String fallback = resolveFallbackKey();
        if (!fallback.isBlank() && !fallback.equals(keyToUse)) {
          try {
            ResponseEntity<String> response = callEndpoint(matchId.trim(), fallback);
            MatchSnapshot parsed = parseSnapshot(response.getBody());
            MatchSnapshot safe = parsed == null ? MatchSnapshot.empty() : parsed;
            cache.put(matchId, new CachedSnapshot(safe, now.plus(CACHE_TTL)));
            return safe;
          } catch (Exception ignored) {
            // fall through to warning below
          }
        }
      }
      LOGGER.warn(
          "[STATPAL_ODDS_DEBUG] request failed matchId={} status={} body={}",
          matchId,
          ex.getStatusCode().value(),
          ex.getResponseBodyAsString());
      return MatchSnapshot.empty();
    } catch (Exception ex) {
      LOGGER.warn("[STATPAL_ODDS_DEBUG] request failed matchId={} error={}", matchId, ex.getMessage());
      return MatchSnapshot.empty();
    }
  }

  private ResponseEntity<String> callEndpoint(String matchId, String key) {
    String url = baseUrl + "?match_id=" + matchId + "&access_key=" + key;
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
    HttpEntity<Void> request = new HttpEntity<>(headers);
    return restTemplate.exchange(url, HttpMethod.GET, request, String.class);
  }

  private String resolvePrimaryKey() {
    if (accessKey != null && !accessKey.isBlank()) {
      return accessKey.trim();
    }
    return resolveFallbackKey();
  }

  private String resolveFallbackKey() {
    return fallbackAccessKey == null ? "" : fallbackAccessKey.trim();
  }

  private MatchSnapshot parseSnapshot(String body) {
    if (body == null || body.isBlank()) {
      return MatchSnapshot.empty();
    }
    try {
      JsonNode root = objectMapper.readTree(body);
      JsonNode match = findMatchNode(root);
      if (match == null || match.isMissingNode() || match.isNull()) {
        return MatchSnapshot.empty();
      }
      String home = text(match.path("home").path("name"));
      String away = text(match.path("away").path("name"));
      String status = text(match.path("status"));
      String minute = text(match.path("minute"));
      String injMinute = text(match.path("inj_minute"));
      if (!injMinute.isBlank()) {
        minute = minute.isBlank() ? injMinute : minute + "+" + injMinute;
      }
      String homeGoals = text(match.path("home").path("goals"));
      String awayGoals = text(match.path("away").path("goals"));
      String score = "";
      if (!homeGoals.isBlank() && !awayGoals.isBlank() && !"?".equals(homeGoals) && !"?".equals(awayGoals)) {
        score = homeGoals + "-" + awayGoals;
      }
      return new MatchSnapshot(home, away, status, minute, score);
    } catch (Exception ex) {
      LOGGER.warn("[STATPAL_ODDS_DEBUG] parse error={}", ex.getMessage());
      return MatchSnapshot.empty();
    }
  }

  private JsonNode findMatchNode(JsonNode root) {
    if (root == null || root.isMissingNode() || root.isNull()) {
      return null;
    }
    if (root.isObject()) {
      JsonNode directMatch = root.path("match");
      if (directMatch.isObject()) {
        return directMatch;
      }
      JsonNode liveMatches = root.path("live_matches");
      JsonNode nestedMatch = liveMatches.path("match");
      if (nestedMatch.isObject()) {
        return nestedMatch;
      }
      JsonNode oddsMatch = root.path("odds").path("match");
      if (oddsMatch.isObject()) {
        return oddsMatch;
      }
      if (root.has("home") && root.has("away")) {
        return root;
      }
      java.util.Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
      while (fields.hasNext()) {
        JsonNode found = findMatchNode(fields.next().getValue());
        if (found != null) {
          return found;
        }
      }
      return null;
    }
    if (root.isArray()) {
      for (JsonNode node : root) {
        JsonNode found = findMatchNode(node);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  private String text(JsonNode node) {
    return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText("").trim();
  }

  public static final class MatchSnapshot {
    private final String homeTeam;
    private final String awayTeam;
    private final String status;
    private final String minute;
    private final String score;

    private MatchSnapshot(String homeTeam, String awayTeam, String status, String minute, String score) {
      this.homeTeam = homeTeam == null ? "" : homeTeam;
      this.awayTeam = awayTeam == null ? "" : awayTeam;
      this.status = status == null ? "" : status;
      this.minute = minute == null ? "" : minute;
      this.score = score == null ? "" : score;
    }

    private static MatchSnapshot empty() {
      return new MatchSnapshot("", "", "", "", "");
    }

    public String getHomeTeam() {
      return homeTeam;
    }

    public String getAwayTeam() {
      return awayTeam;
    }

    public String getStatus() {
      return status;
    }

    public String getMinute() {
      return minute;
    }

    public String getScore() {
      return score;
    }
  }

  private record CachedSnapshot(MatchSnapshot snapshot, Instant expiresAt) {}
}
