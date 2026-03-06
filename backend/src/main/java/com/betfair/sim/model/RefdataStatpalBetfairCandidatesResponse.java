package com.betfair.sim.model;

import java.util.List;

public class RefdataStatpalBetfairCandidatesResponse {
  private String date;
  private List<RefdataStatpalBetfairApiMatch> apiMatches;
  private List<RefdataStatpalBetfairBetfairMatch> betfairMatches;
  private List<RefdataStatpalBetfairMappingEntry> mappings;

  public RefdataStatpalBetfairCandidatesResponse() {}

  public RefdataStatpalBetfairCandidatesResponse(
      String date,
      List<RefdataStatpalBetfairApiMatch> apiMatches,
      List<RefdataStatpalBetfairBetfairMatch> betfairMatches,
      List<RefdataStatpalBetfairMappingEntry> mappings) {
    this.date = date;
    this.apiMatches = apiMatches;
    this.betfairMatches = betfairMatches;
    this.mappings = mappings;
  }

  public String getDate() {
    return date;
  }

  public void setDate(String date) {
    this.date = date;
  }

  public List<RefdataStatpalBetfairApiMatch> getApiMatches() {
    return apiMatches;
  }

  public void setApiMatches(List<RefdataStatpalBetfairApiMatch> apiMatches) {
    this.apiMatches = apiMatches;
  }

  public List<RefdataStatpalBetfairBetfairMatch> getBetfairMatches() {
    return betfairMatches;
  }

  public void setBetfairMatches(List<RefdataStatpalBetfairBetfairMatch> betfairMatches) {
    this.betfairMatches = betfairMatches;
  }

  public List<RefdataStatpalBetfairMappingEntry> getMappings() {
    return mappings;
  }

  public void setMappings(List<RefdataStatpalBetfairMappingEntry> mappings) {
    this.mappings = mappings;
  }
}
