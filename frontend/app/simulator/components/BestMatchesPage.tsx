import React, { useEffect, useMemo, useState } from "react";

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
  dayLabel?: string;
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
function formatOdds(value?: number | null): number | null {
  if (value === null || value === undefined) return null;
  const numeric = Number(value);
  return Number.isNaN(numeric) ? null : numeric;
}

function formatLabel(date: Date): string {
  return date.toLocaleDateString("en-GB", {
    weekday: "short",
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
}

function buildDates(): { iso: string; label: string }[] {
  const today = new Date();
  return Array.from({ length: 3 }, (_, index) => {
    const date = new Date(today);
    date.setDate(today.getDate() + index);
    const iso = [
      date.getFullYear(),
      String(date.getMonth() + 1).padStart(2, "0"),
      String(date.getDate()).padStart(2, "0"),
    ].join("-");
    return { iso, label: formatLabel(date) };
  });
}

// -----------------------------
// Componente
// -----------------------------
export default function BestMatchesPage({ onBack }: { onBack: () => void }) {
  const days = useMemo(buildDates, []);
  const todayIso = useMemo(() => {
    const today = new Date();
    return [
      today.getFullYear(),
      String(today.getMonth() + 1).padStart(2, "0"),
      String(today.getDate()).padStart(2, "0"),
    ].join("-");
  }, []);

  const [selection, setSelection] = useState<Selection>({});
  const [submitState, setSubmitState] = useState<SubmitState>({ status: "idle", message: "" });
  const [state, setState] = useState<State>({ loading: true, error: null, games: [] });

  // -----------------------------
  // Carregar jogos
  // -----------------------------
  useEffect(() => {
    let active = true;
    setState({ loading: true, error: null, games: [] });
    setSubmitState({ status: "idle", message: "" });

    Promise.all(
      days.map((day) =>
        fetch(`http://localhost:8089/api/betfair/today-odds?date=${encodeURIComponent(day.iso)}`)
          .then((res) => {
            if (!res.ok) throw new Error("Failed to load games");
            return res.json();
          })
          .then((games: Game[]) =>
            games.map((game) => ({
              ...game,
              dayLabel: day.label,
            }))
          )
      )
    )
      .then((chunks) => {
        if (!active) return;
        const allGames = chunks.flat();

        const filtered = allGames.filter((game) => {
          const home = formatOdds(game.homeOdds);
          const away = formatOdds(game.awayOdds);
          const draw = formatOdds(game.drawOdds);

          if (home === null || away === null || draw === null) return false;
          const favourite = Math.min(home, away);
          return favourite > 1.7 && favourite < 2.2 && draw > 3.4;
        });

        const sorted = filtered.sort((a, b) => {
          const aTime = Date.parse(a.startTime ?? "");
          const bTime = Date.parse(b.startTime ?? "");
          if (Number.isNaN(aTime) && Number.isNaN(bTime)) return 0;
          if (Number.isNaN(aTime)) return 1;
          if (Number.isNaN(bTime)) return -1;
          return aTime - bTime;
        });

        setState({ loading: false, error: null, games: sorted });
      })
      .catch((error: Error) => {
        if (!active) return;
        setState({ loading: false, error: error.message, games: [] });
      });

    return () => {
      active = false;
    };
  }, [days]);

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

  const submitSelectedGames = () => {
    const entries = state.games
      .filter((game) => selection[game.id])
      .map((game) => {
        const teams = `${game.homeTeam} vs ${game.awayTeam}`;
        const startTime = game.startTime ?? "";
        return `${game.marketId},${startTime},${teams}`;
      })
      .filter((line) => line.split(",")[0]);

    if (entries.length === 0) {
      setSubmitState({ status: "error", message: "Select at least one game before submitting." });
      return;
    }

    setSubmitState({ status: "loading", message: "Saving selected games..." });

    fetch("http://localhost:8089/api/betfair/selected-games", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ date: todayIso, entries }),
    })
      .then((res) =>
        res.json().then((data) => {
          if (!res.ok || data.status !== "OK") {
            throw new Error(data.message || "Failed to save selected games");
          }
          return data;
        })
      )
      .then((data) => {
        setSubmitState({
          status: "success",
          message: `Saved ${data.savedCount} games to ${data.file}`,
        });
      })
      .catch((error: Error) => {
        setSubmitState({ status: "error", message: error.message || "Failed to save selected games" });
      });
  };

  const selectAllGames = (checked: boolean) => {
    setSelection(() => {
      const next: Selection = {};
      state.games.forEach((game) => {
        next[game.id] = checked;
      });
      return next;
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
          <h1>Best Matches</h1>
          <p className="subhead">
            Next 3 days, 1X2 markets where the favourite is &gt; 1.7 and the draw is &gt; 3.4.
          </p>
        </div>
        <div className="hero-card">
          <p className="hero-card__title">Window</p>
          <p className="hero-card__value">3 days</p>
          <div className="hero-card__actions">
            <button className="action-button action-button--ghost" type="button" onClick={onBack}>
              Back to games
            </button>
          </div>
        </div>
      </header>

      <main>
        {state.loading ? (
          <section className="panel">
            <p className="status">Loading best matches...</p>
          </section>
        ) : state.error ? (
          <section className="panel panel--error">
            <p className="status">{state.error}</p>
          </section>
        ) : state.games.length === 0 ? (
          <section className="panel">
            <p className="status">No matches meet the criteria for the next 3 days.</p>
          </section>
        ) : (
          <section className="panel">
            <div className="panel__header">
              <h2>Matches to watch</h2>
              <p className="panel__meta">{state.games.length} fixtures</p>
            </div>
            <div className="panel__actions">
              <button className="action-button action-button--ghost" type="button" onClick={() => selectAllGames(true)}>
                Select all
              </button>
              <button className="action-button action-button--ghost" type="button" onClick={() => selectAllGames(false)}>
                Clear all
              </button>
              <button className="action-button" type="button" onClick={submitSelectedGames}>
                Submit selected games
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
              {days.map((day) => {
                const dayGames = state.games.filter((game) => game.dayLabel === day.label);
                return (
                  <section key={day.iso}>
                    <div className="panel__header">
                      <h3>{day.label}</h3>
                      <p className="panel__meta">{dayGames.length} fixtures</p>
                    </div>
                    {dayGames.length === 0 ? (
                      <p className="status">No matches meet the criteria.</p>
                    ) : (
                      <div className="odds-grid">
                        {dayGames.map((game) => (
                          <article key={`${game.id}-${game.dayLabel}`} className="odds-card">
                            <div className="odds-card__details">
                              <p className="game-card__league">{game.league}</p>
                              <h3 className="game-card__match">
                                {game.homeTeam} vs {game.awayTeam}
                              </h3>
                              <p className="game-card__time">{game.startTime}</p>
                            </div>
                            <div className="odds-card__market">
                              <div className="odds-line">
                                <label className="checkbox checkbox--compact">
                                  <input
                                    type="checkbox"
                                    checked={selection[game.id] || false}
                                    onChange={(e) => updateSelected(game.id, e.target.checked)}
                                  />
                                  Select
                                </label>
                                <div className="market-group">
                                  <div className="market-group__header">1X2</div>
                                  <div className="market-group__row">
                                    <div className="odds-pill odds-pill--compact">
                                      <span className="odds-pill__team">Home</span>
                                      <span className="odds-pill__value">{formatOdds(game.homeOdds)?.toFixed(2) ?? "N/A"}</span>
                                    </div>
                                    <div className="odds-pill odds-pill--draw odds-pill--compact">
                                      <span className="odds-pill__team">Draw</span>
                                      <span className="odds-pill__value">{formatOdds(game.drawOdds)?.toFixed(2) ?? "N/A"}</span>
                                    </div>
                                    <div className="odds-pill odds-pill--compact">
                                      <span className="odds-pill__team">Away</span>
                                      <span className="odds-pill__value">{formatOdds(game.awayOdds)?.toFixed(2) ?? "N/A"}</span>
                                    </div>
                                  </div>
                                </div>
                              </div>
                            </div>
                          </article>
                        ))}
                      </div>
                    )}
                  </section>
                );
              })}
            </div>
          </section>
        )}
      </main>
    </>
  );
}
