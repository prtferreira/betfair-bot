import React from "react";

interface Day {
  iso: string;
  label: string;
}

interface DayTabsProps {
  days: Day[];
  selectedDay: string;
  onSelect: (iso: string) => void;
}

export default function DayTabs({ days, selectedDay, onSelect }: DayTabsProps) {
  return (
    <section className="tabs">
      <div className="tabs__rail">
        {days.map((day) => (
          <button
            key={day.iso}
            className={`tab ${selectedDay === day.iso ? "tab--active" : ""}`}
            type="button"
            onClick={() => onSelect(day.iso)}
          >
            <span className="tab__label">{day.label}</span>
            <span className="tab__date">{day.iso}</span>
          </button>
        ))}
      </div>
    </section>
  );
}

