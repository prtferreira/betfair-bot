package com.betfair.sim.service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class BetfairSessionStore {
  private final AtomicReference<String> sessionToken = new AtomicReference<>("");
  private final AtomicReference<Instant> lastUpdated = new AtomicReference<>(Instant.EPOCH);

  public void updateSessionToken(String token) {
    sessionToken.set(token == null ? "" : token);
    lastUpdated.set(Instant.now());
  }

  public String getSessionToken() {
    return sessionToken.get();
  }

  public Instant getLastUpdated() {
    return lastUpdated.get();
  }
}
