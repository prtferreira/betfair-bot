package com.betfair.sim.model;

public class LiveGameEntry {
  private String eventId;
  private String marketId;
  private String league;
  private String homeTeam;
  private String awayTeam;
  private String startTime;
  private String marketStatus;
  private boolean inPlay;
  private Double homeOdds;
  private Double drawOdds;
  private Double awayOdds;
  private String score;
  private String minute;
  private String minuteSource;

  public LiveGameEntry() {}

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

  public String getLeague() {
    return league;
  }

  public void setLeague(String league) {
    this.league = league;
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

  public String getStartTime() {
    return startTime;
  }

  public void setStartTime(String startTime) {
    this.startTime = startTime;
  }

  public String getMarketStatus() {
    return marketStatus;
  }

  public void setMarketStatus(String marketStatus) {
    this.marketStatus = marketStatus;
  }

  public boolean isInPlay() {
    return inPlay;
  }

  public void setInPlay(boolean inPlay) {
    this.inPlay = inPlay;
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

  public String getScore() {
    return score;
  }

  public void setScore(String score) {
    this.score = score;
  }

  public String getMinute() {
    return minute;
  }

  public void setMinute(String minute) {
    this.minute = minute;
  }

  public String getMinuteSource() {
    return minuteSource;
  }

  public void setMinuteSource(String minuteSource) {
    this.minuteSource = minuteSource;
  }
}
