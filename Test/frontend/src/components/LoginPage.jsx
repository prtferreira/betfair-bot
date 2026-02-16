import React, { useState } from "react";

export default function LoginPage({ onResult }) {
  const [status, setStatus] = useState(null);

  const handleLogin = () => {
    setStatus({ state: "loading", message: "Logging in..." });
    fetch("/api/betfair/login", { method: "POST" })
      .then((response) => response.json())
      .then((data) => {
        if (data.status === "SUCCESS") {
          setStatus({ state: "success", message: data.message });
          onResult({ status: "SUCCESS", message: data.message });
        } else {
          const message = data.message || "Login failed";
          setStatus({ state: "error", message });
          onResult({ status: "FAILED", message });
        }
      })
      .catch(() => {
        setStatus({ state: "error", message: "Login failed" });
        onResult({ status: "FAILED", message: "Login failed" });
      });
  };

  return (
    <section className="login">
      <div className="login__panel">
        <p className="eyebrow">Betfair Trade Simulator</p>
        <h1>Connect your Betfair session</h1>
        <p className="subhead">
          Login initializes a session token on the server. Once authenticated,
          you will be redirected to the game list.
        </p>
        <button className="action-button" type="button" onClick={handleLogin}>
          Login to Betfair
        </button>
        {status && (
          <p className={`login-status login-status--${status.state}`}>
            {status.message}
          </p>
        )}
      </div>
    </section>
  );
}
