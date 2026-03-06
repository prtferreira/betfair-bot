"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import "./ended-00.css";

interface EndedZeroZeroEntry {
  gameKey: string;
  displayName: string;
  date: string;
  preKickoffZeroZeroOdds?: number;
  finalZeroZeroOdds?: number;
  finalUnder05Odds?: number;
  finalOver05Odds?: number;
}

const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL?.trim() || "http://localhost:8089";

function formatLocalDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function formatOdds(value?: number): string {
  if (value == null || Number.isNaN(value)) {
    return "-";
  }
  return value.toFixed(2);
}

export default function EndedZeroZeroPage() {
  const [selectedDate, setSelectedDate] = useState<string>(() =>
    formatLocalDate(new Date())
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [games, setGames] = useState<EndedZeroZeroEntry[]>([]);

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
        `${API_BASE}/api/betfair/analytics/ended-zero-zero?date=${encodeURIComponent(
          date
        )}`
      );
      if (!response.ok) {
        throw new Error(`Failed to load ended 0-0 games (${response.status})`);
      }
      const payload = (await response.json()) as EndedZeroZeroEntry[];
      setGames(Array.isArray(payload) ? payload : []);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Failed to load ended 0-0 games";
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
    <main className="ended00-page">
      <section className="ended00-panel">
        <p className="ended00-back">
          <Link href="/games">Back to games</Link>
        </p>
        <h1 className="ended00-title">Ended 0-0</h1>
        <p className="ended00-subtitle">
          Matches inferred as FT 0-0 by combining CORRECT_SCORE (0-0) and
          OVER_UNDER_05, with the pre-kickoff 0-0 odds.
        </p>

        <div className="ended00-controls">
          <label htmlFor="ended00-date" className="ended00-label">
            Date
          </label>
          <input
            id="ended00-date"
            type="date"
            value={selectedDate}
            onChange={(event) => setSelectedDate(event.currentTarget.value)}
            className="ended00-date"
          />
        </div>

        {loading ? <p className="ended00-hint">Loading games...</p> : null}
        {error ? <p className="ended00-hint ended00-hint--error">{error}</p> : null}
        {!loading && !error && sortedGames.length === 0 ? (
          <p className="ended00-hint">No confirmed FT 0-0 games for this date.</p>
        ) : null}

        {!loading && !error && sortedGames.length > 0 ? (
          <div className="ended00-table-wrap">
            <table className="ended00-table">
              <thead>
                <tr>
                  <th>Game</th>
                  <th>Pre-KO 0-0</th>
                  <th>Final 0-0</th>
                  <th>Final Under 0.5</th>
                  <th>Final Over 0.5</th>
                </tr>
              </thead>
              <tbody>
                {sortedGames.map((game) => (
                  <tr key={game.gameKey}>
                    <td>{game.displayName || game.gameKey}</td>
                    <td>{formatOdds(game.preKickoffZeroZeroOdds)}</td>
                    <td>{formatOdds(game.finalZeroZeroOdds)}</td>
                    <td>{formatOdds(game.finalUnder05Odds)}</td>
                    <td>{formatOdds(game.finalOver05Odds)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </section>
    </main>
  );
}
