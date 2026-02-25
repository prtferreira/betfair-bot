"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import "./analytics.css";

interface AnalyticsGameEntry {
  gameKey: string;
  displayName: string;
  markets?: string[];
  marketCount?: number;
  guessedGoals?: number;
}

const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL?.trim() || "http://localhost:8089";

function formatLocalDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export default function GameAnalyticsPage() {
  const [selectedDate, setSelectedDate] = useState<string>(() =>
    formatLocalDate(new Date())
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [games, setGames] = useState<AnalyticsGameEntry[]>([]);

  const sortedGames = useMemo(
    () =>
      [...games].sort((a, b) =>
        (a.displayName || "").localeCompare(b.displayName || "")
      ),
    [games]
  );

  const loadGames = async (date: string): Promise<void> => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch(
        `${API_BASE}/api/betfair/analytics/games?date=${encodeURIComponent(
          date
        )}`
      );
      if (!response.ok) {
        throw new Error(`Failed to load analytics games (${response.status})`);
      }
      const payload = (await response.json()) as AnalyticsGameEntry[];
      setGames(Array.isArray(payload) ? payload : []);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Failed to load analytics games";
      setError(message);
      setGames([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadGames(selectedDate);
  }, [selectedDate]);

  return (
    <main className="analytics-page">
      <section className="analytics-panel">
        <p className="analytics-back">
          <Link href="/games">Back to games</Link>
        </p>
        <h1 className="analytics-title">Game Analytics</h1>
        <p className="analytics-subtitle">
          Pick a date and load unique games from `backend/data/yyyyMMdd`.
        </p>

        <div className="analytics-controls">
          <label htmlFor="analytics-date" className="analytics-label">
            Date
          </label>
          <input
            id="analytics-date"
            type="date"
            value={selectedDate}
            onChange={(event) => setSelectedDate(event.currentTarget.value)}
            className="analytics-date"
          />
        </div>

        {loading ? <p className="analytics-hint">Loading games...</p> : null}
        {error ? <p className="analytics-hint analytics-hint--error">{error}</p> : null}
        {!loading && !error && sortedGames.length === 0 ? (
          <p className="analytics-hint">
            No game files found for this date.
          </p>
        ) : null}

        {!loading && !error && sortedGames.length > 0 ? (
          <ul className="analytics-list">
            {sortedGames.map((game) => (
              <li key={game.gameKey} className="analytics-row">
                <p className="analytics-name">{game.displayName || game.gameKey}</p>
                <p className="analytics-meta">key: {game.gameKey}</p>
                <p className="analytics-meta">
                  markets found: {game.marketCount ?? game.markets?.length ?? 0}
                </p>
                <p className="analytics-meta analytics-goals">
                  guessed goals: {game.guessedGoals ?? 0}
                </p>
                <p className="analytics-actions">
                  <Link
                    href={`/games/analytics/${encodeURIComponent(
                      game.gameKey
                    )}?date=${encodeURIComponent(selectedDate)}`}
                  >
                    Guess goals details
                  </Link>
                </p>
              </li>
            ))}
          </ul>
        ) : null}
      </section>
    </main>
  );
}
