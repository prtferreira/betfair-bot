package com.betfair.sim.model;

import java.util.List;

public class SimulationBetRequest {
  private String strategyId;
  private String strategyName;
  private List<SimulationBetEntry> bets;

  public SimulationBetRequest() {}

  public SimulationBetRequest(
      String strategyId, String strategyName, List<SimulationBetEntry> bets) {
    this.strategyId = strategyId;
    this.strategyName = strategyName;
    this.bets = bets;
  }

  public String getStrategyId() {
    return strategyId;
  }

  public void setStrategyId(String strategyId) {
    this.strategyId = strategyId;
  }

  public String getStrategyName() {
    return strategyName;
  }

  public void setStrategyName(String strategyName) {
    this.strategyName = strategyName;
  }

  public List<SimulationBetEntry> getBets() {
    return bets;
  }

  public void setBets(List<SimulationBetEntry> bets) {
    this.bets = bets;
  }
}
