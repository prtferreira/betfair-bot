"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import "./games.css";

type TabKey = "today" | "tomorrow" | "afterTomorrow";
type PhaseFilter = "all" | "inPlay" | "scheduled";

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
  fullTime00Odds?: number;
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
const GAMES_FILTERS_SESSION_KEY = "gamesFiltersSessionV1";

const TAB_LABEL: Record<TabKey, string> = {
  today: "Today",
  tomorrow: "Tomorrow",
  afterTomorrow: "After Tomorrow",
};

const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL?.trim() || "http://localhost:8089";
const ODDS_MIN = 1.01;
const ODDS_MAX = 100;
const ODDS_STEP = 0.01;
const SLOW_END = 5;
const SLOW_PORTION = 0.7;
const SLIDER_MIN = 0;
const SLIDER_MAX = 1000;

interface OddsRange {
  min: number;
  max: number;
}

interface MatchOddsFilter {
  home: OddsRange;
  draw: OddsRange;
  away: OddsRange;
  fullTime00: OddsRange;
}

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

function isInPlayOrStarted(game: Game): boolean {
  if (game.inPlay) {
    return true;
  }
  return toEpochMs(game.startTime) <= Date.now();
}

function submittedGameKey(game: SubmittedGame): string {
  return `${game.eventId}|${game.marketId}|${game.date}`;
}

function defaultOddsRange(): OddsRange {
  return { min: ODDS_MIN, max: ODDS_MAX };
}

function defaultFilter(): MatchOddsFilter {
  return {
    home: defaultOddsRange(),
    draw: defaultOddsRange(),
    away: defaultOddsRange(),
    fullTime00: defaultOddsRange(),
  };
}

function isDefaultRange(range: OddsRange): boolean {
  return range.min <= ODDS_MIN && range.max >= ODDS_MAX;
}

function oddsWithinRange(odds: number | undefined, range: OddsRange): boolean {
  if (odds == null || Number.isNaN(odds)) {
    return isDefaultRange(range);
  }
  return odds >= range.min && odds <= range.max;
}

function clampOdds(value: number): number {
  if (Number.isNaN(value)) {
    return ODDS_MIN;
  }
  return Math.min(ODDS_MAX, Math.max(ODDS_MIN, value));
}

function toPercent(value: number): number {
  return ((value - SLIDER_MIN) / (SLIDER_MAX - SLIDER_MIN)) * 100;
}

function sliderToOdds(sliderValue: number): number {
  const clamped = Math.min(SLIDER_MAX, Math.max(SLIDER_MIN, sliderValue));
  const t = (clamped - SLIDER_MIN) / (SLIDER_MAX - SLIDER_MIN);
  if (t <= SLOW_PORTION) {
    const local = t / SLOW_PORTION;
    return Number((ODDS_MIN + (SLOW_END - ODDS_MIN) * local).toFixed(2));
  }
  const local = (t - SLOW_PORTION) / (1 - SLOW_PORTION);
  return Number((SLOW_END + (ODDS_MAX - SLOW_END) * local).toFixed(2));
}

function oddsToSlider(oddsValue: number): number {
  const odds = clampOdds(oddsValue);
  if (odds <= SLOW_END) {
    const local = (odds - ODDS_MIN) / (SLOW_END - ODDS_MIN);
    return Math.round(SLIDER_MIN + local * SLOW_PORTION * (SLIDER_MAX - SLIDER_MIN));
  }
  const local = (odds - SLOW_END) / (ODDS_MAX - SLOW_END);
  return Math.round(
    SLIDER_MIN + (SLOW_PORTION + local * (1 - SLOW_PORTION)) * (SLIDER_MAX - SLIDER_MIN)
  );
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
  const [matchOddsFilter, setMatchOddsFilter] = useState<MatchOddsFilter>(
    defaultFilter()
  );
  const [phaseFilter, setPhaseFilter] = useState<PhaseFilter>("all");

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

  useEffect(() => {
    try {
      const raw = sessionStorage.getItem(GAMES_FILTERS_SESSION_KEY);
      if (!raw) {
        return;
      }
      const parsed = JSON.parse(raw) as {
        phaseFilter?: PhaseFilter;
        matchOddsFilter?: Partial<MatchOddsFilter>;
      };
      if (
        parsed.phaseFilter === "all" ||
        parsed.phaseFilter === "inPlay" ||
        parsed.phaseFilter === "scheduled"
      ) {
        setPhaseFilter(parsed.phaseFilter);
      }
      if (parsed.matchOddsFilter) {
        setMatchOddsFilter((prev) => ({
          home: {
            min: clampOdds(parsed.matchOddsFilter?.home?.min ?? prev.home.min),
            max: clampOdds(parsed.matchOddsFilter?.home?.max ?? prev.home.max),
          },
          draw: {
            min: clampOdds(parsed.matchOddsFilter?.draw?.min ?? prev.draw.min),
            max: clampOdds(parsed.matchOddsFilter?.draw?.max ?? prev.draw.max),
          },
          away: {
            min: clampOdds(parsed.matchOddsFilter?.away?.min ?? prev.away.min),
            max: clampOdds(parsed.matchOddsFilter?.away?.max ?? prev.away.max),
          },
          fullTime00: {
            min: clampOdds(parsed.matchOddsFilter?.fullTime00?.min ?? prev.fullTime00.min),
            max: clampOdds(parsed.matchOddsFilter?.fullTime00?.max ?? prev.fullTime00.max),
          },
        }));
      }
    } catch {
      // Ignore malformed persisted filters.
    }
  }, []);

  useEffect(() => {
    try {
      sessionStorage.setItem(
        GAMES_FILTERS_SESSION_KEY,
        JSON.stringify({
          phaseFilter,
          matchOddsFilter,
        })
      );
    } catch {
      // Ignore storage failures.
    }
  }, [phaseFilter, matchOddsFilter]);

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
  const filteredGames = useMemo(
    () =>
      current.games.filter(
        (game) =>
          (phaseFilter === "all" ||
            (phaseFilter === "inPlay" && isInPlayOrStarted(game)) ||
            (phaseFilter === "scheduled" && !isInPlayOrStarted(game))) &&
          oddsWithinRange(game.homeOdds, matchOddsFilter.home) &&
          oddsWithinRange(game.drawOdds, matchOddsFilter.draw) &&
          oddsWithinRange(game.awayOdds, matchOddsFilter.away) &&
          oddsWithinRange(game.fullTime00Odds, matchOddsFilter.fullTime00)
      ),
    [current.games, matchOddsFilter, phaseFilter]
  );
  const selectedVisibleCount = filteredGames.filter((game) =>
    selectedForTab.has(game.id)
  ).length;

  const allSelected =
    filteredGames.length > 0 &&
    filteredGames.every((game) => selectedForTab.has(game.id));

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
    const ids = filteredGames.map((game) => game.id);
    setSelectedIds((prev) => {
      const next = new Set(prev[tab]);
      ids.forEach((id) => next.add(id));
      return { ...prev, [tab]: next };
    });
  };

  const clearAllForTab = (tab: TabKey): void => {
    setSelectedIds((prev) => ({ ...prev, [tab]: new Set() }));
  };

  const updateOddsRange = (
    runner: keyof MatchOddsFilter,
    edge: keyof OddsRange,
    value: number
  ): void => {
    setMatchOddsFilter((prev) => {
      const currentRange = prev[runner];
      const nextValue = clampOdds(value);
      let nextMin = currentRange.min;
      let nextMax = currentRange.max;
      if (edge === "min") {
        nextMin = Math.min(nextValue, currentRange.max);
      } else {
        nextMax = Math.max(nextValue, currentRange.min);
      }
      return {
        ...prev,
        [runner]: {
          min: Number(nextMin.toFixed(2)),
          max: Number(nextMax.toFixed(2)),
        },
      };
    });
  };

  const updateOddsInput = (
    runner: keyof MatchOddsFilter,
    edge: keyof OddsRange,
    rawValue: string
  ): void => {
    if (!rawValue.trim()) {
      return;
    }
    updateOddsRange(runner, edge, Number(rawValue));
  };

  const resetFilters = (): void => {
    setMatchOddsFilter(defaultFilter());
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

        <div className="filter-panel">
          <div className="filter-head">
            <p className="filter-title">MATCH_ODDS filters (1.01 - 10.00)</p>
            <button
              type="button"
              className="filter-reset"
              onClick={resetFilters}
            >
              Reset filters
            </button>
          </div>
          <div className="phase-filter-row">
            <span className="phase-filter-label">Game phase:</span>
            {(
              [
                ["all", "All"],
                ["inPlay", "In-Play"],
                ["scheduled", "Scheduled"],
              ] as [PhaseFilter, string][]
            ).map(([value, label]) => (
              <button
                key={value}
                type="button"
                onClick={() => setPhaseFilter(value)}
                className={`phase-filter-button ${
                  phaseFilter === value ? "phase-filter-button--active" : ""
                }`}
              >
                {label}
              </button>
            ))}
          </div>
          {(
            [
              ["fullTime00", "FT 0-0"],
              ["home", "Home"],
              ["draw", "Draw"],
              ["away", "Away"],
            ] as [keyof MatchOddsFilter, string][]
          ).map(([runnerKey, label]) => (
            <div key={runnerKey} className="filter-row">
              <span className="filter-label">{label}</span>
              <div className="filter-boxes">
                <label className="filter-input-wrap">
                  <span className="filter-input-prefix">@</span>
                  <input
                    type="number"
                    min={ODDS_MIN}
                    max={ODDS_MAX}
                    step={ODDS_STEP}
                    value={matchOddsFilter[runnerKey].min}
                    onChange={(event) =>
                      updateOddsInput(runnerKey, "min", event.currentTarget.value)
                    }
                  />
                </label>
                <span className="filter-to">to</span>
                <label className="filter-input-wrap">
                  <span className="filter-input-prefix">@</span>
                  <input
                    type="number"
                    min={ODDS_MIN}
                    max={ODDS_MAX}
                    step={ODDS_STEP}
                    value={matchOddsFilter[runnerKey].max}
                    onChange={(event) =>
                      updateOddsInput(runnerKey, "max", event.currentTarget.value)
                    }
                  />
                </label>
              </div>
              <div className="filter-sliders filter-sliders--dual">
                {(() => {
                  const minSlider = oddsToSlider(matchOddsFilter[runnerKey].min);
                  const maxSlider = oddsToSlider(matchOddsFilter[runnerKey].max);
                  return (
                    <>
                <div
                  className="dual-track"
                  style={{
                    background: `linear-gradient(to right, #d5dbe0 0%, #d5dbe0 ${toPercent(
                      minSlider
                    )}%, #7bbd92 ${toPercent(
                      minSlider
                    )}%, #7bbd92 ${toPercent(
                      maxSlider
                    )}%, #d5dbe0 ${toPercent(
                      maxSlider
                    )}%, #d5dbe0 100%)`,
                  }}
                />
                <input
                  className="dual-range dual-range--min"
                  type="range"
                  min={SLIDER_MIN}
                  max={SLIDER_MAX}
                  step={1}
                  value={minSlider}
                  onChange={(event) =>
                    updateOddsRange(
                      runnerKey,
                      "min",
                      sliderToOdds(Number(event.currentTarget.value))
                    )
                  }
                />
                <input
                  className="dual-range dual-range--max"
                  type="range"
                  min={SLIDER_MIN}
                  max={SLIDER_MAX}
                  step={1}
                  value={maxSlider}
                  onChange={(event) =>
                    updateOddsRange(
                      runnerKey,
                      "max",
                      sliderToOdds(Number(event.currentTarget.value))
                    )
                  }
                />
                    </>
                  );
                })()}
              </div>
            </div>
          ))}
        </div>

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
          <Link href="/games/analytics" className="market-link">
            Go to Game Analytics
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
              Selected: {selectedVisibleCount} / {filteredGames.length}
              {allSelected ? " (all selected)" : ""}
            </p>
            <ul className="event-list">
            {filteredGames.map((game) => (
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
                      <div className="event-market-board" aria-hidden="true">
                        <div className="event-market-board__head">
                          <span>Home</span>
                          <span>Draw</span>
                          <span>Away</span>
                        </div>
                        <div className="event-market-board__row">
                          <span className="event-market-board__name">Match Odds</span>
                          <span className="event-market-pill">{game.homeOdds ?? "-"}</span>
                          <span className="event-market-pill">{game.drawOdds ?? "-"}</span>
                          <span className="event-market-pill">{game.awayOdds ?? "-"}</span>
                        </div>
                        <div className="event-market-board__row">
                          <span className="event-market-board__name">FT 0-0</span>
                          <span className="event-market-pill event-market-pill--ftwide">
                            {game.fullTime00Odds ?? "-"}
                          </span>
                        </div>
                      </div>
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
