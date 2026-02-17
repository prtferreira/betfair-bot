import React, { useEffect, useMemo, useState } from "react";
import DayTabs from "./DayTabs";

const DAYS_TO_SHOW = 3;

// -----------------------------
// Tipos
// -----------------------------
interface Game {
  id: string;
  marketId?: string;
  league?: string;
  homeTeam: string;
  awayTeam: string;
  startTime?: string;
  homeOdds?: number;
  drawOdds?: number;
  awayOdds?: number;
  over15Odds?: number;
  under15Odds?: number;
  over25Odds?: number;
  under25Odds?: number;
  htHomeOdds?: number;
  htDrawOdds?: number;
  htAwayOdds?: number;
}

type Selection = Record<string, boolean>;

interface SubmitState {
  status: "idle" | "loading" | "success" | "error";
  message: string;
}

interface State {
  loading: boolean;
  error: string | null;
  games: Game[];
}

// -----------------------------
// Utilitários
// -----------------------------
function formatLabel(date: Date): string {
  return date.toLocaleDateString("en-GB", {
    weekday: "short",
    day: "2-digit",
    month: "short",
    year: "numeric"
  });
}

function formatOdds(value?: number | null): string {
  if (value === null || value === undefined) return "N/A";
  const num = Number(value);
  return Number.isNaN(num) ? "N/A" : num.toFixed(2);
}

function buildDayList() {
  const today = new Date();
  return Array.from({ length: DAYS_TO_SHOW }, (_, index) => {
    const date = new Date(today);
    date.setDate(today.getDate() + index);
    const iso = date.toISOString().slice(0, 10);
    const label = index === 0 ? "Today" : index === 1 ? "Tomorrow" : "Day After";
    return { iso, label, fullLabel: formatLabel(date) };
  });
}

// -----------------------------
// Componente
// -----------------------------
export default function TodayOddsPage({ onBack }: { onBack: () => void }) {
  const days = useMemo(buildDayList, []);
  const [selectedDay, setSelectedDay] = useState(days[0].iso);
  const selectedDayInfo = days.find((day) => day.iso === selectedDay) || days[0];
  const showExtraMarkets = Boolean(selectedDayInfo);

  const [selection, setSelection] = useState<Selection>({});
  const [submitState, setSubmitState] = useState<SubmitState>({ status: "idle", message: "" });
  const [state, setState] = useState<State>({ loading: true, error: null, games: [] });

  // -----------------------------
  // Efeito: carregar jogos do dia
  // -----------------------------
  useEffect(() => {
    let active = true;
    setState({ loading: true, error: null, games: [] });
    setSubmitState({ status: "idle", message: "" });

    fetch(`http://localhost:8089/api/betfair/today-odds?date=${encodeURIComponent(selectedDay)}`)
      .then((response) => {
        if (!response.ok) throw new Error("Failed to load games");
        return response.json();
      })
      .then((data: Game[]) => {
        if (!active) return;
        setState({ loading: false, error: null, games: data });
      })
      .catch((error: Error) => {
        if (!active) return;
        setState({ loading: false, error: error.message, games: [] });
      });

    return () => { active = false; };
  }, [selectedDay]);

  // -----------------------------
  // Inicializar seleção
  // -----------------------------
  useEffect(() => {
    setSelection((prev) => {
      const next: Selection = {};
      state.games.forEach((game) => {
        next[game.id] = prev[game.id] || false;
      });
      return next;
    });
  }, [state.games]);

  const updateSelected = (gameId: string, checked: boolean) => {
    setSelection((prev) => ({ ...prev, [gameId]: checked }));
  };

  const saveSelectedMatchIds = () => {
    const ids = state.games
      .filter((game) => selection[game.id])
      .map((game) => game.marketId)
      .filter(Boolean);

    if (ids.length === 0) {
      setSubmitState({ status: "error", message: "Select at least one game before submitting." });
      return;
    }

    setSubmitState({ status: "loading", message: "Saving followed games..." });

    fetch("http://localhost:8089/api/betfair/followed-games", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ date: selectedDay, marketIds: ids })
    })
      .then((response) =>
        response.json().then((data) => {
          if (!response.ok || data.status !== "OK") {
            throw new Error(data.message || "Failed to save followed games");
          }
          return data;
        })
      )
      .then((data) => {
        setSubmitState({
          status: "success",
          message: `Saved ${data.savedCount} match IDs to ${data.file}`
        });
      })
      .catch((error: Error) => {
        setSubmitState({
          status: "error",
          message: error.message || "Failed to save followed games"
        });
      });
  };

  // -----------------------------
  // Render
  // -----------------------------
  return (
    <>
      <header className="hero">
        <div>
          <p className="eyebrow">Betfair Trade Simulator</p>
          <h1>Match odds board</h1>
          <p className="subhead">
            Future games scheduled for {selectedDayInfo.fullLabel} with Match Odds (1X2).
          </p>
        </div>
        <div className="hero-card">
          <p className="hero-card__title">Mode</p>
          <p className="hero-card__value">{selectedDayInfo.label}</p>
          <div className="hero-card__actions">
            <button className="action-button action-button--ghost" type="button" onClick={onBack}>
              Back to games
            </button>
          </div>
        </div>
      </header>

      <DayTabs days={days} selectedDay={selectedDay} onSelect={setSelectedDay} />

      <main>
        {state.loading ? (
          <section className="panel">
            <p className="status">Loading games and odds for {selectedDayInfo.label}...</p>
          </section>
        ) : state.error ? (
          <section className="panel panel--error">
            <p className="status">{state.error}</p>
          </section>
        ) : state.games.length === 0 ? (
          <section className="panel">
            <p className="status">No future games scheduled for {selectedDayInfo.label}.</p>
          </section>
        ) : (
          <section className="panel">
            <div className="panel__header">
              <h2>All games on {selectedDayInfo.label}</h2>
              <p className="panel__meta">{state.games.length} fixtures</p>
            </div>
            <div className="panel__actions">
              <button className="action-button" type="button" onClick={saveSelectedMatchIds}>
                Submit followed games
              </button>
              {submitState.status !== "idle" && (
                <p
                  className={`login-status ${
                    submitState.status === "success"
                      ? "login-status--success"
                      : submitState.status === "loading"
                      ? "login-status--loading"
                      : "login-status--error"
                  }`}
                >
                  {submitState.message}
                </p>
              )}
            </div>
            <div className="odds-grid">
              {state.games.map((game) => (
                <article key={game.id} className="odds-card">
                  <div className="odds-card__details">
                    <p className="game-card__league">{game.league || "-"}</p>
                    <h3 className="game-card__match">{game.homeTeam} vs {game.awayTeam}</h3>
                    <p className="game-card__time">{game.startTime || "-"}</p>
                  </div>
                  <div className="odds-card__market">
                    <div className="odds-line">
                      <label className="checkbox checkbox--compact">
                        <input
                          type="checkbox"
                          checked={selection[game.id] || false}
                          onChange={(e) => updateSelected(game.id, e.target.checked)}
                        />
                        Follow
                      </label>
                      <div className="market-group">
                        <div className="market-group__header">1X2</div>
                        <div className="market-group__row">
                          <div className="odds-pill odds-pill--compact">
                            <span className="odds-pill__team">Home</span>
                            <span className="odds-pill__value">{formatOdds(game.homeOdds)}</span>
                          </div>
                          <div className="odds-pill odds-pill--draw odds-pill--compact">
                            <span className="odds-pill__team">Draw</span>
                            <span className="odds-pill__value">{formatOdds(game.drawOdds)}</span>
                          </div>
                          <div className="odds-pill odds-pill--compact">
                            <span className="odds-pill__team">Away</span>
                            <span className="odds-pill__value">{formatOdds(game.awayOdds)}</span>
                          </div>
                        </div>
                      </div>

                      {showExtraMarkets && (
                        <>
                          {/* O/U 1.5 */}
                          <div className="market-group">
                            <div className="market-group__header">O/U 1.5</div>
                            <div className="market-group__row">
                              <div className="odds-pill odds-pill--compact">
                                <span className="odds-pill__team">Over</span>
                                <span className="odds-pill__value">{formatOdds(game.over15Odds)}</span>
                              </div>
                              <div className="odds-pill odds-pill--compact">
                                <span className="odds-pill__team">Under</span>
                                <span className="odds-pill__value">{formatOdds(game.under15Odds)}</span>
                              </div>
                            </div>
                          </div>
                        </>
                      )}
                    </div>
                  </div>
                </article>
              ))}
            </div>
          </section>
        )}
      </main>
    </>
  );
}

