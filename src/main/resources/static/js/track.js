const params    = new URLSearchParams(window.location.search);
let rideId      = params.get("rideId");
const routeKey  = params.get("routeKey");

let routeStops     = [];
let fullRouteStops = [];
let busLocation    = null;
let rideInfo       = null;

/* ─── INIT ─────────────────────────────────────────────────────── */
if (routeKey) {
  const [src, dest] = routeKey.split("_");
  document.getElementById("routeTitle").innerText = `${src} → ${dest}`;
  loadRoute(src, dest);
}

if (!rideId && routeKey) {
  autoSelectRide();
} else if (rideId) {
  fetchRideInfo();
}

setInterval(tick, 3000);

/* ─── LOAD ROUTE STOPS ──────────────────────────────────────────── */
async function loadRoute(src, dest) {
  try {
    const segRes = await fetch(
        `/api/routes?source=${encodeURIComponent(src)}&destination=${encodeURIComponent(dest)}`
    );
    routeStops = await segRes.json();
    if (rideId) await loadFullRoute();
    renderTimeline();
  } catch (err) {
    console.error("Failed to load route:", err);
  }
}

/* ─── LOAD FULL ROUTE ───────────────────────────────────────────── */
async function loadFullRoute() {
  try {
    const res   = await fetch("/api/ride/active/all");
    const rides = await res.json();
    const ride  = rides.find(r => String(r.rideId) === String(rideId));
    if (!ride || !ride.routeKey) return;

    rideInfo = ride;
    document.getElementById("busNumberDisplay").innerText = ride.busNumber || "—";

    const [rideSrc, rideDest] = ride.routeKey.split("_");
    const fullRes = await fetch(
        `/api/routes?source=${encodeURIComponent(rideSrc)}&destination=${encodeURIComponent(rideDest)}`
    );
    fullRouteStops = await fullRes.json();
  } catch (err) {
    console.error("Failed to load full route:", err);
    fullRouteStops = routeStops;
  }
}

/* ─── AUTO SELECT RIDE ──────────────────────────────────────────── */
async function autoSelectRide() {
  if (!routeKey) return;
  const [src, dest] = routeKey.split("_");
  try {
    const res   = await fetch(
        `/api/ride/active?source=${encodeURIComponent(src)}&destination=${encodeURIComponent(dest)}`
    );
    const buses = await res.json();
    if (buses.length > 0) {
      rideId = buses[0].rideId;
      await loadFullRoute();
      tick();
    }
  } catch (err) {
    console.error("Auto-select failed:", err);
  }
}

/* ─── FETCH RIDE INFO ───────────────────────────────────────────── */
async function fetchRideInfo() {
  try {
    const res   = await fetch("/api/ride/active/all");
    const rides = await res.json();
    rideInfo    = rides.find(r => String(r.rideId) === String(rideId));
    if (rideInfo) {
      document.getElementById("busNumberDisplay").innerText =
          rideInfo.busNumber || "—";
      if (!fullRouteStops.length) await loadFullRoute();
    }
  } catch (err) {
    console.error("Ride info failed:", err);
  }
}

/* ─── TICK ──────────────────────────────────────────────────────── */
async function tick() {
  if (!rideId) return;
  try {
    const res = await fetch(`/api/location/last-loc/${rideId}`);
    if (!res.ok) return;
    busLocation = await res.json();

    document.getElementById("liveBadge").classList.remove("hidden");
    document.getElementById("liveBadge").classList.add("flex");
    document.getElementById("lastUpdated").innerText =
        "Updated " + timeAgo(busLocation.timestamp);

    renderTimeline();
  } catch (err) {
    console.error("Tick failed:", err);
  }
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
  const progress  = !busPos ? 0 : Math.min(100, Math.round((busDistKm / totalDist) * 100));

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

    const rowCl  = `stop-row${isCurrent ? ' row-current' : ''}${isPassed ? ' row-passed' : ''}`;
    const nameCl = isPassed ? "color:var(--text-faint)" : isCurrent ? "color:var(--accent)" : "color:var(--text-primary)";
    const metaCl = isPassed ? "color:var(--text-faint)" : "color:var(--text-muted)";
    const animDel = `animation-delay:${i * 40}ms`;

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
          <span style="font-size:15px; font-weight:600; ${nameCl}">${stop.stopName}</span>
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
}

/* ─── HELPERS ───────────────────────────────────────────────────── */
function timeAgo(ts) {
  if (!ts) return "just now";
  const diff = Math.floor((Date.now() - new Date(ts).getTime()) / 1000);
  if (diff < 10)  return "just now";
  if (diff < 60)  return `${diff}s ago`;
  return `${Math.floor(diff/60)}m ago`;
}

function goBack() { window.history.back(); }