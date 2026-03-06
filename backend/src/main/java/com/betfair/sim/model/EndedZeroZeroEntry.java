package com.betfair.sim.model;

public class EndedZeroZeroEntry {
  private String gameKey;
  private String displayName;
  private String date;
  private Double preKickoffZeroZeroOdds;
  private Double finalZeroZeroOdds;
  private Double finalUnder05Odds;
  private Double finalOver05Odds;

  public EndedZeroZeroEntry() {}

  public EndedZeroZeroEntry(
      String gameKey,
      String displayName,
      String date,
      Double preKickoffZeroZeroOdds,
      Double finalZeroZeroOdds,
      Double finalUnder05Odds,
      Double finalOver05Odds) {
    this.gameKey = gameKey;
    this.displayName = displayName;
    this.date = date;
    this.preKickoffZeroZeroOdds = preKickoffZeroZeroOdds;
    this.finalZeroZeroOdds = finalZeroZeroOdds;
    this.finalUnder05Odds = finalUnder05Odds;
    this.finalOver05Odds = finalOver05Odds;
  }

  public String getGameKey() {
    return gameKey;
  }

  public void setGameKey(String gameKey) {
    this.gameKey = gameKey;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getDate() {
    return date;
  }

  public void setDate(String date) {
    this.date = date;
  }

  public Double getPreKickoffZeroZeroOdds() {
    return preKickoffZeroZeroOdds;
  }

  public void setPreKickoffZeroZeroOdds(Double preKickoffZeroZeroOdds) {
    this.preKickoffZeroZeroOdds = preKickoffZeroZeroOdds;
  }

  public Double getFinalZeroZeroOdds() {
    return finalZeroZeroOdds;
  }

  public void setFinalZeroZeroOdds(Double finalZeroZeroOdds) {
    this.finalZeroZeroOdds = finalZeroZeroOdds;
  }

  public Double getFinalUnder05Odds() {
    return finalUnder05Odds;
  }

  public void setFinalUnder05Odds(Double finalUnder05Odds) {
    this.finalUnder05Odds = finalUnder05Odds;
  }

  public Double getFinalOver05Odds() {
    return finalOver05Odds;
  }

  public void setFinalOver05Odds(Double finalOver05Odds) {
    this.finalOver05Odds = finalOver05Odds;
  }
}
