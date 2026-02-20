package com.betfair.sim.model;

public class Game {
  private String id;
  private String sport;
  private String league;
  private String homeTeam;
  private String awayTeam;
  private String startTime;
  private String marketId;
  private String marketStatus;
  private Boolean inPlay;
  private String htMarketStatus;
  private String ou05MarketStatus;
  private Double homeOdds;
  private Double drawOdds;
  private Double awayOdds;
  private Double homeLayOdds;
  private Double drawLayOdds;
  private Double awayLayOdds;
  private Long homeSelectionId;
  private Long drawSelectionId;
  private Long awaySelectionId;
  private Double over15Odds;
  private Double under15Odds;
  private Double over05Odds;
  private Double under05Odds;
  private Double over25Odds;
  private Double under25Odds;
  private Double htHomeOdds;
  private Double htDrawOdds;
  private Double htAwayOdds;

  public Game() {}

  public Game(
      String id,
      String sport,
      String league,
      String homeTeam,
      String awayTeam,
      String startTime,
      String marketId) {
    this(id, sport, league, homeTeam, awayTeam, startTime, marketId, null, null, null);
  }

  public Game(
      String id,
      String sport,
      String league,
      String homeTeam,
      String awayTeam,
      String startTime,
      String marketId,
      Double homeOdds,
      Double drawOdds,
      Double awayOdds) {
    this.id = id;
    this.sport = sport;
    this.league = league;
    this.homeTeam = homeTeam;
    this.awayTeam = awayTeam;
    this.startTime = startTime;
    this.marketId = marketId;
    this.homeOdds = homeOdds;
    this.drawOdds = drawOdds;
    this.awayOdds = awayOdds;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getSport() {
    return sport;
  }

  public void setSport(String sport) {
    this.sport = sport;
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

  public String getMarketId() {
    return marketId;
  }

  public void setMarketId(String marketId) {
    this.marketId = marketId;
  }

  public String getMarketStatus() {
    return marketStatus;
  }

  public void setMarketStatus(String marketStatus) {
    this.marketStatus = marketStatus;
  }

  public Boolean getInPlay() {
    return inPlay;
  }

  public void setInPlay(Boolean inPlay) {
    this.inPlay = inPlay;
  }

  public String getHtMarketStatus() {
    return htMarketStatus;
  }

  public void setHtMarketStatus(String htMarketStatus) {
    this.htMarketStatus = htMarketStatus;
  }

  public String getOu05MarketStatus() {
    return ou05MarketStatus;
  }

  public void setOu05MarketStatus(String ou05MarketStatus) {
    this.ou05MarketStatus = ou05MarketStatus;
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

  public Long getHomeSelectionId() {
    return homeSelectionId;
  }

  public void setHomeSelectionId(Long homeSelectionId) {
    this.homeSelectionId = homeSelectionId;
  }

  public Long getDrawSelectionId() {
    return drawSelectionId;
  }

  public void setDrawSelectionId(Long drawSelectionId) {
    this.drawSelectionId = drawSelectionId;
  }

  public Long getAwaySelectionId() {
    return awaySelectionId;
  }

  public void setAwaySelectionId(Long awaySelectionId) {
    this.awaySelectionId = awaySelectionId;
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

  public Double getOver05Odds() {
    return over05Odds;
  }

  public void setOver05Odds(Double over05Odds) {
    this.over05Odds = over05Odds;
  }

  public Double getUnder05Odds() {
    return under05Odds;
  }

  public void setUnder05Odds(Double under05Odds) {
    this.under05Odds = under05Odds;
  }

  public Double getOver25Odds() {
    return over25Odds;
  }

  public void setOver25Odds(Double over25Odds) {
    this.over25Odds = over25Odds;
  }

  public Double getUnder25Odds() {
    return under25Odds;
  }

  public void setUnder25Odds(Double under25Odds) {
    this.under25Odds = under25Odds;
  }

  public Double getHtHomeOdds() {
    return htHomeOdds;
  }

  public void setHtHomeOdds(Double htHomeOdds) {
    this.htHomeOdds = htHomeOdds;
  }

  public Double getHtDrawOdds() {
    return htDrawOdds;
  }

  public void setHtDrawOdds(Double htDrawOdds) {
    this.htDrawOdds = htDrawOdds;
  }

  public Double getHtAwayOdds() {
    return htAwayOdds;
  }

  public void setHtAwayOdds(Double htAwayOdds) {
    this.htAwayOdds = htAwayOdds;
  }
}
