package com.betfair.sim.service;

import com.betfair.sim.model.Game;
import com.betfair.sim.model.EventMarket;
import com.betfair.sim.model.EventSelection;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class BetfairApiClient {
  private static final String FOOTBALL_EVENT_TYPE_ID = "1";
  private static final int MARKET_BOOK_BATCH_SIZE = 40;
  private static final int AUX_EVENT_BATCH_SIZE = 20;
  private static final Pattern GOAL_LINE_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");
  private static final Pattern SCORE_SPAN_PATTERN =
      Pattern.compile("<span[^>]*class=\"[^\"]*score[^\"]*\"[^>]*>([^<]+)</span>", Pattern.CASE_INSENSITIVE);
  private static final Logger LOGGER = LoggerFactory.getLogger(BetfairApiClient.class);

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final BetfairSessionStore sessionStore;
  private final String rpcBaseUrl;
  private final String appKey;
  private final String sessionToken;
  private final boolean domScorePlaywrightEnabled;
  private final String domScorePlaywrightNodeCommand;
  private final String domScorePlaywrightScriptPath;
  private final int domScorePlaywrightTimeoutMs;

  public BetfairApiClient(
      RestTemplateBuilder restTemplateBuilder,
      ObjectMapper objectMapper,
      BetfairSessionStore sessionStore,
      @Value("${betfair.rpc.base-url:https://api.betfair.com/exchange/betting/json-rpc/v1}")
          String rpcBaseUrl,
      @Value("${betfair.app-key:}") String appKey,
      @Value("${betfair.session-token:}") String sessionToken,
      @Value("${betfair.dom-score.playwright.enabled:true}") boolean domScorePlaywrightEnabled,
      @Value("${betfair.dom-score.playwright.node-command:node}") String domScorePlaywrightNodeCommand,
      @Value("${betfair.dom-score.playwright.script-path:../tools/betfair-score.js}") String domScorePlaywrightScriptPath,
      @Value("${betfair.dom-score.playwright.timeout-ms:18000}") int domScorePlaywrightTimeoutMs) {
    this.restTemplate = restTemplateBuilder.build();
    this.objectMapper = objectMapper;
    this.sessionStore = sessionStore;
    this.rpcBaseUrl = rpcBaseUrl;
    this.appKey = appKey;
    this.sessionToken = sessionToken;
    this.domScorePlaywrightEnabled = domScorePlaywrightEnabled;
    this.domScorePlaywrightNodeCommand = domScorePlaywrightNodeCommand;
    this.domScorePlaywrightScriptPath = domScorePlaywrightScriptPath;
    this.domScorePlaywrightTimeoutMs = domScorePlaywrightTimeoutMs;
  }

  public boolean isEnabled() {
    return !appKey.isBlank() && !resolveSessionToken().isBlank();
  }

  public boolean hasAppKey() {
    return !appKey.isBlank();
  }

  public boolean hasSessionToken() {
    return !resolveSessionToken().isBlank();
  }

  public List<Game> listGames(LocalDate date) {
    return listEvents(date);
  }

  public List<Game> listEventsAllFootball() {
    return listEvents(null);
  }

  public List<Game> listMatchOddsForDate(LocalDate date) {
    if (!isEnabled()) {
      LOGGER.warn("Betfair API disabled. appKeyPresent={}, sessionTokenPresent={}", hasAppKey(), hasSessionToken());
      return List.of();
    }

    LocalDate resolvedDate = date == null ? LocalDate.now(ZoneOffset.UTC) : date;
    Instant now = Instant.now();
    try {
      Instant dayStart = resolvedDate.atStartOfDay(ZoneOffset.UTC).toInstant();
      Instant dayEnd = resolvedDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
      Instant start = dayStart;
      if (start.isAfter(dayEnd)) {
        return List.of();
      }

      LOGGER.info(
          "Betfair listMatchOddsForDate request window for {}: {} -> {}",
          resolvedDate,
          start,
          dayEnd);

      List<MatchOddsMarket> markets = listMatchOddsMarketsForWindows(start, dayEnd);
      List<MatchOddsMarket> inPlayMarkets = listInPlayFootballMarkets();
      if (!inPlayMarkets.isEmpty()) {
        Map<String, MatchOddsMarket> merged = new LinkedHashMap<>();
        for (MatchOddsMarket market : markets) {
          merged.put(market.marketId, market);
        }
        for (MatchOddsMarket market : inPlayMarkets) {
          merged.putIfAbsent(market.marketId, market);
        }
        markets = new ArrayList<>(merged.values());
      }
      if (markets.isEmpty()) {
        return List.of();
      }
      if (LOGGER.isInfoEnabled()) {
        String latest =
            markets.stream()
                .map(m -> m.startTimeText)
                .filter(t -> t != null && !t.isBlank())
                .max(String::compareTo)
                .orElse("n/a");
        LOGGER.info(
            "Betfair listMarketCatalogue returned {} MATCH_ODDS markets for {} (latest start={})",
            markets.size(),
            resolvedDate,
            latest);
      }
      LOGGER.info("Betfair listMarketCatalogue returned {} MATCH_ODDS markets for {}", markets.size(), resolvedDate);
      Map<String, MatchOddsMarket> byMarketId =
          markets.stream().collect(Collectors.toMap(m -> m.marketId, m -> m));

      Map<String, MarketBookOdds> oddsByMarket =
          fetchMarketBookOdds(new ArrayList<>(byMarketId.keySet()), resolvedDate);
      Map<String, AuxiliaryOdds> auxiliaryOddsByEventId =
          fetchAuxiliaryOdds(markets, resolvedDate);

      List<Game> games = new ArrayList<>();
      for (MatchOddsMarket market : markets) {
        MarketBookOdds odds = oddsByMarket.get(market.marketId);
        if (LOGGER.isDebugEnabled()) {
          Double homeOdds = odds == null ? null : odds.backOddsBySelection.get(market.homeSelectionId);
          Double drawOdds =
              (odds == null || market.drawSelectionId < 0)
                  ? null
                  : odds.backOddsBySelection.get(market.drawSelectionId);
          Double awayOdds = odds == null ? null : odds.backOddsBySelection.get(market.awaySelectionId);
          boolean inPlay = odds != null && odds.inPlay;
          LOGGER.debug(
              "MATCH_ODDS marketId={} eventId={} {} vs {} start={} inPlay={} odds(H/D/A)={}/{}/{}",
              market.marketId,
              market.eventId,
              market.homeTeam,
              market.awayTeam,
              market.startTimeText,
              inPlay,
              homeOdds,
              drawOdds,
              awayOdds);
        }
        Double homeOdds = odds == null ? null : odds.backOddsBySelection.get(market.homeSelectionId);
        Double drawOdds =
            (odds == null || market.drawSelectionId < 0)
                ? null
                : odds.backOddsBySelection.get(market.drawSelectionId);
        Double awayOdds = odds == null ? null : odds.backOddsBySelection.get(market.awaySelectionId);
        Double homeLayOdds = odds == null ? null : odds.layOddsBySelection.get(market.homeSelectionId);
        Double drawLayOdds =
            (odds == null || market.drawSelectionId < 0)
                ? null
                : odds.layOddsBySelection.get(market.drawSelectionId);
        Double awayLayOdds = odds == null ? null : odds.layOddsBySelection.get(market.awaySelectionId);
        Game game =
            new Game(
                market.eventId,
                "Football",
                market.league,
                market.homeTeam,
                market.awayTeam,
                market.startTimeText,
                market.marketId,
                homeOdds,
                drawOdds,
                awayOdds);
        game.setHomeSelectionId(market.homeSelectionId);
        game.setDrawSelectionId(market.drawSelectionId);
        game.setAwaySelectionId(market.awaySelectionId);
        game.setMarketStatus(odds == null ? "" : odds.status);
        game.setInPlay(odds != null && odds.inPlay);
        game.setHomeLayOdds(homeLayOdds);
        game.setDrawLayOdds(drawLayOdds);
        game.setAwayLayOdds(awayLayOdds);
        AuxiliaryOdds auxiliaryOdds = auxiliaryOddsByEventId.get(market.eventId);
        if (auxiliaryOdds != null) {
          game.setOver05Odds(auxiliaryOdds.over05Odds);
          game.setUnder05Odds(auxiliaryOdds.under05Odds);
          game.setOver15Odds(auxiliaryOdds.over15Odds);
          game.setUnder15Odds(auxiliaryOdds.under15Odds);
          game.setOver25Odds(auxiliaryOdds.over25Odds);
          game.setUnder25Odds(auxiliaryOdds.under25Odds);
          game.setHtHomeOdds(auxiliaryOdds.htHomeOdds);
          game.setHtDrawOdds(auxiliaryOdds.htDrawOdds);
          game.setHtAwayOdds(auxiliaryOdds.htAwayOdds);
          game.setFullTime00Odds(auxiliaryOdds.fullTime00Odds);
          game.setOu05MarketStatus(auxiliaryOdds.ou05MarketStatus);
          game.setHtMarketStatus(auxiliaryOdds.htMarketStatus);
        }
        games.add(game);
      }
      games.sort(Comparator.comparing(Game::getStartTime, Comparator.nullsLast(String::compareTo)));
      return games;
    } catch (Exception ex) {
      LOGGER.warn("Betfair listMatchOddsForDate failed for {}", resolvedDate, ex);
      return List.of();
    }
  }

  public List<Game> listInPlayBrazilSerieA() {
    if (!isEnabled()) {
      LOGGER.warn("Betfair API disabled. appKeyPresent={}, sessionTokenPresent={}", hasAppKey(), hasSessionToken());
      return List.of();
    }

    HttpEntity<List<Map<String, Object>>> catalogueRequest =
        new HttpEntity<>(List.of(buildListInPlayMarketCatalogueRequest()), buildHeaders());

    try {
      String response = restTemplate.postForObject(rpcBaseUrl, catalogueRequest, String.class);
      if (response == null || response.isBlank()) {
        LOGGER.warn("Betfair in-play listMarketCatalogue returned empty response");
        return List.of();
      }

      List<MatchOddsMarket> markets = parseMatchOddsMarkets(response).stream()
          .filter(this::isBrazilSerieAMarket)
          .toList();
      if (markets.isEmpty()) {
        return List.of();
      }

      Map<String, MarketBookOdds> oddsByMarket =
          fetchMarketBookOdds(
              markets.stream().map(m -> m.marketId).toList(),
              LocalDate.now(ZoneOffset.UTC));

      List<Game> games = new ArrayList<>();
      for (MatchOddsMarket market : markets) {
        MarketBookOdds odds = oddsByMarket.get(market.marketId);
        if (odds == null || !odds.inPlay) {
          continue;
        }
        Double homeOdds = odds.backOddsBySelection.get(market.homeSelectionId);
        Double drawOdds =
            market.drawSelectionId < 0 ? null : odds.backOddsBySelection.get(market.drawSelectionId);
        Double awayOdds = odds.backOddsBySelection.get(market.awaySelectionId);
        Double homeLayOdds = odds.layOddsBySelection.get(market.homeSelectionId);
        Double drawLayOdds =
            market.drawSelectionId < 0 ? null : odds.layOddsBySelection.get(market.drawSelectionId);
        Double awayLayOdds = odds.layOddsBySelection.get(market.awaySelectionId);
        Game game =
            new Game(
                market.marketId,
                "Football",
                market.league,
                market.homeTeam,
                market.awayTeam,
                market.startTimeText,
                market.marketId,
                homeOdds,
                drawOdds,
                awayOdds);
        game.setHomeSelectionId(market.homeSelectionId);
        game.setDrawSelectionId(market.drawSelectionId);
        game.setAwaySelectionId(market.awaySelectionId);
        game.setMarketStatus(odds.status);
        game.setInPlay(odds.inPlay);
        game.setHomeLayOdds(homeLayOdds);
        game.setDrawLayOdds(drawLayOdds);
        game.setAwayLayOdds(awayLayOdds);
        games.add(
            game);
      }
      games.sort(Comparator.comparing(Game::getStartTime, Comparator.nullsLast(String::compareTo)));
      return games;
    } catch (Exception ex) {
      LOGGER.warn("Betfair listInPlayBrazilSerieA failed", ex);
      return List.of();
    }
  }

  public List<Game> listInPlayFootballMatchOdds() {
    if (!isEnabled()) {
      LOGGER.warn("Betfair API disabled. appKeyPresent={}, sessionTokenPresent={}", hasAppKey(), hasSessionToken());
      return List.of();
    }

    try {
      List<MatchOddsMarket> markets = listInPlayFootballMarkets();
      if (markets.isEmpty()) {
        return List.of();
      }

      Map<String, MarketBookOdds> oddsByMarket =
          fetchMarketBookOdds(
              markets.stream().map(m -> m.marketId).toList(),
              LocalDate.now(ZoneOffset.UTC));

      List<Game> games = new ArrayList<>();
      for (MatchOddsMarket market : markets) {
        MarketBookOdds odds = oddsByMarket.get(market.marketId);
        if (odds == null) {
          continue;
        }
        Double homeOdds = odds.backOddsBySelection.get(market.homeSelectionId);
        Double drawOdds =
            market.drawSelectionId < 0 ? null : odds.backOddsBySelection.get(market.drawSelectionId);
        Double awayOdds = odds.backOddsBySelection.get(market.awaySelectionId);
        Double homeLayOdds = odds.layOddsBySelection.get(market.homeSelectionId);
        Double drawLayOdds =
            market.drawSelectionId < 0 ? null : odds.layOddsBySelection.get(market.drawSelectionId);
        Double awayLayOdds = odds.layOddsBySelection.get(market.awaySelectionId);
        Game game =
            new Game(
                market.eventId,
                "Football",
                market.league,
                market.homeTeam,
                market.awayTeam,
                market.startTimeText,
                market.marketId,
                homeOdds,
                drawOdds,
                awayOdds);
        game.setHomeSelectionId(market.homeSelectionId);
        game.setDrawSelectionId(market.drawSelectionId);
        game.setAwaySelectionId(market.awaySelectionId);
        game.setMarketStatus(odds.status);
        game.setInPlay(odds.inPlay);
        game.setHomeLayOdds(homeLayOdds);
        game.setDrawLayOdds(drawLayOdds);
        game.setAwayLayOdds(awayLayOdds);
        games.add(game);
      }
      games.sort(Comparator.comparing(Game::getStartTime, Comparator.nullsLast(String::compareTo)));
      return games;
    } catch (Exception ex) {
      LOGGER.warn("Betfair listInPlayFootballMatchOdds failed", ex);
      return List.of();
    }
  }

  public List<Game> listMatchOddsByMarketIds(List<String> marketIds) {
    if (!isEnabled()) {
      return List.of();
    }
    if (marketIds == null || marketIds.isEmpty()) {
      return List.of();
    }

    List<String> uniqueMarketIds =
        marketIds.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(id -> !id.isBlank())
            .distinct()
            .toList();
    if (uniqueMarketIds.isEmpty()) {
      return List.of();
    }

    try {
      List<MatchOddsMarket> markets = new ArrayList<>();
      for (int i = 0; i < uniqueMarketIds.size(); i += MARKET_BOOK_BATCH_SIZE) {
        List<String> batch =
            uniqueMarketIds.subList(i, Math.min(i + MARKET_BOOK_BATCH_SIZE, uniqueMarketIds.size()));
        HttpEntity<List<Map<String, Object>>> catalogueRequest =
            new HttpEntity<>(List.of(buildListMarketCatalogueRequestForMarketIds(batch, true)), buildHeaders());
        String response = restTemplate.postForObject(rpcBaseUrl, catalogueRequest, String.class);
        if (response == null || response.isBlank()) {
          continue;
        }
        markets.addAll(parseMatchOddsMarkets(response));
      }
      if (markets.isEmpty()) {
        return List.of();
      }

      Map<String, MatchOddsMarket> marketById = new LinkedHashMap<>();
      for (MatchOddsMarket market : markets) {
        marketById.putIfAbsent(market.marketId, market);
      }

      LocalDate resolvedDate = LocalDate.now(ZoneOffset.UTC);
      Map<String, MarketBookOdds> oddsByMarket =
          fetchMarketBookOdds(new ArrayList<>(marketById.keySet()), resolvedDate);
      Map<String, AuxiliaryOdds> auxiliaryOddsByEventId =
          fetchAuxiliaryOdds(new ArrayList<>(marketById.values()), resolvedDate);

      List<Game> games = new ArrayList<>();
      for (MatchOddsMarket market : marketById.values()) {
        MarketBookOdds odds = oddsByMarket.get(market.marketId);
        Double homeOdds = odds == null ? null : odds.backOddsBySelection.get(market.homeSelectionId);
        Double drawOdds =
            (odds == null || market.drawSelectionId < 0)
                ? null
                : odds.backOddsBySelection.get(market.drawSelectionId);
        Double awayOdds = odds == null ? null : odds.backOddsBySelection.get(market.awaySelectionId);
        Double homeLayOdds = odds == null ? null : odds.layOddsBySelection.get(market.homeSelectionId);
        Double drawLayOdds =
            (odds == null || market.drawSelectionId < 0)
                ? null
                : odds.layOddsBySelection.get(market.drawSelectionId);
        Double awayLayOdds = odds == null ? null : odds.layOddsBySelection.get(market.awaySelectionId);

        Game game =
            new Game(
                market.marketId,
                "Football",
                market.league,
                market.homeTeam,
                market.awayTeam,
                market.startTimeText,
                market.marketId,
                homeOdds,
                drawOdds,
                awayOdds);
        game.setHomeSelectionId(market.homeSelectionId);
        game.setDrawSelectionId(market.drawSelectionId);
        game.setAwaySelectionId(market.awaySelectionId);
        game.setMarketStatus(odds == null ? "" : odds.status);
        game.setInPlay(odds != null && odds.inPlay);
        game.setHomeLayOdds(homeLayOdds);
        game.setDrawLayOdds(drawLayOdds);
        game.setAwayLayOdds(awayLayOdds);
        AuxiliaryOdds auxiliaryOdds = auxiliaryOddsByEventId.get(market.eventId);
        if (auxiliaryOdds != null) {
          game.setOver05Odds(auxiliaryOdds.over05Odds);
          game.setUnder05Odds(auxiliaryOdds.under05Odds);
          game.setOver15Odds(auxiliaryOdds.over15Odds);
          game.setUnder15Odds(auxiliaryOdds.under15Odds);
          game.setOver25Odds(auxiliaryOdds.over25Odds);
          game.setUnder25Odds(auxiliaryOdds.under25Odds);
          game.setHtHomeOdds(auxiliaryOdds.htHomeOdds);
          game.setHtDrawOdds(auxiliaryOdds.htDrawOdds);
          game.setHtAwayOdds(auxiliaryOdds.htAwayOdds);
          game.setFullTime00Odds(auxiliaryOdds.fullTime00Odds);
          game.setOu05MarketStatus(auxiliaryOdds.ou05MarketStatus);
          game.setHtMarketStatus(auxiliaryOdds.htMarketStatus);
        }
        games.add(game);
      }
      games.sort(Comparator.comparing(Game::getStartTime, Comparator.nullsLast(String::compareTo)));
      return games;
    } catch (Exception ex) {
      LOGGER.warn("Betfair listMatchOddsByMarketIds failed", ex);
      return List.of();
    }
  }

  public Map<String, EventIdentity> resolveEventIdentityForMarketIds(List<String> marketIds) {
    if (!isEnabled()) {
      return Map.of();
    }
    if (marketIds == null || marketIds.isEmpty()) {
      return Map.of();
    }

    List<String> uniqueMarketIds =
        marketIds.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(id -> !id.isBlank())
            .distinct()
            .toList();
    if (uniqueMarketIds.isEmpty()) {
      return Map.of();
    }

    try {
      Map<String, EventRef> refs = fetchEventRefsForMarkets(uniqueMarketIds);
      Map<String, EventIdentity> byMarketId = new LinkedHashMap<>();
      for (String marketId : uniqueMarketIds) {
        EventRef ref = refs.get(marketId);
        if (ref == null) {
          continue;
        }
        byMarketId.put(
            marketId,
            new EventIdentity(
                ref.eventId() == null ? "" : ref.eventId(),
                ref.eventName() == null ? "" : ref.eventName()));
      }
      return byMarketId;
    } catch (Exception ex) {
      LOGGER.warn("Betfair resolveEventIdentityForMarketIds failed", ex);
      return Map.of();
    }
  }

  public String fetchExchangePageScore(
      String eventId, String league, String homeTeam, String awayTeam) {
    ExchangeLiveSnapshot snapshot = fetchExchangeLiveSnapshot(eventId, league, homeTeam, awayTeam);
    return snapshot == null ? "" : snapshot.score();
  }

  public ExchangeLiveSnapshot fetchExchangeLiveSnapshot(
      String eventId, String league, String homeTeam, String awayTeam) {
    if (eventId == null || eventId.isBlank()) {
      return new ExchangeLiveSnapshot("", "");
    }
    String competitionSlug = slugify(league);
    String eventSlug = slugify(homeTeam) + "-v-" + slugify(awayTeam);
    if (competitionSlug.isBlank()) {
      competitionSlug = "football";
    }
    String primaryUrl =
        "https://www.betfair.com/exchange/plus/en/football/"
            + competitionSlug
            + "/"
            + eventSlug
            + "-betting-"
            + eventId;
    List<String> fallbackUrls =
        List.of(
            primaryUrl,
            "https://www.betfair.com/exchange/plus/en/football/" + eventSlug + "-betting-" + eventId,
            "https://www.betfair.com/exchange/plus/en/football/event/" + eventId);

    for (String url : fallbackUrls) {
      ExchangeLiveSnapshot snapshot = parseSnapshotWithPlaywright(url, eventId);
      if (snapshot != null && (!snapshot.score().isBlank() || !snapshot.minute().isBlank())) {
        return snapshot;
      }
    }

    return new ExchangeLiveSnapshot("", "");
  }

  private ExchangeLiveSnapshot parseSnapshotWithPlaywright(String url, String eventId) {
    if (!domScorePlaywrightEnabled) {
      return new ExchangeLiveSnapshot("", "");
    }
    Path scriptPath = resolvePlaywrightScriptPath();
    if (scriptPath == null) {
      return new ExchangeLiveSnapshot("", "");
    }
    ProcessBuilder processBuilder =
        new ProcessBuilder(domScorePlaywrightNodeCommand, scriptPath.toString(), url);
    processBuilder.redirectErrorStream(true);
    try {
      Process process = processBuilder.start();
      boolean finished = process.waitFor(Math.max(1000, domScorePlaywrightTimeoutMs), TimeUnit.MILLISECONDS);
      if (!finished) {
        process.destroyForcibly();
        LOGGER.debug("Playwright score scrape timeout for eventId={} url={}", eventId, url);
        return new ExchangeLiveSnapshot("", "");
      }
      String output;
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        output = reader.lines().collect(Collectors.joining()).trim();
      }
      if (process.exitValue() != 0) {
        LOGGER.debug("Playwright score scrape failed for eventId={} url={} exit={}", eventId, url, process.exitValue());
        return new ExchangeLiveSnapshot("", "");
      }
      if (output == null || output.isBlank()) {
        return new ExchangeLiveSnapshot("", "");
      }
      JsonNode node = objectMapper.readTree(output);
      String score = node.path("score").asText("").trim();
      String minute = node.path("minute").asText("").trim();
      if (!score.matches("\\d+-\\d+")) {
        score = "";
      }
      if (!minute.matches("^\\d{1,3}(?:\\+\\d{1,2})?'$") && !"HT".equals(minute) && !"FT".equals(minute)
          && !"Finished".equalsIgnoreCase(minute)) {
        minute = "";
      }
      return new ExchangeLiveSnapshot(score, minute);
    } catch (Exception ex) {
      LOGGER.debug("Playwright score scrape error for eventId={} url={}", eventId, url);
      return new ExchangeLiveSnapshot("", "");
    }
  }

  private Path resolvePlaywrightScriptPath() {
    Path configured = Paths.get(domScorePlaywrightScriptPath);
    if (Files.exists(configured)) {
      return configured;
    }
    Path localTools = Paths.get("tools", "betfair-score.js");
    if (Files.exists(localTools)) {
      return localTools;
    }
    Path parentTools = Paths.get("..", "tools", "betfair-score.js");
    if (Files.exists(parentTools)) {
      return parentTools;
    }
    return null;
  }

  public Map<String, MarketStatus> getMarketStatuses(List<String> marketIds) {
    if (!isEnabled()) {
      return Map.of();
    }
    if (marketIds == null || marketIds.isEmpty()) {
      return Map.of();
    }

    Map<String, MarketStatus> statusByMarket = new LinkedHashMap<>();
    try {
      HttpEntity<List<Map<String, Object>>> catalogueRequest =
          new HttpEntity<>(List.of(buildListMarketCatalogueRequestForMarketIds(marketIds)), buildHeaders());
      String response = restTemplate.postForObject(rpcBaseUrl, catalogueRequest, String.class);
      if (response == null || response.isBlank()) {
        return Map.of();
      }
      Map<String, Instant> startTimes = parseMarketStartTimes(response);

      Map<String, MarketBookOdds> oddsByMarket =
          fetchMarketBookOdds(marketIds, LocalDate.now(ZoneOffset.UTC));

      for (String marketId : marketIds) {
        Instant startTime = startTimes.get(marketId);
        MarketBookOdds odds = oddsByMarket.get(marketId);
        boolean inPlay = odds != null && odds.inPlay;
        String status = odds == null ? "" : odds.status;
        statusByMarket.put(marketId, new MarketStatus(startTime, inPlay, status));
      }
      return statusByMarket;
    } catch (Exception ex) {
      LOGGER.warn("Betfair getMarketStatuses failed", ex);
      return Map.of();
    }
  }

  public Map<String, MarketOutcome> getMarketOutcomes(List<String> marketIds) {
    if (!isEnabled()) {
      return Map.of();
    }
    if (marketIds == null || marketIds.isEmpty()) {
      return Map.of();
    }

    Map<String, MarketOutcome> outcomesByMarket = new LinkedHashMap<>();
    try {
      for (int i = 0; i < marketIds.size(); i += MARKET_BOOK_BATCH_SIZE) {
        List<String> batch = marketIds.subList(i, Math.min(i + MARKET_BOOK_BATCH_SIZE, marketIds.size()));
        HttpEntity<List<Map<String, Object>>> bookRequest =
            new HttpEntity<>(List.of(buildListMarketBookRequest(batch, true)), buildHeaders());
        String bookResponse = restTemplate.postForObject(rpcBaseUrl, bookRequest, String.class);
        if (bookResponse == null || bookResponse.isBlank()) {
          continue;
        }
        outcomesByMarket.putAll(parseMarketBookOutcomes(bookResponse));
      }
      return outcomesByMarket;
    } catch (Exception ex) {
      LOGGER.warn("Betfair getMarketOutcomes failed", ex);
      return Map.of();
    }
  }

  public Map<String, InferredScore> inferScoresFromClosedGoalMarkets(
      List<String> matchMarketIds) {
    if (!isEnabled()) {
      return Map.of();
    }
    if (matchMarketIds == null || matchMarketIds.isEmpty()) {
      return Map.of();
    }

    try {
      Map<String, EventRef> matchEvents = fetchEventRefsForMarkets(matchMarketIds);
      if (matchEvents.isEmpty()) {
        return Map.of();
      }
      List<String> eventIds =
          matchEvents.values().stream()
              .map(EventRef::eventId)
              .filter(id -> id != null && !id.isBlank())
              .distinct()
              .toList();
      if (eventIds.isEmpty()) {
        return Map.of();
      }

      List<GoalLineMarketRef> goalMarkets = fetchGoalLineMarketsForEvents(eventIds);
      if (goalMarkets.isEmpty()) {
        return Map.of();
      }

      Map<String, MarketBookOutcome> outcomesByMarket =
          fetchMarketBookOutcomeByMarketId(
              goalMarkets.stream().map(GoalLineMarketRef::marketId).distinct().toList());

      Map<String, GoalBounds> boundsByEvent = new HashMap<>();
      for (GoalLineMarketRef ref : goalMarkets) {
        MarketBookOutcome outcome = outcomesByMarket.get(ref.marketId());
        if (outcome == null || !outcome.closed()) {
          continue;
        }
        if (outcome.winnerOver() == null) {
          continue;
        }
        GoalBounds bounds = boundsByEvent.computeIfAbsent(ref.eventId(), key -> new GoalBounds());
        bounds.apply(ref.homeSide(), ref.line(), outcome.winnerOver());
      }

      Map<String, InferredScore> inferred = new HashMap<>();
      for (Map.Entry<String, EventRef> entry : matchEvents.entrySet()) {
        GoalBounds bounds = boundsByEvent.get(entry.getValue().eventId());
        if (bounds == null) {
          continue;
        }
        inferred.put(entry.getKey(), bounds.toInferredScore());
      }
      return inferred;
    } catch (Exception ex) {
      LOGGER.warn("Betfair score inference from closed goal markets failed", ex);
      return Map.of();
    }
  }

  public Map<String, InferredScore> inferScoresFromCorrectScoreMarkets(List<String> matchMarketIds) {
    if (!isEnabled()) {
      return Map.of();
    }
    if (matchMarketIds == null || matchMarketIds.isEmpty()) {
      return Map.of();
    }

    try {
      LOGGER.info("[SCORE_DEBUG] inferScoresFromCorrectScoreMarkets start markets={}", matchMarketIds.size());
      Map<String, EventRef> matchEvents = fetchEventRefsForMarkets(matchMarketIds);
      if (matchEvents.isEmpty()) {
        LOGGER.info("[SCORE_DEBUG] no match events resolved from marketIds");
        return Map.of();
      }
      List<String> eventIds =
          matchEvents.values().stream()
              .map(EventRef::eventId)
              .filter(id -> id != null && !id.isBlank())
              .distinct()
              .toList();
      if (eventIds.isEmpty()) {
        LOGGER.info("[SCORE_DEBUG] no eventIds extracted from match events");
        return Map.of();
      }
      LOGGER.info("[SCORE_DEBUG] resolved events={}", eventIds.size());

      List<CorrectScoreMarketRef> correctScoreMarkets = fetchCorrectScoreMarketsForEvents(eventIds);
      if (correctScoreMarkets.isEmpty()) {
        LOGGER.info("[SCORE_DEBUG] no correct-score markets found for events");
        return Map.of();
      }
      LOGGER.info("[SCORE_DEBUG] correct-score markets found={}", correctScoreMarkets.size());

      Map<String, InferredScore> scoreByCorrectScoreMarketId =
          fetchCorrectScoreByMarketId(
              correctScoreMarkets.stream().map(CorrectScoreMarketRef::marketId).distinct().toList());
      LOGGER.info(
          "[SCORE_DEBUG] parsed scores from correct-score marketBook={}",
          scoreByCorrectScoreMarketId.size());

      Map<String, InferredScore> scoreByEvent = new HashMap<>();
      for (CorrectScoreMarketRef ref : correctScoreMarkets) {
        InferredScore score = scoreByCorrectScoreMarketId.get(ref.marketId());
        if (score == null) {
          continue;
        }
        scoreByEvent.putIfAbsent(ref.eventId(), score);
      }

      Map<String, InferredScore> byMatchMarket = new HashMap<>();
      for (String matchMarketId : matchMarketIds) {
        EventRef eventRef = matchEvents.get(matchMarketId);
        if (eventRef == null) {
          continue;
        }
        InferredScore score = scoreByEvent.get(eventRef.eventId());
        if (score != null) {
          byMatchMarket.put(matchMarketId, score);
        }
      }
      LOGGER.info("[SCORE_DEBUG] final scores mapped to match markets={}", byMatchMarket.size());
      return byMatchMarket;
    } catch (Exception ex) {
      LOGGER.warn("Betfair score inference from CORRECT_SCORE failed", ex);
      return Map.of();
    }
  }

  private Map<String, MarketBookOdds> fetchMarketBookOdds(
      List<String> marketIds, LocalDate resolvedDate) throws Exception {
    if (marketIds.isEmpty()) {
      return Map.of();
    }

    Map<String, MarketBookOdds> combined = new HashMap<>();
    for (int i = 0; i < marketIds.size(); i += MARKET_BOOK_BATCH_SIZE) {
      List<String> batch = marketIds.subList(i, Math.min(i + MARKET_BOOK_BATCH_SIZE, marketIds.size()));
      HttpEntity<List<Map<String, Object>>> bookRequest =
          new HttpEntity<>(List.of(buildListMarketBookRequest(batch, false)), buildHeaders());
      String bookResponse = restTemplate.postForObject(rpcBaseUrl, bookRequest, String.class);
      if (bookResponse == null || bookResponse.isBlank()) {
        LOGGER.warn(
            "Betfair listMarketBook returned empty response for {} (batch {}-{})",
            resolvedDate,
            i,
            i + batch.size());
        continue;
      }
      combined.putAll(parseMarketBookOdds(bookResponse));
    }
    return combined;
  }

  private List<MatchOddsMarket> listMatchOddsMarketsForWindows(Instant start, Instant end)
      throws Exception {
    List<MatchOddsMarket> combined = new ArrayList<>();
    Map<String, MatchOddsMarket> byId = new HashMap<>();

    Instant windowStart = start;
    while (windowStart.isBefore(end)) {
      Instant windowEnd = windowStart.plusSeconds(6 * 60 * 60);
      if (windowEnd.isAfter(end)) {
        windowEnd = end;
      }

      HttpEntity<List<Map<String, Object>>> catalogueRequest =
          new HttpEntity<>(
              List.of(buildListMarketCatalogueRequest(windowStart, windowEnd)), buildHeaders());
      String response = restTemplate.postForObject(rpcBaseUrl, catalogueRequest, String.class);
      if (response == null || response.isBlank()) {
        LOGGER.warn("Betfair listMarketCatalogue returned empty response for window {} -> {}", windowStart, windowEnd);
        windowStart = windowEnd;
        continue;
      }

      List<MatchOddsMarket> markets = parseMatchOddsMarkets(response);
      LOGGER.info(
          "Betfair listMarketCatalogue window {} -> {} returned {} markets",
          windowStart,
          windowEnd,
          markets.size());
      for (MatchOddsMarket market : markets) {
        if (!byId.containsKey(market.marketId)) {
          byId.put(market.marketId, market);
          combined.add(market);
        }
      }

      windowStart = windowEnd;
    }

    return combined;
  }

  public String fetchEventsRaw(LocalDate date) {
    if (!isEnabled()) {
      LOGGER.warn("Betfair API disabled. appKeyPresent={}, sessionTokenPresent={}", hasAppKey(), hasSessionToken());
      return "";
    }
    HttpEntity<List<Map<String, Object>>> request =
        new HttpEntity<>(List.of(buildListEventsRequest(date)), buildHeaders());
    try {
      String response = restTemplate.postForObject(rpcBaseUrl, request, String.class);
      return response == null ? "" : response;
    } catch (Exception ex) {
      LOGGER.warn("Betfair listEvents failed for raw response {}", date == null ? "all dates" : date, ex);
      return "";
    }
  }

  public List<EventMarket> listMarketsForEvent(String eventId, List<String> marketTypes) {
    if (!isEnabled()) {
      LOGGER.warn("Betfair API disabled. appKeyPresent={}, sessionTokenPresent={}", hasAppKey(), hasSessionToken());
      return List.of();
    }
    if (eventId == null || eventId.isBlank()) {
      return List.of();
    }

    HttpEntity<List<Map<String, Object>>> request =
        new HttpEntity<>(
            List.of(buildListEventMarketsCatalogueRequest(List.of(eventId.trim()))),
            buildHeaders());
    try {
      String response = restTemplate.postForObject(rpcBaseUrl, request, String.class);
      if (response == null || response.isBlank()) {
        return List.of();
      }
      List<EventMarket> markets = new ArrayList<>(parseEventMarkets(response, eventId.trim()));
      Set<String> allowedTypes =
          marketTypes == null
              ? Set.of()
              : marketTypes.stream()
                  .filter(Objects::nonNull)
                  .map(this::normalizeMarketType)
                  .filter(type -> !type.isBlank())
                  .collect(Collectors.toSet());
      if (!allowedTypes.isEmpty()) {
        markets =
            markets.stream()
                .filter(m -> allowedTypes.contains(normalizeMarketType(m.getMarketType())))
                .collect(Collectors.toCollection(ArrayList::new));
      }
      Map<String, MarketBookOdds> oddsByMarket =
          fetchMarketBookOdds(
              markets.stream().map(EventMarket::getMarketId).filter(Objects::nonNull).toList(),
              LocalDate.now(ZoneOffset.UTC));
      for (EventMarket market : markets) {
        MarketBookOdds odds = oddsByMarket.get(market.getMarketId());
        market.setMarketStatus(odds == null ? "" : odds.status);
        if (odds == null || market.getSelections() == null) {
          continue;
        }
        for (EventSelection selection : market.getSelections()) {
          if (selection == null || selection.getSelectionId() == null) {
            continue;
          }
          selection.setBackOdds(odds.backOddsBySelection.get(selection.getSelectionId()));
          selection.setLayOdds(odds.layOddsBySelection.get(selection.getSelectionId()));
        }
      }
      markets.sort(Comparator.comparing(EventMarket::getStartTime, Comparator.nullsLast(String::compareTo)));
      return markets;
    } catch (Exception ex) {
      LOGGER.warn("Betfair listMarketsForEvent failed for eventId={}", eventId, ex);
      return List.of();
    }
  }

  private List<Game> listEvents(LocalDate date) {
    if (!isEnabled()) {
      LOGGER.warn("Betfair API disabled. appKeyPresent={}, sessionTokenPresent={}", hasAppKey(), hasSessionToken());
      return List.of();
    }

    HttpEntity<List<Map<String, Object>>> request =
        new HttpEntity<>(List.of(buildListEventsRequest(date)), buildHeaders());

    try {
      String response = restTemplate.postForObject(rpcBaseUrl, request, String.class);
      if (response == null || response.isBlank()) {
        LOGGER.warn("Betfair listEvents returned an empty response for {}", date == null ? "all dates" : date);
        return List.of();
      }
      List<Game> games = parseEvents(response);
      LOGGER.info(
          "Betfair listEvents returned {} events for {}",
          games.size(),
          date == null ? "all dates" : date);
      return games;
    } catch (Exception ex) {
      LOGGER.warn("Betfair listEvents failed for {}", date == null ? "all dates" : date, ex);
      return List.of();
    }
  }

  private HttpHeaders buildHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Application", appKey);
    headers.set("X-Authentication", resolveSessionToken());
    return headers;
  }

  private Map<String, Object> buildListEventsRequest(LocalDate date) {
    Map<String, Object> filter = new HashMap<>();
    filter.put("eventTypeIds", List.of(FOOTBALL_EVENT_TYPE_ID));
    if (date != null) {
      String from = date.atStartOfDay(ZoneOffset.UTC).toInstant().toString();
      String to = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toString();
      Map<String, String> marketStartTime = new HashMap<>();
      marketStartTime.put("from", from);
      marketStartTime.put("to", to);
      filter.put("marketStartTime", marketStartTime);
    }

    Map<String, Object> params = new HashMap<>();
    params.put("filter", filter);

    Map<String, Object> rpcRequest = new HashMap<>();
    rpcRequest.put("jsonrpc", "2.0");
    rpcRequest.put("method", "SportsAPING/v1.0/listEvents");
    rpcRequest.put("params", params);
    rpcRequest.put("id", 1);
    return rpcRequest;
  }

  private Map<String, AuxiliaryOdds> fetchAuxiliaryOdds(
      List<MatchOddsMarket> matchOddsMarkets, LocalDate resolvedDate) {
    List<String> eventIds =
        matchOddsMarkets.stream()
            .map(m -> m.eventId)
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .toList();
    if (eventIds.isEmpty()) {
      return Map.of();
    }
    try {
      List<AuxiliaryMarket> auxMarkets = new ArrayList<>();
      List<String> auxiliaryMarketTypes =
          List.of(
              "OVER_UNDER_05",
              "OVER_UNDER_15",
              "OVER_UNDER_25",
              "HALF_TIME",
              "CORRECT_SCORE",
              "CORRECT_SCORE2",
              "ALT_CORRECT_SCORE");
      for (String marketType : auxiliaryMarketTypes) {
        for (int i = 0; i < eventIds.size(); i += AUX_EVENT_BATCH_SIZE) {
          List<String> batch = eventIds.subList(i, Math.min(i + AUX_EVENT_BATCH_SIZE, eventIds.size()));
          HttpEntity<List<Map<String, Object>>> catalogueRequest =
              new HttpEntity<>(
                  List.of(buildListAuxiliaryMarketCatalogueRequest(batch, marketType)),
                  buildHeaders());
          String response = restTemplate.postForObject(rpcBaseUrl, catalogueRequest, String.class);
          if (response == null || response.isBlank()) {
            continue;
          }
          auxMarkets.addAll(parseAuxiliaryMarkets(response));
        }
      }
      if (auxMarkets.isEmpty()) {
        return Map.of();
      }

      Map<String, MarketBookOdds> oddsByMarket =
          fetchMarketBookOdds(
              auxMarkets.stream().map(m -> m.marketId).distinct().toList(),
              resolvedDate);

      Map<String, AuxiliaryOdds> byEvent = new HashMap<>();
      for (AuxiliaryMarket market : auxMarkets) {
        MarketBookOdds odds = oddsByMarket.get(market.marketId);
        if (odds == null) {
          continue;
        }
        AuxiliaryOdds target = byEvent.computeIfAbsent(market.eventId, key -> new AuxiliaryOdds());
        if ("OVER_UNDER_05".equals(market.marketType)) {
          target.over05Odds = odds.backOddsBySelection.get(market.primarySelectionId);
          target.under05Odds = odds.backOddsBySelection.get(market.secondarySelectionId);
          target.ou05MarketStatus = odds.status;
        } else if ("OVER_UNDER_15".equals(market.marketType)) {
          target.over15Odds = odds.backOddsBySelection.get(market.primarySelectionId);
          target.under15Odds = odds.backOddsBySelection.get(market.secondarySelectionId);
        } else if ("OVER_UNDER_25".equals(market.marketType)) {
          target.over25Odds = odds.backOddsBySelection.get(market.primarySelectionId);
          target.under25Odds = odds.backOddsBySelection.get(market.secondarySelectionId);
        } else if ("HALF_TIME".equals(market.marketType)) {
          target.htHomeOdds = odds.backOddsBySelection.get(market.primarySelectionId);
          target.htDrawOdds = odds.backOddsBySelection.get(market.drawSelectionId);
          target.htAwayOdds = odds.backOddsBySelection.get(market.secondarySelectionId);
          target.htMarketStatus = odds.status;
        } else if (isCorrectScoreMarketType(market.marketType)) {
          Double odd = odds.backOddsBySelection.get(market.primarySelectionId);
          if (target.fullTime00Odds == null && odd != null) {
            target.fullTime00Odds = odd;
          }
        }
      }
      return byEvent;
    } catch (Exception ex) {
      LOGGER.warn("Betfair auxiliary markets failed for {}", resolvedDate, ex);
      return Map.of();
    }
  }

  private Map<String, Object> buildListMarketCatalogueRequest(Instant from, Instant to) {
    return buildListMarketCatalogueRequest(from, to, List.of("MATCH_ODDS"));
  }

  private Map<String, Object> buildListMarketCatalogueRequest(
      Instant from, Instant to, List<String> marketTypeCodes) {
    Map<String, Object> filter = new HashMap<>();
    filter.put("eventTypeIds", List.of(FOOTBALL_EVENT_TYPE_ID));
    filter.put("marketTypeCodes", marketTypeCodes);
    filter.put("inPlayOnly", false);
    Map<String, String> marketStartTime = new HashMap<>();
    marketStartTime.put("from", from.toString());
    marketStartTime.put("to", to.toString());
    filter.put("marketStartTime", marketStartTime);

    Map<String, Object> params = new HashMap<>();
    params.put("filter", filter);
    params.put("maxResults", "200");
    params.put(
        "marketProjection",
        List.of("EVENT", "RUNNER_DESCRIPTION", "MARKET_START_TIME", "MARKET_DESCRIPTION"));

    Map<String, Object> rpcRequest = new HashMap<>();
    rpcRequest.put("jsonrpc", "2.0");
    rpcRequest.put("method", "SportsAPING/v1.0/listMarketCatalogue");
    rpcRequest.put("params", params);
    rpcRequest.put("id", 1);
    return rpcRequest;
  }

  private Map<String, Object> buildListMarketCatalogueRequestForMarketIds(List<String> marketIds) {
    return buildListMarketCatalogueRequestForMarketIds(marketIds, false);
  }

  private Map<String, Object> buildListMarketCatalogueRequestForMarketIds(
      List<String> marketIds, boolean includeEvent) {
    Map<String, Object> filter = new HashMap<>();
    // Use only explicit marketIds to avoid over-restricting catalogue lookups.
    filter.put("marketIds", marketIds);

    Map<String, Object> params = new HashMap<>();
    params.put("filter", filter);
    params.put("maxResults", "200");
    params.put(
        "marketProjection",
        includeEvent
            ? List.of("MARKET_START_TIME", "EVENT", "RUNNER_DESCRIPTION", "COMPETITION")
            : List.of("MARKET_START_TIME"));

    Map<String, Object> rpcRequest = new HashMap<>();
    rpcRequest.put("jsonrpc", "2.0");
    rpcRequest.put("method", "SportsAPING/v1.0/listMarketCatalogue");
    rpcRequest.put("params", params);
    rpcRequest.put("id", 1);
    return rpcRequest;
  }

  private Map<String, Object> buildListEventMarketsCatalogueRequest(List<String> eventIds) {
    Map<String, Object> filter = new HashMap<>();
    filter.put("eventTypeIds", List.of(FOOTBALL_EVENT_TYPE_ID));
    filter.put("eventIds", eventIds);

    Map<String, Object> params = new HashMap<>();
    params.put("filter", filter);
    params.put("maxResults", "1000");
    params.put(
        "marketProjection",
        List.of("EVENT", "RUNNER_DESCRIPTION", "MARKET_DESCRIPTION", "MARKET_START_TIME"));

    Map<String, Object> rpcRequest = new HashMap<>();
    rpcRequest.put("jsonrpc", "2.0");
    rpcRequest.put("method", "SportsAPING/v1.0/listMarketCatalogue");
    rpcRequest.put("params", params);
    rpcRequest.put("id", 1);
    return rpcRequest;
  }

  private Map<String, Object> buildListInPlayMarketCatalogueRequest() {
    Map<String, Object> filter = new HashMap<>();
    filter.put("eventTypeIds", List.of(FOOTBALL_EVENT_TYPE_ID));
    filter.put("marketTypeCodes", List.of("MATCH_ODDS"));
    filter.put("marketCountries", List.of("BR"));
    filter.put("inPlayOnly", true);

    Map<String, Object> params = new HashMap<>();
    params.put("filter", filter);
    params.put("maxResults", "200");
    params.put(
        "marketProjection",
        List.of("EVENT", "COMPETITION", "RUNNER_DESCRIPTION", "MARKET_START_TIME"));

    Map<String, Object> rpcRequest = new HashMap<>();
    rpcRequest.put("jsonrpc", "2.0");
    rpcRequest.put("method", "SportsAPING/v1.0/listMarketCatalogue");
    rpcRequest.put("params", params);
    rpcRequest.put("id", 1);
    return rpcRequest;
  }

  private Map<String, Object> buildListInPlayAllMarketCatalogueRequest() {
    Map<String, Object> filter = new HashMap<>();
    filter.put("eventTypeIds", List.of(FOOTBALL_EVENT_TYPE_ID));
    filter.put("marketTypeCodes", List.of("MATCH_ODDS"));
    filter.put("inPlayOnly", true);

    Map<String, Object> params = new HashMap<>();
    params.put("filter", filter);
    params.put("maxResults", "500");
    params.put(
        "marketProjection",
        List.of("EVENT", "COMPETITION", "RUNNER_DESCRIPTION", "MARKET_START_TIME"));

    Map<String, Object> rpcRequest = new HashMap<>();
    rpcRequest.put("jsonrpc", "2.0");
    rpcRequest.put("method", "SportsAPING/v1.0/listMarketCatalogue");
    rpcRequest.put("params", params);
    rpcRequest.put("id", 1);
    return rpcRequest;
  }

  private Map<String, Object> buildListAuxiliaryMarketCatalogueRequest(
      List<String> eventIds, String marketTypeCode) {
    Map<String, Object> filter = new HashMap<>();
    filter.put("eventTypeIds", List.of(FOOTBALL_EVENT_TYPE_ID));
    filter.put("eventIds", eventIds);
    filter.put("marketTypeCodes", List.of(marketTypeCode));
    filter.put("inPlayOnly", false);

    Map<String, Object> params = new HashMap<>();
    params.put("filter", filter);
    params.put("maxResults", "300");
    params.put(
        "marketProjection",
        List.of("EVENT", "RUNNER_DESCRIPTION", "MARKET_START_TIME", "MARKET_DESCRIPTION"));

    Map<String, Object> rpcRequest = new HashMap<>();
    rpcRequest.put("jsonrpc", "2.0");
    rpcRequest.put("method", "SportsAPING/v1.0/listMarketCatalogue");
    rpcRequest.put("params", params);
    rpcRequest.put("id", 1);
    return rpcRequest;
  }

  private Map<String, Object> buildListMarketBookRequest(
      List<String> marketIds, boolean includeMarketDefinition) {
    Map<String, Object> priceProjection = new HashMap<>();
    priceProjection.put("priceData", List.of("EX_BEST_OFFERS"));
    Map<String, Object> bestOffers = new HashMap<>();
    bestOffers.put("bestPricesDepth", 1);
    priceProjection.put("exBestOffersOverrides", bestOffers);

    Map<String, Object> params = new HashMap<>();
    params.put("marketIds", marketIds);
    params.put("priceProjection", priceProjection);
    if (includeMarketDefinition) {
      params.put("marketProjection", List.of("MARKET_DEF"));
    }

    Map<String, Object> rpcRequest = new HashMap<>();
    rpcRequest.put("jsonrpc", "2.0");
    rpcRequest.put("method", "SportsAPING/v1.0/listMarketBook");
    rpcRequest.put("params", params);
    rpcRequest.put("id", 1);
    return rpcRequest;
  }

  private String resolveSessionToken() {
    String cached = sessionStore.getSessionToken();
    if (!cached.isBlank()) {
      return cached;
    }
    return sessionToken == null ? "" : sessionToken;
  }

  private List<MatchOddsMarket> listInPlayFootballMarkets() {
    try {
      HttpEntity<List<Map<String, Object>>> request =
          new HttpEntity<>(List.of(buildListInPlayAllMarketCatalogueRequest()), buildHeaders());
      String response = restTemplate.postForObject(rpcBaseUrl, request, String.class);
      if (response == null || response.isBlank()) {
        return List.of();
      }
      return parseMatchOddsMarkets(response);
    } catch (Exception ex) {
      LOGGER.warn("Betfair listInPlayFootballMarkets failed", ex);
      return List.of();
    }
  }

  private String normalizeMarketType(String value) {
    return value == null ? "" : value.trim().toUpperCase();
  }

  private List<AuxiliaryMarket> parseAuxiliaryMarkets(String response) throws Exception {
    JsonNode root = objectMapper.readTree(response);
    if (!root.isArray() || root.isEmpty()) {
      return List.of();
    }
    JsonNode first = root.get(0);
    JsonNode error = first.path("error");
    if (!error.isMissingNode() && !error.isNull()) {
      LOGGER.warn("Betfair auxiliary listMarketCatalogue error: {}", error.toString());
      return List.of();
    }
    JsonNode result = first.path("result");
    if (!result.isArray()) {
      return List.of();
    }

    List<AuxiliaryMarket> markets = new ArrayList<>();
    for (JsonNode node : result) {
      String marketId = node.path("marketId").asText();
      String marketType = resolveAuxiliaryMarketType(node);
      String eventId = node.path("event").path("id").asText();
      if (marketType.isBlank() || eventId.isBlank()) {
        continue;
      }

      if ("OVER_UNDER_05".equals(marketType)
          || "OVER_UNDER_15".equals(marketType)
          || "OVER_UNDER_25".equals(marketType)) {
        String lineValue = auxiliaryGoalLineText(marketType);
        Optional<RunnerSelection> over =
            findRunner(node.path("runners"), "over", lineValue);
        Optional<RunnerSelection> under =
            findRunner(node.path("runners"), "under", lineValue);
        if (over.isEmpty() || under.isEmpty()) {
          continue;
        }
        markets.add(new AuxiliaryMarket(marketId, eventId, marketType, over.get().selectionId, under.get().selectionId, -1L));
      } else if ("HALF_TIME".equals(marketType)) {
        List<RunnerSelection> nonDraw = new ArrayList<>();
        RunnerSelection draw = null;
        for (JsonNode runner : node.path("runners")) {
          String runnerName = runner.path("runnerName").asText();
          long selectionId = runner.path("selectionId").asLong();
          if (isDrawRunner(runnerName)) {
            draw = new RunnerSelection(runnerName, selectionId);
          } else {
            nonDraw.add(new RunnerSelection(runnerName, selectionId));
          }
        }
        if (nonDraw.size() != 2 || draw == null) {
          continue;
        }
        markets.add(
            new AuxiliaryMarket(
                marketId,
                eventId,
                marketType,
                nonDraw.get(0).selectionId,
                nonDraw.get(1).selectionId,
                draw.selectionId));
      } else if (isCorrectScoreMarketType(marketType)) {
        Optional<RunnerSelection> nilNil = findCorrectScoreRunner(node.path("runners"), 0, 0);
        if (nilNil.isEmpty()) {
          continue;
        }
        markets.add(
            new AuxiliaryMarket(marketId, eventId, marketType, nilNil.get().selectionId, -1L, -1L));
      }
    }
    return markets;
  }

  private String resolveAuxiliaryMarketType(JsonNode node) {
    String marketType = node.path("description").path("marketType").asText("");
    if (!marketType.isBlank()) {
      return marketType;
    }
    String marketName = node.path("marketName").asText("").toLowerCase();
    if (marketName.contains("over/under 1.5")) {
      return "OVER_UNDER_15";
    }
    if (marketName.contains("over/under 0.5")) {
      return "OVER_UNDER_05";
    }
    if (marketName.contains("over/under 2.5")) {
      return "OVER_UNDER_25";
    }
    if (marketName.contains("half time") || marketName.contains("ht result")) {
      return "HALF_TIME";
    }
    if (marketName.contains("correct score")) {
      return "CORRECT_SCORE";
    }
    return "";
  }

  private Optional<RunnerSelection> findRunner(JsonNode runners, String containsA, String containsB) {
    for (JsonNode runner : runners) {
      String name = runner.path("runnerName").asText("");
      String normalized = name.toLowerCase();
      if (normalized.contains(containsA) && normalized.contains(containsB)) {
        return Optional.of(new RunnerSelection(name, runner.path("selectionId").asLong()));
      }
    }
    return Optional.empty();
  }

  private Optional<RunnerSelection> findCorrectScoreRunner(
      JsonNode runners, int homeScore, int awayScore) {
    if (!runners.isArray()) {
      return Optional.empty();
    }
    for (JsonNode runner : runners) {
      String runnerName = runner.path("runnerName").asText("").trim();
      if (runnerName.isBlank()) {
        continue;
      }
      InferredScore parsed = parseScoreFromRunnerName(runnerName);
      if (parsed == null || parsed.getHomeScore() == null || parsed.getAwayScore() == null) {
        continue;
      }
      if (parsed.getHomeScore() == homeScore && parsed.getAwayScore() == awayScore) {
        long selectionId = runner.path("selectionId").asLong(-1L);
        if (selectionId > 0L) {
          return Optional.of(new RunnerSelection(runnerName, selectionId));
        }
      }
    }
    return Optional.empty();
  }

  private boolean isCorrectScoreMarketType(String marketType) {
    if (marketType == null || marketType.isBlank()) {
      return false;
    }
    return "CORRECT_SCORE".equals(marketType)
        || "CORRECT_SCORE2".equals(marketType)
        || "ALT_CORRECT_SCORE".equals(marketType);
  }

  private String auxiliaryGoalLineText(String marketType) {
    if ("OVER_UNDER_05".equals(marketType)) {
      return "0.5";
    }
    if ("OVER_UNDER_15".equals(marketType)) {
      return "1.5";
    }
    if ("OVER_UNDER_25".equals(marketType)) {
      return "2.5";
    }
    return "";
  }

  private Map<String, Instant> parseMarketStartTimes(String response) throws Exception {
    JsonNode root = objectMapper.readTree(response);
    if (!root.isArray() || root.isEmpty()) {
      return Map.of();
    }
    JsonNode first = root.get(0);
    JsonNode error = first.path("error");
    if (!error.isMissingNode() && !error.isNull()) {
      LOGGER.warn("Betfair listMarketCatalogue error: {}", error.toString());
      return Map.of();
    }
    JsonNode result = first.path("result");
    if (!result.isArray()) {
      return Map.of();
    }

    Map<String, Instant> startTimes = new HashMap<>();
    for (JsonNode node : result) {
      String marketId = node.path("marketId").asText();
      String startTimeText = node.path("marketStartTime").asText();
      if (marketId.isBlank() || startTimeText.isBlank()) {
        continue;
      }
      try {
        startTimes.put(marketId, Instant.parse(startTimeText));
      } catch (Exception ignored) {
        // ignore malformed times
      }
    }
    return startTimes;
  }

  private List<Game> parseEvents(String response) throws Exception {
    JsonNode root = objectMapper.readTree(response);
    if (!root.isArray() || root.isEmpty()) {
      return List.of();
    }
    JsonNode first = root.get(0);
    JsonNode error = first.path("error");
    if (!error.isMissingNode() && !error.isNull()) {
      LOGGER.warn("Betfair listEvents error: {}", error.toString());
      return List.of();
    }
    JsonNode result = first.path("result");
    if (!result.isArray()) {
      return List.of();
    }
    List<Game> games = new ArrayList<>();
    for (JsonNode node : result) {
      JsonNode event = node.path("event");
      String eventId = event.path("id").asText();
      String eventName = event.path("name").asText();
      String startTime = event.path("openDate").asText();
      String league =
          node.path("competition").path("name").asText(event.path("countryCode").asText("Unknown"));
      String[] teams = splitTeams(eventName);

      games.add(
          new Game(
              eventId,
              "Football",
              league,
              teams[0],
              teams[1],
              startTime,
              eventId));
    }
    return games;
  }

  private List<MatchOddsMarket> parseMatchOddsMarkets(String response) throws Exception {
    JsonNode root = objectMapper.readTree(response);
    if (!root.isArray() || root.isEmpty()) {
      return List.of();
    }
    JsonNode first = root.get(0);
    JsonNode error = first.path("error");
    if (!error.isMissingNode() && !error.isNull()) {
      LOGGER.warn("Betfair listMarketCatalogue error: {}", error.toString());
      return List.of();
    }
    JsonNode result = first.path("result");
    if (!result.isArray()) {
      return List.of();
    }
    List<MatchOddsMarket> markets = new ArrayList<>();
    for (JsonNode node : result) {
      String marketId = node.path("marketId").asText();
      String startTimeText = node.path("marketStartTime").asText();
      Instant startTime = null;
      if (!startTimeText.isBlank()) {
        try {
          startTime = Instant.parse(startTimeText);
        } catch (Exception ignored) {
          startTime = null;
        }
      }

      JsonNode event = node.path("event");
      String eventId = event.path("id").asText();
      String eventName = event.path("name").asText();
      String league =
          node.path("competition").path("name").asText(event.path("countryCode").asText("Unknown"));
      String countryCode = event.path("countryCode").asText("Unknown");

      List<RunnerSelection> runners = new ArrayList<>();
      Long drawSelectionId = null;
      for (JsonNode runner : node.path("runners")) {
        String runnerName = runner.path("runnerName").asText();
        long selectionId = runner.path("selectionId").asLong();
        if (isDrawRunner(runnerName)) {
          drawSelectionId = selectionId;
        } else {
          runners.add(new RunnerSelection(runnerName, selectionId));
        }
      }
      // Keep only true 1X2 markets (Home/Draw/Away).
      if (drawSelectionId == null) {
        continue;
      }
      // For MATCH_ODDS, runners are already ordered Home, Away (plus Draw).
      if (runners.size() != 2) {
        continue;
      }
      RunnerSelection homeRunner = runners.get(0);
      RunnerSelection awayRunner = runners.get(1);

      markets.add(
          new MatchOddsMarket(
              marketId,
              eventId,
              league,
              countryCode,
              homeRunner.name,
              awayRunner.name,
              startTimeText,
              startTime,
              homeRunner.selectionId,
              Objects.requireNonNullElse(drawSelectionId, -1L),
              awayRunner.selectionId));
    }
    return markets;
  }

  private List<EventMarket> parseEventMarkets(String response, String eventId) throws Exception {
    JsonNode root = objectMapper.readTree(response);
    if (!root.isArray() || root.isEmpty()) {
      return List.of();
    }
    JsonNode first = root.get(0);
    JsonNode error = first.path("error");
    if (!error.isMissingNode() && !error.isNull()) {
      LOGGER.warn("Betfair listMarketCatalogue error: {}", error.toString());
      return List.of();
    }
    JsonNode result = first.path("result");
    if (!result.isArray()) {
      return List.of();
    }

    List<EventMarket> markets = new ArrayList<>();
    for (JsonNode node : result) {
      String nodeEventId = node.path("event").path("id").asText("");
      if (!eventId.equals(nodeEventId)) {
        continue;
      }
      String marketId = node.path("marketId").asText("");
      if (marketId.isBlank()) {
        continue;
      }
      String marketName = node.path("marketName").asText("");
      String marketType =
          node.path("description")
              .path("marketType")
              .asText(
                  node.path("description")
                      .path("marketTypeCode")
                      .asText(node.path("marketTypeCode").asText("")));
      String startTime = node.path("marketStartTime").asText("");
      EventMarket eventMarket = new EventMarket(marketId, marketName, marketType, startTime);
      List<EventSelection> selections = new ArrayList<>();
      for (JsonNode runner : node.path("runners")) {
        long selectionId = runner.path("selectionId").asLong(-1L);
        String runnerName = runner.path("runnerName").asText("");
        if (selectionId <= 0L) {
          continue;
        }
        selections.add(new EventSelection(selectionId, runnerName, null, null));
      }
      eventMarket.setSelections(selections);
      markets.add(eventMarket);
    }
    return markets;
  }

  private Map<String, MarketBookOdds> parseMarketBookOdds(String response) throws Exception {
    JsonNode root = objectMapper.readTree(response);
    if (!root.isArray() || root.isEmpty()) {
      return Map.of();
    }
    JsonNode first = root.get(0);
    JsonNode error = first.path("error");
    if (!error.isMissingNode() && !error.isNull()) {
      LOGGER.warn("Betfair listMarketBook error: {}", error.toString());
      return Map.of();
    }
    JsonNode result = first.path("result");
    if (!result.isArray()) {
      return Map.of();
    }
    Map<String, MarketBookOdds> oddsByMarket = new HashMap<>();
    for (JsonNode market : result) {
      String marketId = market.path("marketId").asText();
      boolean inPlay = market.path("inplay").asBoolean(false);
      String status = market.path("status").asText("");
      Map<Long, Double> runnerBackOdds = new HashMap<>();
      Map<Long, Double> runnerLayOdds = new HashMap<>();
      for (JsonNode runner : market.path("runners")) {
        long selectionId = runner.path("selectionId").asLong();
        JsonNode availableToBack = runner.path("ex").path("availableToBack");
        if (availableToBack.isArray() && availableToBack.size() > 0) {
          double price = availableToBack.get(0).path("price").asDouble();
          runnerBackOdds.put(selectionId, price);
        }
        JsonNode availableToLay = runner.path("ex").path("availableToLay");
        if (availableToLay.isArray() && availableToLay.size() > 0) {
          double price = availableToLay.get(0).path("price").asDouble();
          runnerLayOdds.put(selectionId, price);
        }
      }
      oddsByMarket.put(marketId, new MarketBookOdds(runnerBackOdds, runnerLayOdds, inPlay, status));
    }
    return oddsByMarket;
  }

  private Map<String, MarketOutcome> parseMarketBookOutcomes(String response) throws Exception {
    JsonNode root = objectMapper.readTree(response);
    if (!root.isArray() || root.isEmpty()) {
      return Map.of();
    }
    JsonNode first = root.get(0);
    JsonNode error = first.path("error");
    if (!error.isMissingNode() && !error.isNull()) {
      LOGGER.warn("Betfair listMarketBook error: {}", error.toString());
      return Map.of();
    }
    JsonNode result = first.path("result");
    if (!result.isArray()) {
      return Map.of();
    }

    Map<String, MarketOutcome> outcomes = new HashMap<>();
    for (JsonNode market : result) {
      String marketId = market.path("marketId").asText();
      boolean inPlay = market.path("inplay").asBoolean(false);
      JsonNode marketDefinition = market.path("marketDefinition");
      String status =
          marketDefinition.isMissingNode() || marketDefinition.isNull()
              ? market.path("status").asText("")
              : marketDefinition.path("status").asText(market.path("status").asText(""));
      Long winnerSelectionId = null;
      JsonNode runners =
          marketDefinition.isMissingNode() || marketDefinition.isNull()
              ? market.path("runners")
              : marketDefinition.path("runners");
      if (runners.isArray()) {
        for (JsonNode runner : runners) {
          String runnerStatus = runner.path("status").asText("");
          if ("WINNER".equalsIgnoreCase(runnerStatus)) {
            winnerSelectionId = runner.path("selectionId").asLong();
            break;
          }
        }
      }
      Integer homeScore = extractScoreValue(marketDefinition.path("score"), "home", "homeScore");
      Integer awayScore = extractScoreValue(marketDefinition.path("score"), "away", "awayScore");
      if (homeScore == null) {
        homeScore = extractScoreValue(market, "homeScore", "scoreHome");
      }
      if (awayScore == null) {
        awayScore = extractScoreValue(market, "awayScore", "scoreAway");
      }
      outcomes.put(
          marketId,
          new MarketOutcome(
              marketId, status == null ? "" : status, inPlay, winnerSelectionId, homeScore, awayScore));
    }
    return outcomes;
  }

  private Map<String, EventRef> fetchEventRefsForMarkets(List<String> marketIds) throws Exception {
    Map<String, EventRef> refs = new HashMap<>();
    for (int i = 0; i < marketIds.size(); i += MARKET_BOOK_BATCH_SIZE) {
      List<String> batch = marketIds.subList(i, Math.min(i + MARKET_BOOK_BATCH_SIZE, marketIds.size()));
      HttpEntity<List<Map<String, Object>>> catalogueRequest =
          new HttpEntity<>(List.of(buildListMarketCatalogueRequestForMarketIds(batch, true)), buildHeaders());
      String response = restTemplate.postForObject(rpcBaseUrl, catalogueRequest, String.class);
      if (response == null || response.isBlank()) {
        continue;
      }
      refs.putAll(parseEventRefsByMarketId(response));
    }
    return refs;
  }

  private List<GoalLineMarketRef> fetchGoalLineMarketsForEvents(List<String> eventIds) throws Exception {
    List<GoalLineMarketRef> refs = new ArrayList<>();
    for (int i = 0; i < eventIds.size(); i += AUX_EVENT_BATCH_SIZE) {
      List<String> batch = eventIds.subList(i, Math.min(i + AUX_EVENT_BATCH_SIZE, eventIds.size()));
      HttpEntity<List<Map<String, Object>>> catalogueRequest =
          new HttpEntity<>(List.of(buildListEventMarketsCatalogueRequest(batch)), buildHeaders());
      String response = restTemplate.postForObject(rpcBaseUrl, catalogueRequest, String.class);
      if (response == null || response.isBlank()) {
        continue;
      }
      refs.addAll(parseGoalLineMarkets(response));
    }
    return refs;
  }

  private List<CorrectScoreMarketRef> fetchCorrectScoreMarketsForEvents(List<String> eventIds)
      throws Exception {
    List<CorrectScoreMarketRef> refs = new ArrayList<>();
    List<String> scoreMarketTypes = List.of("CORRECT_SCORE", "CORRECT_SCORE2", "ALT_CORRECT_SCORE");
    for (int i = 0; i < eventIds.size(); i += AUX_EVENT_BATCH_SIZE) {
      List<String> batch = eventIds.subList(i, Math.min(i + AUX_EVENT_BATCH_SIZE, eventIds.size()));
      for (String marketType : scoreMarketTypes) {
        HttpEntity<List<Map<String, Object>>> catalogueRequest =
            new HttpEntity<>(
                List.of(buildListAuxiliaryMarketCatalogueRequest(batch, marketType)),
                buildHeaders());
        String response = restTemplate.postForObject(rpcBaseUrl, catalogueRequest, String.class);
        if (response == null || response.isBlank()) {
          continue;
        }
        refs.addAll(parseCorrectScoreMarkets(response));
      }
    }
    return refs;
  }

  private Map<String, MarketBookOutcome> fetchMarketBookOutcomeByMarketId(List<String> marketIds)
      throws Exception {
    Map<String, MarketBookOutcome> outcomes = new HashMap<>();
    for (int i = 0; i < marketIds.size(); i += MARKET_BOOK_BATCH_SIZE) {
      List<String> batch = marketIds.subList(i, Math.min(i + MARKET_BOOK_BATCH_SIZE, marketIds.size()));
      HttpEntity<List<Map<String, Object>>> bookRequest =
          new HttpEntity<>(List.of(buildListMarketBookRequest(batch, true)), buildHeaders());
      String response = restTemplate.postForObject(rpcBaseUrl, bookRequest, String.class);
      if (response == null || response.isBlank()) {
        continue;
      }
      outcomes.putAll(parseMarketBookGoalOutcomes(response));
    }
    return outcomes;
  }

  private Map<String, InferredScore> fetchCorrectScoreByMarketId(List<String> marketIds)
      throws Exception {
    Map<String, InferredScore> scores = new HashMap<>();
    for (int i = 0; i < marketIds.size(); i += MARKET_BOOK_BATCH_SIZE) {
      List<String> batch = marketIds.subList(i, Math.min(i + MARKET_BOOK_BATCH_SIZE, marketIds.size()));
      HttpEntity<List<Map<String, Object>>> bookRequest =
          new HttpEntity<>(List.of(buildListMarketBookRequest(batch, true)), buildHeaders());
      String response = restTemplate.postForObject(rpcBaseUrl, bookRequest, String.class);
      if (response == null || response.isBlank()) {
        continue;
      }
      scores.putAll(parseCorrectScoreFromMarketBook(response));
    }
    return scores;
  }

  private Map<String, EventRef> parseEventRefsByMarketId(String response) throws Exception {
    JsonNode root = objectMapper.readTree(response);
    if (!root.isArray() || root.isEmpty()) {
      return Map.of();
    }
    JsonNode first = root.get(0);
    JsonNode error = first.path("error");
    if (!error.isMissingNode() && !error.isNull()) {
      LOGGER.warn("Betfair listMarketCatalogue error: {}", error.toString());
      return Map.of();
    }
    JsonNode result = first.path("result");
    if (!result.isArray()) {
      return Map.of();
    }
    Map<String, EventRef> refs = new HashMap<>();
    for (JsonNode node : result) {
      String marketId = node.path("marketId").asText("");
      String eventId = node.path("event").path("id").asText("");
      String eventName = node.path("event").path("name").asText("");
      if (marketId.isBlank() || eventId.isBlank()) {
        continue;
      }
      refs.put(marketId, new EventRef(eventId, eventName));
    }
    return refs;
  }

  private List<GoalLineMarketRef> parseGoalLineMarkets(String response) throws Exception {
    JsonNode root = objectMapper.readTree(response);
    if (!root.isArray() || root.isEmpty()) {
      return List.of();
    }
    JsonNode first = root.get(0);
    JsonNode error = first.path("error");
    if (!error.isMissingNode() && !error.isNull()) {
      LOGGER.warn("Betfair listMarketCatalogue error: {}", error.toString());
      return List.of();
    }
    JsonNode result = first.path("result");
    if (!result.isArray()) {
      return List.of();
    }
    List<GoalLineMarketRef> refs = new ArrayList<>();
    for (JsonNode node : result) {
      String marketId = node.path("marketId").asText("");
      String eventId = node.path("event").path("id").asText("");
      String eventName = node.path("event").path("name").asText("");
      String marketName = node.path("marketName").asText("");
      String marketType = node.path("description").path("marketType").asText("");
      if (marketId.isBlank() || eventId.isBlank()) {
        continue;
      }
      if (!isGoalLineMarket(marketName, marketType)) {
        continue;
      }
      Double line = extractGoalLine(marketName, marketType);
      if (line == null) {
        continue;
      }
      Boolean homeSide = resolveHomeSide(eventName, marketName, marketType);
      if (homeSide == null) {
        continue;
      }
      refs.add(new GoalLineMarketRef(marketId, eventId, homeSide, line));
    }
    return refs;
  }

  private List<CorrectScoreMarketRef> parseCorrectScoreMarkets(String response) throws Exception {
    JsonNode root = objectMapper.readTree(response);
    if (!root.isArray() || root.isEmpty()) {
      return List.of();
    }
    JsonNode first = root.get(0);
    JsonNode error = first.path("error");
    if (!error.isMissingNode() && !error.isNull()) {
      LOGGER.warn("Betfair listMarketCatalogue error: {}", error.toString());
      return List.of();
    }
    JsonNode result = first.path("result");
    if (!result.isArray()) {
      return List.of();
    }
    List<CorrectScoreMarketRef> refs = new ArrayList<>();
    for (JsonNode node : result) {
      String marketId = node.path("marketId").asText("");
      String eventId = node.path("event").path("id").asText("");
      if (marketId.isBlank() || eventId.isBlank()) {
        continue;
      }
      refs.add(new CorrectScoreMarketRef(marketId, eventId));
    }
    return refs;
  }

  private Map<String, MarketBookOutcome> parseMarketBookGoalOutcomes(String response) throws Exception {
    JsonNode root = objectMapper.readTree(response);
    if (!root.isArray() || root.isEmpty()) {
      return Map.of();
    }
    JsonNode first = root.get(0);
    JsonNode error = first.path("error");
    if (!error.isMissingNode() && !error.isNull()) {
      LOGGER.warn("Betfair listMarketBook error: {}", error.toString());
      return Map.of();
    }
    JsonNode result = first.path("result");
    if (!result.isArray()) {
      return Map.of();
    }

    Map<String, MarketBookOutcome> outcomes = new HashMap<>();
    for (JsonNode market : result) {
      String marketId = market.path("marketId").asText("");
      if (marketId.isBlank()) {
        continue;
      }
      JsonNode marketDefinition = market.path("marketDefinition");
      String status =
          marketDefinition.isMissingNode() || marketDefinition.isNull()
              ? market.path("status").asText("")
              : marketDefinition.path("status").asText(market.path("status").asText(""));
      boolean closed = "CLOSED".equalsIgnoreCase(status);
      JsonNode runnerNamesSource = market.path("runners");
      Map<Long, String> runnerNamesBySelectionId = new HashMap<>();
      if (runnerNamesSource.isArray()) {
        for (JsonNode runner : runnerNamesSource) {
          long selectionId = runner.path("selectionId").asLong(-1L);
          if (selectionId <= 0L) {
            continue;
          }
          String name = runner.path("runnerName").asText(runner.path("name").asText(""));
          if (name != null && !name.isBlank()) {
            runnerNamesBySelectionId.put(selectionId, name);
          }
        }
      }

      Long winnerSelectionId = null;
      JsonNode runners =
          marketDefinition.isMissingNode() || marketDefinition.isNull()
              ? market.path("runners")
              : marketDefinition.path("runners");
      if (runners.isArray()) {
        for (JsonNode runner : runners) {
          if (!"WINNER".equalsIgnoreCase(runner.path("status").asText(""))) {
            continue;
          }
          long selectionId = runner.path("selectionId").asLong(-1L);
          if (selectionId > 0L) {
            winnerSelectionId = selectionId;
          }
          break;
        }
      }
      Boolean winnerOver = null;
      if (winnerSelectionId != null) {
        String winnerName = runnerNamesBySelectionId.getOrDefault(winnerSelectionId, "").toLowerCase();
        if (winnerName.contains("over")) {
          winnerOver = true;
        } else if (winnerName.contains("under")) {
          winnerOver = false;
        }
      }
      outcomes.put(marketId, new MarketBookOutcome(closed, winnerOver));
    }
    return outcomes;
  }

  private Map<String, InferredScore> parseCorrectScoreFromMarketBook(String response) throws Exception {
    JsonNode root = objectMapper.readTree(response);
    if (!root.isArray() || root.isEmpty()) {
      return Map.of();
    }
    JsonNode first = root.get(0);
    JsonNode error = first.path("error");
    if (!error.isMissingNode() && !error.isNull()) {
      LOGGER.warn("Betfair listMarketBook error: {}", error.toString());
      return Map.of();
    }
    JsonNode result = first.path("result");
    if (!result.isArray()) {
      return Map.of();
    }
    Map<String, InferredScore> scores = new HashMap<>();
    int closedMarkets = 0;
    int marketsWithScoreNode = 0;
    int marketsWithWinnerRunner = 0;
    int marketsWithLikelyRunner = 0;
    for (JsonNode market : result) {
      String marketId = market.path("marketId").asText("");
      if (marketId.isBlank()) {
        continue;
      }
      JsonNode marketDefinition = market.path("marketDefinition");
      JsonNode scoreNode = marketDefinition.path("score");
      if (scoreNode.isMissingNode() || scoreNode.isNull()) {
        scoreNode = market.path("score");
      }
      if (!scoreNode.isMissingNode() && !scoreNode.isNull()) {
        marketsWithScoreNode++;
      }
      Integer home = extractScoreValue(scoreNode, "home", "homeScore", "homeGoals");
      Integer away = extractScoreValue(scoreNode, "away", "awayScore", "awayGoals");
      if (home != null && away != null) {
        scores.put(marketId, new InferredScore(home, away, home + "-" + away));
        continue;
      }

      String status =
          marketDefinition.isMissingNode() || marketDefinition.isNull()
              ? market.path("status").asText("")
              : marketDefinition.path("status").asText(market.path("status").asText(""));
      if (!"CLOSED".equalsIgnoreCase(status)) {
        continue;
      }
      closedMarkets++;

      Map<Long, String> runnerNamesBySelectionId = new HashMap<>();
      JsonNode runnersWithNames = market.path("runners");
      if (runnersWithNames.isArray()) {
        for (JsonNode runner : runnersWithNames) {
          long selectionId = runner.path("selectionId").asLong(-1L);
          if (selectionId <= 0L) {
            continue;
          }
          String runnerName = runner.path("runnerName").asText(runner.path("name").asText(""));
          if (runnerName != null && !runnerName.isBlank()) {
            runnerNamesBySelectionId.put(selectionId, runnerName);
          }
        }
      }

      Long winnerSelectionId = null;
      JsonNode runners =
          marketDefinition.isMissingNode() || marketDefinition.isNull()
              ? market.path("runners")
              : marketDefinition.path("runners");
      if (runners.isArray()) {
        for (JsonNode runner : runners) {
          if (!"WINNER".equalsIgnoreCase(runner.path("status").asText(""))) {
            continue;
          }
          long selectionId = runner.path("selectionId").asLong(-1L);
          if (selectionId > 0L) {
            winnerSelectionId = selectionId;
          }
          break;
        }
      }
      if (winnerSelectionId == null) {
        continue;
      }
      marketsWithWinnerRunner++;
      String winnerName = runnerNamesBySelectionId.getOrDefault(winnerSelectionId, "");
      InferredScore parsed = parseScoreFromRunnerName(winnerName);
      if (parsed != null) {
        scores.put(marketId, parsed);
        continue;
      }

      InferredScore likely = inferLikelyScoreFromRunnerPrices(market);
      if (likely != null) {
        marketsWithLikelyRunner++;
        scores.put(marketId, likely);
      }
    }
    LOGGER.info(
        "[SCORE_DEBUG] marketBook scan total={} scoreNode={} closed={} winnerRunner={} likelyRunner={} parsed={}",
        result.size(),
        marketsWithScoreNode,
        closedMarkets,
        marketsWithWinnerRunner,
        marketsWithLikelyRunner,
        scores.size());
    return scores;
  }

  private InferredScore inferLikelyScoreFromRunnerPrices(JsonNode market) {
    JsonNode runners = market.path("runners");
    if (!runners.isArray()) {
      return null;
    }

    double bestPrice = Double.MAX_VALUE;
    String bestRunnerName = null;
    for (JsonNode runner : runners) {
      String runnerName = runner.path("runnerName").asText(runner.path("name").asText(""));
      if (runnerName == null || runnerName.isBlank()) {
        continue;
      }
      Double candidate = extractBestRunnerPrice(runner);
      if (candidate == null) {
        continue;
      }
      if (candidate < bestPrice) {
        bestPrice = candidate;
        bestRunnerName = runnerName;
      }
    }

    if (bestRunnerName == null) {
      return null;
    }
    InferredScore parsed = parseScoreFromRunnerName(bestRunnerName);
    if (parsed == null) {
      return null;
    }
    String label = parsed.getLabel();
    if (label == null || label.isBlank()) {
      return null;
    }
    return new InferredScore(parsed.getHomeScore(), parsed.getAwayScore(), label + " (likely)");
  }

  private Double extractBestRunnerPrice(JsonNode runner) {
    Double back = firstPrice(runner.path("ex").path("availableToBack"));
    Double lay = firstPrice(runner.path("ex").path("availableToLay"));
    if (back == null && lay == null) {
      return null;
    }
    if (back == null) {
      return lay;
    }
    if (lay == null) {
      return back;
    }
    return Math.min(back, lay);
  }

  private Double firstPrice(JsonNode prices) {
    if (!prices.isArray() || prices.isEmpty()) {
      return null;
    }
    JsonNode first = prices.get(0);
    if (first == null || first.isNull()) {
      return null;
    }
    double price = first.path("price").asDouble(Double.NaN);
    return Double.isNaN(price) ? null : price;
  }

  private InferredScore parseScoreFromRunnerName(String runnerName) {
    if (runnerName == null || runnerName.isBlank()) {
      return null;
    }
    Matcher matcher = Pattern.compile("(\\d+)\\s*[-:]\\s*(\\d+)").matcher(runnerName);
    if (!matcher.find()) {
      return new InferredScore(null, null, runnerName);
    }
    try {
      Integer home = Integer.parseInt(matcher.group(1));
      Integer away = Integer.parseInt(matcher.group(2));
      return new InferredScore(home, away, home + "-" + away);
    } catch (Exception ignored) {
      return new InferredScore(null, null, runnerName);
    }
  }

  private boolean isGoalLineMarket(String marketName, String marketType) {
    String type = marketType == null ? "" : marketType.toUpperCase();
    if (type.startsWith("TEAM_A_OVER_UNDER_") || type.startsWith("TEAM_B_OVER_UNDER_")) {
      return true;
    }
    String name = marketName == null ? "" : marketName.toLowerCase();
    return name.contains("over/under") && name.contains("goal");
  }

  private Double extractGoalLine(String marketName, String marketType) {
    String type = marketType == null ? "" : marketType.toUpperCase();
    if (type.endsWith("_05")) return 0.5d;
    if (type.endsWith("_15")) return 1.5d;
    if (type.endsWith("_25")) return 2.5d;
    if (type.endsWith("_35")) return 3.5d;
    if (type.endsWith("_45")) return 4.5d;
    String name = marketName == null ? "" : marketName;
    Matcher matcher = GOAL_LINE_PATTERN.matcher(name);
    if (!matcher.find()) {
      return null;
    }
    try {
      return Double.parseDouble(matcher.group(1));
    } catch (Exception ignored) {
      return null;
    }
  }

  private Boolean resolveHomeSide(String eventName, String marketName, String marketType) {
    String type = marketType == null ? "" : marketType.toUpperCase();
    if (type.startsWith("TEAM_A_OVER_UNDER_")) {
      return true;
    }
    if (type.startsWith("TEAM_B_OVER_UNDER_")) {
      return false;
    }

    String name = marketName == null ? "" : marketName.toLowerCase();
    if (name.contains("team a")) return true;
    if (name.contains("team b")) return false;

    String[] teams = splitTeams(eventName);
    String home = teams[0].trim().toLowerCase();
    String away = teams[1].trim().toLowerCase();
    if (!home.isBlank() && name.contains(home)) {
      return true;
    }
    if (!away.isBlank() && name.contains(away)) {
      return false;
    }
    return null;
  }

  private Integer extractScoreValue(JsonNode node, String... keys) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    for (String key : keys) {
      JsonNode value = node.path(key);
      if (value.isMissingNode() || value.isNull()) {
        continue;
      }
      if (value.isNumber()) {
        return value.asInt();
      }
      if (value.isTextual()) {
        try {
          return Integer.parseInt(value.asText().trim());
        } catch (Exception ignored) {
          // ignore non-numeric score strings
        }
      }
      if (value.isObject()) {
        Integer nested =
            extractScoreValue(value, "goals", "score", "value", "current", "fullTime");
        if (nested != null) {
          return nested;
        }
      }
    }
    return null;
  }

  private String[] splitTeams(String eventName) {
    if (eventName == null || eventName.isBlank()) {
      return new String[] {"TBD", "TBD"};
    }
    String delimiter = eventName.contains(" v ") ? " v " : " vs ";
    if (eventName.contains(delimiter)) {
      String[] parts = eventName.split(delimiter, 2);
      return new String[] {parts[0].trim(), parts[1].trim()};
    }
    return new String[] {eventName.trim(), "TBD"};
  }

  private String slugify(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String normalized =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");
    return normalized.isBlank() ? "" : normalized;
  }

  private boolean isDrawRunner(String runnerName) {
    if (runnerName == null || runnerName.isBlank()) {
      return false;
    }
    String normalized = runnerName.trim().toLowerCase();
    return normalized.equals("draw")
        || normalized.equals("the draw")
        || normalized.contains("draw");
  }

  private boolean isBrazilSerieAMarket(MatchOddsMarket market) {
    String country = market.countryCode == null ? "" : market.countryCode.trim();
    String leagueName = market.league == null ? "" : market.league.toLowerCase();
    return "BR".equalsIgnoreCase(country) && leagueName.contains("serie a");
  }

  private static final class RunnerSelection {
    private final String name;
    private final long selectionId;

    private RunnerSelection(String name, long selectionId) {
      this.name = name;
      this.selectionId = selectionId;
    }
  }

  private static final class AuxiliaryMarket {
    private final String marketId;
    private final String eventId;
    private final String marketType;
    private final long primarySelectionId;
    private final long secondarySelectionId;
    private final long drawSelectionId;

    private AuxiliaryMarket(
        String marketId,
        String eventId,
        String marketType,
        long primarySelectionId,
        long secondarySelectionId,
        long drawSelectionId) {
      this.marketId = marketId;
      this.eventId = eventId;
      this.marketType = marketType;
      this.primarySelectionId = primarySelectionId;
      this.secondarySelectionId = secondarySelectionId;
      this.drawSelectionId = drawSelectionId;
    }
  }

  private static final class AuxiliaryOdds {
    private Double over05Odds;
    private Double under05Odds;
    private Double over15Odds;
    private Double under15Odds;
    private Double over25Odds;
    private Double under25Odds;
    private Double htHomeOdds;
    private Double htDrawOdds;
    private Double htAwayOdds;
    private Double fullTime00Odds;
    private String ou05MarketStatus;
    private String htMarketStatus;
  }

  private static final class MatchOddsMarket {
    private final String marketId;
    private final String eventId;
    private final String league;
    private final String countryCode;
    private final String homeTeam;
    private final String awayTeam;
    private final String startTimeText;
    private final Instant startTime;
    private final long homeSelectionId;
    private final long drawSelectionId;
    private final long awaySelectionId;

    private MatchOddsMarket(
        String marketId,
        String eventId,
        String league,
        String countryCode,
        String homeTeam,
        String awayTeam,
        String startTimeText,
        Instant startTime,
        long homeSelectionId,
        long drawSelectionId,
        long awaySelectionId) {
      this.marketId = marketId;
      this.eventId = eventId;
      this.league = league;
      this.countryCode = countryCode;
      this.homeTeam = homeTeam;
      this.awayTeam = awayTeam;
      this.startTimeText = startTimeText;
      this.startTime = startTime;
      this.homeSelectionId = homeSelectionId;
      this.drawSelectionId = drawSelectionId;
      this.awaySelectionId = awaySelectionId;
    }
  }

  private static final class MarketBookOdds {
    private final Map<Long, Double> backOddsBySelection;
    private final Map<Long, Double> layOddsBySelection;
    private final boolean inPlay;
    private final String status;

    private MarketBookOdds(
        Map<Long, Double> backOddsBySelection,
        Map<Long, Double> layOddsBySelection,
        boolean inPlay,
        String status) {
      this.backOddsBySelection = backOddsBySelection;
      this.layOddsBySelection = layOddsBySelection;
      this.inPlay = inPlay;
      this.status = status;
    }
  }

  public static final class ExchangeLiveSnapshot {
    private final String score;
    private final String minute;

    public ExchangeLiveSnapshot(String score, String minute) {
      this.score = score == null ? "" : score;
      this.minute = minute == null ? "" : minute;
    }

    public String score() {
      return score;
    }

    public String minute() {
      return minute;
    }
  }

  public static final class MarketStatus {
    private final Instant startTime;
    private final boolean inPlay;
    private final String status;

    private MarketStatus(Instant startTime, boolean inPlay, String status) {
      this.startTime = startTime;
      this.inPlay = inPlay;
      this.status = status;
    }

    public Instant getStartTime() {
      return startTime;
    }

    public boolean isInPlay() {
      return inPlay;
    }

    public String getStatus() {
      return status;
    }
  }

  public static final class MarketOutcome {
    private final String marketId;
    private final String status;
    private final boolean inPlay;
    private final Long winnerSelectionId;
    private final Integer homeScore;
    private final Integer awayScore;

    private MarketOutcome(
        String marketId,
        String status,
        boolean inPlay,
        Long winnerSelectionId,
        Integer homeScore,
        Integer awayScore) {
      this.marketId = marketId;
      this.status = status;
      this.inPlay = inPlay;
      this.winnerSelectionId = winnerSelectionId;
      this.homeScore = homeScore;
      this.awayScore = awayScore;
    }

    public String getMarketId() {
      return marketId;
    }

    public String getStatus() {
      return status;
    }

    public boolean isInPlay() {
      return inPlay;
    }

    public Long getWinnerSelectionId() {
      return winnerSelectionId;
    }

    public Integer getHomeScore() {
      return homeScore;
    }

    public Integer getAwayScore() {
      return awayScore;
    }
  }

  public static final class InferredScore {
    private final Integer homeScore;
    private final Integer awayScore;
    private final String label;

    private InferredScore(Integer homeScore, Integer awayScore, String label) {
      this.homeScore = homeScore;
      this.awayScore = awayScore;
      this.label = label;
    }

    public Integer getHomeScore() {
      return homeScore;
    }

    public Integer getAwayScore() {
      return awayScore;
    }

    public String getLabel() {
      return label;
    }
  }

  public static final class EventIdentity {
    private final String eventId;
    private final String eventName;

    public EventIdentity(String eventId, String eventName) {
      this.eventId = eventId;
      this.eventName = eventName;
    }

    public String getEventId() {
      return eventId;
    }

    public String getEventName() {
      return eventName;
    }
  }

  private static final class EventRef {
    private final String eventId;
    private final String eventName;

    private EventRef(String eventId, String eventName) {
      this.eventId = eventId;
      this.eventName = eventName;
    }

    private String eventId() {
      return eventId;
    }

    private String eventName() {
      return eventName;
    }
  }

  private static final class GoalLineMarketRef {
    private final String marketId;
    private final String eventId;
    private final boolean homeSide;
    private final double line;

    private GoalLineMarketRef(String marketId, String eventId, boolean homeSide, double line) {
      this.marketId = marketId;
      this.eventId = eventId;
      this.homeSide = homeSide;
      this.line = line;
    }

    private String marketId() {
      return marketId;
    }

    private String eventId() {
      return eventId;
    }

    private boolean homeSide() {
      return homeSide;
    }

    private double line() {
      return line;
    }
  }

  private static final class CorrectScoreMarketRef {
    private final String marketId;
    private final String eventId;

    private CorrectScoreMarketRef(String marketId, String eventId) {
      this.marketId = marketId;
      this.eventId = eventId;
    }

    private String marketId() {
      return marketId;
    }

    private String eventId() {
      return eventId;
    }
  }

  private static final class MarketBookOutcome {
    private final boolean closed;
    private final Boolean winnerOver;

    private MarketBookOutcome(boolean closed, Boolean winnerOver) {
      this.closed = closed;
      this.winnerOver = winnerOver;
    }

    private boolean closed() {
      return closed;
    }

    private Boolean winnerOver() {
      return winnerOver;
    }
  }

  private static final class GoalBounds {
    private int homeMin = 0;
    private int homeMax = 12;
    private int awayMin = 0;
    private int awayMax = 12;

    private void apply(boolean homeSide, double line, boolean winnerOver) {
      int floor = (int) Math.floor(line);
      int overMin = floor + 1;
      if (homeSide) {
        if (winnerOver) {
          homeMin = Math.max(homeMin, overMin);
        } else {
          homeMax = Math.min(homeMax, floor);
        }
      } else {
        if (winnerOver) {
          awayMin = Math.max(awayMin, overMin);
        } else {
          awayMax = Math.min(awayMax, floor);
        }
      }
    }

    private InferredScore toInferredScore() {
      Integer homeExact = homeMin == homeMax ? homeMin : null;
      Integer awayExact = awayMin == awayMax ? awayMin : null;
      String homeLabel = homeExact != null ? String.valueOf(homeExact) : toRangeLabel(homeMin, homeMax);
      String awayLabel = awayExact != null ? String.valueOf(awayExact) : toRangeLabel(awayMin, awayMax);
      return new InferredScore(homeExact, awayExact, homeLabel + "-" + awayLabel);
    }

    private String toRangeLabel(int min, int max) {
      if (min <= 0 && max >= 12) {
        return "?";
      }
      if (min <= 0) {
        return "<=" + max;
      }
      if (max >= 12) {
        return ">=" + min;
      }
      return min + "-" + max;
    }
  }
}
