package com.betfair.sim.model;

public class RefdataStatpalBetfairApiMatch {
  private String apiMatchId;
  private String date;
  private String leagueName;
  private String homeTeam;
  private String awayTeam;
  private String displayName;

  public RefdataStatpalBetfairApiMatch() {}

  public RefdataStatpalBetfairApiMatch(
      String apiMatchId,
      String date,
      String leagueName,
      String homeTeam,
      String awayTeam,
      String displayName) {
    this.apiMatchId = apiMatchId;
    this.date = date;
    this.leagueName = leagueName;
    this.homeTeam = homeTeam;
    this.awayTeam = awayTeam;
    this.displayName = displayName;
  }

  public String getApiMatchId() {
    return apiMatchId;
  }

  public void setApiMatchId(String apiMatchId) {
    this.apiMatchId = apiMatchId;
  }

  public String getDate() {
    return date;
  }

  public void setDate(String date) {
    this.date = date;
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

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }
}
