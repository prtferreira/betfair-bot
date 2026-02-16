import React, { useEffect, useMemo, useState } from "react";
import DayTabs from "./DayTabs.jsx";

const DAYS_TO_SHOW = 3;

function formatLabel(date) {
  return date.toLocaleDateString("en-GB", {
    weekday: "short",
    day: "2-digit",
    month: "short",
    year: "numeric"
  });
}

function formatOdds(value) {
  if (value === null || value === undefined) {
    return "N/A";
  }
  return Number(value).toFixed(2);
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

export default function TodayOddsPage({ onBack }) {
  const days = useMemo(buildDayList, []);
  const [selectedDay, setSelectedDay] = useState(days[0].iso);
  const selectedDayInfo = days.find((day) => day.iso === selectedDay) || days[0];
  const showExtraMarkets = Boolean(selectedDayInfo);
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
    setState((prev) => ({ ...prev, loading: true, error: null }));
    setSubmitState({ status: "idle", message: "" });

    fetch(`/api/betfair/today-odds?date=${encodeURIComponent(selectedDay)}`)
      .then((response) => {
        if (!response.ok) {
          throw new Error("Failed to load games");
        }
        return response.json();
      })
      .then((data) => {
        if (!active) return;
        setState({ loading: false, error: null, games: data });
      })
      .catch((error) => {
        if (!active) return;
        setState({ loading: false, error: error.message, games: [] });
      });

    return () => {
      active = false;
    };
  }, [selectedDay]);

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

  const saveSelectedMatchIds = () => {
    const ids = state.games
      .filter((game) => selection[game.id])
      .map((game) => game.marketId)
      .filter(Boolean);
    if (ids.length === 0) {
      setSubmitState({
        status: "error",
        message: "Select at least one game before submitting."
      });
      return;
    }

    setSubmitState({ status: "loading", message: "Saving followed games..." });
    fetch("/api/betfair/followed-games", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
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
      .catch((error) => {
        setSubmitState({
          status: "error",
          message: error.message || "Failed to save followed games"
        });
      });
  };

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

      <DayTabs
        days={days}
        selectedDay={selectedDay}
        onSelect={setSelectedDay}
      />

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
              <button
                className="action-button"
                type="button"
                onClick={saveSelectedMatchIds}
              >
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
                          <div className="market-group">
                            <div className="market-group__header">O/U 2.5</div>
                            <div className="market-group__row">
                              <div className="odds-pill odds-pill--compact">
                                <span className="odds-pill__team">Over</span>
                                <span className="odds-pill__value">{formatOdds(game.over25Odds)}</span>
                              </div>
                              <div className="odds-pill odds-pill--compact">
                                <span className="odds-pill__team">Under</span>
                                <span className="odds-pill__value">{formatOdds(game.under25Odds)}</span>
                              </div>
                            </div>
                          </div>
                          <div className="market-group">
                            <div className="market-group__header">HT Result</div>
                            <div className="market-group__row">
                              <div className="odds-pill odds-pill--compact">
                                <span className="odds-pill__team">Home</span>
                                <span className="odds-pill__value">{formatOdds(game.htHomeOdds)}</span>
                              </div>
                              <div className="odds-pill odds-pill--draw odds-pill--compact">
                                <span className="odds-pill__team">Draw</span>
                                <span className="odds-pill__value">{formatOdds(game.htDrawOdds)}</span>
                              </div>
                              <div className="odds-pill odds-pill--compact">
                                <span className="odds-pill__team">Away</span>
                                <span className="odds-pill__value">{formatOdds(game.htAwayOdds)}</span>
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
