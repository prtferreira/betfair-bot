"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import "./markets.css";

interface EventMarket {
  marketId: string;
  marketName?: string;
  marketType?: string;
  startTime?: string;
  selections?: EventSelection[];
}

interface EventSelection {
  selectionId: number;
  selectionName?: string;
  backOdds?: number;
  layOdds?: number;
}

interface PageProps {
  params: Promise<{ eventId: string }>;
}

const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL?.trim() || "http://localhost:8089";

const DEFAULT_MARKET_TYPES = [
  "CORRECT_SCORE",
  "HALF_TIME",
  "HALF_TIME_SCORE",
  "MATCH_ODDS",
  "OVER_UNDER_05",
  "OVER_UNDER_15",
  "OVER_UNDER_25",
  "OVER_UNDER_35",
  "OVER_UNDER_45",
  "OVER_UNDER_55",
  "OVER_UNDER_65",
  "OVER_UNDER_75",
  "OVER_UNDER_85",
];

function toEpochMs(startTime?: string): number {
  if (!startTime) return Number.MAX_SAFE_INTEGER;
  const millis = Date.parse(startTime);
  if (!Number.isNaN(millis)) return millis;
  return Number.MAX_SAFE_INTEGER;
}

function normalizeMarketType(value?: string): string {
  return (value || "").trim().toUpperCase();
}

function parseMarketTypes(raw: string): string[] {
  return Array.from(
    new Set(
      raw
        .split(",")
        .map((item) => normalizeMarketType(item))
        .filter((item) => item.length > 0)
    )
  );
}

export default function EventMarketsPage({ params }: PageProps) {
  const [eventId, setEventId] = useState<string>("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [markets, setMarkets] = useState<EventMarket[]>([]);
  const [selected, setSelected] = useState<Record<string, boolean>>({});
  const search = useSearchParams();
  const eventName = search.get("name") || "Event";
  const requestedTypes = useMemo(() => {
    const fromUrl = search.get("types");
    if (fromUrl && fromUrl.trim().length > 0) {
      return parseMarketTypes(fromUrl);
    }
    const fromEnv = process.env.NEXT_PUBLIC_MARKET_TYPES;
    if (fromEnv && fromEnv.trim().length > 0) {
      return parseMarketTypes(fromEnv);
    }
    return DEFAULT_MARKET_TYPES;
  }, [search]);

  useEffect(() => {
    let cancelled = false;

    const init = async (): Promise<void> => {
      const resolved = await params;
      if (cancelled) return;
      setEventId(resolved.eventId);
    };

    void init();
    return () => {
      cancelled = true;
    };
  }, [params]);

  useEffect(() => {
    if (!eventId) return;

    let cancelled = false;
    setLoading(true);
    setError(null);

    const url = new URL(`${API_BASE}/api/betfair/event-markets`);
    url.searchParams.set("eventId", eventId);

    fetch(url.toString())
      .then((response) => {
        if (!response.ok) {
          throw new Error(`Failed to load market IDs (${response.status})`);
        }
        return response.json() as Promise<EventMarket[]>;
      })
      .then((data) => {
        if (cancelled) return;
        setMarkets(data);
        const nextSelected: Record<string, boolean> = {};
        data.forEach((market) => {
          (market.selections || []).forEach((selection) => {
            nextSelected[`${market.marketId}:${selection.selectionId}`] = false;
          });
        });
        setSelected(nextSelected);
      })
      .catch((err: Error) => {
        if (cancelled) return;
        setError(err.message);
        setMarkets([]);
      })
      .finally(() => {
        if (cancelled) return;
        setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [eventId, requestedTypes]);

  const sortedMarkets = useMemo(() => {
    const allowed = new Set(requestedTypes.map((item) => normalizeMarketType(item)));
    return [...markets]
      .filter((market) => {
        if (allowed.size === 0) {
          return true;
        }
        return allowed.has(normalizeMarketType(market.marketType));
      })
      .sort((a, b) => {
      const delta = toEpochMs(a.startTime) - toEpochMs(b.startTime);
      if (delta !== 0) return delta;
      return (a.marketName || "").localeCompare(b.marketName || "");
    });
  }, [markets]);

  const toggleSelection = (marketId: string, selectionId: number): void => {
    const key = `${marketId}:${selectionId}`;
    setSelected((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  return (
    <main className="markets-page">
      <section className="markets-panel">
        <p className="markets-back">
          <Link href="/games">Back to games</Link>
        </p>
        <h1 className="markets-title">Market IDs</h1>
        <p className="markets-subtitle">{eventName}</p>
        <p className="markets-subtitle">
          eventId: <strong>{eventId}</strong>
        </p>

        {loading ? <p className="markets-hint">Loading...</p> : null}
        {error ? <p className="markets-hint markets-hint--error">{error}</p> : null}
        {!loading && !error && sortedMarkets.length === 0 ? (
          <p className="markets-hint">No markets found for this event.</p>
        ) : null}

        {!loading && !error && sortedMarkets.length > 0 ? (
          <ul className="markets-list">
            {sortedMarkets.map((market) => (
              <li key={market.marketId} className="markets-row">
                <p className="markets-row-title">{market.marketName || "Unnamed market"}</p>
                <p className="markets-row-meta">marketId: {market.marketId}</p>
                {market.marketType ? (
                  <p className="markets-row-meta">type: {market.marketType}</p>
                ) : null}
                {market.selections && market.selections.length > 0 ? (
                  <div className="selection-box">
                    {market.selections.map((selection) => {
                      const key = `${market.marketId}:${selection.selectionId}`;
                      return (
                        <label key={key} className="selection-row">
                          <input
                            type="checkbox"
                            checked={selected[key] || false}
                            onChange={() =>
                              toggleSelection(market.marketId, selection.selectionId)
                            }
                          />
                          <span className="selection-name">
                            {selection.selectionName || `Selection ${selection.selectionId}`}
                          </span>
                          <span className="selection-odds">
                            Back: {selection.backOdds ?? "-"} | Lay: {selection.layOdds ?? "-"}
                          </span>
                        </label>
                      );
                    })}
                  </div>
                ) : (
                  <p className="markets-row-meta">No selections available.</p>
                )}
              </li>
            ))}
          </ul>
        ) : null}
      </section>
    </main>
  );
}
