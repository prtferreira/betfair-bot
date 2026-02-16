package com.betfair.sim.model;

import java.util.List;

public class SelectedGamesRequest {
  private String date;
  private List<String> entries;

  public SelectedGamesRequest() {}

  public SelectedGamesRequest(String date, List<String> entries) {
    this.date = date;
    this.entries = entries;
  }

  public String getDate() {
    return date;
  }

  public void setDate(String date) {
    this.date = date;
  }

  public List<String> getEntries() {
    return entries;
  }

  public void setEntries(List<String> entries) {
    this.entries = entries;
  }
}
