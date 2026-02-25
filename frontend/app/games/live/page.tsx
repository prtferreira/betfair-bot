"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import "./live.css";

interface LiveGameEntry {
  eventId: string;
  marketId: string;
  league?: string;
  homeTeam?: string;
  awayTeam?: string;
  startTime?: string;
  marketStatus?: string;
  inPlay?: boolean;
  homeOdds?: number;
  drawOdds?: number;
  awayOdds?: number;
  score?: string;
  minute?: string;
  minuteSource?: string;
}

const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL?.trim() || "http://localhost:8089";

function eventName(game: LiveGameEntry): string {
  const home = game.homeTeam?.trim();
  const away = game.awayTeam?.trim();
  if (home && away) {
    return `${home} vs ${away}`;
  }
  return game.eventId || "Unknown game";
}

export default function LiveGamesPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [games, setGames] = useState<LiveGameEntry[]>([]);
  const [lastUpdated, setLastUpdated] = useState<string>("");
  const [goalAlerts, setGoalAlerts] = useState<Record<string, { minute: string }>>(
    {}
  );
  const previousScoresRef = useRef<Map<string, string>>(new Map());
  const goalAlertsRef = useRef<Map<string, { expiresAt: number; minute: string }>>(
    new Map()
  );

  const gameKey = (game: LiveGameEntry): string =>
    `${game.marketId || ""}|${game.eventId || ""}`;

  const parseScore = (score?: string): [number, number] | null => {
    const text = (score || "").trim();
    const match = text.match(/^(\d+)\s*-\s*(\d+)$/);
    if (!match) {
      return null;
    }
    return [Number(match[1]), Number(match[2])];
  };

  const isGoalChange = (previousScore?: string, nextScore?: string): boolean => {
    const prev = parseScore(previousScore);
    const next = parseScore(nextScore);
    if (!prev || !next) {
      return false;
    }
    const prevTotal = prev[0] + prev[1];
    const nextTotal = next[0] + next[1];
    return nextTotal > prevTotal;
  };

  const syncGoalAlertsState = (): void => {
    const now = Date.now();
    let changed = false;
    for (const [key, alert] of goalAlertsRef.current.entries()) {
      if (alert.expiresAt <= now) {
        goalAlertsRef.current.delete(key);
        changed = true;
      }
    }
    if (!changed && Object.keys(goalAlerts).length === goalAlertsRef.current.size) {
      return;
    }
    const nextState: Record<string, { minute: string }> = {};
    for (const [key, alert] of goalAlertsRef.current.entries()) {
      nextState[key] = { minute: alert.minute };
    }
    setGoalAlerts(nextState);
  };

  const load = async (): Promise<void> => {
    try {
      setError(null);
      const response = await fetch(
        `${API_BASE}/api/betfair/live-games?ts=${Date.now()}`,
        { cache: "no-store" }
      );
      if (!response.ok) {
        throw new Error(`Failed to load live games (${response.status})`);
      }
      const payload = (await response.json()) as LiveGameEntry[];
      const list = Array.isArray(payload) ? payload : [];
      const nowMs = Date.now();

      for (const game of list) {
        const key = gameKey(game);
        const nextScore = (game.score || "").trim();
        const previousScore = previousScoresRef.current.get(key);
        if (
          previousScore &&
          nextScore &&
          previousScore !== nextScore &&
          isGoalChange(previousScore, nextScore)
        ) {
          const minute = (game.minute || "").trim() || "?";
          goalAlertsRef.current.set(key, {
            minute,
            expiresAt: nowMs + 60_000,
          });
        }
        if (nextScore) {
          previousScoresRef.current.set(key, nextScore);
        }
      }

      syncGoalAlertsState();
      setGames(list);
      setLastUpdated(new Date().toLocaleTimeString());
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Failed to load live games";
      setError(message);
      setGames([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    const timer = setInterval(() => {
      void load();
    }, 30000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    const cleanupTimer = setInterval(() => {
      syncGoalAlertsState();
    }, 1000);
    return () => clearInterval(cleanupTimer);
  });

  const sorted = useMemo(
    () => [...games].sort((a, b) => eventName(a).localeCompare(eventName(b))),
    [games]
  );

  return (
    <main className="live-page">
      <section className="live-panel">
        <p className="live-back">
          <Link href="/games">Back to games</Link>
        </p>
        <h1 className="live-title">Live Games</h1>
        <p className="live-subtitle">
          In-play matches with minute estimate from live feed and kickoff time.
        </p>

        <div className="live-actions">
          <button type="button" className="live-refresh" onClick={() => void load()}>
            Refresh now
          </button>
          <span className="live-updated">
            Last updated: {lastUpdated || "not yet"}
          </span>
        </div>

        {loading ? <p className="live-hint">Loading live games...</p> : null}
        {error ? <p className="live-hint live-hint--error">{error}</p> : null}
        {!loading && !error && sorted.length === 0 ? (
          <p className="live-hint">No in-play games right now.</p>
        ) : null}

        {!loading && !error && sorted.length > 0 ? (
          <ul className="live-list">
            {sorted.map((game) => {
              const key = gameKey(game);
              const goalAlert = goalAlerts[key];
              return (
              <li key={`${game.marketId}:${game.eventId}`} className="live-row">
                <p className="live-name">{eventName(game)}</p>
                {goalAlert ? (
                  <p className="live-goal-alert">
                    GOAAAAALLLLLLLLLLL {goalAlert.minute}
                  </p>
                ) : null}
                <p className="live-meta">{game.league || "Unknown league"}</p>
                <p className="live-meta">
                  minute: <strong>{game.minute || "Live"}</strong>
                  {game.minuteSource ? ` (${game.minuteSource})` : ""}
                </p>
                <p className="live-meta">score: {game.score || "-"}</p>
                <p className="live-meta">
                  odds H/D/A: {game.homeOdds ?? "-"} / {game.drawOdds ?? "-"} /{" "}
                  {game.awayOdds ?? "-"}
                </p>
              </li>
            )})}
          </ul>
        ) : null}
      </section>
    </main>
  );
}
