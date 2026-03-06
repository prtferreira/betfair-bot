package com.betfair.sim.model;

public class RecentEventEntry {
  private String mainId;
  private String date;
  private String leagueName;
  private String homeTeam;
  private String awayTeam;
  private Integer htHomeGoals;
  private Integer htAwayGoals;
  private Integer ftHomeGoals;
  private Integer ftAwayGoals;
  private String status;

  public RecentEventEntry() {}

  public RecentEventEntry(
      String mainId,
      String date,
      String leagueName,
      String homeTeam,
      String awayTeam,
      Integer htHomeGoals,
      Integer htAwayGoals,
      Integer ftHomeGoals,
      Integer ftAwayGoals,
      String status) {
    this.mainId = mainId;
    this.date = date;
    this.leagueName = leagueName;
    this.homeTeam = homeTeam;
    this.awayTeam = awayTeam;
    this.htHomeGoals = htHomeGoals;
    this.htAwayGoals = htAwayGoals;
    this.ftHomeGoals = ftHomeGoals;
    this.ftAwayGoals = ftAwayGoals;
    this.status = status;
  }

  public String getMainId() {
    return mainId;
  }

  public void setMainId(String mainId) {
    this.mainId = mainId;
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

  public Integer getHtHomeGoals() {
    return htHomeGoals;
  }

  public void setHtHomeGoals(Integer htHomeGoals) {
    this.htHomeGoals = htHomeGoals;
  }

  public Integer getHtAwayGoals() {
    return htAwayGoals;
  }

  public void setHtAwayGoals(Integer htAwayGoals) {
    this.htAwayGoals = htAwayGoals;
  }

  public Integer getFtHomeGoals() {
    return ftHomeGoals;
  }

  public void setFtHomeGoals(Integer ftHomeGoals) {
    this.ftHomeGoals = ftHomeGoals;
  }

  public Integer getFtAwayGoals() {
    return ftAwayGoals;
  }

  public void setFtAwayGoals(Integer ftAwayGoals) {
    this.ftAwayGoals = ftAwayGoals;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
