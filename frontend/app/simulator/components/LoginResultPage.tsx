import React from "react";

interface LoginResultPageProps {
  result: { status: "SUCCESS" | "FAILED"; message: string } | null;
  onContinue: () => void;
  onEvents: () => void;
  onEventsAll: () => void;
  onTodayOdds: () => void;
  onMainStrategy: () => void;
  onInPlayBrasil: () => void;
  onBestMatches: () => void;
  onBestMatchesToLay: () => void;
  onBestStrategy: () => void;
  onBalancedMatches: () => void;
  onBalancedStatus: () => void;
  onRetry: () => void;
}

export default function LoginResultPage({
  result,
  onContinue,
  onEvents,
  onEventsAll,
  onTodayOdds,
  onMainStrategy,
  onInPlayBrasil,
  onBestMatches,
  onBestMatchesToLay,
  onBestStrategy,
  onBalancedMatches,
  onBalancedStatus,
  onRetry
}: LoginResultPageProps) {
  const status = result?.status || "FAILED";
  const message = result?.message || "Login failed.";
  const isSuccess = status === "SUCCESS";

  return (
    <section className="login">
      <div className="login__panel">
        <p className="eyebrow">Betfair Trade Simulator</p>
        <h1>{isSuccess ? "Login successful" : "Login failed"}</h1>
        <p className="subhead">{message}</p>
        <div className="login-actions">
          {isSuccess ? (
            <>
              <button className="action-button" type="button" onClick={onContinue}>
                Continue to games
              </button>
              <button className="action-button action-button--ghost" type="button" onClick={onEvents}>
                Football Betfair Events
              </button>
              <button className="action-button action-button--ghost" type="button" onClick={onEventsAll}>
                Football Events (All)
              </button>
              <button className="action-button action-button--ghost" type="button" onClick={onTodayOdds}>
                Today's odds board
              </button>
              <button className="action-button action-button--ghost" type="button" onClick={onMainStrategy}>
                Main strategy board
              </button>
              <button className="action-button action-button--ghost" type="button" onClick={onInPlayBrasil}>
                In-play Brazil Serie A
              </button>
              <button className="action-button action-button--ghost" type="button" onClick={onBestMatches}>
                Best Matches
              </button>
              <button className="action-button action-button--ghost" type="button" onClick={onBestMatchesToLay}>
                Best Matches to Lay
              </button>
              <button className="action-button action-button--ghost" type="button" onClick={onBestStrategy}>
                Best Strategy
              </button>
              <button className="action-button action-button--ghost" type="button" onClick={onBalancedMatches}>
                Balanced Matches
              </button>
              <button className="action-button action-button--ghost" type="button" onClick={onBalancedStatus}>
                In-Play Status
              </button>
            </>
          ) : (
            <button className="action-button" type="button" onClick={onRetry}>
              Try again
            </button>
          )}
        </div>
      </div>
    </section>
  );
}
