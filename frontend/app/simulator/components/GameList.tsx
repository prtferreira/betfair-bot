import React, { useEffect, useMemo, useState } from "react";

interface Game {
  id: string;
  marketId: string;
  homeTeam: string;
  awayTeam: string;
  league?: string;
  sport?: string;
  startTime?: string;
  homeOdds: number;
  awayOdds: number;
}

interface Strategy {
  id: string;
  name: string;
}

interface SelectionItem {
  selected: boolean;
  strategies: string[];
}

interface Props {
  date?: string;
  apiPath?: string;
  includeDate?: boolean;
}

interface State {
  loading: boolean;
  error: string | null;
  games: Game[];
}

export default function GameList({
  date,
  apiPath = "http://localhost:8089/api/games",
  includeDate = true,
}: Props) {
  const [state, setState] = useState<State>({
    loading: true,
    error: null,
    games: [],
  });

  const [strategies, setStrategies] = useState<Strategy[]>([]);
  const [selection, setSelection] = useState<Record<string, SelectionItem>>({});
  const [openMenuId, setOpenMenuId] = useState<string | null>(null);
  const [{ maxOdd, minOdd }, setOddFilter] = useState<{
    maxOdd: number;
    minOdd: number;
  }>({
    maxOdd: 0,
    minOdd: 0,
  });

  const strategyOptions = useMemo(() => strategies, [strategies]);

  // Fetch games
  useEffect(() => {
    let active = true;
    setState((prev) => ({ ...prev, loading: true, error: null }));

    const url = includeDate ? `${apiPath}?date=${date}` : apiPath;

    fetch(url)
      .then((response) => {
        if (!response.ok) throw new Error("Failed to load games");
        return response.json() as Promise<Game[]>;
      })
      .then((data) => {
        if (!active) return;
        setState({ loading: false, error: null, games: data });
      })
      .catch((error: Error) => {
        if (!active) return;
        setState({ loading: false, error: error.message, games: [] });
      });

    return () => {
      active = false;
    };
  }, [date, apiPath, includeDate]);

  // Fetch strategies
  useEffect(() => {
    let active = true;

    fetch("http://localhost:8089/api/strategies")
      .then((response) => {
        if (!response.ok) throw new Error("Failed to load strategies");
        return response.json() as Promise<Strategy[]>;
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

  // Sync selection with games
  useEffect(() => {
    setSelection((prev) => {
      const next: Record<string, SelectionItem> = {};
      state.games.forEach((game) => {
        next[game.id] = prev[game.id] || {
          selected: false,
          strategies: [],
        };
      });
      return next;
    });
  }, [state.games]);

  const updateSelected = (gameId: string, value: boolean): void => {
    setSelection((prev) => ({
      ...prev,
      [gameId]: { ...(prev[gameId] || { strategies: [] }), selected: value },
    }));
  };

  const toggleStrategy = (gameId: string, strategyId: string): void => {
    setSelection((prev) => {
      const current = prev[gameId] || { selected: false, strategies: [] };

      const exists = current.strategies.includes(strategyId);
      const nextStrategies = exists
        ? current.strategies.filter((id) => id !== strategyId)
        : [...current.strategies, strategyId];

      return {
        ...prev,
        [gameId]: { ...current, strategies: nextStrategies },
      };
    });
  };

  const downloadSelectedMatchIds = (): void => {
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

  const games = useMemo(() => {
    if (maxOdd !== 0 && minOdd !== 0) {
      return state.games.filter(
        (game) =>
          game.awayOdds >= minOdd &&
          game.awayOdds <= maxOdd &&
          game.homeOdds <= maxOdd &&
          game.homeOdds >= minOdd
      );
    }
    return state.games;
  }, [maxOdd, minOdd, state.games]);

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

      <div className="games">
        {games.map((game) => (
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
                  type="button"
                  onClick={() =>
                    setOpenMenuId((prev) =>
                      prev === game.id ? null : game.id
                    )
                  }
                >
                  Strategies ({selection[game.id]?.strategies.length || 0})
                </button>

                {openMenuId === game.id && (
                  <div>
                    {strategyOptions.map((strategy) => (
                      <label key={strategy.id}>
                        <input
                          type="checkbox"
                          checked={
                            selection[game.id]?.strategies.includes(
                              strategy.id
                            ) || false
                          }
                          onChange={() =>
                            toggleStrategy(game.id, strategy.id)
                          }
                        />
                        {strategy.name}
                      </label>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

