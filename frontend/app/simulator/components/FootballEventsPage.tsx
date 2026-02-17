import React, { useMemo, useState } from "react";
import DayTabs from "./DayTabs";
import GameList from "./GameList";

const DAYS_TO_SHOW = 7;

interface Day {
  iso: string;
  label: string;
}

interface FootballEventsPageProps {
  onBack: () => void;
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

export default function FootballEventsPage({ onBack }: FootballEventsPageProps) {
  const days = useMemo(buildDayList, []);
  const [selectedDay, setSelectedDay] = useState<string>(days[0].iso);

  return (
    <>
      <header className="hero">
        <div>
          <p className="eyebrow">Betfair Trade Simulator</p>
          <h1>Football Betfair Events</h1>
          <p className="subhead">
            Events returned directly from Betfair listEvents for football.
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

      <DayTabs days={days} selectedDay={selectedDay} onSelect={setSelectedDay} />

      <main>
        <GameList date={selectedDay} apiPath="http://localhost:8089/api/betfair/football" />
      </main>
    </>
  );
}
