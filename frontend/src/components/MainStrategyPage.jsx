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
    return null;
  }
  const numeric = Number(value);
  return Number.isNaN(numeric) ? null : Number(numeric.toFixed(2));
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

function calculateLiability(bet, stake) {
  const stakeValue = Number(stake);
  if (!bet || !Number.isFinite(stakeValue) || stakeValue <= 0) {
    return null;
  }
  if (!Number.isFinite(bet.odds)) {
    return null;
  }
  if (bet.side === "back") {
    return stakeValue;
  }
  const liability = (bet.odds - 1) * stakeValue;
  return liability < 0 ? null : liability;
}

function buildBetLabel(bet) {
  if (!bet) return "No selection";
  const sideLabel = bet.side === "lay" ? "Lay" : "Back";
  return `${sideLabel} ${bet.outcome}`;
}

function resolveBetDay(bet) {
  const source = bet?.settledAt || bet?.createdAt;
  if (!source) return null;
  const date = new Date(source);
  if (Number.isNaN(date.getTime())) return null;
  return date.toISOString().slice(0, 10);
}

function inDateRange(day, from, to) {
  if (!day) return false;
  if (from && day < from) return false;
  if (to && day > to) return false;
  return true;
}

function formatInPlayStatus(bet) {
  const marketStatus = (bet?.marketStatus || "").toUpperCase();
  if (marketStatus === "CLOSED" || bet?.status === "SETTLED") {
    return "Finished";
  }
  if (bet?.inPlay) {
    return "In play";
  }
  return "Scheduled";
}

function formatLiveScore(bet) {
  if (bet?.inferredScore) {
    return bet.inferredScore;
  }
  const home = Number.isInteger(bet?.homeScore) ? bet.homeScore : null;
  const away = Number.isInteger(bet?.awayScore) ? bet.awayScore : null;
  if (home === null || away === null) {
    return "N/A";
  }
  return `${home}-${away}`;
}

export default function MainStrategyPage({ onBack }) {
  const days = useMemo(buildDayList, []);
  const [selectedDay, setSelectedDay] = useState(days[0].iso);
  const selectedDayInfo = days.find((day) => day.iso === selectedDay) || days[0];
  const [activeTab, setActiveTab] = useState("board");
  const [historyFrom, setHistoryFrom] = useState("");
  const [historyTo, setHistoryTo] = useState("");
  const [state, setState] = useState({
    loading: true,
    error: null,
    games: []
  });
  const [strategies, setStrategies] = useState([]);
  const [selectedStrategyIds, setSelectedStrategyIds] = useState([]);
  const [stakes, setStakes] = useState({});
  const [selectedBets, setSelectedBets] = useState({});
  const [statusState, setStatusState] = useState({
    loading: true,
    error: null,
    data: null
  });
  const [submitState, setSubmitState] = useState({
    status: "idle",
    message: ""
  });

  useEffect(() => {
    let active = true;
    setState((prev) => ({ ...prev, loading: true, error: null }));

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
    let active = true;
    fetch("/api/strategies")
      .then((response) => {
        if (!response.ok) {
          throw new Error("Failed to load strategies");
        }
        return response.json();
      })
      .then((data) => {
        if (!active) return;
        setStrategies(data);
        if (data.length > 0) {
          setSelectedStrategyIds((prev) =>
            prev.length > 0 ? prev : [data[0].id]
          );
        }
      })
      .catch(() => {
        if (!active) return;
        setStrategies([]);
      });
    return () => {
      active = false;
    };
  }, []);

  const loadStatus = (silent = false) => {
    if (!silent) {
      setStatusState((prev) => ({ ...prev, loading: true, error: null }));
    }
    fetch("/api/sim/bets/status")
      .then((response) => {
        if (!response.ok) {
          throw new Error("Failed to load simulation status");
        }
        return response.json();
      })
      .then((data) => {
        setStatusState({ loading: false, error: null, data });
      })
      .catch((error) => {
        setStatusState({ loading: false, error: error.message, data: null });
      });
  };

  useEffect(() => {
    loadStatus(true);
  }, []);

  useEffect(() => {
    if (activeTab !== "board") {
      loadStatus(false);
    }
  }, [activeTab]);

  useEffect(() => {
    setStakes((prev) => {
      const next = { ...prev };
      state.games.forEach((game) => {
        if (next[game.id] === undefined) {
          next[game.id] = 10;
        }
      });
      return next;
    });
  }, [state.games]);

  const handleStakeChange = (gameId, value) => {
    setStakes((prev) => ({
      ...prev,
      [gameId]: value
    }));
  };

  const handleSelectBet = (gameId, side, outcome, odds) => {
    if (!Number.isFinite(odds)) {
      return;
    }
    setSelectedBets((prev) => ({
      ...prev,
      [gameId]: { side, outcome, odds }
    }));
  };

  const buildSelectionId = (game, outcome) => {
    if (outcome === "Home") return game.homeSelectionId;
    if (outcome === "Draw") return game.drawSelectionId;
    if (outcome === "Away") return game.awaySelectionId;
    return null;
  };

  const submitBets = () => {
    const bets = state.games
      .map((game) => {
        const bet = selectedBets[game.id];
        if (!bet) return null;
        const selectionId = buildSelectionId(game, bet.outcome);
        if (!selectionId) return null;
        const stake = Number(stakes[game.id]);
        if (!Number.isFinite(stake) || stake <= 0) return null;
        if (!Number.isFinite(bet.odds)) return null;
        return {
          marketId: game.marketId,
          selectionId,
          selectionName: bet.outcome,
          homeTeam: game.homeTeam,
          awayTeam: game.awayTeam,
          side: bet.side,
          odds: bet.odds,
          stake
        };
      })
      .filter(Boolean);

    if (selectedStrategyIds.length === 0) {
      setSubmitState({
        status: "error",
        message: "Select at least one strategy."
      });
      return;
    }

    if (bets.length === 0) {
      setSubmitState({
        status: "error",
        message: "Select at least one bet with stake before submitting."
      });
      return;
    }

    setSubmitState({ status: "loading", message: "Submitting simulation bets..." });
    const requests = selectedStrategyIds.map((strategyId) => {
      const strategy = strategies.find((item) => item.id === strategyId);
      return fetch("/api/sim/bets", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          strategyId,
          strategyName: strategy?.name || strategyId,
          bets
        })
      }).then((response) =>
        response.json().then((data) => {
          if (!response.ok || data.status !== "OK") {
            throw new Error(data.message || "Failed to submit bets");
          }
          return data;
        })
      );
    });

    Promise.all(requests)
      .then((results) => {
        const savedCount = results.reduce(
          (sum, item) => sum + (item.savedCount || 0),
          0
        );
        setSubmitState({
          status: "success",
          message: `Saved ${savedCount} simulation bets.`
        });
        loadStatus(true);
      })
      .catch((error) => {
        setSubmitState({
          status: "error",
          message: error.message || "Failed to submit bets"
        });
      });
  };

  const renderOutcomeRow = (game, outcomeLabel, backOddsValue, layOddsValue) => {
    const backOdds = formatOdds(backOddsValue);
    const layOdds = formatOdds(layOddsValue);
    const selection = selectedBets[game.id];
    const isBackActive =
      selection?.side === "back" && selection?.outcome === outcomeLabel;
    const isLayActive =
      selection?.side === "lay" && selection?.outcome === outcomeLabel;

    return (
      <div className="strategy-odds__row">
        <span className="strategy-odds__team">{outcomeLabel}</span>
        <button
          className={`strategy-odds__button ${
            isBackActive ? "strategy-odds__button--active" : ""
          }`}
          type="button"
          disabled={backOdds === null}
          onClick={() => handleSelectBet(game.id, "back", outcomeLabel, backOdds)}
        >
          {backOdds === null ? "N/A" : backOdds.toFixed(2)}
        </button>
        <button
          className={`strategy-odds__button strategy-odds__button--lay ${
            isLayActive ? "strategy-odds__button--active" : ""
          }`}
          type="button"
          disabled={layOdds === null}
          onClick={() => handleSelectBet(game.id, "lay", outcomeLabel, layOdds)}
        >
          {layOdds === null ? "N/A" : layOdds.toFixed(2)}
        </button>
      </div>
    );
  };

  const statusData = statusState.data;
  const balanceEntries =
    statusData?.balanceByStrategy
      ? Object.entries(statusData.balanceByStrategy)
      : [];
  const valueGlobalWins = Number(statusData?.valueGlobalWins || 0);
  const valueGlobalLosses = Number(statusData?.valueGlobalLosses || 0);
  const valueDailyEntries =
    statusData?.valueDailyWinLosses
      ? Object.entries(statusData.valueDailyWinLosses).sort(([a], [b]) =>
          a < b ? 1 : a > b ? -1 : 0
        )
      : [];
  const allBets = statusData?.bets || [];
  const inPlayBets = allBets.filter(
    (bet) => bet.inPlay && bet.status !== "SETTLED"
  );
  const pendingBets = allBets.filter(
    (bet) => !bet.inPlay && bet.status !== "SETTLED"
  );
  const settledBets = allBets.filter((bet) => bet.status === "SETTLED");
  const selectedDaySettledBets = settledBets.filter(
    (bet) => resolveBetDay(bet) === selectedDay
  );
  const historySettledBets = settledBets.filter((bet) =>
    inDateRange(resolveBetDay(bet), historyFrom, historyTo)
  );

  return (
    <>
      <header className="hero">
        <div>
          <p className="eyebrow">Betfair Trade Simulator</p>
          <h1>Main Strategy</h1>
          <p className="subhead">
            Choose a stake, click back or lay odds, and see your liability per game.
          </p>
        </div>
        <div className="hero-card">
          <p className="hero-card__title">Mode</p>
          <p className="hero-card__value">{selectedDayInfo.label}</p>
          <p className="hero-card__note">{selectedDayInfo.fullLabel}</p>
          <div className="hero-card__actions">
            <button className="action-button action-button--ghost" type="button" onClick={onBack}>
              Back to games
            </button>
          </div>
        </div>
      </header>

      <section className="panel panel--balances">
        <div className="panel__header">
          <h2>Simulation balances</h2>
          <div className="panel__actions">
            <button
              className="action-button action-button--ghost"
              type="button"
              onClick={() => loadStatus(false)}
            >
              Refresh balances
            </button>
          </div>
        </div>
        {statusState.loading ? (
          <p className="status">Loading balances...</p>
        ) : statusState.error ? (
          <p className="status">{statusState.error}</p>
        ) : (
          <div className="balance-grid">
            <div className="balance-card">
              <p className="balance-card__label">Global balance</p>
              <p className="balance-card__value">
                €{Number(statusData?.globalBalance || 0).toFixed(2)}
              </p>
            </div>
            <div className="balance-card">
              <p className="balance-card__label">Per strategy</p>
              {balanceEntries.length === 0 ? (
                <p className="balance-card__meta">No settled bets yet.</p>
              ) : (
                <div className="balance-list">
                  {balanceEntries.map(([name, value]) => (
                    <div key={name} className="balance-list__row">
                      <span>{name}</span>
                      <span>€{Number(value).toFixed(2)}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
            <div className="balance-card">
              <p className="balance-card__label">Value wins/losses (global)</p>
              <p className="balance-card__meta">
                Wins: {valueGlobalWins} | Losses: {valueGlobalLosses}
              </p>
            </div>
            <div className="balance-card">
              <p className="balance-card__label">Value wins/losses per day</p>
              {valueDailyEntries.length === 0 ? (
                <p className="balance-card__meta">No settled value bets yet.</p>
              ) : (
                <div className="balance-list">
                  {valueDailyEntries.map(([day, counts]) => (
                    <div key={day} className="balance-list__row">
                      <span>{day}</span>
                      <span>
                        {Number(counts?.wins || 0)}W / {Number(counts?.losses || 0)}L
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </section>

      <div className="subtabs">
        <button
          className={`subtab ${activeTab === "board" ? "subtab--active" : ""}`}
          type="button"
          onClick={() => setActiveTab("board")}
        >
          Strategy board
        </button>
        <button
          className={`subtab ${activeTab === "pending" ? "subtab--active" : ""}`}
          type="button"
          onClick={() => setActiveTab("pending")}
        >
          Pending games
        </button>
        <button
          className={`subtab ${activeTab === "inplay" ? "subtab--active" : ""}`}
          type="button"
          onClick={() => setActiveTab("inplay")}
        >
          In-play bets
        </button>
        <button
          className={`subtab ${activeTab === "results" ? "subtab--active" : ""}`}
          type="button"
          onClick={() => setActiveTab("results")}
        >
          Results
        </button>
        <button
          className={`subtab ${activeTab === "history" ? "subtab--active" : ""}`}
          type="button"
          onClick={() => setActiveTab("history")}
        >
          History
        </button>
      </div>

      <DayTabs
        days={days}
        selectedDay={selectedDay}
        onSelect={setSelectedDay}
      />

      <main>
        {activeTab === "board" ? (
          state.loading ? (
            <section className="panel">
              <p className="status">Loading games for {selectedDayInfo.label}...</p>
            </section>
          ) : state.error ? (
            <section className="panel panel--error">
              <p className="status">{state.error}</p>
            </section>
          ) : state.games.length === 0 ? (
            <section className="panel">
              <p className="status">No games available for {selectedDayInfo.label}.</p>
            </section>
          ) : (
            <section className="panel">
              <div className="panel__header">
                <h2>Strategy board</h2>
                <p className="panel__meta">{state.games.length} fixtures</p>
              </div>
              <div className="panel__actions panel__actions--stack">
                <div className="strategy-checkboxes">
                  <span className="strategy-checkboxes__label">Strategies</span>
                  {strategies.length === 0 ? (
                    <span className="strategy-checkboxes__empty">No strategies</span>
                  ) : (
                    strategies.map((strategy) => (
                      <label key={strategy.id} className="checkbox checkbox--compact">
                        <input
                          type="checkbox"
                          checked={selectedStrategyIds.includes(strategy.id)}
                          onChange={(event) => {
                            const checked = event.target.checked;
                            setSelectedStrategyIds((prev) =>
                              checked
                                ? [...prev, strategy.id]
                                : prev.filter((id) => id !== strategy.id)
                            );
                          }}
                        />
                        {strategy.name}
                      </label>
                    ))
                  )}
                </div>
                <button className="action-button" type="button" onClick={submitBets}>
                  Submit simulation bets
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
                {state.games.map((game) => {
                  const bet = selectedBets[game.id];
                  const stake = stakes[game.id];
                  const liability = calculateLiability(bet, stake);
                  return (
                    <article key={game.id} className="odds-card odds-card--strategy">
                      <div className="odds-card__details">
                        <p className="game-card__league">{game.league}</p>
                        <h3 className="game-card__match">
                          {game.homeTeam} vs {game.awayTeam}
                        </h3>
                        <p className="game-card__time">{game.startTime}</p>
                        <div className="strategy-summary">
                          <span className="strategy-summary__label">Selection</span>
                          <span className="strategy-summary__value">{buildBetLabel(bet)}</span>
                        </div>
                        <div className="strategy-summary">
                          <span className="strategy-summary__label">Liability</span>
                          <span className="strategy-summary__value">
                            {liability === null ? "N/A" : `€${liability.toFixed(2)}`}
                          </span>
                        </div>
                      </div>
                      <div className="odds-card__market">
                        <div className="strategy-stake">
                          <label className="strategy-stake__label" htmlFor={`stake-${game.id}`}>
                            Stake
                          </label>
                          <input
                            id={`stake-${game.id}`}
                            className="strategy-stake__input"
                            type="number"
                            min="0"
                            step="0.5"
                            value={stake ?? ""}
                            onChange={(event) =>
                              handleStakeChange(game.id, event.target.value)
                            }
                          />
                        </div>
                        <div className="strategy-odds">
                          <div className="strategy-odds__header">
                            <span>Outcome</span>
                            <span>Back</span>
                            <span>Lay</span>
                          </div>
                          {renderOutcomeRow(
                            game,
                            "Home",
                            game.homeOdds,
                            game.homeLayOdds
                          )}
                          {renderOutcomeRow(
                            game,
                            "Draw",
                            game.drawOdds,
                            game.drawLayOdds
                          )}
                          {renderOutcomeRow(
                            game,
                            "Away",
                            game.awayOdds,
                            game.awayLayOdds
                          )}
                        </div>
                      </div>
                    </article>
                  );
                })}
              </div>
            </section>
          )
        ) : activeTab === "pending" ? (
          <section className="panel">
            <div className="panel__header">
              <h2>Pending games</h2>
              <p className="panel__meta">{pendingBets.length} pending bets</p>
            </div>
            <div className="panel__actions">
              <button
                className="action-button action-button--ghost"
                type="button"
                onClick={() => loadStatus(false)}
              >
                Refresh pending
              </button>
            </div>
            {statusState.loading ? (
              <p className="status">Loading pending bets...</p>
            ) : statusState.error ? (
              <p className="status">{statusState.error}</p>
            ) : pendingBets.length === 0 ? (
              <p className="status">No pending bets yet.</p>
            ) : (
              <div className="bet-table">
                {pendingBets.map((bet) => (
                  <div key={bet.id} className="bet-row">
                    <div>
                      <p className="bet-row__title">
                        {bet.homeTeam || "Home"} vs {bet.awayTeam || "Away"}
                      </p>
                      <p className="bet-row__meta">
                        {bet.side?.toUpperCase()} {bet.selectionName} @ {Number(bet.odds).toFixed(2)}
                      </p>
                    </div>
                    <div className="bet-row__right">
                      <span className="pill">Stake €{Number(bet.stake).toFixed(2)}</span>
                      <span className="pill pill--outline">{bet.strategyName || bet.strategyId}</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>
        ) : activeTab === "inplay" ? (
          <section className="panel">
            <div className="panel__header">
              <h2>In-play bets</h2>
              <p className="panel__meta">{inPlayBets.length} live bets</p>
            </div>
            <div className="panel__actions">
              <button
                className="action-button action-button--ghost"
                type="button"
                onClick={() => loadStatus(false)}
              >
                Refresh in-play
              </button>
            </div>
            {statusState.loading ? (
              <p className="status">Loading in-play bets...</p>
            ) : statusState.error ? (
              <p className="status">{statusState.error}</p>
            ) : inPlayBets.length === 0 ? (
              <p className="status">No in-play bets yet.</p>
            ) : (
              <div className="bet-table">
                {inPlayBets.map((bet) => (
                  <div key={bet.id} className="bet-row">
                    <div>
                      <p className="bet-row__title">
                        {bet.homeTeam || "Home"} vs {bet.awayTeam || "Away"}
                      </p>
                      <p className="bet-row__meta">
                        {bet.side?.toUpperCase()} {bet.selectionName} @ {Number(bet.odds).toFixed(2)}
                      </p>
                      <p className="bet-row__meta">
                        {formatInPlayStatus(bet)} | {bet.matchClock || "Live"}
                      </p>
                      <p className="bet-row__meta">Score: {formatLiveScore(bet)}</p>
                    </div>
                    <div className="bet-row__right">
                      <span className="pill">Stake EUR {Number(bet.stake).toFixed(2)}</span>
                      <span className="pill pill--outline">{bet.strategyName || bet.strategyId}</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>
        ) : activeTab === "results" ? (
          <section className="panel">
            <div className="panel__header">
              <h2>Results</h2>
              <p className="panel__meta">{selectedDaySettledBets.length} settled bets</p>
            </div>
            <div className="panel__actions">
              <button
                className="action-button action-button--ghost"
                type="button"
                onClick={() => loadStatus(false)}
              >
                Refresh results
              </button>
            </div>
            {statusState.loading ? (
              <p className="status">Loading results...</p>
            ) : statusState.error ? (
              <p className="status">{statusState.error}</p>
            ) : selectedDaySettledBets.length === 0 ? (
              <p className="status">No settled bets for {selectedDayInfo.label}.</p>
            ) : (
              <div className="bet-table">
                {selectedDaySettledBets.map((bet) => (
                  <div key={bet.id} className="bet-row">
                    <div>
                      <p className="bet-row__title">
                        {bet.homeTeam || "Home"} vs {bet.awayTeam || "Away"}
                      </p>
                      <p className="bet-row__meta">
                        {bet.side?.toUpperCase()} {bet.selectionName} @ {Number(bet.odds).toFixed(2)}
                      </p>
                    </div>
                    <div
                      className={`bet-row__right ${
                        Number(bet.profit || 0) < 0
                          ? "bet-row__right--loss"
                          : "bet-row__right--profit"
                      }`}
                    >
                      <span className="pill">EUR {Number(bet.profit || 0).toFixed(2)}</span>
                      <span className="pill pill--outline">
                        {bet.strategyName || bet.strategyId}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>
        ) : (
          <section className="panel">
            <div className="panel__header">
              <h2>History</h2>
              <p className="panel__meta">{historySettledBets.length} bets in range</p>
            </div>
            <div className="panel__actions history-filters">
              <label className="history-filter">
                <span>From</span>
                <input
                  className="strategy-stake__input"
                  type="date"
                  value={historyFrom}
                  onChange={(event) => setHistoryFrom(event.target.value)}
                />
              </label>
              <label className="history-filter">
                <span>To</span>
                <input
                  className="strategy-stake__input"
                  type="date"
                  value={historyTo}
                  onChange={(event) => setHistoryTo(event.target.value)}
                />
              </label>
              <button
                className="action-button action-button--ghost"
                type="button"
                onClick={() => {
                  setHistoryFrom("");
                  setHistoryTo("");
                }}
              >
                Clear filters
              </button>
            </div>
            {statusState.loading ? (
              <p className="status">Loading history...</p>
            ) : statusState.error ? (
              <p className="status">{statusState.error}</p>
            ) : historySettledBets.length === 0 ? (
              <p className="status">No settled bets in this range.</p>
            ) : (
              <div className="bet-table">
                {historySettledBets.map((bet) => (
                  <div key={bet.id} className="bet-row">
                    <div>
                      <p className="bet-row__title">
                        {bet.homeTeam || "Home"} vs {bet.awayTeam || "Away"}
                      </p>
                      <p className="bet-row__meta">
                        {resolveBetDay(bet) || "Unknown date"} | {bet.side?.toUpperCase()} {bet.selectionName}
                      </p>
                    </div>
                    <div
                      className={`bet-row__right ${
                        Number(bet.profit || 0) < 0
                          ? "bet-row__right--loss"
                          : "bet-row__right--profit"
                      }`}
                    >
                      <span className="pill">EUR {Number(bet.profit || 0).toFixed(2)}</span>
                      <span className="pill pill--outline">
                        {bet.strategyName || bet.strategyId}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>
        )}
      </main>
    </>
  );
}

