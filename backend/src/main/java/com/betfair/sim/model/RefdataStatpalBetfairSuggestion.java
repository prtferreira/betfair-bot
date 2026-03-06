package com.betfair.sim.model;

public class RefdataStatpalBetfairSuggestion {
  private String apiMatchId;
  private String betfairEventId;
  private Double score;
  private String reason;

  public RefdataStatpalBetfairSuggestion() {}

  public RefdataStatpalBetfairSuggestion(
      String apiMatchId, String betfairEventId, Double score, String reason) {
    this.apiMatchId = apiMatchId;
    this.betfairEventId = betfairEventId;
    this.score = score;
    this.reason = reason;
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

  public Double getScore() {
    return score;
  }

  public void setScore(Double score) {
    this.score = score;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }
}
