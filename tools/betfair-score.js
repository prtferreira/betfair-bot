#!/usr/bin/env node
"use strict";

const { chromium } = require("playwright");

function detectMinuteFromDom() {
  const minuteRegex = /^\d{1,3}(?:\+\d{1,2})?['’]$/;
  const direct = Array.from(document.querySelectorAll("span"))
    .map((s) => ({
      cls: String(s.className || ""),
      txt: String(s.textContent || "").trim().replace(/’/g, "'"),
    }))
    .filter((x) => x.txt)
    .find(
      (x) =>
        minuteRegex.test(x.txt) &&
        !x.cls.includes("match-time") &&
        !x.cls.includes("halftime-fulltime")
    );
  if (direct) return direct.txt;

  const status = Array.from(document.querySelectorAll("span"))
    .map((s) => String(s.textContent || "").trim())
    .find((txt) => txt === "HT" || txt === "FT" || txt === "Finished");
  return String(status || "").replace(/’/g, "'");
}

async function main() {
  const url = process.argv[2];
  if (!url) {
    process.stderr.write("Missing URL argument\n");
    process.exit(1);
  }

  const browser = await chromium.launch({ headless: true });
  try {
    const context = await browser.newContext({
      userAgent:
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
      locale: "en-GB",
      timezoneId: "UTC",
    });
    const page = await context.newPage();
    await page.route("**/*", (route) => {
      const type = route.request().resourceType();
      if (type === "image" || type === "font" || type === "media" || type === "stylesheet") {
        route.abort();
      } else {
        route.continue();
      }
    });
    await page.goto(url, { waitUntil: "domcontentloaded", timeout: 30000 });
    await page.locator("span.score").first().waitFor({ timeout: 6000 }).catch(() => null);
    await page.waitForTimeout(700);

    // Betfair renders score in a dynamic span.score on live events.
    const scoreText = await page
      .locator("span.score")
      .first()
      .textContent()
      .catch(() => "");
    const cleanScore = String(scoreText || "").trim().replace(/\s+/g, "");
    const minute = await page.evaluate(detectMinuteFromDom).catch(() => "");
    const payload = {
      score: /^\d+-\d+$/.test(cleanScore) ? cleanScore : "",
      minute: String(minute || "").trim(),
    };
    process.stdout.write(JSON.stringify(payload));
  } finally {
    await browser.close();
  }
}

main().catch((err) => {
  process.stderr.write(String(err && err.message ? err.message : err));
  process.exit(2);
});
