"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import "./simulations.css";

type StrategyTab = "overunder1_5";
type BetState = "OPEN" | "WON" | "LOST";

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

function parseMinute(raw?: string): number | null {
  const text = (raw || "").trim();
  if (!text) return null;
  const upper = text.toUpperCase().replace(/\s+/g, "");
  if (upper === "HT" || upper === "HT'") {
    return 45;
  }
  const match = text.match(/^(\d{1,3})(?:\+\d{1,2})?/);
  if (!match) return null;
  return Number(match[1]);
}

function formatUpdatedAt(raw: string): string {
  if (!raw) return "-";
  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) return raw;
  return date.toLocaleTimeString();
}

export default function SimulationsPage() {
  const [activeTab, setActiveTab] = useState<StrategyTab>("overunder1_5");
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

  const betsByEventId = useMemo(() => {
    const map: Record<string, SimBet> = {};
    for (const bet of status?.bets || []) {
      map[bet.eventId] = bet;
    }
    return map;
  }, [status]);

  const visibleLiveGames = useMemo(
    () =>
      (status?.liveGames || []).filter((game) => {
        const hasBet = Boolean(betsByEventId[game.eventId]);
        const minuteValue = parseMinute(game.minute);
        if (!hasBet && minuteValue != null && minuteValue > 30) {
          return false;
        }
        return true;
      }),
    [status, betsByEventId]
  );

  const bank = status?.bank ?? 0;
  const settledProfit = status?.settledProfit ?? 0;
  const wins = status?.wins ?? 0;
  const losses = status?.losses ?? 0;
  const wonValue = status?.wonValue ?? 0;
  const lostValue = status?.lostValue ?? 0;
  const open = status?.openBets ?? 0;
  const finished = status?.finishedBets ?? 0;
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

      {!loading && !error ? (
        <ul className="sim-list">
          {visibleLiveGames.map((game) => {
            const bet = betsByEventId[game.eventId];
            const fixture = `${game.homeTeam || "Home"} vs ${game.awayTeam || "Away"}`;
            const state = bet?.state || "OPEN";
            const stateClass =
              state === "WON" ? "sim-pill--won" : state === "LOST" ? "sim-pill--lost" : "sim-pill--open";
            const possibleProfit =
              bet && bet.state === "OPEN"
                ? Number((bet.stake * bet.odds - bet.stake).toFixed(2))
                : null;
            const possibleLoss = bet && bet.state === "OPEN" ? Number(bet.stake.toFixed(2)) : null;

            return (
              <li key={`${game.marketId}|${game.eventId}`} className="sim-row">
                <div className="sim-row-top">
                  <div>
                    <div className="sim-fixture">{fixture}</div>
                    <div className="sim-league">{game.league || "-"}</div>
                  </div>
                  <span className={`sim-pill ${stateClass}`}>{bet ? state : "NO BET"}</span>
                </div>

                <div className="sim-grid">
                  <div>
                    <div className="sim-k">Live minute</div>
                    <div className="sim-v">{game.minute || "-"}</div>
                  </div>
                  <div>
                    <div className="sim-k">Live goals</div>
                    <div className="sim-v">{game.score || "-"}</div>
                  </div>
                  <div>
                    <div className="sim-k">Entry odds</div>
                    <div className="sim-v">{bet ? bet.odds.toFixed(2) : "-"}</div>
                  </div>
                  <div>
                    <div className="sim-k">
                      {bet && bet.state === "OPEN" ? "Possible P/L" : "Virtual P/L"}
                    </div>
                    <div className={`sim-v ${bet && bet.profit < 0 ? "sim-neg" : "sim-pos"}`}>
                      {bet && bet.state === "OPEN"
                        ? `+${possibleProfit?.toFixed(2)} / -${possibleLoss?.toFixed(2)}`
                        : bet
                        ? `${bet.profit >= 0 ? "+" : ""}${bet.profit.toFixed(2)}`
                        : "-"}
                    </div>
                  </div>
                </div>
                {bet && bet.state === "OPEN" ? (
                  <div className="sim-league">
                    {bet.stake.toFixed(2)} EUR * {bet.odds.toFixed(2)} = Possible profit +{possibleProfit?.toFixed(2)} EUR vs possible loss -{possibleLoss?.toFixed(2)} EUR
                  </div>
                ) : null}
              </li>
            );
          })}
        </ul>
      ) : null}
    </main>
  );
}
