package com.betfair.sim.model;

public class BestStrategyMonitorEntry {
  private String marketId;
  private String startTime;
  private String teams;
  private String status;
  private String ftMarketStatus;
  private String htMarketStatus;
  private String ou05MarketStatus;
  private boolean started;
  private Double homeOdds;
  private Double drawOdds;
  private Double awayOdds;
  private Double over05Odds;
  private Double under05Odds;
  private Double htHomeOdds;
  private Double htDrawOdds;
  private Double htAwayOdds;
  private String oddsFile;

  public String getMarketId() {
    return marketId;
  }

  public void setMarketId(String marketId) {
    this.marketId = marketId;
  }

  public String getStartTime() {
    return startTime;
  }

  public void setStartTime(String startTime) {
    this.startTime = startTime;
  }

  public String getTeams() {
    return teams;
  }

  public void setTeams(String teams) {
    this.teams = teams;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getFtMarketStatus() {
    return ftMarketStatus;
  }

  public void setFtMarketStatus(String ftMarketStatus) {
    this.ftMarketStatus = ftMarketStatus;
  }

  public String getHtMarketStatus() {
    return htMarketStatus;
  }

  public void setHtMarketStatus(String htMarketStatus) {
    this.htMarketStatus = htMarketStatus;
  }

  public String getOu05MarketStatus() {
    return ou05MarketStatus;
  }

  public void setOu05MarketStatus(String ou05MarketStatus) {
    this.ou05MarketStatus = ou05MarketStatus;
  }

  public boolean isStarted() {
    return started;
  }

  public void setStarted(boolean started) {
    this.started = started;
  }

  public Double getHomeOdds() {
    return homeOdds;
  }

  public void setHomeOdds(Double homeOdds) {
    this.homeOdds = homeOdds;
  }

  public Double getDrawOdds() {
    return drawOdds;
  }

  public void setDrawOdds(Double drawOdds) {
    this.drawOdds = drawOdds;
  }

  public Double getAwayOdds() {
    return awayOdds;
  }

  public void setAwayOdds(Double awayOdds) {
    this.awayOdds = awayOdds;
  }

  public Double getOver05Odds() {
    return over05Odds;
  }

  public void setOver05Odds(Double over05Odds) {
    this.over05Odds = over05Odds;
  }

  public Double getUnder05Odds() {
    return under05Odds;
  }

  public void setUnder05Odds(Double under05Odds) {
    this.under05Odds = under05Odds;
  }

  public Double getHtHomeOdds() {
    return htHomeOdds;
  }

  public void setHtHomeOdds(Double htHomeOdds) {
    this.htHomeOdds = htHomeOdds;
  }

  public Double getHtDrawOdds() {
    return htDrawOdds;
  }

  public void setHtDrawOdds(Double htDrawOdds) {
    this.htDrawOdds = htDrawOdds;
  }

  public Double getHtAwayOdds() {
    return htAwayOdds;
  }

  public void setHtAwayOdds(Double htAwayOdds) {
    this.htAwayOdds = htAwayOdds;
  }

  public String getOddsFile() {
    return oddsFile;
  }

  public void setOddsFile(String oddsFile) {
    this.oddsFile = oddsFile;
  }
}
