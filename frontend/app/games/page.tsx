"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import "./games.css";

type TabKey = "today" | "tomorrow" | "afterTomorrow";

interface Game {
  id: string;
  sport?: string;
  league?: string;
  homeTeam?: string;
  awayTeam?: string;
  startTime?: string;
  marketId?: string;
  inPlay?: boolean;
  homeOdds?: number;
  drawOdds?: number;
  awayOdds?: number;
}

interface DayState {
  loading: boolean;
  error: string | null;
  games: Game[];
}

interface BetfairStatus {
  appKeyPresent?: string;
  sessionTokenPresent?: string;
}

interface SubmittedGame {
  eventId: string;
  marketId: string;
  eventName: string;
  startTime: string;
  dateTab: TabKey;
  date: string;
  submittedAt: string;
  manualSubmit: boolean;
}

const SUBMITTED_GAMES_KEY = "submittedGames";
const SUBMITTED_GAMES_SCHEMA_KEY = "submittedGamesSchemaVersion";
const SUBMITTED_GAMES_SCHEMA_VERSION = "2";

const TAB_LABEL: Record<TabKey, string> = {
  today: "Today",
  tomorrow: "Tomorrow",
  afterTomorrow: "After Tomorrow",
};

const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL?.trim() || "http://localhost:8089";

function formatLocalDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function addDays(base: Date, days: number): Date {
  const copy = new Date(base);
  copy.setDate(copy.getDate() + days);
  return copy;
}

function isSoccerGame(game: Game): boolean {
  if (!game.sport) return true;
  const normalized = game.sport.trim().toLowerCase();
  return normalized === "soccer" || normalized === "football";
}

function eventName(game: Game): string {
  const home = game.homeTeam?.trim();
  const away = game.awayTeam?.trim();
  if (home && away) {
    return `${home} vs ${away}`;
  }
  return game.league?.trim() || "Unknown Event";
}

function toEpochMs(startTime?: string): number {
  if (!startTime) return Number.MAX_SAFE_INTEGER;
  const millis = Date.parse(startTime);
  if (!Number.isNaN(millis)) return millis;
  return Number.MAX_SAFE_INTEGER;
}

function compareByStartAndName(a: Game, b: Game): number {
  const delta = toEpochMs(a.startTime) - toEpochMs(b.startTime);
  if (delta !== 0) return delta;
  return eventName(a).localeCompare(eventName(b));
}

function submittedGameKey(game: SubmittedGame): string {
  return `${game.eventId}|${game.marketId}|${game.date}`;
}

export default function GamesPage() {
  const [activeTab, setActiveTab] = useState<TabKey>("today");
  const [isConnecting, setIsConnecting] = useState(false);
  const [authMessage, setAuthMessage] = useState<string | null>(null);
  const [sessionReady, setSessionReady] = useState<boolean | null>(null);
  const [dayData, setDayData] = useState<Record<TabKey, DayState>>({
    today: { loading: true, error: null, games: [] },
    tomorrow: { loading: true, error: null, games: [] },
    afterTomorrow: { loading: true, error: null, games: [] },
  });
  const [selectedIds, setSelectedIds] = useState<Record<TabKey, Set<string>>>({
    today: new Set(),
    tomorrow: new Set(),
    afterTomorrow: new Set(),
  });
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitMessage, setSubmitMessage] = useState<string | null>(null);

  const datesByTab = useMemo(() => {
    const now = new Date();
    return {
      today: formatLocalDate(now),
      tomorrow: formatLocalDate(addDays(now, 1)),
      afterTomorrow: formatLocalDate(addDays(now, 2)),
    };
  }, []);

  const loadGames = async (): Promise<void> => {
    const tabs: TabKey[] = ["today", "tomorrow", "afterTomorrow"];
    await Promise.all(
      tabs.map(async (tab) => {
        const date = datesByTab[tab];
        try {
          const response = await fetch(
            `${API_BASE}/api/betfair/football?date=${encodeURIComponent(date)}`
          );
          if (!response.ok) {
            throw new Error(
              `Failed to load ${TAB_LABEL[tab]} games (${response.status})`
            );
          }
          const games = (await response.json()) as Game[];
          const soccerGames = games
            .filter(isSoccerGame)
            .sort(compareByStartAndName);
          setDayData((prev) => ({
            ...prev,
            [tab]: { loading: false, error: null, games: soccerGames },
          }));
          setSelectedIds((prev) => ({
            ...prev,
            [tab]: new Set(
              [...(prev[tab] || new Set<string>())].filter((id) =>
                soccerGames.some((game) => game.id === id)
              )
            ),
          }));
        } catch (error) {
          const message =
            error instanceof Error
              ? error.message
              : `Failed to load ${TAB_LABEL[tab]} games`;
          setDayData((prev) => ({
            ...prev,
            [tab]: { loading: false, error: message, games: [] },
          }));
        }
      })
    );
  };

  useEffect(() => {
    const tabs: TabKey[] = ["today", "tomorrow", "afterTomorrow"];
    setDayData({
      today: { loading: true, error: null, games: [] },
      tomorrow: { loading: true, error: null, games: [] },
      afterTomorrow: { loading: true, error: null, games: [] },
    });
    setSelectedIds({
      today: new Set(),
      tomorrow: new Set(),
      afterTomorrow: new Set(),
    });

    const init = async (): Promise<void> => {
      try {
        const statusResponse = await fetch(`${API_BASE}/api/betfair/status`);
        const status = (await statusResponse.json()) as BetfairStatus;
        const hasSession = status.sessionTokenPresent === "true";
        setSessionReady(hasSession);
        if (!hasSession) {
          setAuthMessage(
            "Betfair session expired after backend restart. Click Connect Betfair."
          );
        } else {
          setAuthMessage(null);
        }
      } catch {
        setSessionReady(null);
        setAuthMessage(
          `Cannot reach backend at ${API_BASE}. Verify backend is running and CORS is enabled for http://localhost:5173.`
        );
      }

      await loadGames();
    };

    void init();
  }, [datesByTab]);

  const connectBetfair = async (): Promise<void> => {
    setIsConnecting(true);
    setAuthMessage(null);
    try {
      const response = await fetch(`${API_BASE}/api/betfair/login`, {
        method: "POST",
      });
      const payload = (await response.json()) as { status?: string; message?: string };
      if (payload.status !== "SUCCESS") {
        throw new Error(payload.message || "Login failed");
      }
      setSessionReady(true);
      setAuthMessage("Connected. Reloading games...");
      await loadGames();
    } catch (error) {
      const message = error instanceof Error ? error.message : "Login failed";
      setSessionReady(false);
      setAuthMessage(`Betfair login failed: ${message}`);
    } finally {
      setIsConnecting(false);
    }
  };

  const current = dayData[activeTab];
  const selectedForTab = selectedIds[activeTab] ?? new Set<string>();

  const allSelected =
    current.games.length > 0 &&
    current.games.every((game) => selectedForTab.has(game.id));

  const toggleGame = (tab: TabKey, eventId: string): void => {
    setSelectedIds((prev) => {
      const next = new Set(prev[tab]);
      if (next.has(eventId)) {
        next.delete(eventId);
      } else {
        next.add(eventId);
      }
      return { ...prev, [tab]: next };
    });
  };

  const selectAllForTab = (tab: TabKey): void => {
    const ids = dayData[tab].games.map((game) => game.id);
    setSelectedIds((prev) => ({ ...prev, [tab]: new Set(ids) }));
  };

  const clearAllForTab = (tab: TabKey): void => {
    setSelectedIds((prev) => ({ ...prev, [tab]: new Set() }));
  };

  const collectSelectedGames = (): SubmittedGame[] => {
    const nowIso = new Date().toISOString();
    const selected: SubmittedGame[] = [];
    const tab = activeTab;
    const date = datesByTab[tab];
    const setForTab = selectedIds[tab] ?? new Set<string>();
    dayData[tab].games.forEach((game) => {
      if (!setForTab.has(game.id) || !game.marketId) {
        return;
      }
      selected.push({
        eventId: game.id,
        marketId: game.marketId,
        eventName: eventName(game),
        startTime: game.startTime || "",
        dateTab: tab,
        date,
        submittedAt: nowIso,
        manualSubmit: true,
      });
    });
    return selected;
  };

  const submitSelectedGames = async (): Promise<void> => {
    const selectedGames = collectSelectedGames();
    if (selectedGames.length === 0) {
      setSubmitMessage("No selected games with marketId to submit.");
      return;
    }

    setIsSubmitting(true);
    setSubmitMessage(null);

    try {
      const entries = selectedGames.map(
        (game) => `${game.marketId},${game.startTime},${game.eventName}`
      );
      const response = await fetch(`${API_BASE}/api/betfair/selected-games`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          date: datesByTab[activeTab],
          entries,
        }),
      });
      const payload = (await response.json()) as {
        status?: string;
        message?: string;
        savedCount?: number;
      };

      if (payload.status !== "OK") {
        throw new Error(payload.message || "Submit failed");
      }

      localStorage.setItem(SUBMITTED_GAMES_SCHEMA_KEY, SUBMITTED_GAMES_SCHEMA_VERSION);
      let existingGames: SubmittedGame[] = [];
      try {
        const raw = localStorage.getItem(SUBMITTED_GAMES_KEY);
        if (raw) {
          const parsed = JSON.parse(raw) as SubmittedGame[];
          existingGames = Array.isArray(parsed) ? parsed : [];
        }
      } catch {
        existingGames = [];
      }
      const mergedByKey = new Map<string, SubmittedGame>();
      for (const game of existingGames.filter((item) => item?.manualSubmit === true)) {
        mergedByKey.set(submittedGameKey(game), game);
      }
      for (const game of selectedGames) {
        mergedByKey.set(submittedGameKey(game), game);
      }
      localStorage.setItem(
        SUBMITTED_GAMES_KEY,
        JSON.stringify(Array.from(mergedByKey.values()))
      );
      setSubmitMessage(
        `Submitted ${payload.savedCount ?? selectedGames.length} games.`
      );
    } catch (error) {
      const message = error instanceof Error ? error.message : "Submit failed";
      setSubmitMessage(`Submit failed: ${message}`);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className="games-page">
      <header className="games-header">
        <Link href="/" className="back-link">
          Back
        </Link>
        <h1 className="games-title">Betfair Soccer Events</h1>
        <p className="games-subtitle">
          Select games by day and view event name + eventId.
        </p>
      </header>

      <section className="games-panel">
        {authMessage ? (
          <div className="status-banner">
            <span>{authMessage}</span>
            <button
              type="button"
              onClick={connectBetfair}
              disabled={isConnecting}
              className="connect-button"
            >
              {isConnecting ? "Connecting..." : "Connect Betfair"}
            </button>
          </div>
        ) : null}

        <div className="tab-row">
        {(["today", "tomorrow", "afterTomorrow"] as TabKey[]).map((tab) => (
          <button
            key={tab}
            type="button"
            onClick={() => setActiveTab(tab)}
            className={`tab-button ${tab === activeTab ? "tab-button--active" : ""}`}
          >
            {TAB_LABEL[tab]}
          </button>
        ))}
        </div>

        <p className="date-line">
          Date: <strong>{datesByTab[activeTab]}</strong>
        </p>

        <div className="action-row">
          <button
            type="button"
            onClick={() => selectAllForTab(activeTab)}
            className="action-button"
          >
            Select all
          </button>
          <button
            type="button"
            onClick={() => clearAllForTab(activeTab)}
            className="action-button action-button--ghost"
          >
            Clear all
          </button>
          <button
            type="button"
            onClick={submitSelectedGames}
            disabled={isSubmitting}
            className="action-button"
          >
            {isSubmitting ? "Submitting..." : "Submit selected games"}
          </button>
          <Link href="/games/selected" className="market-link">
            View submitted games
          </Link>
        </div>

        {submitMessage ? <p className="hint">{submitMessage}</p> : null}

        {sessionReady === false ? (
          <p className="hint">
            Session token is missing, so Betfair may return no events until you reconnect.
          </p>
        ) : null}

        {current.loading ? <p className="hint">Loading...</p> : null}
        {current.error ? <p className="hint hint--error">{current.error}</p> : null}
        {!current.loading && !current.error && current.games.length === 0 ? (
          <p className="hint">
            No events found for this day. If unexpected, reconnect Betfair and retry.
          </p>
        ) : null}

        {!current.loading && !current.error && current.games.length > 0 ? (
          <>
            <p className="selected-line">
              Selected: {selectedForTab.size} / {current.games.length}
              {allSelected ? " (all selected)" : ""}
            </p>
            <ul className="event-list">
            {current.games.map((game) => (
                <li key={game.id} className="event-row">
                  <div className="event-label">
                  <input
                    type="checkbox"
                    checked={selectedForTab.has(game.id)}
                    onChange={() => toggleGame(activeTab, game.id)}
                  />
                    <span className="event-main">{eventName(game)}</span>
                    <span className="event-id">
                      eventId: {game.id}
                      {game.inPlay ? " | InPlay" : ""}
                    </span>
                    <span className="event-time">
                      start: {game.startTime || "unknown"}
                    </span>
                    {game.inPlay ? (
                      <span className="event-live">InPlay</span>
                    ) : toEpochMs(game.startTime) <= Date.now() ? (
                      <span className="event-live">Started / In-Play</span>
                    ) : null}
                    <div className="event-market-box">
                      <span className="event-market-label">MATCH_ODDS</span>
                      <span className="event-market-id">
                        {game.marketId
                          ? `marketId: ${game.marketId}`
                          : "marketId: not found"}
                      </span>
                      <span className="event-market-odds">
                        H: {game.homeOdds ?? "-"} | D: {game.drawOdds ?? "-"} | A:{" "}
                        {game.awayOdds ?? "-"}
                      </span>
                    </div>
                    <div className="event-actions">
                      <Link
                        href={`/games/${encodeURIComponent(game.id)}/markets?name=${encodeURIComponent(eventName(game))}`}
                        className="market-link"
                      >
                        View market IDs
                      </Link>
                    </div>
                  </div>
                </li>
            ))}
            </ul>
          </>
        ) : null}
      </section>
    </main>
  );
}
