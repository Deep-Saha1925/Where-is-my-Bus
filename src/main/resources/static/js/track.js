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
let hasEnteredOnce = false; // only slide the rows in on the first render, not every WS/poll refresh

let stompClient        = null;
let wsSubscribedRideId = null;
let wsReconnectTimer   = null;
let lastWsPushAt       = 0;   // when the last live push arrived (0 = never)

/* ─── HELPERS ──────────────────────────────────────────────────── */
// Split "SRC_DEST" into [source, destination]. Stop names can contain "_"
// (e.g. "My_seat"), so the server works out the right split point by checking
// which halves are real stops on the route. Falls back to the first "_".
async function splitRouteKey(key, code) {
  if (!key || key.indexOf("_") === -1) return [key || "", ""];
  try {
    const codeParam = code ? `&routeCode=${encodeURIComponent(code)}` : "";
    const res = await fetch(`/api/routes/split?routeKey=${encodeURIComponent(key)}${codeParam}`);
    if (res.ok) {
      const d = await res.json();
      return [d.source, d.destination];
    }
  } catch (_) { /* use fallback below */ }
  const idx = key.indexOf("_");
  return [key.substring(0, idx).trim(), key.substring(idx + 1).trim()];
}

// fetch + JSON that throws on HTTP errors instead of silently returning an
// {error: "..."} object that later code treats as a list of stops.
async function getJson(url) {
  const res = await fetch(url);
  if (!res.ok) {
    let msg = `HTTP ${res.status}`;
    try { msg = (await res.json()).error || msg; } catch (_) {}
    throw new Error(msg);
  }
  return res.json();
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
    const stops = await getJson(
        `/api/routes?source=${encodeURIComponent(src)}&destination=${encodeURIComponent(dest)}${routeParam}`
    );
    routeStops = Array.isArray(stops) ? stops : [];
    if (rideId) await loadFullRoute();
    posDirty = true;
    lastProgressKm = null; lastLocated = null; // new route/stops -> forget old progress
    renderTimeline();
  } catch (err) {
    console.error("Failed to load route:", err);
    container.innerHTML =
        `<div style="padding:32px 16px; text-align:center; font-size:14px; color:var(--text-faint);">` +
        `Could not load stops (${escapeHtml(err.message)})</div>`;
  }
}

/* ─── LOAD FULL ROUTE ───────────────────────────────────────────── */
// The bus's own journey (its start -> end stop), used to place the bus.
async function loadFullRoute() {
  try {
    if (!rideInfo || !rideInfo.routeKey) {
      const rides = await getJson("/api/ride/active/all");
      rideInfo = rides.find(r => String(r.rideId) === String(rideId)) || null;
    }
    const ride = rideInfo;
    if (!ride || !ride.routeKey) { fullRouteStops = routeStops; return; }

    document.getElementById("busNumberDisplay").innerText = ride.busNumber || "—";

    // The ride's own routeCode is authoritative (straight from the DB).
    if (ride.routeCode) routeCode = ride.routeCode;
    const routeParam = routeCode ? `&routeCode=${encodeURIComponent(routeCode)}` : "";

    const [rideSrc, rideDest] = await splitRouteKey(ride.routeKey, routeCode);
    const stops = await getJson(
        `/api/routes?source=${encodeURIComponent(rideSrc)}&destination=${encodeURIComponent(rideDest)}${routeParam}`
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
async function fetchRideInfo() {
  try {
    const rides = await getJson("/api/ride/active/all");
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
async function tick() {
  if (!rideId) return;
  try {
    const res = await fetch(`/api/location/last-loc/${rideId}`);
    if (!res.ok) return;
    applyLocation(await res.json());
  } catch (err) {
    console.error("Tick failed:", err);
  }
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
  if (isNaN(ms)) return "just now";
  const diff = Math.floor((Date.now() - ms) / 1000);
  if (diff < 10)   return "just now";
  if (diff < 60)   return `${diff}s ago`;
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  return `${Math.floor(diff / 3600)}h ${Math.floor((diff % 3600) / 60)}m ago`;
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

/* ─── APPLY LOCATION (shared by the WebSocket push and the fallback poll) */
function applyLocation(loc) {
  if (!loc || typeof loc.latitude !== "number" || typeof loc.longitude !== "number") return;

  // A slow REST poll must never overwrite a fresher WebSocket push.
  const ts = parseTs(loc.timestamp);
  if (!isNaN(ts) && lastLocTs != null && ts < lastLocTs) return;

  busLocation = loc;
  lastLocTs   = isNaN(ts) ? Date.now() : ts;
  posDirty    = true;

  document.getElementById("liveBadge").classList.remove("hidden");
  document.getElementById("liveBadge").classList.add("flex");
  updateLastUpdatedLabel();

  renderTimeline();
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
function calcETA(stopDistKm, busDistKm) {
  const diff = stopDistKm - busDistKm;
  if (diff <= 0) return null;
  const mins = Math.ceil((diff / 30) * 60);
  if (mins <= 1)  return "Arriving";
  if (mins < 60)  return `${mins} min`;
  return `${Math.floor(mins/60)}h ${mins%60}m`;
}

/* ─── RENDER TIMELINE ───────────────────────────────────────────── */
function renderTimeline() {
  const container    = document.getElementById("stopList");
  const displayStops = fullRouteStops.length ? fullRouteStops : routeStops;

  if (!displayStops.length) {
    container.innerHTML =
        `<div style="padding:32px 16px; text-align:center; font-size:14px; color:var(--text-faint);">Loading stops...</div>`;
    return;
  }

  if (posDirty) { updateBusPos(displayStops); posDirty = false; }

  const hasPos   = !!busPos && !busPos.noPosition;
  const atIdx    = hasPos ? busPos.atIdx : -1;
  const between  = hasPos && atIdx === -1;
  const nextIdx  = hasPos ? busPos.nextIdx : -1;
  const passedThrough = hasPos ? busPos.passedThrough : -1;

  const srcName   = routeStops[0]?.stopName?.trim().toUpperCase();
  const destName  = routeStops[routeStops.length-1]?.stopName?.trim().toUpperCase();
  const srcOnFull = displayStops.findIndex(
      s => s.stopName.trim().toUpperCase() === srcName
  );
  // Where the bus sits on the stop list: exactly on a stop, or half-way past the previous one
  const busIdxFloat  = !hasPos ? null : (atIdx !== -1 ? atIdx : passedThrough + 0.5);
  const notYetArrived = hasPos && srcOnFull >= 0 && busIdxFloat < srcOnFull;

  const progressKm = hasPos ? busPos.progressKm : 0;
  const totalDist  = displayStops[displayStops.length-1].distanceFromStartKm;
  const progress   = (!hasPos || !totalDist) ? 0 : Math.max(0, Math.min(100, Math.round((progressKm / totalDist) * 100)));

  document.getElementById("progressBar").style.width  = progress + "%";
  document.getElementById("progressPct").innerText    = progress + "%";
  document.getElementById("progressStart").innerText  = displayStops[0].stopName;
  document.getElementById("progressEnd").innerText    = displayStops[displayStops.length-1].stopName;
  document.getElementById("remainingDisplay").innerText = !hasPos
      ? "—" : `${Math.max(0, totalDist - progressKm).toFixed(1)} km`;

  let currentText = "—";
  if (hasPos) {
    if (atIdx !== -1)                 currentText = displayStops[atIdx].stopName;
    else if (nextIdx !== -1)          currentText = `→ ${displayStops[nextIdx].stopName}`;
  } else if (busPos && busPos.offRoute) {
    currentText = "Off route";
  }
  document.getElementById("currentStopDisplay").innerText = currentText;

  const ageSec = ageSeconds();
  wasStale = ageSec != null && ageSec > STALE_AFTER_SEC;

  let bannerHtml = "";
  if (wasStale) {
    bannerHtml += `
    <div class="banner banner-yellow">
      <span style="font-size:18px">📡</span>
      No recent GPS update from the driver (last seen ${timeAgo(lastLocTs)}). Showing the last known position.
    </div>`;
  }
  if (busPos && busPos.offRoute) {
    bannerHtml += `
    <div class="banner banner-yellow">
      <span style="font-size:18px">⚠️</span>
      Bus location is about ${busPos.offKm.toFixed(1)} km away from this route — the GPS fix may be
      inaccurate or the bus is on a diversion.${busPos.noPosition ? "" : " Showing the last position on the route."}
    </div>`;
  }
  if (notYetArrived) {
    bannerHtml += `
    <div class="banner banner-yellow">
      <span style="font-size:18px">🚌</span>
      Bus is on its way — not yet reached your boarding stop
    </div>`;
  }

  let stopsHtml = "";
  displayStops.forEach((stop, i) => {
    const isFirst   = i === 0;
    const isLast    = i === displayStops.length - 1;
    const isPassed  = hasPos && i <= passedThrough;
    const isCurrent = hasPos && i === atIdx;
    const isNext    = between && i === nextIdx;
    const busOnLineBelow = between && i === passedThrough && !isLast;

    const topLineCl = (isPassed || isCurrent) ? "line-done" : "line-empty";
    const botLineCl =  isPassed               ? "line-done" : "line-empty";

    let dotCl = "dot ";
    if      (isPassed)          dotCl += "dot-done";
    else if (isCurrent)         dotCl += "dot-current";
    else if (isFirst || isLast) dotCl += "dot-endpoint";

    const isPassengerSrc  = stop.stopName.trim().toUpperCase() === srcName;
    const isPassengerDest = stop.stopName.trim().toUpperCase() === destName;

    const eta = hasPos ? calcETA(stop.distanceFromStartKm, progressKm) : null;

    let badgeHtml = "";
    if (isPassed) {
      badgeHtml = `<span class="badge badge-passed">Passed</span>`;
    } else if (isCurrent) {
      badgeHtml = `<span class="badge badge-here">● Here</span>`;
    } else if (isNext) {
      badgeHtml = `<span class="badge badge-arriving">Next${eta && eta !== "Arriving" ? " · " + eta : ""}</span>`;
    } else if (eta === "Arriving") {
      badgeHtml = `<span class="badge badge-arriving">Arriving</span>`;
    } else if (eta) {
      badgeHtml = `<span class="badge badge-eta">${eta}</span>`;
    }

    let markerHtml = "";
    if (isPassengerSrc)  markerHtml = `<span class="badge badge-src">📍 Your stop</span>`;
    if (isPassengerDest) markerHtml = `<span class="badge badge-dest">🏁 Your dest</span>`;

    const enterCl = hasEnteredOnce ? '' : ' row-enter';
    const rowCl  = `stop-row${enterCl}${isCurrent ? ' row-current' : ''}${isPassed ? ' row-passed' : ''}`;
    const nameCl = isPassed ? "color:var(--text-faint)" : isCurrent ? "color:var(--accent)" : "color:var(--text-primary)";
    const metaCl = isPassed ? "color:var(--text-faint)" : "color:var(--text-muted)";
    const animDel = hasEnteredOnce ? "" : `animation-delay:${i * 40}ms`;

    // Bus travelling between two stops: put the bus on the connecting line
    const bottomLine = busOnLineBelow
        ? `<div style="position:relative; display:flex; flex-direction:column; align-items:center; flex:1;">
             <div class="line-seg ${botLineCl}" style="flex:1"></div>
             <span class="bus-icon" style="top:calc(50% - 12px);">🚌</span>
           </div>`
        : `<div class="line-seg ${botLineCl}" style="${isLast ? 'visibility:hidden' : ''}"></div>`;

    stopsHtml += `
    <div class="${rowCl}" style="${animDel}">
      <div style="width:32px; display:flex; flex-direction:column; align-items:center;
                  flex-shrink:0; padding:8px 0; position:relative;">
        <div class="line-seg ${topLineCl}" style="${isFirst ? 'visibility:hidden' : ''}"></div>

        ${isCurrent ? `
          <div style="position:relative; display:flex; align-items:center; justify-content:center;">
            <div class="${dotCl}"></div>
            <span class="bus-icon">🚌</span>
          </div>
        ` : `<div class="${dotCl}"></div>`}

        ${bottomLine}
      </div>

      <div style="flex:1; padding:14px 0 14px 14px;">
        <div style="display:flex; align-items:center; gap:7px; flex-wrap:wrap;">
          <span style="font-size:15px; font-weight:600; ${nameCl}">${escapeHtml(stop.stopName)}</span>
          ${markerHtml}
          ${badgeHtml}
        </div>
        <div style="display:flex; gap:12px; margin-top:4px; font-size:13px; ${metaCl}">
          <span>${stop.distanceFromStartKm} km</span>
          ${stop.slackTimeMin > 0 ? `<span>Halt ${stop.slackTimeMin} min</span>` : ""}
        </div>
      </div>
    </div>`;
  });

  container.innerHTML = bannerHtml + stopsHtml;
  hasEnteredOnce = true;
}

function goBack() { window.history.back(); }