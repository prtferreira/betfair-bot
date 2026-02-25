"use client";

import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import "../analytics.css";

interface AnalyticsGoalsEstimate {
  gameKey: string;
  displayName: string;
  guessedGoals: number;
  closedLines?: string[];
}

const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL?.trim() || "http://localhost:8089";

export default function GameGoalsDetailsPage({
}: {}) {
  const routeParams = useParams<{ gameKey: string }>();
  const gameKey = routeParams?.gameKey ?? "";
  const searchParams = useSearchParams();
  const date = searchParams.get("date") || "";
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<AnalyticsGoalsEstimate | null>(null);

  useEffect(() => {
    if (!gameKey) {
      setResult(null);
      setError("Missing game key");
      return;
    }
    const load = async (): Promise<void> => {
      setLoading(true);
      setError(null);
      try {
        const response = await fetch(
          `${API_BASE}/api/betfair/analytics/goals?date=${encodeURIComponent(
            date
          )}&gameKey=${encodeURIComponent(gameKey)}`
        );
        if (!response.ok) {
          throw new Error(`Failed to load goal analysis (${response.status})`);
        }
        const payload = (await response.json()) as AnalyticsGoalsEstimate;
        setResult(payload);
      } catch (err) {
        const message =
          err instanceof Error ? err.message : "Failed to load goal analysis";
        setError(message);
      } finally {
        setLoading(false);
      }
    };
    void load();
  }, [date, gameKey]);

  return (
    <main className="analytics-page">
      <section className="analytics-panel">
        <p className="analytics-back">
          <Link href={`/games/analytics?date=${encodeURIComponent(date)}`}>
            Back to analytics
          </Link>
        </p>
        <h1 className="analytics-title">Goal Guess</h1>
        <p className="analytics-subtitle">
          Game: {(result?.displayName || gameKey).replaceAll("_", " ")}
        </p>
        <p className="analytics-meta">date: {date || "unknown"}</p>

        {loading ? <p className="analytics-hint">Analysing OVER_UNDER files...</p> : null}
        {error ? <p className="analytics-hint analytics-hint--error">{error}</p> : null}

        {!loading && !error && result ? (
          <>
            <p className="analytics-goals">guessed goals: {result.guessedGoals}</p>
            <p className="analytics-meta">closed lines before end:</p>
            {result.closedLines && result.closedLines.length > 0 ? (
              <ul className="analytics-list">
                {result.closedLines.map((line) => (
                  <li key={line} className="analytics-row">
                    <p className="analytics-name">{line}</p>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="analytics-hint">No closed OVER_UNDER lines found before end.</p>
            )}
          </>
        ) : null}
      </section>
    </main>
  );
}
