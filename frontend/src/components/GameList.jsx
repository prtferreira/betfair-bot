import React, { useEffect, useMemo, useState } from "react";

export default function GameList({ date, apiPath = "/api/games", includeDate = true }) {
  const [state, setState] = useState({
    loading: true,
    error: null,
    games: []
  });
  const [strategies, setStrategies] = useState([]);
  const [selection, setSelection] = useState({});
  const [openMenuId, setOpenMenuId] = useState(null);

  const strategyOptions = useMemo(() => strategies, [strategies]);

  useEffect(() => {
    let active = true;
    setState((prev) => ({ ...prev, loading: true, error: null }));

    const url = includeDate ? `${apiPath}?date=${date}` : apiPath;
    fetch(url)
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
  }, [date, apiPath, includeDate]);

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
      })
      .catch(() => {
        if (!active) return;
        setStrategies([]);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    setSelection((prev) => {
      const next = {};
      state.games.forEach((game) => {
        next[game.id] = prev[game.id] || {
          selected: false,
          strategies: []
        };
      });
      return next;
    });
  }, [state.games]);

  const updateSelected = (gameId, value) => {
    setSelection((prev) => ({
      ...prev,
      [gameId]: { ...(prev[gameId] || {}), selected: value }
    }));
  };

  const updateStrategies = (gameId, values) => {
    setSelection((prev) => ({
      ...prev,
      [gameId]: { ...(prev[gameId] || {}), strategies: values }
    }));
  };

  const toggleStrategy = (gameId, strategyId) => {
    setSelection((prev) => {
      const current = prev[gameId] || { selected: false, strategies: [] };
      const exists = current.strategies.includes(strategyId);
      const nextStrategies = exists
        ? current.strategies.filter((id) => id !== strategyId)
        : [...current.strategies, strategyId];
      return {
        ...prev,
        [gameId]: { ...current, strategies: nextStrategies }
      };
    });
  };

  const downloadSelectedMatchIds = () => {
    const ids = state.games
      .filter((game) => selection[game.id]?.selected)
      .map((game) => game.marketId);
    const contents = ids.length ? ids.join("\n") : "";
    const blob = new Blob([contents], { type: "text/plain" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `selected-match-ids-${date}.txt`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  };


  if (state.loading) {
    return (
      <section className="panel">
        <p className="status">Loading games for {date}...</p>
      </section>
    );
  }

  if (state.error) {
    return (
      <section className="panel panel--error">
        <p className="status">{state.error}</p>
      </section>
    );
  }

  if (state.games.length === 0) {
    return (
      <section className="panel">
        <p className="status">No games scheduled for {date}.</p>
      </section>
    );
  }

  return (
    <section className="panel">
      <div className="panel__header">
        <h2>Games on {date}</h2>
        <p className="panel__meta">{state.games.length} fixtures</p>
      </div>
      <div className="panel__actions">
        <button
          className="action-button"
          type="button"
          onClick={downloadSelectedMatchIds}
        >
          Download selected match IDs
        </button>
      </div>
      <div className="games">
        {state.games.map((game) => (
          <article key={game.id} className="game-card">
            <div className="game-card__details">
              <p className="game-card__league">{game.league}</p>
              <h3 className="game-card__match">
                {game.homeTeam} vs {game.awayTeam}
              </h3>
              <p className="game-card__time">{game.startTime}</p>
            </div>
            <div className="game-card__controls">
              <label className="checkbox">
                <input
                  type="checkbox"
                  checked={selection[game.id]?.selected || false}
                  onChange={(event) =>
                    updateSelected(game.id, event.target.checked)
                  }
                />
                Trade this game
              </label>
              <div className="dropdown">
                <button
                  className="dropdown__trigger"
                  type="button"
                  onClick={() =>
                    setOpenMenuId((prev) => (prev === game.id ? null : game.id))
                  }
                >
                  Strategies
                  <span className="dropdown__count">
                    {selection[game.id]?.strategies?.length || 0}
                  </span>
                </button>
                {openMenuId === game.id && (
                  <div className="dropdown__menu">
                    {strategyOptions.map((strategy) => (
                      <label key={strategy.id} className="dropdown__item">
                        <input
                          type="checkbox"
                          checked={
                            selection[game.id]?.strategies?.includes(
                              strategy.id
                            ) || false
                          }
                          onChange={() => toggleStrategy(game.id, strategy.id)}
                        />
                        {strategy.name}
                      </label>
                    ))}
                  </div>
                )}
              </div>
              <div className="game-card__meta">
                <span className="pill">{game.sport}</span>
                <span className="pill pill--outline">{game.marketId}</span>
              </div>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
