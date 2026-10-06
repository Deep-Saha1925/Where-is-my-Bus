/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
"use strict";
// Runs one of the browser scripts (src/main/resources/static/js/*.js) inside a Node "vm"
// sandbox with just enough fake browser objects for its top-level code to run, so its
// pure functions can be tested without a browser or any npm packages.
//
//   const ctx = loadPageScript("track.js");
//   ctx.eval("locateBus(stops, loc)")      // top-level let/const/function are reachable via eval

const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

function fakeElement() {
  const el = {
    style: {}, dataset: {}, innerHTML: "", innerText: "", textContent: "", value: "", disabled: false,
    children: [],
    classList: { add() {}, remove() {}, toggle() {}, contains() { return false; } },
    addEventListener() {}, removeEventListener() {}, appendChild() {}, focus() {},
    querySelector() { return fakeElement(); }, querySelectorAll() { return []; },
    getBoundingClientRect() { return { top: 0, height: 0, left: 0, width: 0 }; },
    scrollIntoView() {},
  };
  return el;
}

function loadPageScript(fileName, { search = "" } = {}) {
  const file = path.join(__dirname, "..", "..", "src", "main", "resources", "static", "js", fileName);
  const source = fs.readFileSync(file, "utf8");

  const sandbox = {
    console: { log() {}, warn() {}, error() {}, info() {} },
    URLSearchParams, Date, Math, JSON, Number, String, Array, Object, Promise, Map, Set, Error,
    encodeURIComponent, decodeURIComponent, parseInt, parseFloat, isNaN, isFinite, NaN, Infinity,
    // timers do nothing: scripts start intervals at load, and the tests must not hang
    setTimeout() { return 0; }, setInterval() { return 0; }, clearTimeout() {}, clearInterval() {},
    // network never answers
    fetch() { return new Promise(() => {}); },
    AbortController,
    alert() {},
    localStorage: { getItem() { return null; }, setItem() {}, removeItem() {} },
    navigator: { geolocation: { watchPosition() { return 1; }, clearWatch() {}, getCurrentPosition() {} } },
    document: {
      readyState: "complete",
      visibilityState: "visible",
      getElementById() { return fakeElement(); },
      querySelector() { return fakeElement(); },
      querySelectorAll() { return []; },
      createElement() { return fakeElement(); },
      addEventListener() {},
      body: fakeElement(), documentElement: fakeElement(),
    },
    location: { search, href: "http://localhost/", pathname: "/" },
    history: { back() {} },
  };
  sandbox.window = sandbox;
  sandbox.window.addEventListener = () => {};
  sandbox.window.innerHeight = 800;
  sandbox.window.scrollY = 0;
  sandbox.window.scrollTo = () => {};
  sandbox.window.matchMedia = () => ({ matches: false, addEventListener() {} });

  const context = vm.createContext(sandbox);
  vm.runInContext(source, context, { filename: fileName });
  return { eval: (expr) => vm.runInContext(expr, context), sandbox };
}

module.exports = { loadPageScript };
