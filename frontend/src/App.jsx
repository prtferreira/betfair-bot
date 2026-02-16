import React, { useMemo, useState } from "react";
import DayTabs from "./components/DayTabs.jsx";
import GameList from "./components/GameList.jsx";
import LoginPage from "./components/LoginPage.jsx";
import LoginResultPage from "./components/LoginResultPage.jsx";
import FootballEventsPage from "./components/FootballEventsPage.jsx";
import FootballEventsAllPage from "./components/FootballEventsAllPage.jsx";
import TodayOddsPage from "./components/TodayOddsPage.jsx";
import InPlayBrazilSerieAPage from "./components/InPlayBrazilSerieAPage.jsx";
import BestMatchesPage from "./components/BestMatchesPage.jsx";
import BestMatchesToLayPage from "./components/BestMatchesToLayPage.jsx";
import BestStrategyPage from "./components/BestStrategyPage.jsx";
import BalancedMatchesPage from "./components/BalancedMatchesPage.jsx";
import InPlayStatusPage from "./components/InPlayStatusPage.jsx";
import MainStrategyPage from "./components/MainStrategyPage.jsx";

const DAYS_TO_SHOW = 7;

function buildDayList() {
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

export default function App() {
  const days = useMemo(buildDayList, []);
  const [selectedDay, setSelectedDay] = useState(days[0].iso);
  const [page, setPage] = useState("login");
  const [loginResult, setLoginResult] = useState(null);

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
            <GameList date={selectedDay} apiPath="/api/betfair/football" />
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
          onResult={(result) => {
            setLoginResult(result);
            setPage("result");
          }}
        />
      )}
    </div>
  );
}

