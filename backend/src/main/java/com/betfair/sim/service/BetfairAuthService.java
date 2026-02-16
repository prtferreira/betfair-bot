package com.betfair.sim.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class BetfairAuthService {
  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final String authBaseUrl;
  private final String appKey;
  private final String username;
  private final String password;

  public BetfairAuthService(
      RestTemplateBuilder restTemplateBuilder,
      ObjectMapper objectMapper,
      @Value("${betfair.auth.base-url:https://identitysso.betfair.com/api}")
          String authBaseUrl,
      @Value("${betfair.app-key:}") String appKey,
      @Value("${betfair.username:}") String username,
      @Value("${betfair.password:}") String password) {
    this.restTemplate = restTemplateBuilder.build();
    this.objectMapper = objectMapper;
    this.authBaseUrl = authBaseUrl;
    this.appKey = appKey;
    this.username = username;
    this.password = password;
  }

  public BetfairLoginResult login() {
    if (appKey.isBlank() || username.isBlank() || password.isBlank()) {
      return BetfairLoginResult.failed("Missing Betfair credentials.");
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    headers.set("X-Application", appKey);

    String body = "username=" + encode(username) + "&password=" + encode(password);
    HttpEntity<String> request = new HttpEntity<>(body, headers);

    try {
      String response =
          restTemplate.postForObject(authBaseUrl + "/login", request, String.class);
      if (response == null || response.isBlank()) {
        return BetfairLoginResult.failed("Empty response from Betfair.");
      }
      return parseResponse(response);
    } catch (Exception ex) {
      return BetfairLoginResult.failed("Betfair login failed.");
    }
  }

  private BetfairLoginResult parseResponse(String response) throws Exception {
    JsonNode node = objectMapper.readTree(response);
    String status = node.path("status").asText("UNKNOWN");
    String token = node.path("token").asText("");
    String error = node.path("error").asText("");
    if ("SUCCESS".equalsIgnoreCase(status) && !token.isBlank()) {
      return BetfairLoginResult.success(token);
    }
    String message = error.isBlank() ? "Betfair login returned " + status : error;
    return BetfairLoginResult.failed(message);
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  public static class BetfairLoginResult {
    private final boolean success;
    private final String token;
    private final String message;

    private BetfairLoginResult(boolean success, String token, String message) {
      this.success = success;
      this.token = token;
      this.message = message;
    }

    public static BetfairLoginResult success(String token) {
      return new BetfairLoginResult(true, token, "OK");
    }

    public static BetfairLoginResult failed(String message) {
      return new BetfairLoginResult(false, "", message);
    }

    public boolean isSuccess() {
      return success;
    }

    public String getToken() {
      return token;
    }

    public String getMessage() {
      return message;
    }
  }
}
