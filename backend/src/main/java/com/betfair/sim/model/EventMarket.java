package com.betfair.sim.model;

import java.util.ArrayList;
import java.util.List;

public class EventMarket {
  private String marketId;
  private String marketName;
  private String marketType;
  private String startTime;
  private String marketStatus;
  private List<EventSelection> selections = new ArrayList<>();

  public EventMarket() {}

  public EventMarket(String marketId, String marketName, String marketType, String startTime) {
    this.marketId = marketId;
    this.marketName = marketName;
    this.marketType = marketType;
    this.startTime = startTime;
  }

  public String getMarketId() {
    return marketId;
  }

  public void setMarketId(String marketId) {
    this.marketId = marketId;
  }

  public String getMarketName() {
    return marketName;
  }

  public void setMarketName(String marketName) {
    this.marketName = marketName;
  }

  public String getMarketType() {
    return marketType;
  }

  public void setMarketType(String marketType) {
    this.marketType = marketType;
  }

  public String getStartTime() {
    return startTime;
  }

  public void setStartTime(String startTime) {
    this.startTime = startTime;
  }

  public List<EventSelection> getSelections() {
    return selections;
  }

  public void setSelections(List<EventSelection> selections) {
    this.selections = selections == null ? new ArrayList<>() : selections;
  }

  public String getMarketStatus() {
    return marketStatus;
  }

  public void setMarketStatus(String marketStatus) {
    this.marketStatus = marketStatus;
  }
}
