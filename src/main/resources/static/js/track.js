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

/* ─── HELPERS ──────────────────────────────────────────────────── */
// Split "SRC_DEST" on the FIRST underscore only (matches the backend), so a
// stop name is never cut in the wrong place.
function splitRouteKey(key) {
  const idx = key ? key.indexOf("_") : -1;
  if (idx === -1) return [key || "", ""];
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
  if (routeKey) {
    const [src, dest] = splitRouteKey(routeKey);
    document.getElementById("routeTitle").innerText = `${src} → ${dest}`;
  }

  if (rideId) {
    // Resolve the ride FIRST so we know its routeCode before asking for
    // stops. Previously the stops were requested before this was known, so
    // rides on any non-default route asked the default route for stops and
    // got an error back -> the stop list never loaded.
    await fetchRideInfo();
    tick(); // one immediate fetch so the page isn't blank while the socket connects
  }

  if (routeKey) {
    await loadRoute(...splitRouteKey(routeKey));
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
setInterval(tick, 20000);

/* ─── WEBSOCKET (live push, replaces 3s polling) ──────────────────── */
function connectWebSocket() {
  try {
    const socket = new SockJS("/ws");
    stompClient = Stomp.over(socket);
    stompClient.debug = null; // silence stomp.js's verbose console logging

    stompClient.connect({}, () => {
      if (rideId) subscribeToRide(rideId);
    }, () => {
      // onError — stomp.js has no built-in auto-reconnect, so retry by hand
      scheduleWsReconnect();
    });

    socket.onclose = scheduleWsReconnect;
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

    const [rideSrc, rideDest] = splitRouteKey(ride.routeKey);
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
  const [src, dest] = splitRouteKey(routeKey);
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

/* ─── APPLY LOCATION (shared by the WebSocket push and the fallback poll) */
function applyLocation(loc) {
  busLocation = loc;

  document.getElementById("liveBadge").classList.remove("hidden");
  document.getElementById("liveBadge").classList.add("flex");
  document.getElementById("lastUpdated").innerText =
      "Updated " + timeAgo(busLocation.timestamp);

  renderTimeline();
}

/* ─── BUS POSITION (on full route) ─────────────────────────────── */
function getBusPosition() {
  if (!busLocation) return null;

  const stopsToSearch = fullRouteStops.length ? fullRouteStops : routeStops;
  const R   = 6371000;
  const lat = busLocation.latitude;
  const lng = busLocation.longitude;
  let minDist    = Infinity;
  let nearestIdx = 0;

  stopsToSearch.forEach((stop, i) => {
    const dLat = (stop.latitude  - lat) * Math.PI / 180;
    const dLng = (stop.longitude - lng) * Math.PI / 180;
    const a =
        Math.sin(dLat/2)**2 +
        Math.cos(lat * Math.PI/180) *
        Math.cos(stop.latitude * Math.PI/180) *
        Math.sin(dLng/2)**2;
    const dist = R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    if (dist < minDist) { minDist = dist; nearestIdx = i; }
  });

  return {
    nearestIdx,
    stopName: stopsToSearch[nearestIdx].stopName.trim().toUpperCase()
  };
}

/* ─── ETA CALC ──────────────────────────────────────────────────── */
function calcETA(stopDistKm, busDistKm) {
  const diff = stopDistKm - busDistKm;
  if (diff <= 0) return null;
  const mins = Math.ceil((diff / 30) * 60);
  if (mins <= 1)  return "Arriving";
  if (mins < 60)  return `${mins} min`;
  return `${Math.round(mins/60)}h ${mins%60}m`;
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

  const busPos = getBusPosition();

  const srcName   = routeStops[0]?.stopName?.trim().toUpperCase();
  const destName  = routeStops[routeStops.length-1]?.stopName?.trim().toUpperCase();
  const srcOnFull = displayStops.findIndex(
      s => s.stopName.trim().toUpperCase() === srcName
  );
  const notYetArrived = busPos && srcOnFull >= 0 && busPos.nearestIdx < srcOnFull;

  const busDistKm = busPos
      ? displayStops[busPos.nearestIdx]?.distanceFromStartKm || 0
      : 0;
  const totalDist = displayStops[displayStops.length-1].distanceFromStartKm;
  const progress  = (!busPos || !totalDist) ? 0 : Math.min(100, Math.round((busDistKm / totalDist) * 100));

  document.getElementById("progressBar").style.width  = progress + "%";
  document.getElementById("progressPct").innerText    = progress + "%";
  document.getElementById("progressStart").innerText  = displayStops[0].stopName;
  document.getElementById("progressEnd").innerText    = displayStops[displayStops.length-1].stopName;
  document.getElementById("remainingDisplay").innerText = !busPos
      ? "—" : `${(totalDist - busDistKm).toFixed(1)} km`;
  document.getElementById("currentStopDisplay").innerText = busPos
      ? displayStops[busPos.nearestIdx].stopName : "—";

  let bannerHtml = "";
  if (notYetArrived) {
    bannerHtml = `
    <div class="banner banner-yellow">
      <span style="font-size:18px">🚌</span>
      Bus is on its way — not yet reached your boarding stop
    </div>`;
  }

  let stopsHtml = "";
  displayStops.forEach((stop, i) => {
    const isFirst   = i === 0;
    const isLast    = i === displayStops.length - 1;
    const isPassed  = busPos ? busPos.nearestIdx > i  : false;
    const isCurrent = busPos ? busPos.nearestIdx === i : false;

    const topLineCl = (isPassed || isCurrent) ? "line-done" : "line-empty";
    const botLineCl =  isPassed               ? "line-done" : "line-empty";

    let dotCl = "dot ";
    if      (isPassed)          dotCl += "dot-done";
    else if (isCurrent)         dotCl += "dot-current";
    else if (isFirst || isLast) dotCl += "dot-endpoint";

    const isPassengerSrc  = stop.stopName.trim().toUpperCase() === srcName;
    const isPassengerDest = stop.stopName.trim().toUpperCase() === destName;

    const eta = calcETA(stop.distanceFromStartKm, busDistKm);

    let badgeHtml = "";
    if (isPassed) {
      badgeHtml = `<span class="badge badge-passed">Passed</span>`;
    } else if (isCurrent) {
      badgeHtml = `<span class="badge badge-here">● Here</span>`;
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

        <div class="line-seg ${botLineCl}" style="${isLast ? 'visibility:hidden' : ''}"></div>
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

function timeAgo(ts) {
  if (!ts) return "just now";
  const diff = Math.floor((Date.now() - new Date(ts).getTime()) / 1000);
  if (diff < 10)  return "just now";
  if (diff < 60)  return `${diff}s ago`;
  return `${Math.floor(diff/60)}m ago`;
}

function goBack() { window.history.back(); }