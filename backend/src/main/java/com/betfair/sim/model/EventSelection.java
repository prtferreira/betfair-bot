package com.betfair.sim.model;

public class EventSelection {
  private Long selectionId;
  private String selectionName;
  private Double backOdds;
  private Double layOdds;

  public EventSelection() {}

  public EventSelection(Long selectionId, String selectionName, Double backOdds, Double layOdds) {
    this.selectionId = selectionId;
    this.selectionName = selectionName;
    this.backOdds = backOdds;
    this.layOdds = layOdds;
  }

  public Long getSelectionId() {
    return selectionId;
  }

  public void setSelectionId(Long selectionId) {
    this.selectionId = selectionId;
  }

  public String getSelectionName() {
    return selectionName;
  }

  public void setSelectionName(String selectionName) {
    this.selectionName = selectionName;
  }

  public Double getBackOdds() {
    return backOdds;
  }

  public void setBackOdds(Double backOdds) {
    this.backOdds = backOdds;
  }

  public Double getLayOdds() {
    return layOdds;
  }

  public void setLayOdds(Double layOdds) {
    this.layOdds = layOdds;
  }
}
