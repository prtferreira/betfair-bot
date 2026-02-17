import React, { useEffect, useMemo, useState } from "react";

interface Entry {
  marketId: string;
  teams: string;
  startTime: string;
  status: string | null;
}

interface InPlayStatusPageProps {
  onBack: () => void;
}

interface State {
  loading: boolean;
  error: string | null;
  entries: Entry[];
  updatedAt: Date | null;
}

function buildTodayIso(): string {
  const today = new Date();
  return [
    today.getFullYear(),
    String(today.getMonth() + 1).padStart(2, "0"),
    String(today.getDate()).padStart(2, "0")
  ].join("-");
}

function statusClassName(status: string | null): string {
  if (!status) return "status-pill status-pill--unknown";
  const key = status.toLowerCase().replace(/\s+/g, "-");
  return `status-pill status-pill--${key}`;
}

export default function InPlayStatusPage({ onBack }: InPlayStatusPageProps) {
  const todayIso = useMemo(buildTodayIso, []);
  const [state, setState] = useState<State>({
    loading: true,
    error: null,
    entries: [],
    updatedAt: null
  });

  useEffect(() => {
    let active = true;

    const load = (initial = false) => {
      if (!active) return;
      setState((prev) => ({
        ...prev,
        loading: initial ? true : prev.loading
      }));

      fetch(`http://localhost:8089/api/betfair/balanced-games/status?date=${encodeURIComponent(todayIso)}&ts=${Date.now()}`)
        .then((response) => {
          if (!response.ok) throw new Error("Failed to load in-play statuses");
          return response.json();
        })
        .then((entries: Entry[]) => {
          if (!active) return;
          setState({
            loading: false,
            error: null,
            entries: Array.isArray(entries) ? entries : [],
            updatedAt: new Date()
          });
        })
        .catch((error: Error) => {
          if (!active) return;
          setState({
            loading: false,
            error: error.message || "Failed to load in-play statuses",
            entries: [],
            updatedAt: new Date()
          });
        });
    };

    load(true);
    const timer = setInterval(() => load(false), 30000);

    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [todayIso]);

  return (
    <>
      <header className="hero">
        <div>
          <p className="eyebrow">Betfair Trade Simulator</p>
          <h1>In-Play Status</h1>
          <p className="subhead">
            Status for balanced games saved today ({todayIso}).
          </p>
        </div>
        <div className="hero-card">
          <p className="hero-card__title">Refresh</p>
          <p className="hero-card__value">30s</p>
          <p className="hero-card__note">
            {state.updatedAt
              ? `Updated ${state.updatedAt.toLocaleTimeString()}`
              : "Fetching latest status"}
          </p>
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
            <p className="status">Loading in-play status...</p>
          </section>
        ) : state.error ? (
          <section className="panel panel--error">
            <p className="status">{state.error}</p>
          </section>
        ) : state.entries.length === 0 ? (
          <section className="panel">
            <p className="status">No balanced games saved for today.</p>
          </section>
        ) : (
          <section className="panel">
            <div className="panel__header">
              <h2>Balanced games status</h2>
              <p className="panel__meta">{state.entries.length} fixtures</p>
            </div>
            <div className="games">
              {state.entries.map((entry) => (
                <article key={entry.marketId} className="game-card">
                  <div className="game-card__details">
                    <p className="game-card__league">{entry.marketId}</p>
                    <h3 className="game-card__match">{entry.teams}</h3>
                    <p className="game-card__time">{entry.startTime}</p>
                  </div>
                  <div className="game-card__meta">
                    <span className={statusClassName(entry.status)}>{entry.status || "Unknown"}</span>
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
