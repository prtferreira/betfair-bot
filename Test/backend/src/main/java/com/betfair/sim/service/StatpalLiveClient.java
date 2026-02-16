package com.betfair.sim.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

  public StatpalLiveClient(
      RestTemplateBuilder restTemplateBuilder,
      ObjectMapper objectMapper,
      @Value("${statpal.live.base-url:https://statpal.io/api/v2/soccer/matches/live}") String baseUrl,
      @Value("${statpal.access-key:b5b07a3f-b019-4a18-8969-6045169feda9}") String accessKey) {
    this.restTemplate = restTemplateBuilder.build();
    this.objectMapper = objectMapper;
    this.baseUrl = baseUrl;
    this.accessKey = accessKey;
  }

  public List<LiveMatch> fetchLiveMatches() {
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
      JsonNode liveMatchesNode = root.path("live_matches");
      if (!liveMatchesNode.isMissingNode() && !liveMatchesNode.isNull()) {
        collectMatches(liveMatchesNode, matches);
      } else {
        collectMatches(root, matches);
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

  private void collectMatches(JsonNode node, List<LiveMatch> target) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return;
    }
    if (node.isArray()) {
      for (JsonNode child : node) {
        collectMatches(child, target);
      }
      return;
    }
    if (!node.isObject()) {
      return;
    }

    JsonNode matchArray = node.path("match");
    if (matchArray.isArray()) {
      for (JsonNode match : matchArray) {
        LiveMatch parsed = parseMatch(match);
        if (parsed != null) {
          target.add(parsed);
        }
      }
    }

    node.fields().forEachRemaining(entry -> collectMatches(entry.getValue(), target));
  }

  private LiveMatch parseMatch(JsonNode match) {
    if (match == null || !match.isObject()) {
      return null;
    }
    String homeTeam = match.path("home").path("name").asText("").trim();
    String awayTeam = match.path("away").path("name").asText("").trim();
    if (homeTeam.isBlank() || awayTeam.isBlank()) {
      return null;
    }

    Integer homeGoals = parseInt(match.path("home").path("goals").asText(""));
    Integer awayGoals = parseInt(match.path("away").path("goals").asText(""));
    String status = match.path("status").asText("").trim();
    String minute = extractMinute(match);

    return new LiveMatch(homeTeam, awayTeam, homeGoals, awayGoals, status, minute);
  }

  private String extractMinute(JsonNode match) {
    String minute = match.path("minute").asText("").trim();
    if (!minute.isBlank()) {
      String inj = match.path("inj_minute").asText("").trim();
      return inj.isBlank() ? minute + "'" : minute + "+" + inj + "'";
    }
    String inj = match.path("inj_minute").asText("").trim();
    if (!inj.isBlank()) {
      return inj + "'";
    }
    int maxEventMinute = findMaxEventMinute(match.path("events").path("event"));
    if (maxEventMinute > 0) {
      return maxEventMinute + "'";
    }
    return "";
  }

  private int findMaxEventMinute(JsonNode events) {
    if (events == null || events.isNull() || events.isMissingNode()) {
      return 0;
    }
    int max = 0;
    if (events.isArray()) {
      for (JsonNode event : events) {
        int parsed = parseInt(event.path("minute").asText(""), 0);
        if (parsed > max) {
          max = parsed;
        }
      }
      return max;
    }
    int parsed = parseInt(events.path("minute").asText(""), 0);
    return Math.max(0, parsed);
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
    private final String homeTeam;
    private final String awayTeam;
    private final Integer homeGoals;
    private final Integer awayGoals;
    private final String status;
    private final String minute;

    private LiveMatch(
        String homeTeam,
        String awayTeam,
        Integer homeGoals,
        Integer awayGoals,
        String status,
        String minute) {
      this.homeTeam = homeTeam;
      this.awayTeam = awayTeam;
      this.homeGoals = homeGoals;
      this.awayGoals = awayGoals;
      this.status = status;
      this.minute = minute;
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
  }
}
