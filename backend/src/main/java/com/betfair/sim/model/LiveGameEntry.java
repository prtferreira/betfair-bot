package com.betfair.sim.model;

public class LiveGameEntry {
  private String statpalMatchId;
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
  private Double homeLayOdds;
  private Double drawLayOdds;
  private Double awayLayOdds;
  private Double over15Odds;
  private Double under15Odds;
  private String score;
  private String minute;
  private String minuteSource;
  private boolean goalScored;
  private boolean mappedToBetfair;
  private String highlight;
  private boolean zeroZeroAfterHt;

  public LiveGameEntry() {}

  public String getStatpalMatchId() {
    return statpalMatchId;
  }

  public void setStatpalMatchId(String statpalMatchId) {
    this.statpalMatchId = statpalMatchId;
  }

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

  public Double getHomeLayOdds() {
    return homeLayOdds;
  }

  public void setHomeLayOdds(Double homeLayOdds) {
    this.homeLayOdds = homeLayOdds;
  }

  public Double getDrawLayOdds() {
    return drawLayOdds;
  }

  public void setDrawLayOdds(Double drawLayOdds) {
    this.drawLayOdds = drawLayOdds;
  }

  public Double getAwayLayOdds() {
    return awayLayOdds;
  }

  public void setAwayLayOdds(Double awayLayOdds) {
    this.awayLayOdds = awayLayOdds;
  }

  public Double getOver15Odds() {
    return over15Odds;
  }

  public void setOver15Odds(Double over15Odds) {
    this.over15Odds = over15Odds;
  }

  public Double getUnder15Odds() {
    return under15Odds;
  }

  public void setUnder15Odds(Double under15Odds) {
    this.under15Odds = under15Odds;
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

  public boolean isGoalScored() {
    return goalScored;
  }

  public void setGoalScored(boolean goalScored) {
    this.goalScored = goalScored;
  }

  public boolean isMappedToBetfair() {
    return mappedToBetfair;
  }

  public void setMappedToBetfair(boolean mappedToBetfair) {
    this.mappedToBetfair = mappedToBetfair;
  }

  public String getHighlight() {
    return highlight;
  }

  public void setHighlight(String highlight) {
    this.highlight = highlight;
  }

  public boolean isZeroZeroAfterHt() {
    return zeroZeroAfterHt;
  }

  public void setZeroZeroAfterHt(boolean zeroZeroAfterHt) {
    this.zeroZeroAfterHt = zeroZeroAfterHt;
  }
}
