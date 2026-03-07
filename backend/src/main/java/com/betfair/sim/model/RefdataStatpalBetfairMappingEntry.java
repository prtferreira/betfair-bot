package com.betfair.sim.model;

public class RefdataStatpalBetfairMappingEntry {
  private String date;
  private String apiMatchId;
  private String apiHomeTeam;
  private String apiAwayTeam;
  private String betfairEventId;
  private String betfairHomeTeam;
  private String betfairAwayTeam;
  private String apiStartTime;
  private String betfairStartTime;
  private String source;
  private Double confidenceScore;
  private String updatedAt;

  public RefdataStatpalBetfairMappingEntry() {}

  public RefdataStatpalBetfairMappingEntry(
      String date,
      String apiMatchId,
      String apiHomeTeam,
      String apiAwayTeam,
      String betfairEventId,
      String betfairHomeTeam,
      String betfairAwayTeam,
      String apiStartTime,
      String betfairStartTime,
      String source,
      Double confidenceScore,
      String updatedAt) {
    this.date = date;
    this.apiMatchId = apiMatchId;
    this.apiHomeTeam = apiHomeTeam;
    this.apiAwayTeam = apiAwayTeam;
    this.betfairEventId = betfairEventId;
    this.betfairHomeTeam = betfairHomeTeam;
    this.betfairAwayTeam = betfairAwayTeam;
    this.apiStartTime = apiStartTime;
    this.betfairStartTime = betfairStartTime;
    this.source = source;
    this.confidenceScore = confidenceScore;
    this.updatedAt = updatedAt;
  }

  public String getDate() {
    return date;
  }

  public void setDate(String date) {
    this.date = date;
  }

  public String getApiMatchId() {
    return apiMatchId;
  }

  public void setApiMatchId(String apiMatchId) {
    this.apiMatchId = apiMatchId;
  }

  public String getApiHomeTeam() {
    return apiHomeTeam;
  }

  public void setApiHomeTeam(String apiHomeTeam) {
    this.apiHomeTeam = apiHomeTeam;
  }

  public String getApiAwayTeam() {
    return apiAwayTeam;
  }

  public void setApiAwayTeam(String apiAwayTeam) {
    this.apiAwayTeam = apiAwayTeam;
  }

  public String getBetfairEventId() {
    return betfairEventId;
  }

  public void setBetfairEventId(String betfairEventId) {
    this.betfairEventId = betfairEventId;
  }

  public String getBetfairHomeTeam() {
    return betfairHomeTeam;
  }

  public void setBetfairHomeTeam(String betfairHomeTeam) {
    this.betfairHomeTeam = betfairHomeTeam;
  }

  public String getBetfairAwayTeam() {
    return betfairAwayTeam;
  }

  public void setBetfairAwayTeam(String betfairAwayTeam) {
    this.betfairAwayTeam = betfairAwayTeam;
  }

  public String getApiStartTime() {
    return apiStartTime;
  }

  public void setApiStartTime(String apiStartTime) {
    this.apiStartTime = apiStartTime;
  }

  public String getBetfairStartTime() {
    return betfairStartTime;
  }

  public void setBetfairStartTime(String betfairStartTime) {
    this.betfairStartTime = betfairStartTime;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public Double getConfidenceScore() {
    return confidenceScore;
  }

  public void setConfidenceScore(Double confidenceScore) {
    this.confidenceScore = confidenceScore;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(String updatedAt) {
    this.updatedAt = updatedAt;
  }
}
