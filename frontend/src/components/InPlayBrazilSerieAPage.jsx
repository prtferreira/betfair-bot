import React, { useEffect, useState } from "react";

function formatOdds(value) {
  if (value === null || value === undefined) {
    return "N/A";
  }
  return Number(value).toFixed(2);
}

export default function InPlayBrazilSerieAPage({ onBack }) {
  const [state, setState] = useState({
    loading: true,
    error: null,
    games: []
  });
  const [lastUpdated, setLastUpdated] = useState(null);

  const loadGames = (background = false) => {
    if (!background) {
      setState((prev) => ({ ...prev, loading: true, error: null }));
    }
    fetch(`/api/betfair/inplay/brasil-serie-a?_t=${Date.now()}`, { cache: "no-store" })
      .then((response) => {
        if (!response.ok) {
          throw new Error("Failed to load in-play games");
        }
        return response.json();
      })
      .then((data) => {
        setState({ loading: false, error: null, games: data });
        setLastUpdated(new Date());
      })
      .catch((error) => {
        setState({ loading: false, error: error.message, games: [] });
      });
  };

  useEffect(() => {
    loadGames();
    const timerId = setInterval(() => loadGames(true), 30000);
    return () => clearInterval(timerId);
  }, []);

  return (
    <>
      <header className="hero">
        <div>
          <p className="eyebrow">Betfair Trade Simulator</p>
          <h1>In-play Brazil Serie A</h1>
          <p className="subhead">
            Live Match Odds (1X2) for Brazilian Serie A games currently in-play.
          </p>
        </div>
        <div className="hero-card">
          <p className="hero-card__title">Mode</p>
          <p className="hero-card__value">Live now</p>
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
            <p className="status">Loading in-play Brazil Serie A games...</p>
          </section>
        ) : state.error ? (
          <section className="panel panel--error">
            <p className="status">{state.error}</p>
          </section>
        ) : (
          <section className="panel">
            <div className="panel__header">
              <h2>Live games</h2>
              <p className="panel__meta">
                {state.games.length} fixtures
                {lastUpdated ? ` - Updated ${lastUpdated.toLocaleTimeString("en-GB")}` : ""}
              </p>
            </div>
            <div className="panel__actions">
              <button className="action-button" type="button" onClick={loadGames}>
                Refresh live odds
              </button>
            </div>
            {state.games.length === 0 ? (
              <p className="status">No Brazil Serie A games are live right now.</p>
            ) : (
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
                      <span className="odds-label">1X2 odds</span>
                      <div className="odds-row">
                        <div className="odds-pill">
                          <span className="odds-pill__team">{game.homeTeam}</span>
                          <span className="odds-pill__value">{formatOdds(game.homeOdds)}</span>
                        </div>
                        <div className="odds-pill odds-pill--draw">
                          <span className="odds-pill__team">Draw</span>
                          <span className="odds-pill__value">{formatOdds(game.drawOdds)}</span>
                        </div>
                        <div className="odds-pill">
                          <span className="odds-pill__team">{game.awayTeam}</span>
                          <span className="odds-pill__value">{formatOdds(game.awayOdds)}</span>
                        </div>
                      </div>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </section>
        )}
      </main>
    </>
  );
}
