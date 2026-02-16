package com.betfair.sim.model;

import java.util.List;

public class FollowedGamesRequest {
  private String date;
  private List<String> marketIds;

  public FollowedGamesRequest() {}

  public FollowedGamesRequest(String date, List<String> marketIds) {
    this.date = date;
    this.marketIds = marketIds;
  }

  public String getDate() {
    return date;
  }

  public void setDate(String date) {
    this.date = date;
  }

  public List<String> getMarketIds() {
    return marketIds;
  }

  public void setMarketIds(List<String> marketIds) {
    this.marketIds = marketIds;
  }
}
