package com.betfair.sim.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BetfairSessionBootstrap implements ApplicationRunner {
  private static final Logger LOGGER = LoggerFactory.getLogger(BetfairSessionBootstrap.class);

  private final BetfairApiClient betfairApiClient;
  private final BetfairAuthService betfairAuthService;
  private final BetfairSessionStore betfairSessionStore;

  public BetfairSessionBootstrap(
      BetfairApiClient betfairApiClient,
      BetfairAuthService betfairAuthService,
      BetfairSessionStore betfairSessionStore) {
    this.betfairApiClient = betfairApiClient;
    this.betfairAuthService = betfairAuthService;
    this.betfairSessionStore = betfairSessionStore;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (betfairApiClient.hasSessionToken()) {
      return;
    }

    BetfairAuthService.BetfairLoginResult result = betfairAuthService.login();
    if (result.isSuccess()) {
      betfairSessionStore.updateSessionToken(result.getToken());
      LOGGER.info("Betfair auto-login on startup succeeded.");
    } else {
      LOGGER.warn("Betfair auto-login on startup failed: {}", result.getMessage());
    }
  }
}
