'use client'

import { useMemo, useState } from "react";
import DayTabs from "./DayTabs";
import GameList from "./GameList";
import LoginPage from "./LoginPage";
import LoginResultPage from "./LoginResultPage";
import FootballEventsPage from "./FootballEventsPage";
import FootballEventsAllPage from "./FootballEventsAllPage";
import TodayOddsPage from "./TodayOddsPage";
import InPlayBrazilSerieAPage from "./InPlayBrazilSerieAPage";
import BestMatchesPage from "./BestMatchesPage";
import BestMatchesToLayPage from "./BestMatchesToLayPage";
import BestStrategyPage from "./BestStrategyPage";
import BalancedMatchesPage from "./BalancedMatchesPage";
import InPlayStatusPage from "./InPlayStatusPage";
import MainStrategyPage from "./MainStrategyPage";

const DAYS_TO_SHOW = 7;

interface Day {
  iso: string;
  label: string;
}

function buildDayList(): Day[] {
  const today = new Date();
  return Array.from({ length: DAYS_TO_SHOW }, (_, index) => {
    const date = new Date(today);
    date.setDate(today.getDate() + index);
    const iso = date.toISOString().slice(0, 10);
    const label = date.toLocaleDateString("en-GB", {
      weekday: "short",
      day: "2-digit",
      month: "short"
    });
    return { iso, label };
  });
}

type Page =
  | "login"
  | "result"
  | "football"
  | "events"
  | "events-all"
  | "today-odds"
  | "main-strategy"
  | "inplay-brasil-serie-a"
  | "best-matches"
  | "best-matches-to-lay"
  | "best-strategy"
  | "balanced-matches"
  | "balanced-status";

export default function App() {
  const days = useMemo(buildDayList, []);
  const [selectedDay, setSelectedDay] = useState<string>(days[0].iso);
  const [page, setPage] = useState<Page>("login");
  const [loginResult, setLoginResult] = useState<any>(null);

  return (
    <div className="app">
      {page === "football" ? (
        <>
          <header className="hero">
            <div>
              <p className="eyebrow">Betfair Trade Simulator</p>
              <h1>Football markets only</h1>
              <p className="subhead">
                Browse Betfair football games by day and run simulated strategies
                against each match.
              </p>
            </div>
            <div className="hero-card">
              <p className="hero-card__title">Strategies online</p>
              <p className="hero-card__value">3 active</p>
              <p className="hero-card__note">Scalp · Swing · Value</p>
              <div className="hero-card__actions">
                <button
                  className="action-button action-button--ghost"
                  type="button"
                  onClick={() => setPage("today-odds")}
                >
                  View today's odds
                </button>
                <button
                  className="action-button action-button--ghost"
                  type="button"
                  onClick={() => setPage("main-strategy")}
                >
                  Main strategy board
                </button>
                <button
                  className="action-button action-button--ghost"
                  type="button"
                  onClick={() => setPage("inplay-brasil-serie-a")}
                >
                  View in-play Brazil Serie A
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
            <GameList date={selectedDay} apiPath="http://localhost:8089/api/betfair/football" />
          </main>
        </>
      ) : page === "events" ? (
        <FootballEventsPage onBack={() => setPage("football")} />
      ) : page === "events-all" ? (
        <FootballEventsAllPage onBack={() => setPage("football")} />
      ) : page === "today-odds" ? (
        <TodayOddsPage onBack={() => setPage("football")} />
      ) : page === "main-strategy" ? (
        <MainStrategyPage onBack={() => setPage("football")} />
      ) : page === "inplay-brasil-serie-a" ? (
        <InPlayBrazilSerieAPage onBack={() => setPage("football")} />
      ) : page === "best-matches" ? (
        <BestMatchesPage onBack={() => setPage("football")} />
      ) : page === "best-matches-to-lay" ? (
        <BestMatchesToLayPage onBack={() => setPage("football")} />
      ) : page === "best-strategy" ? (
        <BestStrategyPage onBack={() => setPage("football")} />
      ) : page === "balanced-matches" ? (
        <BalancedMatchesPage
          onBack={() => setPage("football")}
          onStatus={() => setPage("balanced-status")}
        />
      ) : page === "balanced-status" ? (
        <InPlayStatusPage onBack={() => setPage("football")} />
      ) : page === "result" ? (
        <LoginResultPage
          result={loginResult}
          onContinue={() => setPage("football")}
          onEvents={() => setPage("events")}
          onEventsAll={() => setPage("events-all")}
          onTodayOdds={() => setPage("today-odds")}
          onMainStrategy={() => setPage("main-strategy")}
          onInPlayBrasil={() => setPage("inplay-brasil-serie-a")}
          onBestMatches={() => setPage("best-matches")}
          onBestMatchesToLay={() => setPage("best-matches-to-lay")}
          onBestStrategy={() => setPage("best-strategy")}
          onBalancedMatches={() => setPage("balanced-matches")}
          onBalancedStatus={() => setPage("balanced-status")}
          onRetry={() => setPage("login")}
        />
      ) : (
        <LoginPage
          onResult={(result: any) => {
            setLoginResult(result);
            setPage("result");
          }}
        />
      )}
    </div>
  );
}
