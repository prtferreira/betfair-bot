package com.betfair.sim.model;

import java.util.ArrayList;
import java.util.List;

public class AnalyticsGoalsEstimate {
  private String gameKey;
  private String displayName;
  private int guessedGoals;
  private List<String> closedLines = new ArrayList<>();

  public AnalyticsGoalsEstimate() {}

  public AnalyticsGoalsEstimate(
      String gameKey, String displayName, int guessedGoals, List<String> closedLines) {
    this.gameKey = gameKey;
    this.displayName = displayName;
    this.guessedGoals = guessedGoals;
    this.closedLines = closedLines == null ? new ArrayList<>() : new ArrayList<>(closedLines);
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

  public int getGuessedGoals() {
    return guessedGoals;
  }

  public void setGuessedGoals(int guessedGoals) {
    this.guessedGoals = guessedGoals;
  }

  public List<String> getClosedLines() {
    return closedLines;
  }

  public void setClosedLines(List<String> closedLines) {
    this.closedLines = closedLines == null ? new ArrayList<>() : new ArrayList<>(closedLines);
  }
}

