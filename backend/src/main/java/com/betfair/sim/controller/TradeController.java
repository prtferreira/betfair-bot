package com.betfair.sim.controller;

import com.betfair.sim.model.Game;
import com.betfair.sim.model.EventMarket;
import com.betfair.sim.model.BestStrategyMonitorEntry;
import com.betfair.sim.model.FollowedGamesRequest;
import com.betfair.sim.model.SelectedGamesRequest;
import com.betfair.sim.model.SimulationRequest;
import com.betfair.sim.model.SimulationResult;
import com.betfair.sim.model.SimulationBetRequest;
import com.betfair.sim.model.SimulationBetStatusResponse;
import com.betfair.sim.model.Strategy;
import java.io.UncheckedIOException;
import com.betfair.sim.service.GameService;
import com.betfair.sim.service.InPlayStatusEntry;
import com.betfair.sim.service.BestStrategyService;
import com.betfair.sim.service.SimulationBetService;
import com.betfair.sim.service.StrategyService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class TradeController {
  private final GameService gameService;
  private final BestStrategyService bestStrategyService;
  private final StrategyService strategyService;
  private final SimulationBetService simulationBetService;

  public TradeController(
      GameService gameService,
      BestStrategyService bestStrategyService,
      StrategyService strategyService,
      SimulationBetService simulationBetService) {
    this.gameService = gameService;
    this.bestStrategyService = bestStrategyService;
    this.strategyService = strategyService;
    this.simulationBetService = simulationBetService;
  }

  @GetMapping("/api/games")
  public List<Game> games(@RequestParam(name = "date", required = false) String date) {
    String resolvedDate = date == null ? LocalDate.now().toString() : date;
    return gameService.gamesForDate(resolvedDate);
  }

  @GetMapping("/api/betfair/football")
  public List<Game> betfairFootball(@RequestParam(name = "date", required = false) String date) {
    String resolvedDate = date == null ? LocalDate.now().toString() : date;
    return gameService.gamesForDateBetfairOnly(resolvedDate);
  }

  @GetMapping("/api/betfair/events")
  public List<Game> betfairFootballEvents() {
    return gameService.betfairFootballEvents();
  }

  @GetMapping("/api/betfair/events/raw")
  public String betfairFootballEventsRaw() {
    return gameService.betfairFootballEventsRaw();
  }

  @GetMapping("/api/betfair/event-markets")
  public List<EventMarket> betfairEventMarkets(
      @RequestParam(name = "eventId") String eventId,
      @RequestParam(name = "marketTypes", required = false) String marketTypes) {
    List<String> requestedTypes =
        marketTypes == null || marketTypes.isBlank()
            ? List.of()
            : java.util.Arrays.stream(marketTypes.split(","))
                .map(String::trim)
                .filter(type -> !type.isBlank())
                .toList();
    return gameService.betfairEventMarkets(eventId, requestedTypes);
  }

  @GetMapping("/api/betfair/today-odds")
  public List<Game> betfairTodayOdds(
      @RequestParam(name = "date", required = false) String date) {
    return gameService.betfairMatchOddsForDate(date);
  }

  @GetMapping("/api/betfair/inplay/brasil-serie-a")
  public List<Game> betfairInPlayBrazilSerieA() {
    return gameService.betfairInPlayBrazilSerieA();
  }

  @GetMapping("/api/strategies")
  public List<Strategy> strategies() {
    return strategyService.getStrategies();
  }

  @PostMapping("/api/betfair/followed-games")
  public Map<String, Object> saveFollowedGames(@RequestBody FollowedGamesRequest request) {
    List<String> marketIds =
        request == null || request.getMarketIds() == null
            ? List.of()
            : request.getMarketIds().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .distinct()
                .toList();
    try {
      String output = gameService.saveFollowedMarketIds(request == null ? null : request.getDate(), marketIds).toString();
      return Map.of("status", "OK", "savedCount", marketIds.size(), "file", output);
    } catch (UncheckedIOException ex) {
      return Map.of("status", "FAILED", "message", ex.getMessage());
    }
  }

  @PostMapping("/api/betfair/selected-games")
  public Map<String, Object> saveSelectedGames(@RequestBody SelectedGamesRequest request) {
    List<String> entries =
        request == null || request.getEntries() == null
            ? List.of()
            : request.getEntries().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .distinct()
                .toList();
    try {
      String output =
          gameService.saveSelectedGames(
              request == null ? null : request.getDate(), entries).toString();
      return Map.of("status", "OK", "savedCount", entries.size(), "file", output);
    } catch (UncheckedIOException ex) {
      return Map.of("status", "FAILED", "message", ex.getMessage());
    }
  }

  @PostMapping("/api/betfair/balanced-games")
  public Map<String, Object> saveBalancedGames(@RequestBody SelectedGamesRequest request) {
    List<String> entries =
        request == null || request.getEntries() == null
            ? List.of()
            : request.getEntries().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .distinct()
                .toList();
    try {
      String output =
          gameService.saveBalancedGames(
              request == null ? null : request.getDate(), entries).toString();
      return Map.of("status", "OK", "savedCount", entries.size(), "file", output);
    } catch (UncheckedIOException ex) {
      return Map.of("status", "FAILED", "message", ex.getMessage());
    }
  }

  @PostMapping("/api/betfair/best-strategy/games")
  public Map<String, Object> saveBestStrategyGames(@RequestBody SelectedGamesRequest request) {
    List<String> entries =
        request == null || request.getEntries() == null
            ? List.of()
            : request.getEntries().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .distinct()
                .toList();
    try {
      String output =
          bestStrategyService
              .saveSelectedGames(request == null ? null : request.getDate(), entries)
              .toString();
      return Map.of("status", "OK", "savedCount", entries.size(), "file", output);
    } catch (UncheckedIOException ex) {
      return Map.of("status", "FAILED", "message", ex.getMessage());
    }
  }

  @GetMapping("/api/betfair/best-strategy/monitor")
  public List<BestStrategyMonitorEntry> bestStrategyMonitor(
      @RequestParam(name = "date", required = false) String date) {
    return bestStrategyService.loadMonitorEntries(date);
  }

  @PostMapping("/api/betfair/lay-matches-report")
  public Map<String, Object> saveLayMatchesReport(@RequestBody SelectedGamesRequest request) {
    List<String> entries =
        request == null || request.getEntries() == null
            ? List.of()
            : request.getEntries().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .distinct()
                .toList();
    try {
      String output =
          gameService.saveLayMatchesReport(
              request == null ? null : request.getDate(), entries).toString();
      return Map.of("status", "OK", "savedCount", entries.size(), "file", output);
    } catch (UncheckedIOException ex) {
      return Map.of("status", "FAILED", "message", ex.getMessage());
    }
  }

  @GetMapping("/api/betfair/balanced-games/status")
  public List<InPlayStatusEntry> balancedGamesStatus(
      @RequestParam(name = "date", required = false) String date) {
    return gameService.loadBalancedGameStatuses(date);
  }

  @PostMapping("/api/strategies/simulate")
  public List<SimulationResult> simulate(@RequestBody SimulationRequest request) {
    String resolvedDate =
        request.getDate() == null ? LocalDate.now().toString() : request.getDate();
    List<Game> games = gameService.gamesForDate(resolvedDate);
    return strategyService.simulate(request.getStrategyId(), games);
  }

  @PostMapping("/api/sim/bets")
  public Map<String, Object> submitSimulationBets(@RequestBody SimulationBetRequest request) {
    if (request == null || request.getBets() == null || request.getBets().isEmpty()) {
      return Map.of("status", "FAILED", "message", "No bets submitted");
    }
    try {
      String output =
          simulationBetService
              .appendBets(request.getStrategyId(), request.getStrategyName(), request.getBets())
              .toString();
      return Map.of("status", "OK", "savedCount", request.getBets().size(), "file", output);
    } catch (UncheckedIOException ex) {
      return Map.of("status", "FAILED", "message", ex.getMessage());
    }
  }

  @GetMapping("/api/sim/bets/status")
  public SimulationBetStatusResponse simulationBetStatus() {
    return simulationBetService.getStatus();
  }
}
