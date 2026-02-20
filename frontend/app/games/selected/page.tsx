"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import "./selected.css";

interface SubmittedGame {
  eventId: string;
  marketId: string;
  eventName: string;
  startTime: string;
  dateTab: "today" | "tomorrow" | "afterTomorrow";
  date: string;
  submittedAt: string;
  manualSubmit?: boolean;
}

const SUBMITTED_GAMES_KEY = "submittedGames";
const SUBMITTED_GAMES_SCHEMA_KEY = "submittedGamesSchemaVersion";
const SUBMITTED_GAMES_SCHEMA_VERSION = "2";

function toEpochMs(startTime?: string): number {
  if (!startTime) return Number.MAX_SAFE_INTEGER;
  const millis = Date.parse(startTime);
  if (!Number.isNaN(millis)) return millis;
  return Number.MAX_SAFE_INTEGER;
}

export default function SubmittedGamesPage() {
  const [games, setGames] = useState<SubmittedGame[]>([]);

  const clearSubmittedGames = (): void => {
    localStorage.removeItem(SUBMITTED_GAMES_KEY);
    localStorage.setItem(SUBMITTED_GAMES_SCHEMA_KEY, SUBMITTED_GAMES_SCHEMA_VERSION);
    setGames([]);
  };

  useEffect(() => {
    const schema = localStorage.getItem(SUBMITTED_GAMES_SCHEMA_KEY);
    if (schema !== SUBMITTED_GAMES_SCHEMA_VERSION) {
      localStorage.removeItem(SUBMITTED_GAMES_KEY);
      localStorage.setItem(SUBMITTED_GAMES_SCHEMA_KEY, SUBMITTED_GAMES_SCHEMA_VERSION);
      setGames([]);
      return;
    }

    const raw = localStorage.getItem(SUBMITTED_GAMES_KEY);
    if (!raw) {
      setGames([]);
      return;
    }
    try {
      const parsed = JSON.parse(raw) as SubmittedGame[];
      const clean = Array.isArray(parsed)
        ? parsed.filter((item) => item && item.manualSubmit === true)
        : [];
      setGames(clean);
    } catch {
      setGames([]);
    }
  }, []);

  const sorted = useMemo(() => {
    return [...games].sort((a, b) => toEpochMs(a.startTime) - toEpochMs(b.startTime));
  }, [games]);

  return (
    <main className="selected-page">
      <section className="selected-panel">
        <p className="selected-back">
          <Link href="/games">Back to games</Link>
        </p>
        <h1 className="selected-title">Submitted Games</h1>
        <button type="button" className="selected-clear" onClick={clearSubmittedGames}>
          Clear submitted games
        </button>

        {sorted.length === 0 ? (
          <p className="selected-hint">No submitted games yet.</p>
        ) : (
          <ul className="selected-list">
            {sorted.map((game) => (
              <li
                key={`${game.eventId}:${game.marketId}:${game.date}`}
                className="selected-row"
              >
                <p className="selected-name">{game.eventName}</p>
                <p className="selected-meta">eventId: {game.eventId}</p>
                <p className="selected-meta">marketId: {game.marketId}</p>
                <p className="selected-meta">start: {game.startTime || "unknown"}</p>
                <p className="selected-meta">day: {game.date}</p>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  );
}
