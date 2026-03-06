"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import "./simulations.css";

type StrategyTab = "overunder1_5";
type BetState = "OPEN" | "WON" | "LOST";
type ViewTab = "open" | "wins" | "losses";

interface LiveGameEntry {
  eventId: string;
  marketId: string;
  league?: string;
  homeTeam?: string;
  awayTeam?: string;
  inPlay?: boolean;
  score?: string;
  minute?: string;
}

interface SimBet {
  eventId: string;
  marketId: string;
  homeTeam: string;
  awayTeam: string;
  league: string;
  placedAtMinute: string;
  odds: number;
  stake: number;
  state: BetState;
  profit: number;
  latestScore: string;
  latestMinute: string;
}

interface OverUnder15Status {
  strategyId: string;
  bank: number;
  settledProfit: number;
  wins: number;
  losses: number;
  openBets: number;
  finishedBets: number;
  wonValue: number;
  lostValue: number;
  stake: number;
  updatedAt: string;
  liveGames: LiveGameEntry[];
  bets: SimBet[];
}

const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL?.trim() || "http://localhost:8089";

function formatUpdatedAt(raw: string): string {
  if (!raw) return "-";
  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) return raw;
  return date.toLocaleTimeString();
}

function csvValue(value: unknown): string {
  const text = String(value ?? "");
  const escaped = text.replace(/"/g, "\"\"");
  return `"${escaped}"`;
}

export default function SimulationsPage() {
  const [activeTab, setActiveTab] = useState<StrategyTab>("overunder1_5");
  const [viewTab, setViewTab] = useState<ViewTab>("open");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<OverUnder15Status | null>(null);

  const fetchSnapshot = async (): Promise<void> => {
    const response = await fetch(
      `${API_BASE}/api/sim/overunder15/status?ts=${Date.now()}`,
      { cache: "no-store" }
    );
    if (!response.ok) {
      throw new Error(`Failed loading simulation status (${response.status})`);
    }
    const payload = (await response.json()) as OverUnder15Status;
    setStatus(payload);
  };

  useEffect(() => {
    let active = true;
    const load = async (): Promise<void> => {
      try {
        if (active) setError(null);
        await fetchSnapshot();
      } catch (err) {
        if (!active) return;
        setError(err instanceof Error ? err.message : "Failed loading simulations");
      } finally {
        if (active) setLoading(false);
      }
    };

    void load();
    const timer = setInterval(() => void load(), 30000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, []);

  const liveGameByEventId = useMemo(() => {
    const map: Record<string, LiveGameEntry> = {};
    for (const game of status?.liveGames || []) {
      map[game.eventId] = game;
    }
    return map;
  }, [status]);

  const openBets = useMemo(
    () =>
      (status?.bets || []).filter((bet) => bet.state === "OPEN"),
    [status]
  );
  const finishedWins = useMemo(
    () =>
      (status?.bets || []).filter((bet) => bet.state === "WON"),
    [status]
  );
  const finishedLosses = useMemo(
    () =>
      (status?.bets || []).filter((bet) => bet.state === "LOST"),
    [status]
  );
  const visibleBets = useMemo(() => {
    if (viewTab === "wins") {
      return finishedWins;
    }
    if (viewTab === "losses") {
      return finishedLosses;
    }
    return openBets;
  }, [viewTab, openBets, finishedWins, finishedLosses]);
  const canExportFinished = viewTab === "wins" || viewTab === "losses";

  const exportFinishedCsv = (): void => {
    if (!canExportFinished || visibleBets.length === 0) {
      return;
    }
    const header = [
      "eventId",
      "marketId",
      "league",
      "homeTeam",
      "awayTeam",
      "state",
      "placedAtMinute",
      "latestMinute",
      "latestScore",
      "odds",
      "stake",
      "profit",
    ];
    const lines = [header.join(",")];
    for (const bet of visibleBets) {
      lines.push(
        [
          csvValue(bet.eventId),
          csvValue(bet.marketId),
          csvValue(bet.league),
          csvValue(bet.homeTeam),
          csvValue(bet.awayTeam),
          csvValue(bet.state),
          csvValue(bet.placedAtMinute),
          csvValue(bet.latestMinute),
          csvValue(bet.latestScore),
          csvValue(bet.odds.toFixed(2)),
          csvValue(bet.stake.toFixed(2)),
          csvValue(bet.profit.toFixed(2)),
        ].join(",")
      );
    }

    const blob = new Blob([lines.join("\n")], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    const dateStamp = new Date().toISOString().slice(0, 10);
    const suffix = viewTab === "wins" ? "finished-wins" : "finished-losses";
    link.href = url;
    link.download = `${suffix}-${dateStamp}.csv`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  };
  const derivedCounts = useMemo(() => {
    const bets = status?.bets || [];
    let openCount = 0;
    let finishedCount = 0;
    let winCount = 0;
    let lossCount = 0;
    for (const bet of bets) {
      const state = (bet.state || "").toUpperCase();
      if (state === "OPEN") {
        openCount++;
      } else {
        finishedCount++;
      }
      if (state === "WON") {
        winCount++;
      } else if (state === "LOST") {
        lossCount++;
      }
    }
    return { openCount, finishedCount, winCount, lossCount };
  }, [status]);
  const countMismatch =
    status != null &&
    (status.openBets !== derivedCounts.openCount ||
      status.finishedBets !== derivedCounts.finishedCount ||
      status.wins !== derivedCounts.winCount ||
      status.losses !== derivedCounts.lossCount);

  const bank = status?.bank ?? 0;
  const settledProfit = status?.settledProfit ?? 0;
  const wins = derivedCounts.winCount;
  const losses = derivedCounts.lossCount;
  const wonValue = status?.wonValue ?? 0;
  const lostValue = status?.lostValue ?? 0;
  const open = derivedCounts.openCount;
  const finished = derivedCounts.finishedCount;
  const stake = status?.stake ?? 20;
  const lastUpdated = formatUpdatedAt(status?.updatedAt || "");

  return (
    <main className="sim-page">
      <header className="sim-head">
        <Link href="/" className="sim-back">
          Back
        </Link>
        <h1 className="sim-title">Simulations</h1>
        <p className="sim-subtitle">
          Auto strategy runner for live games with virtual bankroll tracking.
        </p>
      </header>

      <div className="sim-tabs" role="tablist" aria-label="Strategies">
        <button
          type="button"
          className={`sim-tab ${activeTab === "overunder1_5" ? "sim-tab--active" : ""}`}
          onClick={() => setActiveTab("overunder1_5")}
        >
          overunder1_5
        </button>
      </div>
      <div className="sim-tabs" role="tablist" aria-label="Bet views">
        <button
          type="button"
          className={`sim-tab ${viewTab === "open" ? "sim-tab--active" : ""}`}
          onClick={() => setViewTab("open")}
        >
          Open Bets ({open})
        </button>
        <button
          type="button"
          className={`sim-tab ${viewTab === "wins" ? "sim-tab--active" : ""}`}
          onClick={() => setViewTab("wins")}
        >
          Finished Wins ({wins})
        </button>
        <button
          type="button"
          className={`sim-tab ${viewTab === "losses" ? "sim-tab--active" : ""}`}
          onClick={() => setViewTab("losses")}
        >
          Finished Losses ({losses})
        </button>
      </div>
      {canExportFinished ? (
        <div className="sim-export-row">
          <button
            type="button"
            className="sim-export-btn"
            onClick={exportFinishedCsv}
            disabled={visibleBets.length === 0}
          >
            Export {viewTab === "wins" ? "Finished Wins" : "Finished Losses"} CSV
          </button>
        </div>
      ) : null}

      <section className="sim-card">
        <div className="sim-summary">
          <div className="sim-metric">
            <p className="sim-metric-label">Bank</p>
            <p className={`sim-metric-value ${bank >= 1000 ? "sim-pos" : "sim-neg"}`}>
              {bank.toFixed(2)}
            </p>
          </div>
          <div className="sim-metric">
            <p className="sim-metric-label">Settled P/L</p>
            <p className={`sim-metric-value ${settledProfit >= 0 ? "sim-pos" : "sim-neg"}`}>
              {settledProfit >= 0 ? "+" : ""}
              {settledProfit.toFixed(2)}
            </p>
          </div>
          <div className="sim-metric">
            <p className="sim-metric-label">Wins / Losses</p>
            <p className="sim-metric-value">
              {wins} / {losses}
            </p>
          </div>
          <div className="sim-metric">
            <p className="sim-metric-label">Won / Lost Value</p>
            <p className="sim-metric-value">
              +{wonValue.toFixed(2)} / -{lostValue.toFixed(2)}
            </p>
          </div>
          <div className="sim-metric">
            <p className="sim-metric-label">Finished Bets</p>
            <p className="sim-metric-value">{finished}</p>
          </div>
          <div className="sim-metric">
            <p className="sim-metric-label">Open Bets</p>
            <p className="sim-metric-value">{open}</p>
          </div>
        </div>
        <p className="sim-hint">
          Strategy: Back Over 1.5 at minute 20+ only when score is 0-0 and odds are at least 1.45. Stake {stake} EUR. Last updated{" "}
          {lastUpdated}.
        </p>
      </section>

      {loading ? <p className="sim-hint">Loading live simulations...</p> : null}
      {error ? <p className="sim-hint sim-neg">{error}</p> : null}
      {!loading && !error && countMismatch ? (
        <p className="sim-hint sim-neg">
          Data consistency warning: server summary differs from bet records. Showing counts from bet records.
        </p>
      ) : null}

      {!loading && !error ? (
        <ul className="sim-list">
          {visibleBets.map((bet) => {
            const game = liveGameByEventId[bet.eventId];
            const fixture = `${bet.homeTeam || game?.homeTeam || "Home"} vs ${bet.awayTeam || game?.awayTeam || "Away"}`;
            const state = bet.state;
            const stateClass =
              state === "WON" ? "sim-pill--won" : state === "LOST" ? "sim-pill--lost" : "sim-pill--open";
            const possibleProfit =
              state === "OPEN" ? Number((bet.stake * bet.odds - bet.stake).toFixed(2)) : null;
            const possibleLoss = state === "OPEN" ? Number(bet.stake.toFixed(2)) : null;

            return (
              <li key={`${bet.marketId}|${bet.eventId}`} className="sim-row">
                <div className="sim-row-top">
                  <div>
                    <div className="sim-fixture">{fixture}</div>
                    <div className="sim-league">{bet.league || game?.league || "-"}</div>
                  </div>
                  <span className={`sim-pill ${stateClass}`}>{state}</span>
                </div>

                <div className="sim-grid">
                  <div>
                    <div className="sim-k">Live minute</div>
                    <div className="sim-v">{game?.minute || bet.latestMinute || bet.placedAtMinute || "-"}</div>
                  </div>
                  <div>
                    <div className="sim-k">Live goals</div>
                    <div className="sim-v">{game?.score || bet.latestScore || "-"}</div>
                  </div>
                  <div>
                    <div className="sim-k">Entry odds</div>
                    <div className="sim-v">{bet.odds.toFixed(2)}</div>
                  </div>
                  <div>
                    <div className="sim-k">
                      {state === "OPEN" ? "Possible P/L" : "Virtual P/L"}
                    </div>
                    <div className={`sim-v ${bet.profit < 0 ? "sim-neg" : "sim-pos"}`}>
                      {state === "OPEN"
                        ? `+${possibleProfit?.toFixed(2)} / -${possibleLoss?.toFixed(2)}`
                        : `${bet.profit >= 0 ? "+" : ""}${bet.profit.toFixed(2)}`}
                    </div>
                  </div>
                </div>
                {state === "OPEN" ? (
                  <div className="sim-league">
                    {bet.stake.toFixed(2)} EUR * {bet.odds.toFixed(2)} = Possible profit +{possibleProfit?.toFixed(2)} EUR vs possible loss -{possibleLoss?.toFixed(2)} EUR
                  </div>
                ) : null}
              </li>
            );
          })}
        </ul>
      ) : null}
      {!loading && !error && visibleBets.length === 0 ? (
        <p className="sim-hint">
          {viewTab === "open"
            ? "No open bets."
            : viewTab === "wins"
            ? "No finished winning bets."
            : "No finished losing bets."}
        </p>
      ) : null}
    </main>
  );
}
