package com.betfair.sim.model;

import java.util.List;
import java.util.Map;

public class SimulationBetStatusResponse {
  private double globalBalance;
  private Map<String, Double> balanceByStrategy;
  private int valueGlobalWins;
  private int valueGlobalLosses;
  private Map<String, WinLossCount> valueDailyWinLosses;
  private List<SimulationBetRecord> bets;
  private String lastUpdated;

  public SimulationBetStatusResponse() {}

  public SimulationBetStatusResponse(
      double globalBalance,
      Map<String, Double> balanceByStrategy,
      int valueGlobalWins,
      int valueGlobalLosses,
      Map<String, WinLossCount> valueDailyWinLosses,
      List<SimulationBetRecord> bets,
      String lastUpdated) {
    this.globalBalance = globalBalance;
    this.balanceByStrategy = balanceByStrategy;
    this.valueGlobalWins = valueGlobalWins;
    this.valueGlobalLosses = valueGlobalLosses;
    this.valueDailyWinLosses = valueDailyWinLosses;
    this.bets = bets;
    this.lastUpdated = lastUpdated;
  }

  public double getGlobalBalance() {
    return globalBalance;
  }

  public void setGlobalBalance(double globalBalance) {
    this.globalBalance = globalBalance;
  }

  public Map<String, Double> getBalanceByStrategy() {
    return balanceByStrategy;
  }

  public void setBalanceByStrategy(Map<String, Double> balanceByStrategy) {
    this.balanceByStrategy = balanceByStrategy;
  }

  public int getValueGlobalWins() {
    return valueGlobalWins;
  }

  public void setValueGlobalWins(int valueGlobalWins) {
    this.valueGlobalWins = valueGlobalWins;
  }

  public int getValueGlobalLosses() {
    return valueGlobalLosses;
  }

  public void setValueGlobalLosses(int valueGlobalLosses) {
    this.valueGlobalLosses = valueGlobalLosses;
  }

  public Map<String, WinLossCount> getValueDailyWinLosses() {
    return valueDailyWinLosses;
  }

  public void setValueDailyWinLosses(Map<String, WinLossCount> valueDailyWinLosses) {
    this.valueDailyWinLosses = valueDailyWinLosses;
  }

  public List<SimulationBetRecord> getBets() {
    return bets;
  }

  public void setBets(List<SimulationBetRecord> bets) {
    this.bets = bets;
  }

  public String getLastUpdated() {
    return lastUpdated;
  }

  public void setLastUpdated(String lastUpdated) {
    this.lastUpdated = lastUpdated;
  }

  public static class WinLossCount {
    private int wins;
    private int losses;

    public WinLossCount() {}

    public WinLossCount(int wins, int losses) {
      this.wins = wins;
      this.losses = losses;
    }

    public int getWins() {
      return wins;
    }

    public void setWins(int wins) {
      this.wins = wins;
    }

    public int getLosses() {
      return losses;
    }

    public void setLosses(int losses) {
      this.losses = losses;
    }
  }
}
