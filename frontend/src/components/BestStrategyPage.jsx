import React, { useEffect, useMemo, useState } from "react";

function buildTodayIso() {
  const today = new Date();
  return [
    today.getFullYear(),
    String(today.getMonth() + 1).padStart(2, "0"),
    String(today.getDate()).padStart(2, "0")
  ].join("-");
}

function formatOdds(value) {
  if (value === null || value === undefined) {
    return "N/A";
  }
  const numeric = Number(value);
  if (Number.isNaN(numeric)) {
    return "N/A";
  }
  return numeric.toFixed(2);
}

function parseOdds(value) {
  if (value === null || value === undefined || value === "") {
    return null;
  }
  const numeric = Number(value);
  return Number.isNaN(numeric) ? null : numeric;
}

function statusClassName(status) {
  if (!status) {
    return "status-pill status-pill--unknown";
  }
  const key = status.toLowerCase().replace(/\s+/g, "-");
  return `status-pill status-pill--${key}`;
}

function outcomeOptions(market) {
  if (market === "OU_05") {
    return ["Over 0.5", "Under 0.5"];
  }
  return ["Home", "Draw", "Away"];
}

function resolveLegOdds(entry, leg) {
  const manual = parseOdds(leg.manualOdds);
  if (manual !== null && manual > 1) {
    return manual;
  }
  if (!entry) {
    return null;
  }

  if (leg.market === "FT_1X2") {
    if (leg.selection === "Home") return parseOdds(entry.homeOdds);
    if (leg.selection === "Draw") return parseOdds(entry.drawOdds);
    if (leg.selection === "Away") return parseOdds(entry.awayOdds);
  }

  if (leg.market === "HT_1X2") {
    if (leg.selection === "Home") return parseOdds(entry.htHomeOdds);
    if (leg.selection === "Draw") return parseOdds(entry.htDrawOdds);
    if (leg.selection === "Away") return parseOdds(entry.htAwayOdds);
  }

  if (leg.market === "OU_05") {
    if (leg.selection === "Over 0.5") return parseOdds(entry.over05Odds);
    if (leg.selection === "Under 0.5") return parseOdds(entry.under05Odds);
  }

  return null;
}

export default function BestStrategyPage({ onBack }) {
  const todayIso = useMemo(buildTodayIso, []);
  const [gamesState, setGamesState] = useState({
    loading: true,
    error: null,
    games: []
  });
  const [selection, setSelection] = useState({});
  const [submitState, setSubmitState] = useState({
    status: "idle",
    message: ""
  });
  const [monitorState, setMonitorState] = useState({
    loading: true,
    error: null,
    entries: [],
    updatedAt: null
  });
  const [stake, setStake] = useState("10");
  const [strategyMarketId, setStrategyMarketId] = useState("");
  const [legs, setLegs] = useState([
    {
      id: 1,
      minute: "10",
      betType: "back",
      market: "FT_1X2",
      selection: "Home",
      manualOdds: ""
    }
  ]);

  useEffect(() => {
    let active = true;
    setGamesState({ loading: true, error: null, games: [] });

    Promise.all([
      fetch(`/api/betfair/today-odds?date=${encodeURIComponent(todayIso)}`).then((response) => {
        if (!response.ok) {
          throw new Error("Failed to load scheduled games");
        }
        return response.json();
      }),
      fetch(`/api/betfair/inplay/brasil-serie-a`).then((response) => {
        if (!response.ok) {
          throw new Error("Failed to load in-play games");
        }
        return response.json();
      })
    ])
      .then(([scheduledGames, inPlayGames]) => {
        if (!active) return;
        const byMarketId = new Map();
        (Array.isArray(scheduledGames) ? scheduledGames : []).forEach((game) => {
          byMarketId.set(game.marketId || game.id, {
            ...game,
            listStatus: "Scheduled"
          });
        });
        (Array.isArray(inPlayGames) ? inPlayGames : []).forEach((game) => {
          byMarketId.set(game.marketId || game.id, {
            ...game,
            listStatus: "Started"
          });
        });

        const merged = Array.from(byMarketId.values());
        const sorted = merged.sort((a, b) => {
          const aTime = Date.parse(a.startTime || "");
          const bTime = Date.parse(b.startTime || "");
          if (Number.isNaN(aTime) && Number.isNaN(bTime)) return 0;
          if (Number.isNaN(aTime)) return 1;
          if (Number.isNaN(bTime)) return -1;
          return aTime - bTime;
        });
        setGamesState({ loading: false, error: null, games: sorted });
      })
      .catch((error) => {
        if (!active) return;
        setGamesState({ loading: false, error: error.message, games: [] });
      });

    return () => {
      active = false;
    };
  }, [todayIso]);

  useEffect(() => {
    setSelection((prev) => {
      const next = {};
      gamesState.games.forEach((game) => {
        next[game.id] = prev[game.id] || false;
      });
      return next;
    });
  }, [gamesState.games]);

  useEffect(() => {
    let active = true;
    const loadMonitor = (initial = false) => {
      if (!active) return;
      setMonitorState((prev) => ({
        ...prev,
        loading: initial ? true : prev.loading
      }));
      fetch(`/api/betfair/best-strategy/monitor?date=${encodeURIComponent(todayIso)}&ts=${Date.now()}`)
        .then((response) => {
          if (!response.ok) {
            throw new Error("Failed to load best strategy monitor");
          }
          return response.json();
        })
        .then((entries) => {
          if (!active) return;
          const data = Array.isArray(entries) ? entries : [];
          setMonitorState({
            loading: false,
            error: null,
            entries: data,
            updatedAt: new Date()
          });
        })
        .catch((error) => {
          if (!active) return;
          setMonitorState({
            loading: false,
            error: error.message || "Failed to load best strategy monitor",
            entries: [],
            updatedAt: new Date()
          });
        });
    };

    loadMonitor(true);
    const timer = setInterval(() => loadMonitor(false), 30000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [todayIso]);

  useEffect(() => {
    if (!strategyMarketId && monitorState.entries.length > 0) {
      setStrategyMarketId(monitorState.entries[0].marketId || "");
    }
  }, [strategyMarketId, monitorState.entries]);

  const updateSelected = (gameId, checked) => {
    setSelection((prev) => ({
      ...prev,
      [gameId]: checked
    }));
  };

  const selectAllGames = (checked) => {
    setSelection(() => {
      const next = {};
      gamesState.games.forEach((game) => {
        next[game.id] = checked;
      });
      return next;
    });
  };

  const submitSelectedGames = () => {
    const entries = gamesState.games
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

    setSubmitState({ status: "loading", message: "Saving best strategy games..." });
    fetch("/api/betfair/best-strategy/games", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ date: todayIso, entries })
    })
      .then((response) =>
        response.json().then((data) => {
          if (!response.ok || data.status !== "OK") {
            throw new Error(data.message || "Failed to save best strategy games");
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
          message: error.message || "Failed to save best strategy games"
        });
      });
  };

  const addLeg = () => {
    setLegs((prev) => [
      ...prev,
      {
        id: Date.now(),
        minute: "",
        betType: "back",
        market: "FT_1X2",
        selection: "Home",
        manualOdds: ""
      }
    ]);
  };

  const removeLeg = (id) => {
    setLegs((prev) => prev.filter((leg) => leg.id !== id));
  };

  const updateLeg = (id, field, value) => {
    setLegs((prev) =>
      prev.map((leg) => {
        if (leg.id !== id) {
          return leg;
        }
        if (field === "market") {
          const nextOptions = outcomeOptions(value);
          return {
            ...leg,
            market: value,
            selection: nextOptions[0]
          };
        }
        return {
          ...leg,
          [field]: value
        };
      })
    );
  };

  const inPlayEntries = monitorState.entries.filter((entry) => entry.started);
  const selectedMonitorEntry =
    monitorState.entries.find((entry) => entry.marketId === strategyMarketId) || null;
  const stakeValue = parseOdds(stake);

  let backCombinedOdds = 1;
  let hasBackLeg = false;
  let layLiability = 0;
  let unresolvedLegs = 0;
  for (const leg of legs) {
    const odds = resolveLegOdds(selectedMonitorEntry, leg);
    if (odds === null || odds <= 1) {
      unresolvedLegs += 1;
      continue;
    }
    if (leg.betType === "lay") {
      if (stakeValue !== null) {
        layLiability += stakeValue * (odds - 1);
      }
    } else {
      hasBackLeg = true;
      backCombinedOdds *= odds;
    }
  }

  const estimatedBackReturn =
    stakeValue !== null && hasBackLeg ? stakeValue * backCombinedOdds : null;
  const estimatedBackProfit =
    estimatedBackReturn !== null && stakeValue !== null ? estimatedBackReturn - stakeValue : null;

  return (
    <>
      <header className="hero">
        <div>
          <p className="eyebrow">Betfair Trade Simulator</p>
          <h1>Best Strategy</h1>
          <p className="subhead">
            Select today&apos;s games, monitor in-play odds every 30 seconds, include O/U 0.5, and simulate combined strategies with minute-based steps.
          </p>
        </div>
        <div className="hero-card">
          <p className="hero-card__title">Date</p>
          <p className="hero-card__value">{todayIso}</p>
          <p className="hero-card__note">
            {monitorState.updatedAt
              ? `Monitor updated ${monitorState.updatedAt.toLocaleTimeString()}`
              : "Monitor waiting for first update"}
          </p>
          <div className="hero-card__actions">
            <button className="action-button action-button--ghost" type="button" onClick={onBack}>
              Back to games
            </button>
          </div>
        </div>
      </header>

      <main>
        <section className="panel">
          <div className="panel__header">
            <h2>Today&apos;s games</h2>
            <p className="panel__meta">{gamesState.games.length} fixtures</p>
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
            <button className="action-button" type="button" onClick={submitSelectedGames}>
              Save strategy games
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

          {gamesState.loading ? (
            <p className="status">Loading today&apos;s games...</p>
          ) : gamesState.error ? (
            <p className="status">{gamesState.error}</p>
          ) : gamesState.games.length === 0 ? (
            <p className="status">No games available for today.</p>
          ) : (
            <div className="odds-grid">
              {gamesState.games.map((game) => (
                <article key={game.id} className="odds-card">
                  <div className="odds-card__details">
                    <p className="game-card__league">{game.league}</p>
                    <h3 className="game-card__match">
                      {game.homeTeam} vs {game.awayTeam}
                    </h3>
                    <p className="game-card__time">{game.startTime}</p>
                    <span className={statusClassName(game.listStatus)}>{game.listStatus}</span>
                  </div>
                  <div className="odds-card__market">
                    <div className="odds-line">
                      <label className="checkbox checkbox--compact">
                        <input
                          type="checkbox"
                          checked={selection[game.id] || false}
                          onChange={(event) => updateSelected(game.id, event.target.checked)}
                        />
                        Select
                      </label>
                      <div className="market-group">
                        <div className="market-group__header">Full-time 1X2</div>
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
                      <div className="market-group">
                        <div className="market-group__header">O/U 0.5</div>
                        <div className="market-group__row">
                          <div className="odds-pill odds-pill--compact">
                            <span className="odds-pill__team">Over 0.5</span>
                            <span className="odds-pill__value">{formatOdds(game.over05Odds)}</span>
                          </div>
                          <div className="odds-pill odds-pill--compact">
                            <span className="odds-pill__team">Under 0.5</span>
                            <span className="odds-pill__value">{formatOdds(game.under05Odds)}</span>
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

        <section className="panel">
          <div className="panel__header">
            <h2>Watch Monitor</h2>
            <p className="panel__meta">{inPlayEntries.length} in-play now</p>
          </div>
          {monitorState.loading ? (
            <p className="status">Loading best strategy monitor...</p>
          ) : monitorState.error ? (
            <p className="status">{monitorState.error}</p>
          ) : monitorState.entries.length === 0 ? (
            <p className="status">No selected strategy games saved for today.</p>
          ) : (
            <div className="odds-grid">
              {monitorState.entries.map((entry) => (
                <article key={entry.marketId} className="odds-card">
                  <div className="odds-card__details">
                    <p className="game-card__league">{entry.marketId}</p>
                    <h3 className="game-card__match">{entry.teams}</h3>
                    <p className="game-card__time">{entry.startTime}</p>
                    <p className="panel__meta">File: {entry.oddsFile}</p>
                  </div>
                  <div className="odds-card__market">
                    <span className={statusClassName(entry.status)}>
                      {entry.status || "Unknown"}
                    </span>
                    <div className="market-group">
                      <div className="market-group__header">Full-time 1X2</div>
                      <span className={statusClassName(entry.ftMarketStatus || entry.status)}>
                        {entry.ftMarketStatus || entry.status || "Unknown"}
                      </span>
                      <div className="market-group__row">
                        <div className="odds-pill odds-pill--compact">
                          <span className="odds-pill__team">Home</span>
                          <span className="odds-pill__value">{formatOdds(entry.homeOdds)}</span>
                        </div>
                        <div className="odds-pill odds-pill--draw odds-pill--compact">
                          <span className="odds-pill__team">Draw</span>
                          <span className="odds-pill__value">{formatOdds(entry.drawOdds)}</span>
                        </div>
                        <div className="odds-pill odds-pill--compact">
                          <span className="odds-pill__team">Away</span>
                          <span className="odds-pill__value">{formatOdds(entry.awayOdds)}</span>
                        </div>
                      </div>
                    </div>
                    <div className="market-group">
                      <div className="market-group__header">Half-time 1X2</div>
                      <span className={statusClassName(entry.htMarketStatus || entry.status)}>
                        {entry.htMarketStatus || entry.status || "Unknown"}
                      </span>
                      <div className="market-group__row">
                        <div className="odds-pill odds-pill--compact">
                          <span className="odds-pill__team">Home</span>
                          <span className="odds-pill__value">{formatOdds(entry.htHomeOdds)}</span>
                        </div>
                        <div className="odds-pill odds-pill--draw odds-pill--compact">
                          <span className="odds-pill__team">Draw</span>
                          <span className="odds-pill__value">{formatOdds(entry.htDrawOdds)}</span>
                        </div>
                        <div className="odds-pill odds-pill--compact">
                          <span className="odds-pill__team">Away</span>
                          <span className="odds-pill__value">{formatOdds(entry.htAwayOdds)}</span>
                        </div>
                      </div>
                    </div>
                    <div className="market-group">
                      <div className="market-group__header">O/U 0.5</div>
                      <span className={statusClassName(entry.ou05MarketStatus || entry.status)}>
                        {entry.ou05MarketStatus || entry.status || "Unknown"}
                      </span>
                      <div className="market-group__row">
                        <div className="odds-pill odds-pill--compact">
                          <span className="odds-pill__team">Over 0.5</span>
                          <span className="odds-pill__value">{formatOdds(entry.over05Odds)}</span>
                        </div>
                        <div className="odds-pill odds-pill--compact">
                          <span className="odds-pill__team">Under 0.5</span>
                          <span className="odds-pill__value">{formatOdds(entry.under05Odds)}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>

        <section className="panel">
          <div className="panel__header">
            <h2>Combined Strategy Simulator</h2>
            <p className="panel__meta">Minute-based combined steps</p>
          </div>
          <div className="panel__actions">
            <label className="history-filter">
              Stake
              <input
                className="strategy-stake__input"
                type="number"
                min="0"
                step="0.01"
                value={stake}
                onChange={(event) => setStake(event.target.value)}
              />
            </label>
            <label className="history-filter">
              Match
              <select
                className="strategy-stake__input"
                value={strategyMarketId}
                onChange={(event) => setStrategyMarketId(event.target.value)}
              >
                {monitorState.entries.length === 0 ? (
                  <option value="">No monitored matches</option>
                ) : (
                  monitorState.entries.map((entry) => (
                    <option key={entry.marketId} value={entry.marketId}>
                      {entry.teams}
                    </option>
                  ))
                )}
              </select>
            </label>
            <button className="action-button action-button--ghost" type="button" onClick={addLeg}>
              Add step
            </button>
          </div>

          <div className="strategy-steps">
            {legs.map((leg) => (
              <div key={leg.id} className="strategy-step">
                <input
                  className="strategy-stake__input"
                  type="number"
                  min="0"
                  step="1"
                  value={leg.minute}
                  onChange={(event) => updateLeg(leg.id, "minute", event.target.value)}
                  placeholder="Minute"
                />
                <select
                  className="strategy-stake__input"
                  value={leg.betType}
                  onChange={(event) => updateLeg(leg.id, "betType", event.target.value)}
                >
                  <option value="back">Back</option>
                  <option value="lay">Lay</option>
                </select>
                <select
                  className="strategy-stake__input"
                  value={leg.market}
                  onChange={(event) => updateLeg(leg.id, "market", event.target.value)}
                >
                  <option value="FT_1X2">Full-time 1X2</option>
                  <option value="HT_1X2">Half-time 1X2</option>
                  <option value="OU_05">O/U 0.5</option>
                </select>
                <select
                  className="strategy-stake__input"
                  value={leg.selection}
                  onChange={(event) => updateLeg(leg.id, "selection", event.target.value)}
                >
                  {outcomeOptions(leg.market).map((option) => (
                    <option key={option} value={option}>
                      {option}
                    </option>
                  ))}
                </select>
                <input
                  className="strategy-stake__input"
                  type="number"
                  min="1.01"
                  step="0.01"
                  value={leg.manualOdds}
                  onChange={(event) => updateLeg(leg.id, "manualOdds", event.target.value)}
                  placeholder="Manual odds (opt)"
                />
                <button
                  className="action-button action-button--ghost"
                  type="button"
                  onClick={() => removeLeg(leg.id)}
                  disabled={legs.length === 1}
                >
                  Remove
                </button>
              </div>
            ))}
          </div>

          <div className="balance-list">
            <div className="balance-list__row">
              <span>Back combined odds</span>
              <strong>{hasBackLeg ? backCombinedOdds.toFixed(2) : "N/A"}</strong>
            </div>
            <div className="balance-list__row">
              <span>Estimated back return</span>
              <strong>{estimatedBackReturn === null ? "N/A" : estimatedBackReturn.toFixed(2)}</strong>
            </div>
            <div className="balance-list__row">
              <span>Estimated back profit</span>
              <strong>{estimatedBackProfit === null ? "N/A" : estimatedBackProfit.toFixed(2)}</strong>
            </div>
            <div className="balance-list__row">
              <span>Total lay liability</span>
              <strong>{stakeValue === null ? "N/A" : layLiability.toFixed(2)}</strong>
            </div>
            <div className="balance-list__row">
              <span>Unresolved steps</span>
              <strong>{unresolvedLegs}</strong>
            </div>
          </div>
        </section>
      </main>
    </>
  );
}
