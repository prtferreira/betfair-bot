package com.betfair.sim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BetfairStrategyApplication {
  public static void main(String[] args) {
    SpringApplication.run(BetfairStrategyApplication.class, args);
  }
}
