package com.betfair.sim.model;

public class SimulationResult {
  private String gameId;
  private String strategyId;
  private double expectedProfit;
  private double riskScore;
  private String note;

  public SimulationResult() {}

  public SimulationResult(
      String gameId,
      String strategyId,
      double expectedProfit,
      double riskScore,
      String note) {
    this.gameId = gameId;
    this.strategyId = strategyId;
    this.expectedProfit = expectedProfit;
    this.riskScore = riskScore;
    this.note = note;
  }

  public String getGameId() {
    return gameId;
  }

  public void setGameId(String gameId) {
    this.gameId = gameId;
  }

  public String getStrategyId() {
    return strategyId;
  }

  public void setStrategyId(String strategyId) {
    this.strategyId = strategyId;
  }

  public double getExpectedProfit() {
    return expectedProfit;
  }

  public void setExpectedProfit(double expectedProfit) {
    this.expectedProfit = expectedProfit;
  }

  public double getRiskScore() {
    return riskScore;
  }

  public void setRiskScore(double riskScore) {
    this.riskScore = riskScore;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }
}
