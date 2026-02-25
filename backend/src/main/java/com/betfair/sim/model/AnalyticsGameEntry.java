package com.betfair.sim.model;

import java.util.ArrayList;
import java.util.List;

public class AnalyticsGameEntry {
  private String gameKey;
  private String displayName;
  private List<String> markets = new ArrayList<>();
  private int marketCount;
  private Integer guessedGoals;

  public AnalyticsGameEntry() {}

  public AnalyticsGameEntry(String gameKey, String displayName, List<String> markets) {
    this.gameKey = gameKey;
    this.displayName = displayName;
    this.markets = markets == null ? new ArrayList<>() : new ArrayList<>(markets);
    this.marketCount = this.markets.size();
  }

  public String getGameKey() {
    return gameKey;
  }

  public void setGameKey(String gameKey) {
    this.gameKey = gameKey;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public List<String> getMarkets() {
    return markets;
  }

  public void setMarkets(List<String> markets) {
    this.markets = markets == null ? new ArrayList<>() : new ArrayList<>(markets);
    this.marketCount = this.markets.size();
  }

  public int getMarketCount() {
    return marketCount;
  }

  public void setMarketCount(int marketCount) {
    this.marketCount = marketCount;
  }

  public Integer getGuessedGoals() {
    return guessedGoals;
  }

  public void setGuessedGoals(Integer guessedGoals) {
    this.guessedGoals = guessedGoals;
  }
}
