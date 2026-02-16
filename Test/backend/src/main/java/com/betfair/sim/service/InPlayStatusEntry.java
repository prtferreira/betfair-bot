package com.betfair.sim.service;

public class InPlayStatusEntry {
  public final String marketId;
  public final String startTime;
  public final String teams;
  public String status;

  public InPlayStatusEntry(String marketId, String startTime, String teams, String status) {
    this.marketId = marketId;
    this.startTime = startTime;
    this.teams = teams;
    this.status = status;
  }
}
