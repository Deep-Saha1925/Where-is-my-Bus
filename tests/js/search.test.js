/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const { loadPageScript } = require("./load-page-script");

// ── passenger bus-number search: query normalisation (index.js) ──
test("normalizeBusNumber ignores case, spaces, dashes and other punctuation", () => {
  const { eval: run } = loadPageScript("index.js");
  assert.equal(run('normalizeBusNumber("WB-23A-1245")'), "wb23a1245");
  assert.equal(run('normalizeBusNumber(" wb 23a  1245 ")'), "wb23a1245");
  assert.equal(run('normalizeBusNumber("WB_23A/1245.")'), "wb23a1245");
  assert.equal(run("normalizeBusNumber(null)"), "");
  assert.equal(run('normalizeBusNumber("---")'), "");
});

test("normalizeBusNumber: different spellings of one bus give the same query", () => {
  const { eval: run } = loadPageScript("index.js");
  assert.equal(run('normalizeBusNumber("test_123")'), run('normalizeBusNumber("TEST 123")'));
});

// ── driver page: distance used for the "current stop" card ──
test("distanceMeters: zero for identical points, ~111 km per degree of latitude", () => {
  const { eval: run } = loadPageScript("driver.js");
  assert.equal(run("distanceMeters(26, 89, 26, 89)"), 0);
  const d = run("distanceMeters(26, 89, 27, 89)");
  assert.ok(d > 110000 && d < 112000, `got ${d}`);
});

// ── driver page: route label (stop names can contain "_") ──
test("routeLabel prefers the server's correctly split names over splitting the key", () => {
  const { eval: run } = loadPageScript("driver.js");
  assert.equal(run('routeLabel({sourceName:"MY_SEAT", destinationName:"WP", routeKey:"MY_SEAT_WP"})'), "MY_SEAT → WP");
  assert.equal(run('routeLabel({routeKey:"A_B"})'), "A → B");
});
