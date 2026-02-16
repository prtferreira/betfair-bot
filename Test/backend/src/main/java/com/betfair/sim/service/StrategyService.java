package com.betfair.sim.service;

import com.betfair.sim.model.Game;
import com.betfair.sim.model.SimulationResult;
import com.betfair.sim.model.Strategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Service;

@Service
public class StrategyService {
  private final List<Strategy> strategies =
      List.of(
          new Strategy("scalp", "Scalp", "Quick in-play ticks, low hold time."),
          new Strategy("swing", "Swing", "Ride momentum after price shifts."),
          new Strategy("value", "Value", "Pre-match price inefficiency scan."),
          new Strategy("complex", "Complex", "Composite signals across markets."));

  public List<Strategy> getStrategies() {
    return strategies;
  }

  public List<SimulationResult> simulate(String strategyId, List<Game> games) {
    Strategy strategy =
        strategies.stream()
            .filter(item -> item.getId().equalsIgnoreCase(strategyId))
            .findFirst()
            .orElse(strategies.get(0));

    List<SimulationResult> results = new ArrayList<>();
    for (Game game : games) {
      Random random = new Random((strategy.getId() + game.getId()).hashCode());
      double base = 5 + random.nextDouble() * 30;
      double volatility = 2 + random.nextDouble() * 8;

      double profit = strategy.getId().equals("scalp") ? base * 0.7 : base;
      profit = strategy.getId().equals("value") ? base * 1.1 : profit;
      double risk =
          strategy.getId().equals("swing") ? volatility * 1.4 : volatility * 0.9;

      results.add(
          new SimulationResult(
              game.getId(),
              strategy.getId(),
              round(profit),
              round(risk),
              buildNote(strategy.getId(), game)));
    }
    return results;
  }

  private String buildNote(String strategyId, Game game) {
    if ("scalp".equals(strategyId)) {
      return "Target early ticks in " + game.getHomeTeam() + " vs " + game.getAwayTeam();
    }
    if ("swing".equals(strategyId)) {
      return "Watch momentum swings after the first 15 minutes.";
    }
    return "Value scan highlights " + game.getLeague() + " liquidity.";
  }

  private double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
