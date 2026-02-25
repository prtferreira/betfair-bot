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
  const [dateFilter, setDateFilter] = useState<string>("all");

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

  const availableDates = useMemo(() => {
    const unique = new Set<string>();
    for (const game of sorted) {
      if (game.date) {
        unique.add(game.date);
      }
    }
    return Array.from(unique).sort((a, b) => a.localeCompare(b));
  }, [sorted]);

  useEffect(() => {
    if (dateFilter === "all") {
      return;
    }
    if (!availableDates.includes(dateFilter)) {
      setDateFilter("all");
    }
  }, [availableDates, dateFilter]);

  const filteredGames = useMemo(() => {
    if (dateFilter === "all") {
      return sorted;
    }
    return sorted.filter((game) => game.date === dateFilter);
  }, [sorted, dateFilter]);

  const downloadReport = (): void => {
    if (filteredGames.length === 0) {
      return;
    }
    const generatedAt = new Date().toISOString();
    const lines: string[] = ["date,game"];
    for (const game of filteredGames) {
      const csvLine = [game.date || "", game.eventName || ""]
        .map((value) => `"${String(value ?? "").replaceAll("\"", "\"\"")}"`)
        .join(",");
      lines.push(csvLine);
    }
    const blob = new Blob([lines.join("\n")], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    const stamp = generatedAt.replaceAll(":", "-");
    const suffix = dateFilter === "all" ? "all-dates" : dateFilter;
    link.href = url;
    link.download = `submitted-games-${suffix}-${stamp}.txt`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  return (
    <main className="selected-page">
      <section className="selected-panel">
        <p className="selected-back">
          <Link href="/games">Back to games</Link>
        </p>
        <h1 className="selected-title">Submitted Games</h1>
        <div className="selected-actions">
          <label className="selected-filter">
            <span>Filter by date</span>
            <select
              value={dateFilter}
              onChange={(event) => setDateFilter(event.currentTarget.value)}
            >
              <option value="all">All dates</option>
              {availableDates.map((date) => (
                <option key={date} value={date}>
                  {date}
                </option>
              ))}
            </select>
          </label>
          <button
            type="button"
            className="selected-report"
            onClick={downloadReport}
            disabled={filteredGames.length === 0}
          >
            Generate report (.txt)
          </button>
          <button type="button" className="selected-clear" onClick={clearSubmittedGames}>
            Clear submitted games
          </button>
        </div>

        {filteredGames.length === 0 ? (
          <p className="selected-hint">No submitted games yet.</p>
        ) : (
          <ul className="selected-list">
            {filteredGames.map((game) => (
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
