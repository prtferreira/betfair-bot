package com.betfair.sim.service;

import java.nio.file.Path;
import java.nio.file.Paths;

final class FollowedGamesPathResolver {
  private FollowedGamesPathResolver() {}

  static Path resolve(String configuredPath) {
    String value = configuredPath == null ? "backend/data" : configuredPath.trim();
    if (value.isBlank()) {
      value = "backend/data";
    }

    Path raw = Paths.get(value);
    if (raw.isAbsolute()) {
      return raw.normalize();
    }

    Path cwd = Paths.get("").toAbsolutePath().normalize();
    Path normalizedRelative = raw.normalize();

    // If running from /backend, avoid backend/backend/data and use backend/data.
    if (cwd.getFileName() != null
        && "backend".equalsIgnoreCase(cwd.getFileName().toString())
        && normalizedRelative.getNameCount() > 0
        && "backend".equalsIgnoreCase(normalizedRelative.getName(0).toString())) {
      if (normalizedRelative.getNameCount() == 1) {
        normalizedRelative = Paths.get(".");
      } else {
        normalizedRelative = normalizedRelative.subpath(1, normalizedRelative.getNameCount());
      }
    }

    return cwd.resolve(normalizedRelative).normalize();
  }
}
