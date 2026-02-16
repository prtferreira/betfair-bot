package com.betfair.sim.controller;

import com.betfair.sim.service.BetfairAuthService;
import com.betfair.sim.service.BetfairApiClient;
import com.betfair.sim.service.BetfairSessionStore;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class BetfairAuthController {
  private final BetfairAuthService betfairAuthService;
  private final BetfairSessionStore sessionStore;
  private final BetfairApiClient betfairApiClient;

  public BetfairAuthController(
      BetfairAuthService betfairAuthService,
      BetfairSessionStore sessionStore,
      BetfairApiClient betfairApiClient) {
    this.betfairAuthService = betfairAuthService;
    this.sessionStore = sessionStore;
    this.betfairApiClient = betfairApiClient;
  }

  @PostMapping("/api/betfair/login")
  public Map<String, String> login() {
    BetfairAuthService.BetfairLoginResult result = betfairAuthService.login();
    if (result.isSuccess()) {
      sessionStore.updateSessionToken(result.getToken());
      return Map.of(
          "status", "SUCCESS",
          "message", "Session token cached",
          "lastUpdated", Instant.now().toString());
    }
    return Map.of("status", "FAILED", "message", result.getMessage());
  }

  @GetMapping("/api/betfair/status")
  public Map<String, String> status() {
    return Map.of(
        "appKeyPresent", String.valueOf(betfairApiClient.hasAppKey()),
        "sessionTokenPresent", String.valueOf(betfairApiClient.hasSessionToken()),
        "lastUpdated", sessionStore.getLastUpdated().toString());
  }
}
