/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
const params    = new URLSearchParams(window.location.search);
let rideId      = params.get("rideId");
const routeKey  = params.get("routeKey");
// Which registered route these stops belong to. Without this, every lookup
// below silently fell back to the legacy route only, so a bus running on
// any admin-added route could never have its stops/position resolved here —
// this is why the admin "View on Map" / passenger "Track Bus" screen simply
// never populated for non-legacy routes.
let routeCode   = params.get("routeCode") || null;

let routeStops     = [];
let fullRouteStops = [];
let busLocation    = null;
let rideInfo       = null;
let centeredOnBus  = false; // scroll the bus to the middle of the screen once per opened ride
let userMovedView  = false; // the passenger scrolled/touched the page -- never fight them after that
let hasEnteredOnce = false;
let myPos          = null;  // { latitude, longitude, accuracy } while the passenger shares their location
let myWatchId      = null;
let myNearestIdx   = -1;    // index (in the displayed stop list) of the stop nearest to the passenger
let myNearestM     = null;
const MY_STOP_MAX_M = 5000; // "You are here" is only shown on a stop this close // only slide the rows in on the first render, not every WS/poll refresh

let stompClient        = null;
let wsSubscribedRideId = null;
let wsReconnectTimer   = null;
let lastWsPushAt       = 0;   // when the last live push arrived (0 = never)

/* ─── CLIENT CACHE ──────────────────────────────────────────────────
   Automatic refreshes read from this short-lived cache (sessionStorage) instead of
   asking the server every time; this keeps load off the server when many passengers
   watch the same bus. The reload button always bypasses it and fetches fresh data.
     • bus position  : 15 s  (the live socket keeps it newer than that anyway)
     • ride info     : 30 s
     • stops / route : 10 min (rarely change)                                      */
const LOC_CACHE_TTL_MS      = 15 * 1000;
const RIDEINFO_CACHE_TTL_MS = 30 * 1000;
const ROUTE_CACHE_TTL_MS    = 10 * 60 * 1000;
const CACHE_PREFIX          = "wimb_c:";

function cacheGet(key, ttlMs) {
  try {
    const raw = sessionStorage.getItem(CACHE_PREFIX + key);
    if (!raw) return null;
    const e = JSON.parse(raw);
    return e && Date.now() - e.at <= ttlMs ? e : null;
  } catch (_) { return null; }
}
function cacheSet(key, data) {
  try { sessionStorage.setItem(CACHE_PREFIX + key, JSON.stringify({ at: Date.now(), data })); } catch (_) {}
}
function cacheClear() {
  try {
    Object.keys(sessionStorage).filter(k => k.startsWith(CACHE_PREFIX)).forEach(k => sessionStorage.removeItem(k));
  } catch (_) {}
}

/* ─── HELPERS ──────────────────────────────────────────────────── */
// Split "SRC_DEST" into [source, destination]. Stop names can contain "_"
// (e.g. "My_seat"), so the server works out the right split point by checking
// which halves are real stops on the route. Falls back to the first "_".
async function splitRouteKey(key, code) {
  if (!key || key.indexOf("_") === -1) return [key || "", ""];
  try {
    const codeParam = code ? `&routeCode=${encodeURIComponent(code)}` : "";
    const d = await cachedJson(`/api/routes/split?routeKey=${encodeURIComponent(key)}${codeParam}`, ROUTE_CACHE_TTL_MS);
    if (d && d.source != null) return [d.source, d.destination];
  } catch (_) { /* use fallback below */ }
  const idx = key.indexOf("_");
  return [key.substring(0, idx).trim(), key.substring(idx + 1).trim()];
}

// fetch + JSON that throws on HTTP errors instead of silently returning an
// {error: "..."} object that later code treats as a list of stops.
async function getJson(url, noStore) {
  const res = await fetch(url, noStore ? { cache: "no-store" } : undefined);
  if (!res.ok) {
    let msg = `HTTP ${res.status}`;
    try { msg = (await res.json()).error || msg; } catch (_) {}
    throw new Error(msg);
  }
  return res.json();
}

// getJson with the short-lived cache above; `force` skips it (and the browser cache) and refreshes it.
async function cachedJson(url, ttlMs, force) {
  if (!force) {
    const c = cacheGet("j:" + url, ttlMs);
    if (c) return c.data;
  }
  const data = await getJson(url, !!force);
  cacheSet("j:" + url, data);
  return data;
}

function escapeHtml(str) {
  return String(str ?? "").replace(/[&<>"']/g, c => (
      { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]
  ));
}

/* ─── INIT ─────────────────────────────────────────────────────── */
(async function init() {
  if (rideId) {
    // Resolve the ride FIRST so we know its routeCode before asking for
    // stops. Previously the stops were requested before this was known, so
    // rides on any non-default route asked the default route for stops and
    // got an error back -> the stop list never loaded.
    await fetchRideInfo();
    tick(); // one immediate fetch so the page isn't blank while the socket connects
  }

  if (routeKey) {
    const [src, dest] = await splitRouteKey(routeKey, routeCode);
    document.getElementById("routeTitle").innerText = `${src} → ${dest}`;
    await loadRoute(src, dest);
  }

  if (!rideId && routeKey) {
    autoSelectRide();
  }
})();

connectWebSocket();

// Safety net only — the WebSocket push is what normally keeps this fresh.
// If the socket is ever silently stuck (a flaky network, a proxy that kills
// idle connections), this still catches up within 20s instead of the page
// going stale forever. This is the same endpoint the old 3s poll used, just
// 6-7x less often, since it's now a fallback rather than the primary path.
// Polls every 5s, but skips the request while WebSocket pushes are arriving
// (the driver sends at least every ~10s), so a healthy socket costs nothing extra
// while a silently broken one is noticed within seconds, not 20s.
setInterval(() => {
  if (Date.now() - lastWsPushAt < 15000) return;
  tick();
}, 5000);

// Coming back to the tab (phones sleep background tabs and their sockets): catch up now.
document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible") tick();
});

/* ─── WEBSOCKET (live push, replaces 3s polling) ──────────────────── */
function connectWebSocket() {
  try {
    const socket = new SockJS("/ws");
    stompClient = Stomp.over(socket);
    stompClient.debug = null; // silence stomp.js's verbose console logging

    stompClient.connect({}, () => {
      wsSubscribedRideId = null;   // a new connection has no subscriptions yet
      if (rideId) subscribeToRide(rideId);
    }, () => {
      // onError — stomp.js has no built-in auto-reconnect, so retry by hand
      scheduleWsReconnect();
    });

    const stompOnClose = socket.onclose;   // stomp.js installed its handler inside connect()
    socket.onclose = (e) => {
      if (stompOnClose) stompOnClose(e);
      scheduleWsReconnect();
    };
  } catch (err) {
    console.error("WebSocket connect failed:", err);
    scheduleWsReconnect();
  }
}

function scheduleWsReconnect() {
  if (wsReconnectTimer) return; // already scheduled
  wsReconnectTimer = setTimeout(() => {
    wsReconnectTimer = null;
    wsSubscribedRideId = null;
    connectWebSocket();
  }, 4000);
}

function subscribeToRide(id) {
  if (!id) return;
  if (!stompClient || !stompClient.connected) {
    // autoSelectRide() can resolve a rideId before the socket has finished
    // connecting -- retry briefly rather than dropping the subscription.
    // The fallback 20s poll keeps the page correct in the meantime either way.
    setTimeout(() => subscribeToRide(id), 500);
    return;
  }
  if (wsSubscribedRideId === String(id)) return; // already subscribed to this ride

  stompClient.subscribe(`/topic/ride/${id}`, (message) => {
    try {
      lastWsPushAt = Date.now();
      applyLocation(JSON.parse(message.body));
    } catch (err) {
      console.error("Failed to parse live location push:", err);
    }
  });
  wsSubscribedRideId = String(id);
}

/* ─── LOAD ROUTE STOPS ──────────────────────────────────────────── */
async function loadRoute(src, dest) {
  const container = document.getElementById("stopList");
  try {
    const routeParam = routeCode ? `&routeCode=${encodeURIComponent(routeCode)}` : "";
    const stops = await cachedJson(
        `/api/routes?source=${encodeURIComponent(src)}&destination=${encodeURIComponent(dest)}${routeParam}`,
        ROUTE_CACHE_TTL_MS
    );
    routeStops = Array.isArray(stops) ? stops : [];
    if (rideId) await loadFullRoute();
    posDirty = true;
    lastProgressKm = null; lastLocated = null; // new route/stops -> forget old progress
    speedSamples = []; speedKmh = null;
    renderTimeline();
  } catch (err) {
    console.error("Failed to load route:", err);
    container.innerHTML =
        `<div style="padding:32px 16px; text-align:center; font-size:14px; color:var(--text-faint);">` +
        `${tr("Could not load stops")} (${escapeHtml(err.message)})</div>`;
  }
}

/* ─── LOAD FULL ROUTE ───────────────────────────────────────────── */
// The bus's own journey (its start -> end stop), used to place the bus.
async function loadFullRoute() {
  try {
    if (!rideInfo || !rideInfo.routeKey) {
      const rides = await cachedJson("/api/ride/active/all", RIDEINFO_CACHE_TTL_MS);
      rideInfo = rides.find(r => String(r.rideId) === String(rideId)) || null;
    }
    const ride = rideInfo;
    if (!ride || !ride.routeKey) { fullRouteStops = routeStops; return; }

    document.getElementById("busNumberDisplay").innerText = ride.busNumber || "—";

    // The ride's own routeCode is authoritative (straight from the DB).
    if (ride.routeCode) routeCode = ride.routeCode;
    const routeParam = routeCode ? `&routeCode=${encodeURIComponent(routeCode)}` : "";

    const [rideSrc, rideDest] = await splitRouteKey(ride.routeKey, routeCode);
    const stops = await cachedJson(
        `/api/routes?source=${encodeURIComponent(rideSrc)}&destination=${encodeURIComponent(rideDest)}${routeParam}`,
        ROUTE_CACHE_TTL_MS
    );
    fullRouteStops = Array.isArray(stops) ? stops : routeStops;
  } catch (err) {
    console.error("Failed to load full route:", err);
    fullRouteStops = routeStops;
  }
}

/* ─── AUTO SELECT RIDE ──────────────────────────────────────────── */
async function autoSelectRide() {
  if (!routeKey) return;
  const [src, dest] = await splitRouteKey(routeKey, routeCode);
  try {
    const buses = await getJson(
        `/api/ride/active?source=${encodeURIComponent(src)}&destination=${encodeURIComponent(dest)}`
    );
    if (buses.length > 0) {
      rideId = buses[0].rideId;
      centeredOnBus = false;
      rideInfo = buses[0];
      if (buses[0].routeCode) routeCode = buses[0].routeCode;
      document.getElementById("busNumberDisplay").innerText = buses[0].busNumber || "—";
      await loadRoute(src, dest); // reload with the correct routeCode + full route
      tick();
      subscribeToRide(rideId);
    }
  } catch (err) {
    console.error("Auto-select failed:", err);
  }
}

/* ─── FETCH RIDE INFO ───────────────────────────────────────────── */
async function fetchRideInfo(force) {
  try {
    const rides = await cachedJson("/api/ride/active/all", RIDEINFO_CACHE_TTL_MS, force);
    rideInfo = rides.find(r => String(r.rideId) === String(rideId)) || null;
    if (rideInfo) {
      document.getElementById("busNumberDisplay").innerText = rideInfo.busNumber || "—";
      if (rideInfo.routeCode) routeCode = rideInfo.routeCode;
    }
  } catch (err) {
    console.error("Ride info failed:", err);
  }
}

/* ─── TICK (fallback poll — see the setInterval(tick, 20000) above) ──── */
// Returns true when the position is known to be current (served from the fresh cache, or fetched OK).
async function tick(force) {
  if (!rideId) return false;
  if (!force) {
    // A recent copy (from the socket, an earlier poll, or just before a page reload) is good enough:
    // skip the server. Paint it only if nothing is on screen yet.
    const c = cacheGet("loc:" + rideId, LOC_CACHE_TTL_MS);
    if (c) { if (lastLocTs == null) applyLocation(c.data); return true; }
  }
  try {
    const res = await fetch(`/api/location/last-loc/${rideId}`, force ? { cache: "no-store" } : undefined);
    if (!res.ok) return false;
    applyLocation(await res.json());
    return true;
  } catch (err) {
    console.error("Tick failed:", err);
    return false;
  }
}

/* ─── RELOAD BUTTON ─────────────────────────────────────────────────
   Passenger-triggered refresh: skips every cache, re-reads the bus position and ride info,
   and nudges the live socket if it dropped. Spinner while loading, short cooldown after it
   so mashing the button can't hammer the server.                                      */
const RELOAD_MIN_SPIN_MS = 800;
const RELOAD_COOLDOWN_MS = 3000;
let reloading = false;

async function manualReload() {
  if (reloading) return;
  reloading = true;
  const btn = document.getElementById("reloadBtn");
  const label = document.getElementById("lastUpdated");
  if (btn) { btn.classList.add("spinning"); btn.disabled = true; }
  if (label) label.innerText = "Refreshing…";

  const started = Date.now();
  let ok = true;
  try {
    cacheClear();
    if (!rideId && routeKey) {
      await autoSelectRide();               // no bus picked yet: look for one again
      ok = !!rideId;
    } else {
      await fetchRideInfo(true);
      ok = await tick(true);
    }
    if (!stompClient || !stompClient.connected) scheduleWsReconnect();
  } catch (_) { ok = false; }

  const wait = Math.max(0, RELOAD_MIN_SPIN_MS - (Date.now() - started));
  setTimeout(() => {
    if (btn) btn.classList.remove("spinning");
    if (label) {
      if (ok && busLocation) updateLastUpdatedLabel();
      else label.innerText = ok ? tr("No location yet") : tr("Couldn't refresh · showing last known");
    }
    setTimeout(() => { reloading = false; if (btn) btn.disabled = false; }, RELOAD_COOLDOWN_MS);
  }, wait);
}

/* ─── TRACKING TUNABLES ─────────────────────────────────────────────
   A driver's GPS is never exactly on a stop or exactly on the road, so
   instead of "nearest stop wins" the bus is treated as a circle:
     • within ARRIVAL_RADIUS_M of a stop (or the device's own reported GPS
       accuracy, if that's larger, capped at MAX_ARRIVAL_RADIUS_M) -> "at the stop"
     • otherwise it is placed BETWEEN two stops by projecting its position
       onto the road line joining them, so a stop only counts as passed once
       the bus has genuinely gone beyond it
     • further than OFF_ROUTE_M from the whole route -> "off route" (bad fix
       or a diversion); the last good position is kept instead of jumping   */
const ARRIVAL_RADIUS_M     = 200;
const MAX_ARRIVAL_RADIUS_M = 500;
const OFF_ROUTE_M          = 1500;
const JITTER_KM            = 0.4;   // ignore backwards wobble smaller than this
const STALE_AFTER_SEC      = 120;   // no fix for this long -> warn the passenger

let lastLocTs      = null;   // ms epoch of the newest fix we've applied
let busPos         = null;   // result of locateBus() for the current fix
let lastLocated    = null;   // last good (on-route) result, kept while off-route
let lastProgressKm = null;
let posDirty       = true;
let wasStale       = false;

/* ─── ETA TUNABLES ──────────────────────────────────────────────────
   The ETA for a stop = driving time over the remaining distance at the bus's
   recent real speed (falls back to a typical city-bus speed until enough GPS
   history exists) + the scheduled halt at every stop in between.            */
const DEFAULT_SPEED_KMH  = 30;
const MIN_SPEED_KMH      = 12;    // never assume slower than this (a halt/traffic jam would blow up ETAs)
const MAX_SPEED_KMH      = 55;
const SPEED_WINDOW_MS    = 6 * 60 * 1000;
const SPEED_MIN_SPAN_MS  = 60 * 1000;   // need at least this much history for a measured speed
const SPEED_MIN_KM       = 0.15;
let speedSamples = [];    // [{ ts, km }] newest last
let speedKmh     = null;  // smoothed measured speed, null until measurable

/* ─── TIMESTAMPS ─────────────────────────────────────────────────── */
// The server now sends ISO instants ("...Z"). This also copes with older
// shapes: a zone-less string or a [y,m,d,h,mi,s,nano] array is treated as UTC
// (the server's clock) rather than the viewer's local time, which is what
// used to make every update look 5h30m old in IST.
function parseTs(ts) {
  if (ts == null) return NaN;
  if (typeof ts === "number") return ts;
  if (Array.isArray(ts)) {
    return Date.UTC(ts[0], ts[1] - 1, ts[2], ts[3] || 0, ts[4] || 0, ts[5] || 0,
        Math.floor((ts[6] || 0) / 1e6));
  }
  let str = String(ts);
  if (!/[zZ]$|[+-]\d{2}:?\d{2}$/.test(str)) str += "Z";
  return Date.parse(str);
}

function timeAgo(ts) {
  const ms = typeof ts === "number" ? ts : parseTs(ts);
  if (isNaN(ms)) return tr("just now");
  const diff = Math.floor((Date.now() - ms) / 1000);
  if (diff < 10)   return tr("just now");
  return tr("{t} ago", { t: timeSpan(ms) });
}

// "5s", "3m", "1h 5m" (no "ago")
function timeSpan(ts) {
  const ms = typeof ts === "number" ? ts : parseTs(ts);
  const diff = Math.max(0, Math.floor((Date.now() - ms) / 1000));
  if (diff < 60)   return tr("{n}s", { n: diff });
  if (diff < 3600) return tr("{n}m", { n: Math.floor(diff / 60) });
  return tr("{h}h {m}m", { h: Math.floor(diff / 3600), m: Math.floor((diff % 3600) / 60) });
}

function ageSeconds() {
  return lastLocTs == null ? null : Math.max(0, (Date.now() - lastLocTs) / 1000);
}

function updateLastUpdatedLabel() {
  if (!busLocation) return;
  const acc = Number(busLocation.accuracy);
  const accText = acc > 0 ? ` · GPS ±${Math.round(acc)} m` : "";
  document.getElementById("lastUpdated").innerText = "Updated " + timeAgo(lastLocTs) + accText;
}

// Keep "Updated 12s ago" ticking between fixes, and flip the stale warning
// on/off when the signal goes quiet / comes back.
setInterval(() => {
  if (!busLocation) return;
  updateLastUpdatedLabel();
  const stale = (ageSeconds() || 0) > STALE_AFTER_SEC;
  if (stale !== wasStale) renderTimeline();
}, 5000);

// Between GPS fixes the "in N min" countdown still needs to tick down.
setInterval(() => { if (busLocation && !document.hidden) renderTimeline(); }, 30000);

/* ─── APPLY LOCATION (shared by the WebSocket push and the fallback poll) */
function applyLocation(loc) {
  if (!loc || typeof loc.latitude !== "number" || typeof loc.longitude !== "number") return;

  // A slow REST poll must never overwrite a fresher WebSocket push.
  const ts = parseTs(loc.timestamp);
  if (!isNaN(ts) && lastLocTs != null && ts < lastLocTs) return;

  busLocation = loc;
  lastLocTs   = isNaN(ts) ? Date.now() : ts;
  posDirty    = true;
  if (rideId) cacheSet("loc:" + rideId, loc);

  document.getElementById("liveBadge").classList.remove("hidden");
  document.getElementById("liveBadge").classList.add("flex");
  updateLastUpdatedLabel();

  if (myPos) updateMyLocation(); else renderTimeline();
}

/* ─── GEOMETRY ───────────────────────────────────────────────────── */
function haversineM(lat1, lng1, lat2, lng2) {
  const R = 6371000, rad = Math.PI / 180;
  const dLat = (lat2 - lat1) * rad, dLng = (lng2 - lng1) * rad;
  const a = Math.sin(dLat / 2) ** 2 +
      Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLng / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

// Projects point p onto segment a->b (flat-earth maths is plenty accurate at
// stop-to-stop distances). Returns t in [0,1] along the segment and the
// distance in metres from p to that closest point.
function projectOnSegment(p, a, b) {
  const rad = Math.PI / 180;
  const cosLat = Math.cos(a.latitude * rad);
  const mx = 111320 * cosLat, my = 110540;
  const ax = 0, ay = 0;
  const bx = (b.longitude - a.longitude) * mx, by = (b.latitude - a.latitude) * my;
  const px = (p.longitude - a.longitude) * mx, py = (p.latitude - a.latitude) * my;
  const len2 = bx * bx + by * by;
  let t = len2 === 0 ? 0 : ((px - ax) * bx + (py - ay) * by) / len2;
  t = Math.max(0, Math.min(1, t));
  const cx = ax + t * bx, cy = ay + t * by;
  return { t, distM: Math.hypot(px - cx, py - cy) };
}

/* ─── LOCATE BUS ─────────────────────────────────────────────────── */
// Returns one of:
//   { offRoute:true, offKm }                       – fix is nowhere near the route
//   { atIdx, passedThrough, nextIdx, progressKm, radiusM, distToRouteM }
//     atIdx = index of the stop the bus is at (or -1 when between stops)
//     passedThrough = index of the last stop fully behind the bus
function locateBus(stops, loc) {
  const acc     = Number(loc.accuracy) || 0;
  const radiusM = Math.min(MAX_ARRIVAL_RADIUS_M, Math.max(ARRIVAL_RADIUS_M, acc));
  const p       = { latitude: loc.latitude, longitude: loc.longitude };
  const n       = stops.length;

  // 1) Closest point on the road line (segments between consecutive stops)
  let best = null;
  if (n === 1) {
    best = { seg: 0, t: 0, distM: haversineM(p.latitude, p.longitude, stops[0].latitude, stops[0].longitude) };
  } else {
    for (let i = 0; i < n - 1; i++) {
      const r = projectOnSegment(p, stops[i], stops[i + 1]);
      if (!best || r.distM < best.distM) best = { seg: i, t: r.t, distM: r.distM };
    }
  }

  // 2) Way off the route -> don't move the bus
  if (best.distM > Math.max(OFF_ROUTE_M, acc * 2)) {
    return { offRoute: true, offKm: best.distM / 1000 };
  }

  // 3) Is the bus inside a stop's radius?
  let atIdx = -1, atDist = Infinity;
  stops.forEach((st, i) => {
    const d = haversineM(p.latitude, p.longitude, st.latitude, st.longitude);
    if (d <= radiusM && d < atDist) { atIdx = i; atDist = d; }
  });

  const dAt = i => stops[i].distanceFromStartKm;
  let progressKm;
  if (atIdx !== -1) {
    progressKm = dAt(atIdx);                                   // snap to the stop
  } else {
    const nextI = Math.min(best.seg + 1, n - 1);
    progressKm = dAt(best.seg) + best.t * (dAt(nextI) - dAt(best.seg));
    // GPS wobble must not push the bus backwards a few hundred metres
    if (lastProgressKm != null && progressKm < lastProgressKm && lastProgressKm - progressKm < JITTER_KM) {
      progressKm = lastProgressKm;
    }
  }

  // 4) Which stops are behind the bus?
  let passedThrough, nextIdx;
  if (atIdx !== -1) {
    passedThrough = atIdx - 1;
    nextIdx       = atIdx + 1 < n ? atIdx + 1 : -1;
  } else {
    // re-derive the segment from (possibly jitter-corrected) progress
    // A stop only counts as passed once the bus is strictly beyond it (so a
    // bus alongside a stop, or just short of the first one, hasn't passed it).
    let seg = -1;
    for (let i = 0; i < n - 1; i++) { if (dAt(i) < progressKm - 1e-9) seg = i; }
    passedThrough = seg;
    nextIdx       = Math.min(seg + 1, n - 1);
  }

  lastProgressKm = progressKm;
  return { atIdx, passedThrough, nextIdx, progressKm, radiusM, distToRouteM: best.distM };
}

function updateBusPos(displayStops) {
  if (!busLocation || !displayStops.length) { busPos = null; return; }
  const r = locateBus(displayStops, busLocation);
  if (r.offRoute) {
    busPos = lastLocated ? { ...lastLocated, offRoute: true, offKm: r.offKm }
        : { offRoute: true, offKm: r.offKm, noPosition: true };
  } else {
    lastLocated = r;
    busPos = r;
  }
}

/* ─── ETA CALC ──────────────────────────────────────────────────── */
// Learns the bus's real speed from how fast its progress along the route is changing.
function recordSpeedSample() {
  if (!busPos || busPos.offRoute || busPos.noPosition || lastLocTs == null) return;
  const last = speedSamples[speedSamples.length - 1];
  if (last && last.ts === lastLocTs) return;           // same fix re-rendered
  if (last && busPos.progressKm < last.km - JITTER_KM) speedSamples = [];   // route restarted / jumped back
  speedSamples.push({ ts: lastLocTs, km: busPos.progressKm });
  const cutoff = lastLocTs - SPEED_WINDOW_MS;
  while (speedSamples.length > 2 && speedSamples[1].ts < cutoff) speedSamples.shift();

  const a = speedSamples[0], b = speedSamples[speedSamples.length - 1];
  const span = b.ts - a.ts, km = b.km - a.km;
  if (span >= SPEED_MIN_SPAN_MS && km >= SPEED_MIN_KM) {
    const measured = km / (span / 3600000);
    speedKmh = speedKmh == null ? measured : speedKmh * 0.5 + measured * 0.5;
  }
}

function currentSpeedKmh() {
  const v = speedKmh == null ? DEFAULT_SPEED_KMH : speedKmh;
  // Blend towards the default so one odd reading can't swing every ETA
  const blended = speedKmh == null ? v : v * 0.7 + DEFAULT_SPEED_KMH * 0.3;
  return Math.max(MIN_SPEED_KMH, Math.min(MAX_SPEED_KMH, blended));
}

// ETA for stop index `i`, or null when it is already behind the bus.
// Returns { arriveMs, mins } -- arriveMs is fixed to the last GPS fix, so it only changes when the
// bus actually reports a new position; mins counts down in real time between fixes.
function etaForStop(stops, i, behindIdx) {
  if (!busPos || busPos.offRoute || busPos.noPosition || lastLocTs == null) return null;
  const diffKm = stops[i].distanceFromStartKm - busPos.progressKm;
  if (diffKm <= 0 || i <= behindIdx) return null;
  let haltMin = 0;
  for (let j = behindIdx + 1; j < i; j++) haltMin += Math.max(0, stops[j].slackTimeMin || 0);
  const travelMs = (diffKm / currentSpeedKmh()) * 3600000;
  const arriveMs = lastLocTs + travelMs + haltMin * 60000;
  return { arriveMs, mins: Math.max(0, Math.round((arriveMs - Date.now()) / 60000)) };
}

function formatClock(ms) {
  try { return new Date(ms).toLocaleTimeString(I18N.locale(), { hour: "numeric", minute: "2-digit" }); }
  catch (_) { return ""; }
}

function formatEtaMins(mins) {
  if (mins < 60) return { num: String(mins), unit: "min" };
  const h = Math.floor(mins / 60), m = mins % 60;
  return { num: m ? `${h}h ${m}` : `${h}h`, unit: m ? "m" : "" };
}

/* ─── RENDER TIMELINE ───────────────────────────────────────────── */
/* ─── CENTER THE BUS ON OPEN ─────────────────────────────────────────
   When a passenger opens a ride the timeline can be long, with the bus
   somewhere off-screen. Scroll it into the vertical middle of the visible
   area once; after that the passenger scrolls freely and live updates
   never move the page again. */
['wheel', 'touchstart', 'touchmove', 'keydown', 'mousedown'].forEach(ev =>
    window.addEventListener(ev, () => { userMovedView = true; }, { passive: true })
);

function scrollBusToCenter() {
  const icon = document.querySelector("#stopList .bus-icon");
  if (!icon) return false;
  const top    = document.getElementById("topSticky");
  const sheet  = document.getElementById("sheet");
  const topH   = top   ? top.getBoundingClientRect().height   : 0;
  const botH   = sheet ? sheet.getBoundingClientRect().height : 0;
  const rect    = icon.getBoundingClientRect();
  const visible = window.innerHeight - topH - botH;          // space between the sticky top and the bottom sheet
  const y = rect.top + window.scrollY - topH - (visible - rect.height) / 2;
  window.scrollTo({ top: Math.max(0, y), behavior: "instant" });
  return true;
}

function centerOnBusOnce() {
  if (centeredOnBus || userMovedView) return;
  // Rows slide in over ~0.4s + 40ms per row, which moves them; wait for that so the
  // bus lands exactly in the middle, and re-check once more in case the layout settled.
  if (!scrollBusToCenter()) return;          // no bus position yet -- try on the next update
  centeredOnBus = true;
  const settle = 450 + Math.min(40 * (fullRouteStops.length || routeStops.length), 1200);
  setTimeout(() => { if (!userMovedView) scrollBusToCenter(); }, settle);
}

/* ─── SMALL UI HELPERS ─────────────────────────────────────────── */
function setText(id, text) { const el = document.getElementById(id); if (el) el.innerText = text; }

let toastTimer = null;
function showToast(msg, ms) {
  const el = document.getElementById("toast");
  if (!el) return;
  el.textContent = msg;
  el.classList.remove("hidden");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.add("hidden"), ms || 3200);
}

// "5 min", "1h 5m"
function etaText(mins) {
  if (mins < 60) return tr("{n} min", { n: mins });
  const h = Math.floor(mins / 60), m = mins % 60;
  return m ? tr("{h}h {m}m", { h, m }) : tr("{h}h", { h });
}

/* Bottom sheet: collapsed shows just the status + reload; tap to open the trip details */
function syncSheetPad() {
  const sheet = document.getElementById("sheet");
  if (sheet) document.body.style.paddingBottom = (sheet.offsetHeight + 16) + "px";
}
function toggleSheet() {
  const body = document.getElementById("sheetBody");
  if (!body) return;
  body.classList.toggle("hidden");
  syncSheetPad();
}
window.addEventListener("resize", syncSheetPad);
window.addEventListener("load", () => {
  syncSheetPad();
  if (window.ResizeObserver) new ResizeObserver(syncSheetPad).observe(document.getElementById("sheet"));
});

/* "My location" panel (the existing show / send-to-a-friend card) behind a chip */
function toggleMyLocPanel(forceOpen) {
  const card = document.getElementById("myLocCard");
  const chip = document.getElementById("myLocChip");
  if (!card) return;
  const open = forceOpen === true ? true : card.classList.contains("hidden");
  card.classList.toggle("hidden", !open);
  if (chip) chip.classList.toggle("on", open);
  if (open) card.scrollIntoView({ behavior: "smooth", block: "nearest" });
}

function findBus() {
  userMovedView = false;
  if (!scrollBusToCenter()) showToast(tr("Bus location isn't available yet"));
}

/* Share the tracking link (native share sheet, else copy) */
async function shareBus() {
  const bus   = (document.getElementById("busNumberDisplay") || {}).innerText || "";
  const route = (document.getElementById("routeTitle") || {}).innerText || "";
  const text  = tr("Track bus {bus} {route}", { bus: bus !== "—" ? bus : "", route }).replace(/\s+/g, " ").trim();
  const url   = window.location.href;
  try {
    if (navigator.share) { await navigator.share({ title: tr("Where Is My Bus"), text, url }); return; }
  } catch (err) {
    if (err && err.name === "AbortError") return;
  }
  try { await navigator.clipboard.writeText(`${text}\n${url}`); showToast(tr("Link copied")); }
  catch (_) { showToast(url, 6000); }
}

/* ─── ARRIVAL + DEPARTURE TIMES WE ACTUALLY SAW ────────────────────
   There is no timetable, so "when did the bus reach / leave this stop" is recorded from live fixes:
     arrival   = first fix that shows the bus at (or beyond) the stop
     departure = first fix that shows the bus has left it
   Saved per ride in this browser (survives reloads / reopening the page). Stops passed before the page
   was first opened have no record, so they show "—".   passTimes[i] = { a: arrivalMs, d: departureMs } */
let passTimes = {}, passKey = null, prevCur = null, prevAt = null;
function savePassTimes() {
  try { localStorage.setItem(passKey, JSON.stringify(passTimes)); } catch (_) {}
}
function updatePassTimes(stops, hasPos, atIdx, passedThrough) {
  if (rideId == null) return;
  const key = "wimb_p:" + rideId;
  if (passKey !== key) {
    passKey = key; prevCur = null; prevAt = null; passTimes = {};
    try {
      passTimes = JSON.parse(localStorage.getItem(key) || "{}") || {};
      // forget other rides' records so this doesn't grow forever
      Object.keys(localStorage).forEach(k => { if (k.startsWith("wimb_p:") && k !== key) localStorage.removeItem(k); });
    } catch (_) { passTimes = {}; }
  }
  if (!hasPos || lastLocTs == null) return;
  const ts  = lastLocTs;
  const cur = atIdx !== -1 ? atIdx : passedThrough;
  let changed = false;
  const entry = i => (passTimes[i] = passTimes[i] || {});

  if (prevCur == null) {
    // First look at this ride in this page load. A stop we saw the bus arrive at in an earlier visit but never saw it
    // leave: if the bus has gone on, its departure is arrival + the scheduled halt (we weren't watching to catch it).
    for (let i = 0; i <= passedThrough && i < stops.length; i++) {
      const e = passTimes[i];
      if (e && e.a != null && e.d == null) {
        e.d = Math.min(ts, e.a + Math.max(0, stops[i].slackTimeMin || 0) * 60000);
        changed = true;
      }
    }
  } else {
    // The bus moved on: stamp every stop it reached (and, for stops it drove straight past, left) since the last look
    for (let i = prevCur + 1; i <= cur && i < stops.length; i++) {
      const e = entry(i);
      if (e.a == null) { e.a = ts; changed = true; }
      if (i <= passedThrough && e.d == null) { e.d = ts; changed = true; }
    }
    // It was at a stop and now it isn't: that stop's departure
    if (prevAt != null && prevAt !== atIdx && passedThrough >= prevAt) {
      const e = entry(prevAt);
      if (e.d == null) { e.d = ts; changed = true; }
    }
  }
  prevCur = (prevCur == null || cur >= prevCur) ? cur : prevCur;
  prevAt  = atIdx !== -1 ? atIdx : null;
  if (changed) savePassTimes();
}

/* ─── ALARM: tell the passenger when the bus is close to their stop ─ */
const ALARM_MINS = 5;
let alarmOn = false;
function updateAlarmChip() {
  const chip = document.getElementById("alarmChip");
  if (!chip) return;
  chip.classList.toggle("on", alarmOn);
  chip.innerText = alarmOn ? tr("⏰ Alarm on") : tr("⏰ Alarm");
}
function toggleAlarm() {
  alarmOn = !alarmOn;
  updateAlarmChip();
  if (alarmOn) {
    try {
      if ("Notification" in window && Notification.permission === "default") Notification.requestPermission();
    } catch (_) {}
    showToast(tr("Alarm set — you'll be alerted when the bus is about {n} min from your stop. Keep this page open.", { n: ALARM_MINS }), 4500);
    renderTimeline();
  } else {
    showToast(tr("Alarm off"));
  }
}
function fireAlarm(stopName, mins) {
  alarmOn = false;
  updateAlarmChip();
  const msg = mins <= 1 ? tr("The bus is at {stop}", { stop: stopName }) : tr("Bus reaches {stop} in about {n} min", { stop: stopName, n: mins });
  showToast("⏰ " + msg, 8000);
  try { if (navigator.vibrate) navigator.vibrate([300, 150, 300, 150, 300]); } catch (_) {}
  try {
    if ("Notification" in window && Notification.permission === "granted") new Notification(tr("Where Is My Bus"), { body: msg });
  } catch (_) {}
}
function checkAlarm(stops, srcIdx, destIdx, passedThrough, atIdx) {
  if (!alarmOn || wasStale) return;
  // Watch the boarding stop; once the bus is past it, the destination
  let target = srcIdx;
  if (target < 0 || target <= passedThrough) target = destIdx;
  if (target < 0 || target <= passedThrough) return;
  const name = stops[target].stopName.trim();
  if (atIdx === target) { fireAlarm(name, 0); return; }
  const e = etaForStop(stops, target, passedThrough);
  if (e && e.mins <= ALARM_MINS) fireAlarm(name, e.mins);
}

/* ─── RENDER ───────────────────────────────────────────────────── */
function renderTimeline() {
  const container    = document.getElementById("stopList");
  const displayStops = fullRouteStops.length ? fullRouteStops : routeStops;

  if (!displayStops.length) {
    container.innerHTML =
        `<div style="padding:40px 16px; text-align:center; font-size:14px; color:var(--text-faint);">${tr("Loading stops...")}</div>`;
    return;
  }

  if (posDirty) { updateBusPos(displayStops); posDirty = false; recordSpeedSample(); }

  const hasPos   = !!busPos && !busPos.noPosition;
  const atIdx    = hasPos ? busPos.atIdx : -1;
  const between  = hasPos && atIdx === -1;
  const nextIdx  = hasPos ? busPos.nextIdx : -1;
  const passedThrough = hasPos ? busPos.passedThrough : -1;
  const lastIdx  = displayStops.length - 1;

  const srcName   = routeStops[0]?.stopName?.trim().toUpperCase();
  const destName  = routeStops[routeStops.length-1]?.stopName?.trim().toUpperCase();
  const srcOnFull = displayStops.findIndex(s => s.stopName.trim().toUpperCase() === srcName);
  const destOnFull = displayStops.findIndex(s => s.stopName.trim().toUpperCase() === destName);
  const destIdx   = destOnFull >= 0 ? destOnFull : lastIdx;
  // Where the bus sits on the stop list: exactly on a stop, or half-way past the previous one
  const busIdxFloat  = !hasPos ? null : (atIdx !== -1 ? atIdx : passedThrough + 0.5);
  const notYetArrived = hasPos && srcOnFull >= 0 && busIdxFloat < srcOnFull;

  const progressKm = hasPos ? busPos.progressKm : 0;
  const totalDist  = displayStops[lastIdx].distanceFromStartKm;
  const progress   = (!hasPos || !totalDist) ? 0 : Math.max(0, Math.min(100, Math.round((progressKm / totalDist) * 100)));

  updatePassTimes(displayStops, hasPos, atIdx, passedThrough);

  const ageSec = ageSeconds();
  wasStale = ageSec != null && ageSec > STALE_AFTER_SEC;

  /* ── bottom sheet ── */
  document.getElementById("progressBar").style.width = progress + "%";
  setText("progressPct",   progress + "%");
  setText("progressStart", displayStops[0].stopName.trim());
  setText("progressEnd",   displayStops[lastIdx].stopName.trim());
  setText("remainingDisplay", !hasPos ? "—" : `${Math.max(0, totalDist - progressKm).toFixed(1)} km`);

  let currentText = "—";
  if (hasPos) {
    if (atIdx !== -1)        currentText = displayStops[atIdx].stopName.trim();
    else if (nextIdx !== -1) currentText = `→ ${displayStops[nextIdx].stopName.trim()}`;
  } else if (busPos && busPos.offRoute) {
    currentText = tr("Off route");
  }
  setText("currentStopDisplay", currentText);

  // headline + banner
  let title = tr("Waiting for bus location…"), tone = "gray", bannerTxt = tr("Waiting for bus location"), bannerTone = "gray";
  if (hasPos) {
    const speedTxt = speedKmh != null ? " · ~" + tr("{n} km/h", { n: Math.round(currentSpeedKmh()) }) : "";
    if (atIdx !== -1) {
      const nm = displayStops[atIdx].stopName.trim();
      if (atIdx === lastIdx) { title = tr("Arrived {stop}", { stop: nm }); bannerTxt = tr("Trip complete"); bannerTone = "blue"; }
      else                   { title = tr("At {stop}", { stop: nm });      bannerTxt = tr("At a stop");      bannerTone = "blue"; }
      tone = "green";
    } else if (nextIdx !== -1) {
      title = tr("Running · next {stop}", { stop: displayStops[nextIdx].stopName.trim() });
      tone = "green"; bannerTxt = tr("Running") + speedTxt; bannerTone = "green";
    }
    if (notYetArrived) { bannerTxt = tr("On its way to your boarding stop"); bannerTone = "blue"; }
  } else if (busPos && busPos.offRoute) {
    title = tr("Off route"); tone = "amber"; bannerTxt = tr("Bus is away from the route"); bannerTone = "amber";
  }
  if (wasStale && lastLocTs != null) {
    tone = "amber"; bannerTone = "amber";
    bannerTxt = tr("No GPS update for {t} · last known position", { t: timeSpan(lastLocTs) });
  }
  const tEl = document.getElementById("statusTitle");
  tEl.innerText = title; tEl.className = "status-title tone-" + tone;
  const bEl = document.getElementById("statusBanner");
  bEl.innerText = bannerTxt; bEl.className = "status-banner tone-" + bannerTone;

  // Next stop / Crossed
  const nextI = !hasPos ? -1 : (between ? nextIdx : (atIdx !== -1 && atIdx < lastIdx ? atIdx + 1 : -1));
  if (nextI >= 0) {
    const e = etaForStop(displayStops, nextI, passedThrough);
    const km = Math.max(0, displayStops[nextI].distanceFromStartKm - progressKm);
    setText("nextStopName", displayStops[nextI].stopName.trim());
    setText("nextStopSub", `${km.toFixed(1)} km${e ? " - " + (wasStale ? "~" : "") + formatClock(e.arriveMs) : ""}`);
  } else {
    setText("nextStopName", hasPos && atIdx === lastIdx ? "—" : "—");
    setText("nextStopSub", hasPos && atIdx === lastIdx ? tr("End of route") : "—");
  }
  if (hasPos && passedThrough >= 0) {
    setText("crossedName", displayStops[passedThrough].stopName.trim());
    const ce = passTimes[passedThrough];
    const ct = ce ? (ce.d != null ? ce.d : ce.a) : null;
    setText("crossedSub", ct != null ? formatClock(ct) : "—");
  } else {
    setText("crossedName", "—"); setText("crossedSub", "—");
  }

  /* ── notices ── */
  let bannerHtml = "";
  if (wasStale) {
    bannerHtml += `<div class="banner banner-yellow"><span style="font-size:18px">📡</span>
      <span>${tr("No recent GPS update from the driver (last seen {t}). Showing the last known position.", { t: timeAgo(lastLocTs) })}</span></div>`;
  }
  if (busPos && busPos.offRoute) {
    bannerHtml += `<div class="banner banner-yellow"><span style="font-size:18px">⚠️</span>
      <span>${tr("Bus location is about {n} km away from this route — the GPS fix may be inaccurate or the bus is on a diversion.", { n: busPos.offKm.toFixed(1) })}${busPos.noPosition ? "" : " " + tr("Showing the last position on the route.")}</span></div>`;
  }
  if (notYetArrived) {
    bannerHtml += `<div class="banner banner-yellow"><span style="font-size:18px">🚌</span>
      <span>${tr("Bus is on its way — not yet reached your boarding stop")}</span></div>`;
  }

  /* ── stop rows ── */
  const clockTxt = ms => (wasStale ? "~" : "") + formatClock(ms);
  let stopsHtml = "";
  displayStops.forEach((stop, i) => {
    const isFirst   = i === 0;
    const isLast    = i === lastIdx;
    const isPassed  = hasPos && i <= passedThrough;
    const isCurrent = hasPos && i === atIdx;
    const isNext    = between && i === nextIdx;
    const busBelow  = between && i === passedThrough && !isLast;

    const eta  = (hasPos && !isPassed && !isCurrent) ? etaForStop(displayStops, i, passedThrough) : null;
    const halt = Math.max(0, stop.slackTimeMin || 0);
    const rec  = passTimes[i] || {};
    const seen = rec.a;                                   // arrival we saw (ms) or undefined

    // Arrival (left) column
    let arrHtml = "";
    if (isPassed) {
      arrHtml = seen != null ? `<div class="t-main t-done">${formatClock(seen)}</div><div class="t-sub t-done">${tr("Arrived")}</div>`
          : `<div class="t-main t-none">—</div>`;
    } else if (isCurrent) {
      arrHtml = `<div class="t-main t-here">${seen != null ? formatClock(seen) : tr("Here")}</div><div class="t-sub t-here">${seen != null ? tr("Here") : tr("At stop")}</div>`;
    } else if (eta) {
      const soon = eta.mins <= 1;
      arrHtml = `<div class="t-main${wasStale ? " t-stale" : ""}">${clockTxt(eta.arriveMs)}</div>` +
          `<div class="t-sub${soon ? " t-soon" : ""}${wasStale ? " t-stale" : ""}">${soon ? tr("Now") : tr("in {t}", { t: etaText(eta.mins) })}</div>`;
    }

    // Departure (right) column
    let depHtml = "";
    if (isPassed && !isLast) {
      // Keep showing when the bus left the stop (and how long it actually waited, if that was noticeable)
      const stayMin = (rec.a != null && rec.d != null) ? Math.round((rec.d - rec.a) / 60000) : 0;
      depHtml = rec.d != null
          ? `<div class="t-main t-done">${formatClock(rec.d)}</div><div class="t-sub t-done">${stayMin >= 1 ? tr("Halted {n} min", { n: stayMin }) : tr("Departed")}</div>`
          : `<div class="t-main t-none">—</div>`;
    } else if (!isLast && !isPassed) {
      if (isCurrent) {
        depHtml = (seen != null && halt > 0 ? `<div class="t-main">${formatClock(seen + halt * 60000)}</div>` : "") +
            (halt > 0 ? `<div class="t-sub t-halt">${tr("Halt {n} min", { n: halt })}</div>` : "");
      } else if (eta) {
        depHtml = `<div class="t-main${wasStale ? " t-stale" : ""}">${clockTxt(eta.arriveMs + halt * 60000)}</div>` +
            (halt > 0 ? `<div class="t-sub t-halt">${tr("Halt {n} min", { n: halt })}</div>` : "");
      }
    }

    // Chips next to the km
    const isPassengerSrc  = stop.stopName.trim().toUpperCase() === srcName;
    const isPassengerDest = stop.stopName.trim().toUpperCase() === destName;
    let chips = "";
    if (isCurrent)                      chips += `<span class="badge badge-here">● ${tr("Here")}</span>`;
    else if (isNext)                    chips += `<span class="badge badge-arriving">${tr("Next")}</span>`;
    else if (eta && eta.mins <= 1)      chips += `<span class="badge badge-arriving">${tr("Arriving")}</span>`;
    if (isPassengerSrc)                 chips += `<span class="badge badge-src">📍 ${tr("Your stop")}</span>`;
    if (isPassengerDest)                chips += `<span class="badge badge-dest">🏁 ${tr("Your dest")}</span>`;
    if (i === myNearestIdx && myNearestM !== null && myNearestM <= MY_STOP_MAX_M) {
      chips += `<span class="badge badge-you">${tr("You are here")}</span>`;
    }

    const upDone   = hasPos && i - 1 <= passedThrough && !isFirst && (i <= passedThrough + 1);
    const downDone = hasPos && i <= passedThrough && !isLast;
    const dotCl = `dot${(isFirst || isLast) ? " end" : ""}${isPassed ? " done" : ""}${isCurrent ? " cur" : ""}`;

    const enterCl = hasEnteredOnce ? "" : " row-enter";
    const rowCl = `srow${enterCl}${isCurrent ? " current" : ""}${isPassed ? " passed" : ""}`;
    const animDel = hasEnteredOnce ? "" : `animation-delay:${Math.min(i, 14) * 35}ms`;

    stopsHtml += `
    <div class="${rowCl}" style="${animDel}">
      <div class="tcol arr">${arrHtml}</div>

      <div class="rail">
        <span class="seg up ${isFirst ? "none" : (upDone ? "done" : "")}"></span>
        <span class="seg down ${isLast ? "none" : (downDone ? "done" : "")}"></span>
        <span class="${dotCl}"></span>
        ${isCurrent ? `<span class="bus-icon at"><img src="/images/bus.png" alt="Bus"></span>` : ""}
        ${busBelow  ? `<span class="bus-icon btw"><img src="/images/bus.png" alt="Bus"></span>` : ""}
      </div>

      <div class="scol">
        <div class="sname">${escapeHtml(stop.stopName)}</div>
        <div class="smeta"><span>${stop.distanceFromStartKm} km</span>${chips}</div>
      </div>

      <div class="tcol dep">${depHtml}</div>
    </div>`;
  });

  /* ── summary: when the bus reaches the boarding stop and the end of the line ── */
  let summaryHtml = "";
  if (hasPos && !(busPos && busPos.offRoute && busPos.noPosition)) {
    const items = [];
    const addItem = (label, idx) => {
      if (idx < 0) return;
      const e = etaForStop(displayStops, idx, passedThrough);
      let val;
      if (e) {
        val = (e.mins <= 1 ? tr("Now") : etaText(e.mins)) + `<small>${clockTxt(e.arriveMs)}</small>`;
      } else if (idx === atIdx) {
        val = `${tr("Here")}<small>${tr("at the stop")}</small>`;
      } else {
        val = tr("Passed");
      }
      items.push(`<div class="eta-sum-item"><div class="eta-sum-label">${label}</div><div class="eta-sum-value">${val}</div></div>`);
    };
    if (srcOnFull > 0 || (srcOnFull === 0 && lastIdx > 0)) addItem(`📍 ${escapeHtml(displayStops[srcOnFull].stopName.trim())}`, srcOnFull);
    if (destIdx !== srcOnFull) addItem(`🏁 ${escapeHtml(displayStops[destIdx].stopName.trim())}`, destIdx);
    if (items.length) {
      summaryHtml = `<div class="eta-summary">${items.join("")}</div>` +
          `<div class="eta-sum-note">${wasStale ? tr("Based on the last known position") + " · " : ""}${tr("Estimated from the bus's recent speed and scheduled halts")}</div>`;
    }
  }

  container.innerHTML = bannerHtml + summaryHtml + stopsHtml;
  hasEnteredOnce = true;
  syncSheetPad();
  checkAlarm(displayStops, srcOnFull, destIdx, passedThrough, atIdx);
  centerOnBusOnce();
}

function goBack() { window.history.back(); }

/* ─── MY LOCATION (optional; the position never leaves this browser) ─── */
function setMyLocText(text, isError) {
  const el = document.getElementById("myLocText");
  if (!el) return;
  el.textContent = text;
  el.style.color = isError ? "#b42318" : "";
}

function setMyLocButton(sharing, busy) {
  const btn = document.getElementById("myLocBtn");
  if (!btn) return;
  btn.disabled    = !!busy;
  btn.textContent = busy ? "…" : (sharing ? tr("Stop") : tr("Show"));
  btn.style.background = sharing ? "#6b7280" : "#1f6f5c";
}

function toggleMyLocation() {
  if (myWatchId !== null) stopMyLocation();
  else startMyLocation();
}

async function startMyLocation() {
  setMyLocButton(false, true);
  setMyLocText(tr("Finding your location…"));
  try {
    myPos = await Geo.request();            // shows the browser's permission prompt
    myWatchId = navigator.geolocation.watchPosition(
        pos => {
          myPos = { latitude: pos.coords.latitude, longitude: pos.coords.longitude, accuracy: pos.coords.accuracy };
          updateMyLocation();
        },
        err => setMyLocText(Geo.explainError(err), true),
        { enableHighAccuracy: true, maximumAge: 10000, timeout: 20000 }
    );
    setMyLocButton(true, false);
    updateMyLocation();
  } catch (err) {
    myPos = null;
    setMyLocButton(false, false);
    setMyLocText(Geo.explainError(err), true);
  }
}

function stopMyLocation() {
  if (myWatchId !== null) {
    navigator.geolocation.clearWatch(myWatchId);
    myWatchId = null;
  }
  myPos = null;
  myNearestIdx = -1;
  myNearestM = null;
  setMyLocButton(false, false);
  setMyLocText(tr("Show your location to see how far the bus is from you. It stays on your device."));
  renderTimeline();
}

// Works out the stop nearest to the passenger and the distance to the bus, then redraws.
// Also called whenever the bus moves, so the distance stays current.
function updateMyLocation() {
  if (!myPos) return;
  const stops = (fullRouteStops.length ? fullRouteStops : routeStops);
  const hit = Geo.nearest(myPos.latitude, myPos.longitude, stops);

  myNearestIdx = hit ? hit.index : -1;
  myNearestM   = hit ? hit.distanceM : null;

  const lines = [];
  if (hit) {
    lines.push(hit.distanceM <= MY_STOP_MAX_M
        ? tr("You are near {stop} ({d}).", { stop: hit.point.stopName.trim().toUpperCase(), d: Geo.formatDistance(hit.distanceM) })
        : tr("You are {d} from the nearest stop on this route.", { d: Geo.formatDistance(hit.distanceM) }));
  }
  if (busLocation && typeof busLocation.latitude === "number") {
    const toBus = Geo.distanceM(myPos.latitude, myPos.longitude, busLocation.latitude, busLocation.longitude);
    lines.push(tr("The bus is about {d} from you (straight line).", { d: Geo.formatDistance(toBus) }));
  } else {
    lines.push(tr("Waiting for the bus's location…"));
  }
  setMyLocText(lines.join(" "));
  renderTimeline();
}

window.addEventListener("pagehide", () => {
  if (myWatchId !== null) navigator.geolocation.clearWatch(myWatchId);
});

/* ─── SEND MY LOCATION TO A FRIEND (the passenger picks the app; nothing goes to our server) ─── */
let lastShareText = "";

function setShareNote(text, isError) {
  const el = document.getElementById("shareNote");
  if (!el) return;
  el.textContent   = text || "";
  el.style.display = text ? "block" : "none";
  el.style.color   = isError ? "#b42318" : "#1f6f5c";
}

function showShareFallback(text) {
  lastShareText = text;
  const box = document.getElementById("shareFallback");
  const wa  = document.getElementById("shareWhatsApp");
  if (wa)  wa.href = Geo.whatsappLink(text);
  if (box) box.style.display = "flex";
}

function hideShareFallback() {
  const box = document.getElementById("shareFallback");
  if (box) box.style.display = "none";
}

async function copyShareMessage() {
  try {
    await navigator.clipboard.writeText(lastShareText);
    setShareNote(tr("Copied. Paste it into any chat."), false);
  } catch (err) {
    window.prompt(tr("Copy this message:"), lastShareText);   // browsers that block the clipboard
  }
}

function isLocationError(err) {
  return !!err && (typeof err.code === "number" || err.code === "INSECURE" || err.code === "UNSUPPORTED");
}

async function shareMyLocation() {
  const btn = document.getElementById("shareLocBtn");
  btn.disabled = true;
  setShareNote("");
  hideShareFallback();

  try {
    // Needs a position: use the one already shown, or ask the browser once (it prompts for permission)
    if (!myPos) {
      setShareNote(tr("Finding your location…"), false);
      myPos = await Geo.request();
      updateMyLocation();
      setShareNote("");
    }

    const stops = (fullRouteStops.length ? fullRouteStops : routeStops);
    const hit = Geo.nearest(myPos.latitude, myPos.longitude, stops);
    const text = Geo.buildShareMessage({
      latitude:  myPos.latitude,
      longitude: myPos.longitude,
      accuracy:  myPos.accuracy,
      nearestStopName:  hit ? hit.point.stopName.trim().toUpperCase() : null,
      nearestDistanceM: hit ? hit.distanceM : null,
      busDistanceM: (busLocation && typeof busLocation.latitude === "number")
          ? Geo.distanceM(myPos.latitude, myPos.longitude, busLocation.latitude, busLocation.longitude)
          : null,
      trackUrl: window.location.href
    });

    if (typeof navigator.share === "function") {
      try {
        await navigator.share({ title: tr("My location"), text });
        setShareNote(tr("Sent."), false);
      } catch (shareErr) {
        if (shareErr && shareErr.name === "AbortError") return;   // the passenger closed the share menu
        showShareFallback(text);                                    // sharing blocked: offer the manual options
      }
    } else {
      showShareFallback(text);                                      // desktop browsers have no share menu
    }
  } catch (err) {
    if (isLocationError(err)) {
      setShareNote(Geo.explainError(err), true);
    } else {
      setShareNote(tr("Couldn't share your location. Please try again."), true);
    }
  } finally {
    btn.disabled = false;
  }
}