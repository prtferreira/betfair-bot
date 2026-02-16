package com.betfair.sim.model;

public class SimulationBetRecord {
  private String id;
  private String createdAt;
  private String settledAt;
  private String strategyId;
  private String strategyName;
  private String marketId;
  private long selectionId;
  private String selectionName;
  private String homeTeam;
  private String awayTeam;
  private String side;
  private double odds;
  private double stake;
  private String status;
  private String marketStatus;
  private String marketStartTime;
  private String matchClock;
  private String liveStartedAt;
  private long accumulatedInPlaySeconds;
  private Integer homeScore;
  private Integer awayScore;
  private String inferredScore;
  private boolean inPlay;
  private Double profit;

  public SimulationBetRecord() {}

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }

  public String getSettledAt() {
    return settledAt;
  }

  public void setSettledAt(String settledAt) {
    this.settledAt = settledAt;
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

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getMarketStatus() {
    return marketStatus;
  }

  public void setMarketStatus(String marketStatus) {
    this.marketStatus = marketStatus;
  }

  public String getMarketStartTime() {
    return marketStartTime;
  }

  public void setMarketStartTime(String marketStartTime) {
    this.marketStartTime = marketStartTime;
  }

  public String getMatchClock() {
    return matchClock;
  }

  public void setMatchClock(String matchClock) {
    this.matchClock = matchClock;
  }

  public String getLiveStartedAt() {
    return liveStartedAt;
  }

  public void setLiveStartedAt(String liveStartedAt) {
    this.liveStartedAt = liveStartedAt;
  }

  public long getAccumulatedInPlaySeconds() {
    return accumulatedInPlaySeconds;
  }

  public void setAccumulatedInPlaySeconds(long accumulatedInPlaySeconds) {
    this.accumulatedInPlaySeconds = accumulatedInPlaySeconds;
  }

  public Integer getHomeScore() {
    return homeScore;
  }

  public void setHomeScore(Integer homeScore) {
    this.homeScore = homeScore;
  }

  public Integer getAwayScore() {
    return awayScore;
  }

  public void setAwayScore(Integer awayScore) {
    this.awayScore = awayScore;
  }

  public String getInferredScore() {
    return inferredScore;
  }

  public void setInferredScore(String inferredScore) {
    this.inferredScore = inferredScore;
  }

  public boolean isInPlay() {
    return inPlay;
  }

  public void setInPlay(boolean inPlay) {
    this.inPlay = inPlay;
  }

  public Double getProfit() {
    return profit;
  }

  public void setProfit(Double profit) {
    this.profit = profit;
  }
}
