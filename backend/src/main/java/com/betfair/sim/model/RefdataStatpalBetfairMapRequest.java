package com.betfair.sim.model;

public class RefdataStatpalBetfairMapRequest {
  private String date;
  private String apiMatchId;
  private String betfairEventId;
  private String source;
  private Double confidenceScore;

  public RefdataStatpalBetfairMapRequest() {}

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

  public String getBetfairEventId() {
    return betfairEventId;
  }

  public void setBetfairEventId(String betfairEventId) {
    this.betfairEventId = betfairEventId;
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
}
