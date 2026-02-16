package com.betfair.sim.model;

public class SimulationRequest {
  private String strategyId;
  private String date;

  public SimulationRequest() {}

  public SimulationRequest(String strategyId, String date) {
    this.strategyId = strategyId;
    this.date = date;
  }

  public String getStrategyId() {
    return strategyId;
  }

  public void setStrategyId(String strategyId) {
    this.strategyId = strategyId;
  }

  public String getDate() {
    return date;
  }

  public void setDate(String date) {
    this.date = date;
  }
}
