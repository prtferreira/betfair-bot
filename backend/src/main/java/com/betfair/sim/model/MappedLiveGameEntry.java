package com.betfair.sim.model;

public class MappedLiveGameEntry {
  private String statpalMatchId;
  private String betfairEventId;
  private String homeTeam;
  private String awayTeam;
  private String status;
  private String minute;
  private String score;
  private String source;

  public MappedLiveGameEntry() {}

  public String getStatpalMatchId() {
    return statpalMatchId;
  }

  public void setStatpalMatchId(String statpalMatchId) {
    this.statpalMatchId = statpalMatchId;
  }

  public String getBetfairEventId() {
    return betfairEventId;
  }

  public void setBetfairEventId(String betfairEventId) {
    this.betfairEventId = betfairEventId;
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

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getMinute() {
    return minute;
  }

  public void setMinute(String minute) {
    this.minute = minute;
  }

  public String getScore() {
    return score;
  }

  public void setScore(String score) {
    this.score = score;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }
}
