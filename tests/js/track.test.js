/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
"use strict";
// Run in India time: the original bug was a zone-less timestamp being read as the viewer's LOCAL time
// (making every update look 5h30m old in IST). In a UTC test machine that bug would be invisible.
process.env.TZ = "Asia/Kolkata";

const test = require("node:test");
const assert = require("node:assert/strict");
const { loadPageScript } = require("./load-page-script");

// A straight north-south route, one stop every ~1.11 km (0.01 degree of latitude),
// at a fixed longitude so distances are easy to reason about.
function makeStops() {
  const names = ["A", "B", "C", "D"];
  return names.map((name, i) => ({
    stopName: name,
    latitude: 26.0 + i * 0.01,
    longitude: 89.0,
    distanceFromStartKm: i * 1.1054,
  }));
}

function freshPage() {
  return loadPageScript("track.js", { search: "?rideId=1&routeKey=A_D" });
}

/* ─── parseTs ─────────────────────────────────────────────────── */
test("parseTs: ISO instant with Z", () => {
  const { eval: run } = freshPage();
  assert.equal(run('parseTs("2026-09-19T10:15:03Z")'), Date.UTC(2026, 8, 19, 10, 15, 3));
});

test("parseTs: zone-less string is treated as UTC, not the viewer's local time", () => {
  const { eval: run } = freshPage();
  assert.equal(run('parseTs("2026-09-19T10:15:03")'), Date.UTC(2026, 8, 19, 10, 15, 3));
});

test("parseTs: explicit offset is respected", () => {
  const { eval: run } = freshPage();
  assert.equal(run('parseTs("2026-09-19T15:45:03+05:30")'), Date.UTC(2026, 8, 19, 10, 15, 3));
});

test("parseTs: [y,m,d,h,mi,s,nano] array is read as UTC", () => {
  const { eval: run } = freshPage();
  assert.equal(run("parseTs([2026, 9, 19, 10, 15, 3, 500000000])"), Date.UTC(2026, 8, 19, 10, 15, 3, 500));
});

test("parseTs: numbers pass through, null/undefined give NaN", () => {
  const { eval: run } = freshPage();
  assert.equal(run("parseTs(12345)"), 12345);
  assert.ok(Number.isNaN(run("parseTs(null)")));
  assert.ok(Number.isNaN(run("parseTs(undefined)")));
});

/* ─── haversineM / projectOnSegment ───────────────────────────── */
test("haversineM: same point is 0, one degree of latitude is about 111 km", () => {
  const { eval: run } = freshPage();
  assert.equal(run("haversineM(26, 89, 26, 89)"), 0);
  const d = run("haversineM(26, 89, 27, 89)");
  assert.ok(d > 110000 && d < 112000, `got ${d}`);
});

test("projectOnSegment: clamps to the ends and finds the middle", () => {
  const { eval: run } = freshPage();
  const a = "{latitude:26, longitude:89}", b = "{latitude:26.01, longitude:89}";
  const mid = run(`projectOnSegment({latitude:26.005, longitude:89}, ${a}, ${b})`);
  assert.ok(Math.abs(mid.t - 0.5) < 0.01);
  assert.ok(mid.distM < 1);
  assert.equal(run(`projectOnSegment({latitude:25.9, longitude:89}, ${a}, ${b})`).t, 0);   // before the start
  assert.equal(run(`projectOnSegment({latitude:26.5, longitude:89}, ${a}, ${b})`).t, 1);   // beyond the end
  const side = run(`projectOnSegment({latitude:26.005, longitude:89.01}, ${a}, ${b})`);      // ~1 km sideways
  assert.ok(side.distM > 900 && side.distM < 1200, `got ${side.distM}`);
});

/* ─── calcETA ─────────────────────────────────────────────────── */
test("calcETA: behind the bus -> null, close -> Arriving, minutes, hours", () => {
  const { eval: run } = freshPage();
  assert.equal(run("calcETA(5, 5)"), null);          // bus is at the stop
  assert.equal(run("calcETA(4, 5)"), null);          // bus already passed
  assert.equal(run("calcETA(5.4, 5)"), "Arriving");  // 0.4 km at 30 km/h = under a minute
  assert.equal(run("calcETA(10, 5)"), "10 min");     // 5 km at 30 km/h
  assert.equal(run("calcETA(45, 5)"), "1h 20m");     // 40 km at 30 km/h
});

/* ─── locateBus ───────────────────────────────────────────────── */
function locate(page, loc) {
  page.sandbox.__stops = makeStops();
  page.sandbox.__loc = loc;
  return page.eval("locateBus(__stops, __loc)");
}

test("locateBus: standing on a stop snaps to it", () => {
  const page = freshPage();
  const r = locate(page, { latitude: 26.01, longitude: 89.0, accuracy: 10 });
  assert.equal(r.atIdx, 1);
  assert.equal(r.passedThrough, 0);
  assert.equal(r.nextIdx, 2);
  assert.equal(r.progressKm, makeStops()[1].distanceFromStartKm);
});

test("locateBus: halfway between two stops is 'between stops' with interpolated progress", () => {
  const page = freshPage();
  const stops = makeStops();
  const r = locate(page, { latitude: 26.015, longitude: 89.0, accuracy: 10 });
  assert.equal(r.atIdx, -1);
  assert.equal(r.passedThrough, 1);
  assert.equal(r.nextIdx, 2);
  const expected = (stops[1].distanceFromStartKm + stops[2].distanceFromStartKm) / 2;
  assert.ok(Math.abs(r.progressKm - expected) < 0.02, `got ${r.progressKm}, expected about ${expected}`);
});

test("locateBus: at the first stop nothing has been passed yet", () => {
  const page = freshPage();
  const r = locate(page, { latitude: 26.0, longitude: 89.0, accuracy: 5 });
  assert.equal(r.atIdx, 0);
  assert.equal(r.passedThrough, -1);
  assert.equal(r.nextIdx, 1);
});

test("locateBus: at the last stop there is no next stop", () => {
  const page = freshPage();
  const r = locate(page, { latitude: 26.03, longitude: 89.0, accuracy: 5 });
  assert.equal(r.atIdx, 3);
  assert.equal(r.nextIdx, -1);
});

test("locateBus: a fix far from the route is reported off-route", () => {
  const page = freshPage();
  const r = locate(page, { latitude: 26.015, longitude: 89.5, accuracy: 10 });   // ~50 km sideways
  assert.equal(r.offRoute, true);
  assert.ok(r.offKm > 40);
});

test("locateBus: a poor GPS accuracy widens the arrival radius (capped at 500 m)", () => {
  const page = freshPage();
  // ~330 m north of stop B: outside the 200 m default radius, inside a 400 m accuracy radius
  const loc = { latitude: 26.013, longitude: 89.0 };
  assert.equal(locate(page, { ...loc, accuracy: 10 }).atIdx, -1);
  const wide = locate(freshPage(), { ...loc, accuracy: 400 });
  assert.equal(wide.atIdx, 1);
  assert.equal(locate(freshPage(), { ...loc, accuracy: 5000 }).radiusM, 500);   // capped
});

test("locateBus: small backwards GPS wobble does not move the bus backwards", () => {
  const page = freshPage();
  const first = locate(page, { latitude: 26.016, longitude: 89.0, accuracy: 10 });
  // ~220 m back: less than the 0.4 km jitter limit, and outside any stop radius
  const wobble = locate(page, { latitude: 26.014, longitude: 89.0, accuracy: 10 });
  assert.equal(wobble.progressKm, first.progressKm);
});

test("locateBus: a big move backwards (more than the jitter limit) is believed", () => {
  const page = freshPage();
  const first = locate(page, { latitude: 26.025, longitude: 89.0, accuracy: 10 });
  const back = locate(page, { latitude: 26.0125, longitude: 89.0, accuracy: 10 });   // ~1.4 km back
  assert.ok(back.progressKm < first.progressKm - 1);
});
