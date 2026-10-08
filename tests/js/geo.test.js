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

const geo = (globals) => loadPageScript("geo-utils.js", { globals });
const plain = (value) => JSON.parse(JSON.stringify(value));

/* ─── distance and nearest ────────────────────────────────────── */
test("distanceM: same point is 0, one degree of latitude is about 111 km", () => {
  const { eval: run } = geo();
  assert.equal(run("Geo.distanceM(26, 89, 26, 89)"), 0);
  const d = run("Geo.distanceM(26, 89, 27, 89)");
  assert.ok(d > 110000 && d < 112000, `got ${d}`);
});

test("nearest: picks the closest point and reports its index and distance", () => {
  const page = geo();
  page.sandbox.__points = [
    { name: "FAR",  latitude: 27.0,  longitude: 89.0 },
    { name: "NEAR", latitude: 26.01, longitude: 89.0 },
    { name: "MID",  latitude: 26.5,  longitude: 89.0 },
  ];
  const hit = page.eval("Geo.nearest(26.0, 89.0, __points)");
  assert.equal(hit.point.name, "NEAR");
  assert.equal(hit.index, 1);
  assert.ok(hit.distanceM > 1000 && hit.distanceM < 1200, `got ${hit.distanceM}`);
});

test("nearest: works with route stops that use latitude/longitude too, and ignores bad entries", () => {
  const page = geo();
  page.sandbox.__stops = [null, { stopName: "NO COORDS" }, { stopName: "OK", latitude: 26.2, longitude: 89.1 }];
  const hit = page.eval("Geo.nearest(26.2, 89.1, __stops)");
  assert.equal(hit.point.stopName, "OK");
  assert.equal(hit.index, 2);
});

test("nearest: nothing to compare against gives null", () => {
  const { eval: run } = geo();
  assert.equal(run("Geo.nearest(26, 89, [])"), null);
  assert.equal(run("Geo.nearest(26, 89, null)"), null);
});

/* ─── formatting ──────────────────────────────────────────────── */
test("formatDistance: metres under 1 km, kilometres after, one decimal below 10 km", () => {
  const { eval: run } = geo();
  assert.equal(run("Geo.formatDistance(350)"), "350 m");
  assert.equal(run("Geo.formatDistance(1234)"), "1.2 km");
  assert.equal(run("Geo.formatDistance(15000)"), "15 km");
  assert.equal(run("Geo.formatDistance(NaN)"), "—");
});

/* ─── error messages ──────────────────────────────────────────── */
test("explainError: every failure gives a helpful, specific message", () => {
  const { eval: run } = geo();
  assert.match(run("Geo.explainError({code: 1})"), /turned off|Allow it/i);
  assert.match(run("Geo.explainError({code: 2})"), /GPS|couldn't be found/i);
  assert.match(run("Geo.explainError({code: 3})"), /too long/i);
  assert.match(run('Geo.explainError({code: "INSECURE"})'), /https/i);
  assert.match(run('Geo.explainError({code: "UNSUPPORTED"})'), /can't share/i);
  assert.match(run("Geo.explainError(undefined)"), /try again/i);
});

/* ─── asking the browser ──────────────────────────────────────── */
function fakeGeolocation(behaviour) {
  const calls = [];
  return {
    calls,
    getCurrentPosition(success, failure, options) {
      calls.push(options);
      behaviour(success, failure);
    },
  };
}

test("request: resolves with latitude, longitude and accuracy", async () => {
  const g = fakeGeolocation((ok) => ok({ coords: { latitude: 26.1, longitude: 89.2, accuracy: 25 } }));
  const page = geo({ navigator: { geolocation: g } });
  const pos = await page.eval("Geo.request()");
  assert.deepEqual(plain(pos), { latitude: 26.1, longitude: 89.2, accuracy: 25 });
  assert.equal(g.calls[0].enableHighAccuracy, true);
});

test("request: a denied permission rejects with the browser's code 1", async () => {
  const g = fakeGeolocation((ok, fail) => fail({ code: 1, message: "denied" }));
  const page = geo({ navigator: { geolocation: g } });
  await assert.rejects(page.eval("Geo.request()"), (err) => err.code === 1);
});

test("request: no geolocation support rejects with UNSUPPORTED, without calling anything", async () => {
  const page = geo({ navigator: {} });
  await assert.rejects(page.eval("Geo.request()"), (err) => err.code === "UNSUPPORTED");
});

test("request: an insecure (non-https) page rejects with INSECURE before asking", async () => {
  const g = fakeGeolocation(() => assert.fail("must not ask on an insecure page"));
  const page = geo({ navigator: { geolocation: g }, isSecureContext: false });
  await assert.rejects(page.eval("Geo.request()"), (err) => err.code === "INSECURE");
  assert.equal(g.calls.length, 0);
});

/* ─── the home page "Use my location" button (index.js + geo-utils.js together) ─── */
function homePage({ stops, geolocation, fetchFails = false }) {
  const elements = {};
  const el = (id) => (elements[id] ||= {
    id, value: "", textContent: "", className: "", disabled: false, focused: false,
    style: {}, children: [],
    classList: { _set: new Set(), add(c) { this._set.add(c); }, remove(c) { this._set.delete(c); }, contains(c) { return this._set.has(c); }, toggle() {} },
    addEventListener() {}, focus() { this.focused = true; }, querySelectorAll() { return []; },
  });
  const document = {
    readyState: "complete", visibilityState: "visible",
    getElementById: el, querySelector: () => el("_q"), querySelectorAll: () => [],
    createElement: () => el("_new"), addEventListener() {}, body: el("_body"),
  };
  const fetch = (url) => {
    if (String(url).includes("/api/routes/all-stops")) {
      return fetchFails
        ? Promise.resolve({ ok: false, status: 500, json: async () => ({}) })
        : Promise.resolve({ ok: true, json: async () => stops });
    }
    return Promise.resolve({ ok: true, json: async () => ({ stops: [] }) });
  };
  const page = loadPageScript("index.js", {
    preload: ["geo-utils.js"],
    globals: { document, fetch, navigator: { geolocation } },
  });
  return { page, el };
}

const STOPS = [
  { name: "Alipurduar", latitude: 26.4900, longitude: 89.5270 },
  { name: "Falakata",   latitude: 26.5200, longitude: 89.2000 },
];

test("home: a nearby stop fills the From field in capital letters and explains what happened", async () => {
  const geolocation = fakeGeolocation((ok) => ok({ coords: { latitude: 26.4910, longitude: 89.5270, accuracy: 20 } }));
  const { page, el } = homePage({ stops: STOPS, geolocation });
  await page.eval("useMyLocation()");

  assert.equal(el("source").value, "ALIPURDUAR");
  assert.match(el("geoNote").textContent, /Nearest stop: ALIPURDUAR/);
  assert.match(el("geoNote").textContent, /stays on your device/);
  assert.ok(el("geoNote").className.includes("ok"));
  assert.equal(el("destination").focused, true, "focus moves on to the destination field");
  assert.equal(el("geoBtn").disabled, false, "button is usable again");
});

test("home: a destination the passenger already typed is not stolen", async () => {
  const geolocation = fakeGeolocation((ok) => ok({ coords: { latitude: 26.4910, longitude: 89.5270, accuracy: 20 } }));
  const { page, el } = homePage({ stops: STOPS, geolocation });
  el("destination").value = "FALAKATA";
  await page.eval("useMyLocation()");
  assert.equal(el("destination").focused, false);
});

test("home: a location far from every stop does not fill anything", async () => {
  const geolocation = fakeGeolocation((ok) => ok({ coords: { latitude: 28.6, longitude: 77.2, accuracy: 20 } }));
  const { page, el } = homePage({ stops: STOPS, geolocation });
  await page.eval("useMyLocation()");

  assert.equal(el("source").value, "");
  assert.match(el("geoNote").textContent, /too far/);
  assert.ok(el("geoNote").className.includes("error"));
});

test("home: a denied permission shows how to switch location on and leaves the field alone", async () => {
  const geolocation = fakeGeolocation((ok, fail) => fail({ code: 1 }));
  const { page, el } = homePage({ stops: STOPS, geolocation });
  await page.eval("useMyLocation()");

  assert.equal(el("source").value, "");
  assert.match(el("geoNote").textContent, /Allow it in your browser/);
  assert.ok(el("geoNote").className.includes("error"));
  assert.equal(el("geoBtn").disabled, false);
});

test("home: if the stop list cannot be loaded the passenger gets a message, not a stuck button", async () => {
  const geolocation = fakeGeolocation((ok) => ok({ coords: { latitude: 26.49, longitude: 89.52, accuracy: 20 } }));
  const { page, el } = homePage({ stops: STOPS, geolocation, fetchFails: true });
  await page.eval("useMyLocation()");

  assert.equal(el("source").value, "");
  assert.ok(el("geoNote").className.includes("error"));
  assert.equal(el("geoBtn").disabled, false);
});

test("home: a rough location (accuracy over 2 km) is flagged as approximate", async () => {
  const geolocation = fakeGeolocation((ok) => ok({ coords: { latitude: 26.4910, longitude: 89.5270, accuracy: 6000 } }));
  const { page, el } = homePage({ stops: STOPS, geolocation });
  await page.eval("useMyLocation()");
  assert.match(el("geoNote").textContent, /approximate/);
});

/* ─── the tracking page "My location" card (track.js + geo-utils.js together) ─── */
function trackingPage({ geolocation }) {
  const elements = {};
  const el = (id) => (elements[id] ||= {
    id, value: "", textContent: "", innerText: "", innerHTML: "", className: "", disabled: false,
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
  const page = loadPageScript("track.js", {
    search: "?rideId=1&routeKey=A_C",
    preload: ["geo-utils.js"],
    globals: { document, navigator: { geolocation } },
  });
  // a straight route: A, B, C, about 1.1 km apart
  page.sandbox.__stops = [
    { stopName: "Alpha",   latitude: 26.00, longitude: 89.0, distanceFromStartKm: 0,    slackTimeMin: 0 },
    { stopName: "Bravo",   latitude: 26.01, longitude: 89.0, distanceFromStartKm: 1.1,  slackTimeMin: 0 },
    { stopName: "Charlie", latitude: 26.02, longitude: 89.0, distanceFromStartKm: 2.2,  slackTimeMin: 0 },
  ];
  page.eval("fullRouteStops = __stops; routeStops = __stops;");
  return { page, el };
}

function trackingGeolocation(onPosition) {
  const geo = {
    watchers: [], cleared: [],
    getCurrentPosition(ok, fail) { onPosition(ok, fail); },
    watchPosition(ok) { geo.watchers.push(ok); return 7; },
    clearWatch(id) { geo.cleared.push(id); },
  };
  return geo;
}

test("track: sharing shows the nearest stop, the distance to the bus, and marks the stop on the timeline", async () => {
  const geolocation = trackingGeolocation((ok) => ok({ coords: { latitude: 26.0102, longitude: 89.0, accuracy: 15 } }));
  const { page, el } = trackingPage({ geolocation });
  page.eval("busLocation = { latitude: 26.02, longitude: 89.0 };");

  await page.eval("startMyLocation()");

  assert.match(el("myLocText").textContent, /You are near BRAVO/);
  assert.match(el("myLocText").textContent, /The bus is about 1\.\d km from you/);
  assert.equal(page.eval("myNearestIdx"), 1);
  assert.match(el("stopList").innerHTML, /You are here/, "the timeline shows a marker on the passenger's stop");
  assert.equal(el("myLocBtn").textContent, "Stop");
  assert.equal(geolocation.watchers.length, 1, "keeps watching so the distance stays current");
});

test("track: without a bus position yet, it says it is waiting instead of showing a wrong distance", async () => {
  const geolocation = trackingGeolocation((ok) => ok({ coords: { latitude: 26.0, longitude: 89.0, accuracy: 15 } }));
  const { page, el } = trackingPage({ geolocation });
  await page.eval("startMyLocation()");
  assert.match(el("myLocText").textContent, /Waiting for the bus/);
});

test("track: far from the route, it says so and puts no 'You are here' marker", async () => {
  const geolocation = trackingGeolocation((ok) => ok({ coords: { latitude: 28.0, longitude: 89.0, accuracy: 15 } }));
  const { page, el } = trackingPage({ geolocation });
  await page.eval("startMyLocation()");
  assert.match(el("myLocText").textContent, /from the nearest stop on this route/);
  assert.doesNotMatch(el("stopList").innerHTML, /You are here/);
});

test("track: a denied permission explains how to fix it and the button goes back to Show", async () => {
  const geolocation = trackingGeolocation((ok, fail) => fail({ code: 1 }));
  const { page, el } = trackingPage({ geolocation });
  await page.eval("startMyLocation()");

  assert.match(el("myLocText").textContent, /Allow it in your browser/);
  assert.equal(el("myLocBtn").textContent, "Show");
  assert.equal(el("myLocBtn").disabled, false);
  assert.equal(page.eval("myWatchId"), null);
  assert.equal(geolocation.watchers.length, 0, "no watcher is left running");
});

test("track: Stop stops watching, forgets the position and removes the marker", async () => {
  const geolocation = trackingGeolocation((ok) => ok({ coords: { latitude: 26.0102, longitude: 89.0, accuracy: 15 } }));
  const { page, el } = trackingPage({ geolocation });
  await page.eval("startMyLocation()");
  page.eval("stopMyLocation()");

  assert.deepEqual(geolocation.cleared, [7]);
  assert.equal(page.eval("myPos"), null);
  assert.equal(el("myLocBtn").textContent, "Show");
  assert.doesNotMatch(el("stopList").innerHTML, /You are here/);
});

test("track: a moving passenger updates the card through the watch", async () => {
  const geolocation = trackingGeolocation((ok) => ok({ coords: { latitude: 26.0, longitude: 89.0, accuracy: 15 } }));
  const { page, el } = trackingPage({ geolocation });
  await page.eval("startMyLocation()");
  assert.match(el("myLocText").textContent, /near ALPHA/);

  geolocation.watchers[0]({ coords: { latitude: 26.0198, longitude: 89.0, accuracy: 15 } });
  assert.match(el("myLocText").textContent, /near CHARLIE/);
});

/* ─── sending my location to a friend ─────────────────────────── */
test("buildShareMessage: a map link with the exact position, nearest stop, bus distance and tracking link", () => {
  const page = geo();
  page.sandbox.__d = {
    latitude: 26.4912345678, longitude: 89.5270001, accuracy: 20,
    nearestStopName: "ALIPURDUAR", nearestDistanceM: 350, busDistanceM: 3200,
    trackUrl: "https://wimb.example/track.html?rideId=5",
  };
  const text = page.eval("Geo.buildShareMessage(__d)");
  assert.match(text, /^📍 My location: https:\/\/www\.google\.com\/maps\?q=26\.491235,89\.527000/);
  assert.match(text, /Near ALIPURDUAR \(350 m away\)/);
  assert.match(text, /The bus is about 3\.2 km from me/);
  assert.match(text, /Track the bus live: https:\/\/wimb\.example\/track\.html\?rideId=5/);
  assert.doesNotMatch(text, /Approximate/);
});

test("buildShareMessage: leaves out lines it has nothing true to say about", () => {
  const page = geo();
  page.sandbox.__d = { latitude: 26.1, longitude: 89.2, accuracy: 10, nearestStopName: "FARAWAY", nearestDistanceM: 30000, busDistanceM: null, trackUrl: "" };
  const text = page.eval("Geo.buildShareMessage(__d)");
  assert.doesNotMatch(text, /Near FARAWAY/, "a stop 30 km away is not 'near'");
  assert.doesNotMatch(text, /bus is about/);
  assert.doesNotMatch(text, /Track the bus/);
  assert.equal(text.split("\n").length, 1);
});

test("buildShareMessage: flags a rough location as approximate", () => {
  const page = geo();
  page.sandbox.__d = { latitude: 26.1, longitude: 89.2, accuracy: 4000 };
  assert.match(page.eval("Geo.buildShareMessage(__d)"), /Approximate location, about 4\.0 km/);
});

test("whatsappLink: the message is URL-encoded so links and new lines survive", () => {
  const { eval: run } = geo();
  const link = run('Geo.whatsappLink("a b\\nhttps://x.y/?q=1&z=2")');
  assert.ok(link.startsWith("https://wa.me/?text="));
  assert.ok(!link.includes(" ") && !link.includes("&z=2"), `got ${link}`);
  assert.equal(decodeURIComponent(link.slice("https://wa.me/?text=".length)), "a b\nhttps://x.y/?q=1&z=2");
});

function sharingPage({ geolocation, share, clipboard }) {
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
  const navigator = { geolocation };
  if (share) navigator.share = share;
  if (clipboard) navigator.clipboard = clipboard;
  const prompts = [];
  const page = loadPageScript("track.js", {
    search: "?rideId=5&routeKey=A_C",
    preload: ["geo-utils.js"],
    globals: { document, navigator, prompt: (...args) => prompts.push(args) },
  });
  page.sandbox.__stops = [
    { stopName: "Alpha",   latitude: 26.00, longitude: 89.0, distanceFromStartKm: 0,   slackTimeMin: 0 },
    { stopName: "Bravo",   latitude: 26.01, longitude: 89.0, distanceFromStartKm: 1.1, slackTimeMin: 0 },
  ];
  page.eval("fullRouteStops = __stops; routeStops = __stops; busLocation = { latitude: 26.02, longitude: 89.0 };");
  return { page, el, prompts };
}

const here = (ok) => ok({ coords: { latitude: 26.0102, longitude: 89.0, accuracy: 15 } });

test("share: opens the phone's share menu with the position, nearest stop and tracking link", async () => {
  const shared = [];
  const { page, el } = sharingPage({ geolocation: fakeGeolocation(here), share: async (data) => shared.push(data) });
  await page.eval("shareMyLocation()");

  assert.equal(shared.length, 1);
  assert.equal(shared[0].title, "My location");
  assert.match(shared[0].text, /google\.com\/maps\?q=26\.010200,89\.000000/);
  assert.match(shared[0].text, /Near BRAVO/);
  assert.match(shared[0].text, /Track the bus live: http/);
  assert.equal(el("shareNote").textContent, "Sent.");
  assert.equal(el("shareFallback").style.display, "none");
  assert.equal(el("shareLocBtn").disabled, false);
});

test("share: asks for the location itself if it was not being shown yet", async () => {
  const geolocation = fakeGeolocation(here);
  const { page } = sharingPage({ geolocation, share: async () => {} });
  assert.equal(page.eval("myPos"), null);
  await page.eval("shareMyLocation()");
  assert.equal(geolocation.calls.length, 1);
  assert.notEqual(page.eval("myPos"), null);
});

test("share: closing the share menu is not an error and shows nothing", async () => {
  const cancelled = async () => { const e = new Error("cancelled"); e.name = "AbortError"; throw e; };
  const { page, el } = sharingPage({ geolocation: fakeGeolocation(here), share: cancelled });
  await page.eval("shareMyLocation()");
  assert.equal(el("shareNote").textContent, "");
  assert.equal(el("shareFallback").style.display, "none");
  assert.equal(el("shareLocBtn").disabled, false);
});

test("share: a browser without a share menu gets WhatsApp and Copy options", async () => {
  const { page, el } = sharingPage({ geolocation: fakeGeolocation(here) });
  await page.eval("shareMyLocation()");
  assert.equal(el("shareFallback").style.display, "flex");
  assert.ok(el("shareWhatsApp").href.startsWith("https://wa.me/?text="));
  assert.match(decodeURIComponent(el("shareWhatsApp").href), /My location: https:\/\/www\.google\.com\/maps/);
});

test("share: if sharing is blocked by the browser, the same manual options appear", async () => {
  const blocked = async () => { const e = new Error("blocked"); e.name = "NotAllowedError"; throw e; };
  const { page, el } = sharingPage({ geolocation: fakeGeolocation(here), share: blocked });
  await page.eval("shareMyLocation()");
  assert.equal(el("shareFallback").style.display, "flex");
});

test("share: a denied location permission stops before sharing anything and says how to fix it", async () => {
  const shared = [];
  const denied = fakeGeolocation((ok, fail) => fail({ code: 1 }));
  const { page, el } = sharingPage({ geolocation: denied, share: async (d) => shared.push(d) });
  await page.eval("shareMyLocation()");

  assert.equal(shared.length, 0);
  assert.match(el("shareNote").textContent, /Allow it in your browser/);
  assert.equal(el("shareLocBtn").disabled, false);
});

test("share: Copy message uses the clipboard, or a prompt when the clipboard is blocked", async () => {
  const copied = [];
  const ok = sharingPage({ geolocation: fakeGeolocation(here), clipboard: { writeText: async (t) => copied.push(t) } });
  await ok.page.eval("shareMyLocation()");
  await ok.page.eval("copyShareMessage()");
  assert.equal(copied.length, 1);
  assert.match(copied[0], /My location:/);
  assert.match(ok.el("shareNote").textContent, /Copied/);

  const blocked = sharingPage({ geolocation: fakeGeolocation(here), clipboard: { writeText: async () => { throw new Error("no"); } } });
  await blocked.page.eval("shareMyLocation()");
  await blocked.page.eval("copyShareMessage()");
  assert.equal(blocked.prompts.length, 1);
});
