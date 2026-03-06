"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import "./recent-stats.css";

interface RecentEventEntry {
  mainId: string;
  date: string;
  leagueName?: string;
  homeTeam?: string;
  awayTeam?: string;
  htHomeGoals?: number;
  htAwayGoals?: number;
  ftHomeGoals?: number;
  ftAwayGoals?: number;
  status?: string;
}

const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL?.trim() || "http://localhost:8089";

function formatLocalDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function formatScore(home?: number, away?: number): string {
  if (home == null || away == null) {
    return "-";
  }
  return `${home}-${away}`;
}

function totalGoals(home?: number, away?: number): number | null {
  if (home == null || away == null) {
    return null;
  }
  return home + away;
}

export default function RecentStatsPage() {
  const [selectedDate, setSelectedDate] = useState<string>(() =>
    formatLocalDate(new Date())
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [events, setEvents] = useState<RecentEventEntry[]>([]);
  const [selectedGoalsBucket, setSelectedGoalsBucket] = useState<number | null>(
    null
  );

  const sortedEvents = useMemo(
    () =>
      [...events].sort((a, b) =>
        `${a.leagueName ?? ""}${a.homeTeam ?? ""}${a.awayTeam ?? ""}`.localeCompare(
          `${b.leagueName ?? ""}${b.homeTeam ?? ""}${b.awayTeam ?? ""}`
        )
      ),
    [events]
  );

  const stats = useMemo(() => {
    const totalGames = sortedEvents.length;
    const ht00Games = sortedEvents.filter(
      (event) => event.htHomeGoals === 0 && event.htAwayGoals === 0
    );
    const ht00StayedFt00 = ht00Games.filter(
      (event) => event.ftHomeGoals === 0 && event.ftAwayGoals === 0
    );
    const ht00SecondHalfGoals = ht00Games.filter((event) => {
      const ft = totalGoals(event.ftHomeGoals, event.ftAwayGoals);
      return ft != null && ft > 0;
    });
    const gamesWithSecondHalfGoals = sortedEvents.filter((event) => {
      const ht = totalGoals(event.htHomeGoals, event.htAwayGoals);
      const ft = totalGoals(event.ftHomeGoals, event.ftAwayGoals);
      return ht != null && ft != null && ft > ht;
    });
    const ftGoalsDistribution = new Map<number, number>();
    for (const event of sortedEvents) {
      const ft = totalGoals(event.ftHomeGoals, event.ftAwayGoals);
      if (ft == null) {
        continue;
      }
      ftGoalsDistribution.set(ft, (ftGoalsDistribution.get(ft) ?? 0) + 1);
    }
    const ftGoalsRows = Array.from(ftGoalsDistribution.entries())
      .sort((a, b) => a[0] - b[0])
      .map(([goals, games]) => ({
        goals,
        games,
        pct: totalGames > 0 ? (games / totalGames) * 100 : 0,
      }));
    const totalFtGoals = sortedEvents.reduce((acc, event) => {
      const ft = totalGoals(event.ftHomeGoals, event.ftAwayGoals);
      return acc + (ft ?? 0);
    }, 0);
    return {
      totalGames,
      ht00Count: ht00Games.length,
      ht00StayedFt00Count: ht00StayedFt00.length,
      ht00SecondHalfGoalsCount: ht00SecondHalfGoals.length,
      gamesWithSecondHalfGoalsCount: gamesWithSecondHalfGoals.length,
      totalFtGoals,
      avgFtGoals: totalGames > 0 ? totalFtGoals / totalGames : 0,
      ftGoalsRows,
    };
  }, [sortedEvents]);

  const drillDownGames = useMemo(() => {
    if (selectedGoalsBucket == null) {
      return [];
    }
    return sortedEvents.filter((event) => {
      const ft = totalGoals(event.ftHomeGoals, event.ftAwayGoals);
      return ft === selectedGoalsBucket;
    });
  }, [selectedGoalsBucket, sortedEvents]);

  const loadEvents = async (date: string): Promise<void> => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch(
        `${API_BASE}/api/betfair/recent-events?date=${encodeURIComponent(date)}`
      );
      if (!response.ok) {
        throw new Error(`Failed to load recent games stats (${response.status})`);
      }
      const payload = (await response.json()) as RecentEventEntry[];
      setEvents(Array.isArray(payload) ? payload : []);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Failed to load recent games stats";
      setError(message);
      setEvents([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadEvents(selectedDate);
  }, [selectedDate]);

  return (
    <main className="recent-stats-page">
      <section className="recent-stats-panel">
        <p className="recent-stats-back">
          <Link href="/games">Back to games</Link>
        </p>
        <p className="recent-stats-back">
          <Link href="/games/id-mapping">Open ID Mapping</Link>
        </p>
        <h1 className="recent-stats-title">Recent Games Stats</h1>
        <p className="recent-stats-subtitle">
          Parsed from `backend/data/recent/YYYYMMDD.txt` and stored in
          `recent_events`.
        </p>

        <div className="recent-stats-controls">
          <label htmlFor="recent-stats-date" className="recent-stats-label">
            Date
          </label>
          <input
            id="recent-stats-date"
            type="date"
            value={selectedDate}
            onChange={(event) => setSelectedDate(event.currentTarget.value)}
            className="recent-stats-date"
          />
        </div>

        {!loading && !error && sortedEvents.length > 0 ? (
          <section className="recent-stats-dashboard">
            <h2 className="recent-stats-dashboard-title">Dashboard</h2>
            <div className="recent-stats-cards">
              <article className="recent-stats-card">
                <p className="recent-stats-card-label">Overall Games</p>
                <p className="recent-stats-card-value">{stats.totalGames}</p>
              </article>
              <article className="recent-stats-card">
                <p className="recent-stats-card-label">HT 0-0</p>
                <p className="recent-stats-card-value">{stats.ht00Count}</p>
              </article>
              <article className="recent-stats-card">
                <p className="recent-stats-card-label">HT 0-0 stayed FT 0-0</p>
                <p className="recent-stats-card-value">{stats.ht00StayedFt00Count}</p>
              </article>
              <article className="recent-stats-card">
                <p className="recent-stats-card-label">HT 0-0 then 2nd-half goals</p>
                <p className="recent-stats-card-value">{stats.ht00SecondHalfGoalsCount}</p>
              </article>
              <article className="recent-stats-card">
                <p className="recent-stats-card-label">Games with 2nd-half goals</p>
                <p className="recent-stats-card-value">
                  {stats.gamesWithSecondHalfGoalsCount}
                </p>
              </article>
              <article className="recent-stats-card">
                <p className="recent-stats-card-label">Avg FT goals / game</p>
                <p className="recent-stats-card-value">{stats.avgFtGoals.toFixed(2)}</p>
              </article>
            </div>

            <div className="recent-stats-dist">
              <h3 className="recent-stats-dist-title">FT Goals Distribution</h3>
              <div className="recent-stats-table-wrap">
                <table className="recent-stats-table recent-stats-table--compact">
                  <thead>
                    <tr>
                      <th>Goals</th>
                      <th>Games</th>
                      <th>Share</th>
                      <th>Ratio</th>
                    </tr>
                  </thead>
                  <tbody>
                    {stats.ftGoalsRows.map((row) => (
                      <tr
                        key={`ft-goals-${row.goals}`}
                        className={
                          selectedGoalsBucket === row.goals
                            ? "recent-stats-row--active"
                            : ""
                        }
                      >
                        <td>{row.goals}</td>
                        <td>{row.games}</td>
                        <td>{row.pct.toFixed(1)}%</td>
                        <td>
                          {row.goals} goals / {row.games} games
                        </td>
                        <td>
                          <button
                            type="button"
                            className="recent-stats-drill-btn"
                            onClick={() =>
                              setSelectedGoalsBucket((prev) =>
                                prev === row.goals ? null : row.goals
                              )
                            }
                          >
                            {selectedGoalsBucket === row.goals
                              ? "Hide games"
                              : "Drill down"}
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            {selectedGoalsBucket != null ? (
              <div className="recent-stats-drill">
                <h3 className="recent-stats-dist-title">
                  Games with FT total goals = {selectedGoalsBucket}
                </h3>
                {drillDownGames.length === 0 ? (
                  <p className="recent-stats-hint">No games in this bucket.</p>
                ) : (
                  <div className="recent-stats-table-wrap">
                    <table className="recent-stats-table recent-stats-table--compact">
                      <thead>
                        <tr>
                          <th>Date</th>
                          <th>League</th>
                          <th>Home</th>
                          <th>Away</th>
                          <th>HT</th>
                          <th>FT</th>
                          <th>Status</th>
                        </tr>
                      </thead>
                      <tbody>
                        {drillDownGames.map((event) => (
                          <tr key={`drill-${event.mainId}-${event.date}`}>
                            <td>{event.date || "-"}</td>
                            <td>{event.leagueName || "-"}</td>
                            <td>{event.homeTeam || "-"}</td>
                            <td>{event.awayTeam || "-"}</td>
                            <td>{formatScore(event.htHomeGoals, event.htAwayGoals)}</td>
                            <td>{formatScore(event.ftHomeGoals, event.ftAwayGoals)}</td>
                            <td>{event.status || "-"}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            ) : null}
          </section>
        ) : null}

        {loading ? <p className="recent-stats-hint">Loading recent games...</p> : null}
        {error ? <p className="recent-stats-hint recent-stats-hint--error">{error}</p> : null}
        {!loading && !error && sortedEvents.length === 0 ? (
          <p className="recent-stats-hint">No recent games found for this date.</p>
        ) : null}

        {!loading && !error && sortedEvents.length > 0 ? (
          <div className="recent-stats-table-wrap">
            <table className="recent-stats-table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>League</th>
                  <th>Home</th>
                  <th>Away</th>
                  <th>HT</th>
                  <th>FT</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {sortedEvents.map((event) => (
                  <tr key={`${event.mainId}-${event.date}`}>
                    <td>{event.date || "-"}</td>
                    <td>{event.leagueName || "-"}</td>
                    <td>{event.homeTeam || "-"}</td>
                    <td>{event.awayTeam || "-"}</td>
                    <td>{formatScore(event.htHomeGoals, event.htAwayGoals)}</td>
                    <td>{formatScore(event.ftHomeGoals, event.ftAwayGoals)}</td>
                    <td>{event.status || "-"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </section>
    </main>
  );
}
