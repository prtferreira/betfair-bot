import React from "react";
import GameList from "./GameList";

interface FootballEventsAllPageProps {
  onBack: () => void;
}

export default function FootballEventsAllPage({ onBack }: FootballEventsAllPageProps) {
  return (
    <>
      <header className="hero">
        <div>
          <p className="eyebrow">Betfair Trade Simulator</p>
          <h1>Football Events (All)</h1>
          <p className="subhead">
            Events returned by Betfair listEvents with eventTypeIds = 1.
          </p>
        </div>
        <div className="hero-card">
          <p className="hero-card__title">Mode</p>
          <p className="hero-card__value">Events</p>
          <button className="action-button action-button--ghost" type="button" onClick={onBack}>
            Back to games
          </button>
        </div>
      </header>

      <main>
        <GameList apiPath="http://localhost:8089/api/betfair/events" includeDate={false} />
      </main>
    </>
  );
}

