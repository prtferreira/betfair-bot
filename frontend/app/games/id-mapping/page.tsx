"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import "./id-mapping.css";

interface ApiMatch {
  apiMatchId: string;
  date: string;
  leagueName: string;
  homeTeam: string;
  awayTeam: string;
  displayName: string;
}

interface BetfairMatch {
  betfairEventId: string;
  leagueName: string;
  homeTeam: string;
  awayTeam: string;
  startTime: string;
  displayName: string;
}

interface MappingEntry {
  date: string;
  apiMatchId: string;
  apiHomeTeam: string;
  apiAwayTeam: string;
  betfairEventId: string;
  betfairHomeTeam: string;
  betfairAwayTeam: string;
  source: string;
  confidenceScore?: number;
  updatedAt: string;
}

interface CandidateResponse {
  date: string;
  apiMatches: ApiMatch[];
  betfairMatches: BetfairMatch[];
  mappings: MappingEntry[];
}

const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL?.trim() || "http://localhost:8089";

function formatLocalDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export default function IdMappingPage() {
  const [date, setDate] = useState<string>(() => formatLocalDate(new Date()));
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [payload, setPayload] = useState<CandidateResponse | null>(null);
  const [dragApiId, setDragApiId] = useState<string | null>(null);
  const [betfairSearch, setBetfairSearch] = useState("");
  const [apiSearch, setApiSearch] = useState("");

  const load = async (selectedDate: string): Promise<void> => {
    setLoading(true);
    setError(null);
    setStatusMessage(null);
    try {
      const response = await fetch(
        `${API_BASE}/api/refdata/statpal-betfair/candidates?date=${encodeURIComponent(
          selectedDate
        )}`
      );
      if (!response.ok) {
        throw new Error(`Failed to load mapping data (${response.status})`);
      }
      const next = (await response.json()) as CandidateResponse;
      setPayload(next);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to load mapping data";
      setError(message);
      setPayload(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load(date);
  }, [date]);

  const mappedApiIds = useMemo(
    () => new Set((payload?.mappings ?? []).map((m) => m.apiMatchId)),
    [payload]
  );
  const mappedBetfairIds = useMemo(
    () => new Set((payload?.mappings ?? []).map((m) => m.betfairEventId)),
    [payload]
  );

  const apiUnmapped = useMemo(
    () =>
      (payload?.apiMatches ?? []).filter((match) => !mappedApiIds.has(match.apiMatchId)),
    [payload, mappedApiIds]
  );
  const betfairUnmapped = useMemo(
    () =>
      (payload?.betfairMatches ?? []).filter(
        (match) => !mappedBetfairIds.has(match.betfairEventId)
      ),
    [payload, mappedBetfairIds]
  );
  const filteredBetfairUnmapped = useMemo(() => {
    const query = betfairSearch.trim().toLowerCase();
    if (!query) {
      return betfairUnmapped;
    }
    return betfairUnmapped.filter((match) => {
      const haystack =
        `${match.displayName} ${match.homeTeam} ${match.awayTeam} ${match.leagueName} ${match.betfairEventId}`.toLowerCase();
      return haystack.includes(query);
    });
  }, [betfairUnmapped, betfairSearch]);
  const filteredApiUnmapped = useMemo(() => {
    const query = apiSearch.trim().toLowerCase();
    if (!query) {
      return apiUnmapped;
    }
    return apiUnmapped.filter((match) => {
      const haystack =
        `${match.displayName} ${match.homeTeam} ${match.awayTeam} ${match.leagueName} ${match.apiMatchId}`.toLowerCase();
      return haystack.includes(query);
    });
  }, [apiUnmapped, apiSearch]);

  const saveMap = async (
    apiMatchId: string,
    betfairEventId: string,
    source: string,
    confidenceScore?: number
  ): Promise<void> => {
    setSaving(true);
    setError(null);
    setStatusMessage(null);
    try {
      const response = await fetch(`${API_BASE}/api/refdata/statpal-betfair/map`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          date,
          apiMatchId,
          betfairEventId,
          source,
          confidenceScore,
        }),
      });
      if (!response.ok) {
        throw new Error(`Failed to save mapping (${response.status})`);
      }
      setStatusMessage("Mapping saved.");
      await load(date);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to save mapping";
      setError(message);
    } finally {
      setSaving(false);
    }
  };

  const removeMap = async (apiMatchId: string): Promise<void> => {
    setSaving(true);
    setError(null);
    setStatusMessage(null);
    try {
      const response = await fetch(
        `${API_BASE}/api/refdata/statpal-betfair/map?date=${encodeURIComponent(
          date
        )}&apiMatchId=${encodeURIComponent(apiMatchId)}`,
        { method: "DELETE" }
      );
      if (!response.ok) {
        throw new Error(`Failed to delete mapping (${response.status})`);
      }
      setStatusMessage("Mapping removed.");
      await load(date);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to delete mapping";
      setError(message);
    } finally {
      setSaving(false);
    }
  };

  const removeAllMappings = async (): Promise<void> => {
    setSaving(true);
    setError(null);
    setStatusMessage(null);
    try {
      const response = await fetch(
        `${API_BASE}/api/refdata/statpal-betfair/map/all?date=${encodeURIComponent(date)}`,
        { method: "DELETE" }
      );
      if (!response.ok) {
        throw new Error(`Failed to remove all mappings (${response.status})`);
      }
      const payload = (await response.json()) as { deletedCount?: number };
      setStatusMessage(`Removed ${payload.deletedCount ?? 0} mappings.`);
      await load(date);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Failed to remove all mappings";
      setError(message);
    } finally {
      setSaving(false);
    }
  };

  const autoMap = async (): Promise<void> => {
    setSaving(true);
    setError(null);
    setStatusMessage(null);
    try {
      const response = await fetch(
        `${API_BASE}/api/refdata/statpal-betfair/auto-map?date=${encodeURIComponent(date)}`,
        { method: "POST" }
      );
      if (!response.ok) {
        throw new Error(`Failed to run auto-map (${response.status})`);
      }
      const payload = (await response.json()) as { mappedCount?: number };
      setStatusMessage(`Auto-mapped ${payload.mappedCount ?? 0} games.`);
      await load(date);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to run auto-map";
      setError(message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <main className="idmap-page">
      <section className="idmap-panel">
        <p className="idmap-back">
          <Link href="/games/recent-stats">Back to recent stats</Link>
        </p>
        <h1 className="idmap-title">Statpal x Betfair ID Mapping</h1>
        <p className="idmap-subtitle">
          Drag a Statpal/API game card onto a Betfair game card to map IDs into
          `refdata_statpal_betfair`.
        </p>

        <div className="idmap-controls">
          <label htmlFor="idmap-date">Date</label>
          <input
            id="idmap-date"
            type="date"
            value={date}
            onChange={(event) => setDate(event.currentTarget.value)}
          />
          <button type="button" onClick={() => void autoMap()} disabled={saving || loading}>
            Auto-map by home/away names
          </button>
          <button
            type="button"
            onClick={() => void removeAllMappings()}
            disabled={saving || loading}
          >
            Remove all mappings
          </button>
        </div>

        {loading ? <p className="idmap-hint">Loading mapping candidates...</p> : null}
        {saving ? <p className="idmap-hint">Saving...</p> : null}
        {statusMessage ? <p className="idmap-hint idmap-hint--ok">{statusMessage}</p> : null}
        {error ? <p className="idmap-hint idmap-hint--error">{error}</p> : null}

        {payload ? (
          <div className="idmap-stats">
            <span>API: {payload.apiMatches.length}</span>
            <span>Betfair: {payload.betfairMatches.length}</span>
            <span>Mapped: {payload.mappings.length}</span>
            <span>Unmapped API: {apiUnmapped.length}</span>
            <span>Unmapped Betfair: {betfairUnmapped.length}</span>
          </div>
        ) : null}

        <section className="idmap-grid">
          <div className="idmap-col">
            <h2>Betfair Games</h2>
            <input
              className="idmap-search"
              type="search"
              value={betfairSearch}
              onChange={(event) => setBetfairSearch(event.currentTarget.value)}
              placeholder="Search Betfair games"
            />
            <ul className="idmap-list">
              {filteredBetfairUnmapped.map((betfair) => (
                <li
                  key={betfair.betfairEventId}
                  className={`idmap-card idmap-card--betfair ${
                    dragApiId ? "idmap-card--drop-ready" : ""
                  }`}
                  onDragOver={(event) => event.preventDefault()}
                  onDrop={(event) => {
                    event.preventDefault();
                    const apiMatchId = event.dataTransfer.getData("text/plain");
                    if (!apiMatchId) {
                      return;
                    }
                    void saveMap(apiMatchId, betfair.betfairEventId, "manual-dnd");
                  }}
                >
                  <p className="idmap-name">{betfair.displayName}</p>
                  <p className="idmap-meta">{betfair.leagueName}</p>
                  <p className="idmap-id">betfairEventId: {betfair.betfairEventId}</p>
                </li>
              ))}
            </ul>
          </div>

          <div className="idmap-col">
            <h2>Statpal / API Games</h2>
            <input
              className="idmap-search"
              type="search"
              value={apiSearch}
              onChange={(event) => setApiSearch(event.currentTarget.value)}
              placeholder="Search Statpal/API games"
            />
            <ul className="idmap-list">
              {filteredApiUnmapped.map((api) => (
                <li
                  key={api.apiMatchId}
                  className="idmap-card idmap-card--api"
                  draggable
                  onDragStart={(event) => {
                    setDragApiId(api.apiMatchId);
                    event.dataTransfer.setData("text/plain", api.apiMatchId);
                  }}
                  onDragEnd={() => setDragApiId(null)}
                >
                  <p className="idmap-name">{api.displayName}</p>
                  <p className="idmap-meta">{api.leagueName}</p>
                  <p className="idmap-id">apiMatchId: {api.apiMatchId}</p>
                </li>
              ))}
            </ul>
          </div>
        </section>

        {payload ? (
          <section className="idmap-mapped">
            <h2>Current Mappings</h2>
            <div className="idmap-table-wrap">
              <table className="idmap-table">
                <thead>
                  <tr>
                    <th>API Match</th>
                    <th>Betfair Match</th>
                    <th>Source</th>
                    <th>Confidence</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {payload.mappings.map((mapping) => (
                    <tr key={`${mapping.apiMatchId}-${mapping.betfairEventId}`}>
                      <td>
                        {mapping.apiHomeTeam} vs {mapping.apiAwayTeam}
                        <div className="idmap-id">api: {mapping.apiMatchId}</div>
                      </td>
                      <td>
                        {mapping.betfairHomeTeam} vs {mapping.betfairAwayTeam}
                        <div className="idmap-id">betfair: {mapping.betfairEventId}</div>
                      </td>
                      <td>{mapping.source || "-"}</td>
                      <td>
                        {mapping.confidenceScore == null
                          ? "-"
                          : mapping.confidenceScore.toFixed(2)}
                      </td>
                      <td>
                        <button
                          type="button"
                          onClick={() => void removeMap(mapping.apiMatchId)}
                          disabled={saving}
                        >
                          Remove
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        ) : null}
      </section>
    </main>
  );
}
