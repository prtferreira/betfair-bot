import React, { useEffect, useMemo, useState } from "react";

function formatOdds(value) {
  if (value === null || value === undefined) {
    return null;
  }
  const numeric = Number(value);
  return Number.isNaN(numeric) ? null : numeric;
}

function formatLabel(date) {
  return date.toLocaleDateString("en-GB", {
    weekday: "short",
    day: "2-digit",
    month: "short",
    year: "numeric"
  });
}

function buildDates() {
  const today = new Date();
  return Array.from({ length: 3 }, (_, index) => {
    const date = new Date(today);
    date.setDate(today.getDate() + index);
    const iso = [
      date.getFullYear(),
      String(date.getMonth() + 1).padStart(2, "0"),
      String(date.getDate()).padStart(2, "0")
    ].join("-");
    return {
      iso,
      label: formatLabel(date)
    };
  });
}

export default function BalancedMatchesPage({ onBack, onStatus }) {
  const days = useMemo(buildDates, []);
  const todayIso = useMemo(() => {
    const today = new Date();
    return [
      today.getFullYear(),
      String(today.getMonth() + 1).padStart(2, "0"),
      String(today.getDate()).padStart(2, "0")
    ].join("-");
  }, []);
  const [selection, setSelection] = useState({});
  const [submitState, setSubmitState] = useState({
    status: "idle",
    message: ""
  });
  const [state, setState] = useState({
    loading: true,
    error: null,
    games: []
  });

  useEffect(() => {
    let active = true;
    setState({ loading: true, error: null, games: [] });
    setSubmitState({ status: "idle", message: "" });

    Promise.all(
      days.map((day) =>
        fetch(`/api/betfair/today-odds?date=${encodeURIComponent(day.iso)}`)
          .then((response) => {
            if (!response.ok) {
              throw new Error("Failed to load games");
            }
            return response.json();
          })
          .then((games) =>
            games.map((game) => ({
              ...game,
              dayLabel: day.label
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
          if (home === null || away === null) {
            return false;
          }
          const favourite = Math.min(home, away);
          const underdog = Math.max(home, away);
          return favourite > 1.8 && underdog < 4;
        });
        const sorted = filtered.sort((a, b) => {
          const aTime = Date.parse(a.startTime || "");
          const bTime = Date.parse(b.startTime || "");
          if (Number.isNaN(aTime) && Number.isNaN(bTime)) return 0;
          if (Number.isNaN(aTime)) return 1;
          if (Number.isNaN(bTime)) return -1;
          return aTime - bTime;
        });
        setState({ loading: false, error: null, games: sorted });
      })
      .catch((error) => {
        if (!active) return;
        setState({ loading: false, error: error.message, games: [] });
      });

    return () => {
      active = false;
    };
  }, [days]);

  useEffect(() => {
    setSelection((prev) => {
      const next = {};
      state.games.forEach((game) => {
        next[game.id] = prev[game.id] || false;
      });
      return next;
    });
  }, [state.games]);

  const updateSelected = (gameId, checked) => {
    setSelection((prev) => ({
      ...prev,
      [gameId]: checked
    }));
  };

  const submitSelectedGames = () => {
    const entries = state.games
      .filter((game) => selection[game.id])
      .map((game) => {
        const teams = `${game.homeTeam} vs ${game.awayTeam}`;
        const startTime = game.startTime || "";
        return `${game.marketId},${startTime},${teams}`;
      })
      .filter((line) => line.split(",")[0]);
    if (entries.length === 0) {
      setSubmitState({
        status: "error",
        message: "Select at least one game before submitting."
      });
      return;
    }

    setSubmitState({ status: "loading", message: "Saving balanced games..." });
    fetch("/api/betfair/balanced-games", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ date: todayIso, entries })
    })
      .then((response) =>
        response.json().then((data) => {
          if (!response.ok || data.status !== "OK") {
            throw new Error(data.message || "Failed to save balanced games");
          }
          return data;
        })
      )
      .then((data) => {
        setSubmitState({
          status: "success",
          message: `Saved ${data.savedCount} games to ${data.file}`
        });
      })
      .catch((error) => {
        setSubmitState({
          status: "error",
          message: error.message || "Failed to save balanced games"
        });
      });
  };

  const selectAllGames = (checked) => {
    setSelection(() => {
      const next = {};
      state.games.forEach((game) => {
        next[game.id] = checked;
      });
      return next;
    });
  };

  return (
    <>
      <header className="hero">
        <div>
          <p className="eyebrow">Betfair Trade Simulator</p>
          <h1>Balanced Matches</h1>
          <p className="subhead">
            Next 3 days, favourite &gt; 1.8 and underdog &lt; 4.0 (1X2).
          </p>
        </div>
        <div className="hero-card">
          <p className="hero-card__title">Window</p>
          <p className="hero-card__value">3 days</p>
          <div className="hero-card__actions">
            <button className="action-button action-button--ghost" type="button" onClick={onBack}>
              Back to games
            </button>
            {onStatus && (
              <button className="action-button action-button--ghost" type="button" onClick={onStatus}>
                View in-play status
              </button>
            )}
          </div>
        </div>
      </header>

      <main>
        {state.loading ? (
          <section className="panel">
            <p className="status">Loading balanced matches...</p>
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
              <h2>Balanced games</h2>
              <p className="panel__meta">{state.games.length} fixtures</p>
            </div>
            <div className="panel__actions">
              <button
                className="action-button action-button--ghost"
                type="button"
                onClick={() => selectAllGames(true)}
              >
                Select all
              </button>
              <button
                className="action-button action-button--ghost"
                type="button"
                onClick={() => selectAllGames(false)}
              >
                Clear all
              </button>
              <button
                className="action-button"
                type="button"
                onClick={submitSelectedGames}
              >
                Submit balanced games
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
                                    onChange={(event) =>
                                      updateSelected(game.id, event.target.checked)
                                    }
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
