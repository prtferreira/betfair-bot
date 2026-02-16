package com.betfair.sim.model;

public class SimulationBetEntry {
  private String marketId;
  private long selectionId;
  private String selectionName;
  private String homeTeam;
  private String awayTeam;
  private String side;
  private double odds;
  private double stake;

  public SimulationBetEntry() {}

  public SimulationBetEntry(
      String marketId,
      long selectionId,
      String selectionName,
      String side,
      double odds,
      double stake) {
    this.marketId = marketId;
    this.selectionId = selectionId;
    this.selectionName = selectionName;
    this.side = side;
    this.odds = odds;
    this.stake = stake;
  }

  public String getMarketId() {
    return marketId;
  }

  public void setMarketId(String marketId) {
    this.marketId = marketId;
  }

  public long getSelectionId() {
    return selectionId;
  }

  public void setSelectionId(long selectionId) {
    this.selectionId = selectionId;
  }

  public String getSelectionName() {
    return selectionName;
  }

  public void setSelectionName(String selectionName) {
    this.selectionName = selectionName;
  }

  public String getHomeTeam() {
    return homeTeam;
  }

  public void setHomeTeam(String homeTeam) {
    this.homeTeam = homeTeam;
  }

  public String getAwayTeam() {
    return awayTeam;
  }

  public void setAwayTeam(String awayTeam) {
    this.awayTeam = awayTeam;
  }

  public String getSide() {
    return side;
  }

  public void setSide(String side) {
    this.side = side;
  }

  public double getOdds() {
    return odds;
  }

  public void setOdds(double odds) {
    this.odds = odds;
  }

  public double getStake() {
    return stake;
  }

  public void setStake(double stake) {
    this.stake = stake;
  }
}
