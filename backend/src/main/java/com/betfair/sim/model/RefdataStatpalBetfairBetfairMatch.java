package com.betfair.sim.model;

public class RefdataStatpalBetfairBetfairMatch {
  private String betfairEventId;
  private String leagueName;
  private String homeTeam;
  private String awayTeam;
  private String startTime;
  private String displayName;

  public RefdataStatpalBetfairBetfairMatch() {}

  public RefdataStatpalBetfairBetfairMatch(
      String betfairEventId,
      String leagueName,
      String homeTeam,
      String awayTeam,
      String startTime,
      String displayName) {
    this.betfairEventId = betfairEventId;
    this.leagueName = leagueName;
    this.homeTeam = homeTeam;
    this.awayTeam = awayTeam;
    this.startTime = startTime;
    this.displayName = displayName;
  }

  public String getBetfairEventId() {
    return betfairEventId;
  }

  public void setBetfairEventId(String betfairEventId) {
    this.betfairEventId = betfairEventId;
  }

  public String getLeagueName() {
    return leagueName;
  }

  public void setLeagueName(String leagueName) {
    this.leagueName = leagueName;
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

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }
}
