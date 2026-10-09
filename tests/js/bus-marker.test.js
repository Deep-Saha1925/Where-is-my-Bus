/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { loadPageScript } = require("./load-page-script");

const STATIC = path.join(__dirname, "..", "..", "src", "main", "resources", "static");

// The tracking page with persistent fake elements so the rendered timeline can be read back
function trackingPage() {
  const elements = {};
  const el = (id) => (elements[id] ||= {
    id, value: "", textContent: "", innerText: "", innerHTML: "", className: "", disabled: false, href: "",
    style: {}, dataset: {}, children: [],
    classList: { add() {}, remove() {}, contains() { return false; }, toggle() {} },
    addEventListener() {}, focus() {}, querySelectorAll() { return []; },
    getBoundingClientRect() { return { top: 0, height: 0 }; },
  });
  const document = {
    readyState: "complete", visibilityState: "visible",
    getElementById: el, querySelector: () => el("_q"), querySelectorAll: () => [],
    createElement: () => el("_new"), addEventListener() {}, body: el("_body"),
  };
  const page = loadPageScript("track.js", { search: "?rideId=1&routeKey=A_C", globals: { document } });
  page.sandbox.__stops = [
    { stopName: "Alpha",   latitude: 26.00, longitude: 89.0, distanceFromStartKm: 0,   slackTimeMin: 0 },
    { stopName: "Bravo",   latitude: 26.01, longitude: 89.0, distanceFromStartKm: 1.1, slackTimeMin: 0 },
    { stopName: "Charlie", latitude: 26.02, longitude: 89.0, distanceFromStartKm: 2.2, slackTimeMin: 0 },
  ];
  page.eval("fullRouteStops = __stops; routeStops = __stops;");
  return { page, el };
}

function busAt(page, latitude) {
  page.sandbox.__loc = { rideId: 1, latitude, longitude: 89.0, accuracy: 10, timestamp: new Date().toISOString() };
  page.eval("applyLocation(__loc)");
}

const MARKER = '<span class="bus-icon"><img src="/images/logo.png" alt="Bus"></span>';
const countOf = (html) => html.split(MARKER).length - 1;

test("bus marker: at a stop, the logo is drawn once, inside its circle", () => {
  const { page, el } = trackingPage();
  busAt(page, 26.01);                       // standing on Bravo
  assert.equal(countOf(el("stopList").innerHTML), 1);
});

test("bus marker: between two stops, the logo is drawn once on the road, with no old inline offset", () => {
  const { page, el } = trackingPage();
  busAt(page, 26.015);                      // halfway between Bravo and Charlie
  const html = el("stopList").innerHTML;
  assert.equal(countOf(html), 1);
  assert.doesNotMatch(html, /bus-icon[^>]*top:calc/, "the circle centres itself; an inline top would push it off the line");
});

test("bus marker: the circle is black, round and bigger than the old 28px icon", () => {
  const css = fs.readFileSync(path.join(STATIC, "track.html"), "utf8");
  const rule = css.match(/\.bus-icon\s*\{([^}]*)\}/)[1];
  assert.match(rule, /border-radius:\s*50%/);
  assert.match(rule, /background:\s*#000/);
  const size = Number(rule.match(/width:\s*(\d+)px/)[1]);
  assert.ok(size >= 40, `circle is ${size}px, should be clearly bigger than 28px`);
  assert.doesNotMatch(rule, /left:\s*-22px/, "must be centred on the line, not hung off to the left");
});

test("bus marker: the logo file the page points at really exists in the web folder", () => {
  assert.ok(fs.existsSync(path.join(STATIC, "images", "logo.png")),
    "static/images/logo.png is missing: the root images/ folder is not served by the app");
});
