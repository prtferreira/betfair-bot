package com.betfair.sim.model;

public class EarlyGoalHtBetRecord {
  private String strategyId;
  private String eventId;
  private String marketId;
  private String homeTeam;
  private String awayTeam;
  private String league;
  private String placedAtMinute;
  private double entryBackOdds;
  private double entryLayOdds;
  private double stake;
  private String state;
  private double profit;
  private String exitAtMinute;
  private double exitBackOdds;
  private String closeReason;
  private String latestScore;
  private String latestMinute;
  private String createdAt;
  private String updatedAt;
  private String settledAt;

  public String getStrategyId() {
    return strategyId;
  }

  public void setStrategyId(String strategyId) {
    this.strategyId = strategyId;
  }

  public String getEventId() {
    return eventId;
  }

  public void setEventId(String eventId) {
    this.eventId = eventId;
  }

  public String getMarketId() {
    return marketId;
  }

  public void setMarketId(String marketId) {
    this.marketId = marketId;
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

  public String getLeague() {
    return league;
  }

  public void setLeague(String league) {
    this.league = league;
  }

  public String getPlacedAtMinute() {
    return placedAtMinute;
  }

  public void setPlacedAtMinute(String placedAtMinute) {
    this.placedAtMinute = placedAtMinute;
  }

  public double getEntryBackOdds() {
    return entryBackOdds;
  }

  public void setEntryBackOdds(double entryBackOdds) {
    this.entryBackOdds = entryBackOdds;
  }

  public double getEntryLayOdds() {
    return entryLayOdds;
  }

  public void setEntryLayOdds(double entryLayOdds) {
    this.entryLayOdds = entryLayOdds;
  }

  public double getStake() {
    return stake;
  }

  public void setStake(double stake) {
    this.stake = stake;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public double getProfit() {
    return profit;
  }

  public void setProfit(double profit) {
    this.profit = profit;
  }

  public String getExitAtMinute() {
    return exitAtMinute;
  }

  public void setExitAtMinute(String exitAtMinute) {
    this.exitAtMinute = exitAtMinute;
  }

  public double getExitBackOdds() {
    return exitBackOdds;
  }

  public void setExitBackOdds(double exitBackOdds) {
    this.exitBackOdds = exitBackOdds;
  }

  public String getCloseReason() {
    return closeReason;
  }

  public void setCloseReason(String closeReason) {
    this.closeReason = closeReason;
  }

  public String getLatestScore() {
    return latestScore;
  }

  public void setLatestScore(String latestScore) {
    this.latestScore = latestScore;
  }

  public String getLatestMinute() {
    return latestMinute;
  }

  public void setLatestMinute(String latestMinute) {
    this.latestMinute = latestMinute;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(String updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getSettledAt() {
    return settledAt;
  }

  public void setSettledAt(String settledAt) {
    this.settledAt = settledAt;
  }
}
