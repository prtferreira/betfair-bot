'use client'

import React, { useEffect, useMemo, useState } from "react";
import DayTabs from "./DayTabs";

type BetSide = "back" | "lay";

interface Game {
  id: string;
  marketId: string;
  homeTeam: string;
  awayTeam: string;
  league?: string;
  startTime?: string;

  homeOdds?: number;
  drawOdds?: number;
  awayOdds?: number;

  homeLayOdds?: number;
  drawLayOdds?: number;
  awayLayOdds?: number;

  homeSelectionId?: string;
  drawSelectionId?: string;
  awaySelectionId?: string;
}

interface Strategy {
  id: string;
  name: string;
}

interface SelectedBet {
  side: BetSide;
  outcome: string;
  odds: number;
}

interface SimBet {
  id: string;
  marketId: string;
  strategyId: string;
  strategyName?: string;
  homeTeam?: string;
  awayTeam?: string;
  selectionName: string;
  side: BetSide;
  odds: number;
  stake: number;
  status?: string;
  inPlay?: boolean;
  profit?: number;
  createdAt?: string;
  settledAt?: string;
  marketStatus?: string;
  matchClock?: string;
  homeScore?: number;
  awayScore?: number;
  inferredScore?: string;
}

interface StatusData {
  globalBalance?: number;
  balanceByStrategy?: Record<string, number>;
  valueGlobalWins?: number;
  valueGlobalLosses?: number;
  valueDailyWinLosses?: Record<
    string,
    { wins: number; losses: number }
  >;
  bets?: SimBet[];
}

interface Props {
  onBack: () => void;
}

const DAYS_TO_SHOW = 3;

/* -------------------- Utils -------------------- */

function formatOdds(value?: number | null): number | null {
  if (value === null || value === undefined) return null;
  const numeric = Number(value);
  return Number.isNaN(numeric) ? null : Number(numeric.toFixed(2));
}

function calculateLiability(
  bet: SelectedBet | undefined,
  stake: number
): number | null {
  if (!bet || !Number.isFinite(stake) || stake <= 0) return null;
  if (!Number.isFinite(bet.odds)) return null;

  if (bet.side === "back") return stake;

  const liability = (bet.odds - 1) * stake;
  return liability < 0 ? null : liability;
}

function resolveBetDay(bet: SimBet): string | null {
  const source = bet.settledAt || bet.createdAt;
  if (!source) return null;
  const date = new Date(source);
  if (Number.isNaN(date.getTime())) return null;
  return date.toISOString().slice(0, 10);
}

/* -------------------- Component -------------------- */

export default function MainStrategyPage({
  onBack,
}: Props) {
  const days = useMemo(() => {
    const today = new Date();
    return Array.from({ length: DAYS_TO_SHOW }, (_, i) => {
      const d = new Date(today);
      d.setDate(today.getDate() + i);
      return {
        iso: d.toISOString().slice(0, 10),
        label: i === 0 ? "Today" : i === 1 ? "Tomorrow" : "Day After",
        fullLabel: d.toLocaleDateString("en-GB"),
      };
    });
  }, []);

  const [selectedDay, setSelectedDay] = useState<string>(days[0].iso);
  const [activeTab, setActiveTab] = useState<
    "board" | "pending" | "inplay" | "results" | "history"
  >("board");

  const [state, setState] = useState<{
    loading: boolean;
    error: string | null;
    games: Game[];
  }>({
    loading: true,
    error: null,
    games: [],
  });

  const [strategies, setStrategies] = useState<Strategy[]>([]);
  const [selectedStrategyIds, setSelectedStrategyIds] = useState<string[]>([]);
  const [stakes, setStakes] = useState<Record<string, number>>({});
  const [selectedBets, setSelectedBets] = useState<
    Record<string, SelectedBet>
  >({});
  const [statusState, setStatusState] = useState<{
    loading: boolean;
    error: string | null;
    data: StatusData | null;
  }>({
    loading: true,
    error: null,
    data: null,
  });

  /* -------------------- Fetch games -------------------- */

  useEffect(() => {
    let active = true;

    fetch(`http://localhost:8089/api/betfair/today-odds?date=${selectedDay}`)
      .then((r) => r.json() as Promise<Game[]>)
      .then((data) => {
        if (!active) return;
        setState({ loading: false, error: null, games: data });
      })
      .catch((err: Error) => {
        if (!active) return;
        setState({ loading: false, error: err.message, games: [] });
      });

    return () => {
      active = false;
    };
  }, [selectedDay]);

  /* -------------------- Fetch strategies -------------------- */

  useEffect(() => {
    fetch("http://localhost:8089/api/strategies")
      .then((r) => r.json() as Promise<Strategy[]>)
      .then((data) => {
        setStrategies(data);
        if (data.length) setSelectedStrategyIds([data[0].id]);
      })
      .catch(() => setStrategies([]));
  }, []);

  /* -------------------- Load simulation status -------------------- */

  const loadStatus = (): void => {
    setStatusState((prev) => ({ ...prev, loading: true }));

    fetch("http://localhost:8089/api/sim/bets/status")
      .then((r) => r.json() as Promise<StatusData>)
      .then((data) =>
        setStatusState({ loading: false, error: null, data })
      )
      .catch((err: Error) =>
        setStatusState({ loading: false, error: err.message, data: null })
      );
  };

  useEffect(() => {
    loadStatus();
  }, []);

  /* -------------------- Handlers -------------------- */

  const handleSelectBet = (
    gameId: string,
    side: BetSide,
    outcome: string,
    odds: number | null
  ): void => {
    if (!Number.isFinite(odds ?? NaN)) return;

    setSelectedBets((prev) => ({
      ...prev,
      [gameId]: { side, outcome, odds: odds as number },
    }));
  };

  /* -------------------- Derived data -------------------- */

  const allBets = statusState.data?.bets ?? [];

  const pendingBets = allBets.filter(
    (b) => !b.inPlay && b.status !== "SETTLED"
  );

  const inPlayBets = allBets.filter(
    (b) => b.inPlay && b.status !== "SETTLED"
  );

  const settledBets = allBets.filter(
    (b) => b.status === "SETTLED"
  );

  /* -------------------- Render -------------------- */

  return (
    <>
      <header className="hero">
        <h1>Main Strategy</h1>
        <button onClick={onBack}>Back</button>
      </header>

      <DayTabs
        days={days}
        selectedDay={selectedDay}
        onSelect={setSelectedDay}
      />

      <main>
        {activeTab === "board" && (
          <section>
            {state.games.map((game) => {
              const bet = selectedBets[game.id];
              const stake = stakes[game.id] ?? 10;
              const liability = calculateLiability(bet, stake);

              return (
                <div key={game.id}>
                  <h3>
                    {game.homeTeam} vs {game.awayTeam}
                  </h3>

                  <button
                    onClick={() =>
                      handleSelectBet(
                        game.id,
                        "back",
                        "Home",
                        formatOdds(game.homeOdds)
                      )
                    }
                  >
                    Back Home
                  </button>

                  <p>
                    Liability:{" "}
                    {liability === null
                      ? "N/A"
                      : `€${liability.toFixed(2)}`}
                  </p>
                </div>
              );
            })}
          </section>
        )}

        {activeTab === "pending" && (
          <section>
            {pendingBets.map((bet) => (
              <div key={bet.id}>
                {bet.homeTeam} vs {bet.awayTeam}
              </div>
            ))}
          </section>
        )}

        {activeTab === "inplay" && (
          <section>
            {inPlayBets.map((bet) => (
              <div key={bet.id}>
                {bet.homeTeam} vs {bet.awayTeam}
              </div>
            ))}
          </section>
        )}

        {activeTab === "results" && (
          <section>
            {settledBets.map((bet) => (
              <div key={bet.id}>
                {bet.homeTeam} vs {bet.awayTeam}
              </div>
            ))}
          </section>
        )}
      </main>
    </>
  );
}


