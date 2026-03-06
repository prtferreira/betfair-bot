package com.betfair.sim.model;

import java.util.ArrayList;
import java.util.List;

public class OverUnder15StatusResponse {
  private String strategyId;
  private double bank;
  private double settledProfit;
  private int wins;
  private int losses;
  private int openBets;
  private int finishedBets;
  private double wonValue;
  private double lostValue;
  private double stake;
  private String updatedAt;
  private List<LiveGameEntry> liveGames = new ArrayList<>();
  private List<OverUnder15BetRecord> bets = new ArrayList<>();

  public String getStrategyId() {
    return strategyId;
  }

  public void setStrategyId(String strategyId) {
    this.strategyId = strategyId;
  }

  public double getBank() {
    return bank;
  }

  public void setBank(double bank) {
    this.bank = bank;
  }

  public double getSettledProfit() {
    return settledProfit;
  }

  public void setSettledProfit(double settledProfit) {
    this.settledProfit = settledProfit;
  }

  public int getWins() {
    return wins;
  }

  public void setWins(int wins) {
    this.wins = wins;
  }

  public int getLosses() {
    return losses;
  }

  public void setLosses(int losses) {
    this.losses = losses;
  }

  public int getOpenBets() {
    return openBets;
  }

  public void setOpenBets(int openBets) {
    this.openBets = openBets;
  }

  public int getFinishedBets() {
    return finishedBets;
  }

  public void setFinishedBets(int finishedBets) {
    this.finishedBets = finishedBets;
  }

  public double getWonValue() {
    return wonValue;
  }

  public void setWonValue(double wonValue) {
    this.wonValue = wonValue;
  }

  public double getLostValue() {
    return lostValue;
  }

  public void setLostValue(double lostValue) {
    this.lostValue = lostValue;
  }

  public double getStake() {
    return stake;
  }

  public void setStake(double stake) {
    this.stake = stake;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(String updatedAt) {
    this.updatedAt = updatedAt;
  }

  public List<LiveGameEntry> getLiveGames() {
    return liveGames;
  }

  public void setLiveGames(List<LiveGameEntry> liveGames) {
    this.liveGames = liveGames == null ? new ArrayList<>() : liveGames;
  }

  public List<OverUnder15BetRecord> getBets() {
    return bets;
  }

  public void setBets(List<OverUnder15BetRecord> bets) {
    this.bets = bets == null ? new ArrayList<>() : bets;
  }
}

