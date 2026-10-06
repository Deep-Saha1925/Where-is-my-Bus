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

const tick = () => new Promise((resolve) => setImmediate(resolve));

// A fake BackgroundGeolocation plugin that records what the page asks of it
function fakePlugin() {
  const plugin = {
    addWatcherOptions: null, callback: null, removed: [], resolveWatcher: null,
    addWatcher(options, callback) {
      plugin.addWatcherOptions = options;
      plugin.callback = callback;
      return new Promise((resolve) => { plugin.resolveWatcher = resolve; });
    },
    removeWatcher(args) { plugin.removed.push(args.id); return Promise.resolve(); },
  };
  return plugin;
}

const insideApp = (plugin) => ({ Capacitor: { isNativePlatform: () => true, Plugins: { BackgroundGeolocation: plugin } } });

/* ─── which source is used ────────────────────────────────────── */
test("in a normal browser the web GPS is used", () => {
  const page = loadPageScript("native-gps.js");
  assert.equal(page.eval("GpsSource.isNative()"), false);
  assert.equal(page.eval("GpsSource.available()"), true);
});

test("without any GPS API, nothing is available", () => {
  const page = loadPageScript("native-gps.js", { globals: { navigator: {} } });
  assert.equal(page.eval("GpsSource.available()"), false);
});

test("inside the Android app with the plugin, the native source is used", () => {
  const page = loadPageScript("native-gps.js", { globals: { ...insideApp(fakePlugin()), navigator: {} } });
  assert.equal(page.eval("GpsSource.isNative()"), true);
  assert.equal(page.eval("GpsSource.available()"), true);
});

test("a Capacitor app WITHOUT the plugin falls back to the web GPS instead of breaking", () => {
  const page = loadPageScript("native-gps.js", {
    globals: { Capacitor: { isNativePlatform: () => true, Plugins: {} } },
  });
  assert.equal(page.eval("GpsSource.isNative()"), false);
});

test("Capacitor running as a plain web page (not native) uses the web GPS", () => {
  const page = loadPageScript("native-gps.js", {
    globals: { Capacitor: { isNativePlatform: () => false, Plugins: { BackgroundGeolocation: fakePlugin() } } },
  });
  assert.equal(page.eval("GpsSource.isNative()"), false);
});

/* ─── native source behaviour ─────────────────────────────────── */
test("native: asks for a foreground-service notification, permissions and fresh positions", () => {
  const plugin = fakePlugin();
  const page = loadPageScript("native-gps.js", { globals: insideApp(plugin) });
  page.sandbox.__noop = () => {};
  page.eval("GpsSource.start(__noop, __noop)");
  const o = plugin.addWatcherOptions;
  assert.ok(o.backgroundTitle && o.backgroundTitle.length > 0, "needs a notification title");
  assert.ok(o.backgroundMessage && o.backgroundMessage.length > 0, "needs a notification message");
  assert.equal(o.requestPermissions, true);
  assert.equal(o.stale, false);
});

test("native: a location from the plugin reaches the page as { latitude, longitude, accuracy }", () => {
  const plugin = fakePlugin();
  const page = loadPageScript("native-gps.js", { globals: insideApp(plugin) });
  const fixes = [];
  page.sandbox.__onFix = (f) => fixes.push(f);
  page.sandbox.__onErr = () => assert.fail("no error expected");
  page.eval("GpsSource.start(__onFix, __onErr)");
  plugin.callback({ latitude: 26.5, longitude: 89.4, accuracy: 12, time: 1 }, undefined);
  assert.deepEqual(JSON.parse(JSON.stringify(fixes)), [{ latitude: 26.5, longitude: 89.4, accuracy: 12 }]);
});

test("native: NOT_AUTHORIZED is reported as a permission problem with a helpful message", () => {
  const plugin = fakePlugin();
  const page = loadPageScript("native-gps.js", { globals: insideApp(plugin) });
  const errors = [];
  page.sandbox.__onFix = () => assert.fail("no fix expected");
  page.sandbox.__onErr = (e) => errors.push(e);
  page.eval("GpsSource.start(__onFix, __onErr)");
  plugin.callback(undefined, { code: "NOT_AUTHORIZED", message: "x" });
  assert.equal(errors.length, 1);
  assert.equal(errors[0].notAuthorized, true);
  assert.match(errors[0].message, /permission/i);
});

test("native: stop() removes the watcher once its id is known", async () => {
  const plugin = fakePlugin();
  const page = loadPageScript("native-gps.js", { globals: insideApp(plugin) });
  page.sandbox.__noop = () => {};
  page.eval("globalThis.__h = GpsSource.start(__noop, __noop)");
  plugin.resolveWatcher("watcher-1");
  await tick();
  page.eval("__h.stop()");
  assert.deepEqual(plugin.removed, ["watcher-1"]);
});

test("native: stop() called BEFORE the plugin has started still removes the watcher afterwards", async () => {
  const plugin = fakePlugin();
  const page = loadPageScript("native-gps.js", { globals: insideApp(plugin) });
  page.sandbox.__noop = () => {};
  page.eval("globalThis.__h = GpsSource.start(__noop, __noop)");
  page.eval("__h.stop()");
  assert.deepEqual(plugin.removed, [], "nothing to remove yet");
  plugin.resolveWatcher("watcher-2");
  await tick();
  assert.deepEqual(plugin.removed, ["watcher-2"], "the late watcher must not keep running (it would drain the battery)");
});

/* ─── web source behaviour ────────────────────────────────────── */
function fakeNavigator() {
  const nav = {
    geolocation: {
      options: null, success: null, failure: null, cleared: [],
      watchPosition(success, failure, options) { nav.geolocation.success = success; nav.geolocation.failure = failure; nav.geolocation.options = options; return 42; },
      clearWatch(id) { nav.geolocation.cleared.push(id); },
    },
  };
  return nav;
}

test("web: starts a high-accuracy watch with no cached positions, and maps coordinates", () => {
  const nav = fakeNavigator();
  const page = loadPageScript("native-gps.js", { globals: { navigator: nav } });
  const fixes = [];
  page.sandbox.__onFix = (f) => fixes.push(f);
  page.sandbox.__noop = () => {};
  page.eval("GpsSource.start(__onFix, __noop)");
  assert.equal(nav.geolocation.options.enableHighAccuracy, true);
  assert.equal(nav.geolocation.options.maximumAge, 0);
  nav.geolocation.success({ coords: { latitude: 26.1, longitude: 89.2, accuracy: 30 } });
  assert.deepEqual(JSON.parse(JSON.stringify(fixes)), [{ latitude: 26.1, longitude: 89.2, accuracy: 30 }]);
});

test("web: a denied permission (code 1) is flagged notAuthorized; stop() clears the watch", () => {
  const nav = fakeNavigator();
  const page = loadPageScript("native-gps.js", { globals: { navigator: nav } });
  const errors = [];
  page.sandbox.__onErr = (e) => errors.push(e);
  page.sandbox.__noop = () => {};
  page.eval("globalThis.__h = GpsSource.start(__noop, __onErr)");
  nav.geolocation.failure({ code: 1, message: "User denied Geolocation" });
  assert.equal(errors[0].notAuthorized, true);
  page.eval("__h.stop()");
  assert.deepEqual(nav.geolocation.cleared, [42]);
});

/* ─── driver page: honest status when the GPS fix is old ──────── */
test("describeFixAge: silent while fresh, a warning once the fix is 30s old or more", () => {
  const { eval: run } = loadPageScript("driver.js");
  assert.equal(run("describeFixAge(0)"), "");
  assert.equal(run("describeFixAge(29999)"), "");
  assert.match(run("describeFixAge(30000)"), /No new GPS fix for 30s/);
  assert.match(run("describeFixAge(5 * 60 * 1000)"), /5 min/);
  assert.equal(run("describeFixAge(NaN)"), "");
});

test("formatAge: seconds under 90s, minutes after", () => {
  const { eval: run } = loadPageScript("driver.js");
  assert.equal(run("formatAge(45000)"), "45s");
  assert.equal(run("formatAge(89000)"), "89s");
  assert.equal(run("formatAge(120000)"), "2 min");
});
