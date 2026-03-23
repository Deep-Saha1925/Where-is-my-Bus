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
      `<div class="px-4 py-8 text-center text-sm text-gray-400">Loading stops...</div>`;
    return;
  }

  const busPos = getBusPosition();

  // Passenger's source stop name (to mark it and show banner)
  const srcName  = routeStops[0]?.stopName?.trim().toUpperCase();
  const destName = routeStops[routeStops.length - 1]?.stopName?.trim().toUpperCase();

  // Index of passenger's source on the full route
  const srcOnFull = displayStops.findIndex(
    s => s.stopName.trim().toUpperCase() === srcName
  );

  // Is bus still before the passenger's boarding stop?
  const notYetArrived = busPos && srcOnFull >= 0 && busPos.nearestIdx < srcOnFull;

  // Bus distance and overall progress
  const busDistKm = busPos
    ? displayStops[busPos.nearestIdx]?.distanceFromStartKm || 0
    : 0;
  const totalDist = displayStops[displayStops.length - 1].distanceFromStartKm;
  const progress  = !busPos
    ? 0
    : Math.min(100, Math.round((busDistKm / totalDist) * 100));

  // Update header info
  document.getElementById("progressBar").style.width = progress + "%";
  document.getElementById("remainingDisplay").innerText = !busPos
    ? "—"
    : `${(totalDist - busDistKm).toFixed(1)} km`;
  document.getElementById("currentStopDisplay").innerText = busPos
    ? displayStops[busPos.nearestIdx].stopName
    : "—";

  // Yellow banner if bus hasn't reached passenger's boarding stop yet
  let bannerHtml = "";
  if (notYetArrived) {
    bannerHtml = `
      <div class="px-4 py-2.5 bg-yellow-50 border-b border-yellow-100 flex items-center gap-2 text-xs text-yellow-700 font-medium">
        <span style="font-size:15px">🚌</span>
        Bus is on its way — not yet reached your boarding stop
      </div>`;
  }

  // Build stop rows
  let stopsHtml = "";

  displayStops.forEach((stop, i) => {
    const isFirst   = i === 0;
    const isLast    = i === displayStops.length - 1;
    const isPassed  = busPos ? busPos.nearestIdx > i  : false;
    const isCurrent = busPos ? busPos.nearestIdx === i : false;

    // Line segment colors
    const topLine = (isPassed || isCurrent) ? "bg-blue-500" : "bg-gray-200";
    const botLine =  isPassed               ? "bg-blue-500" : "bg-gray-200";

    // Dot style
    let dotClass = "w-3 h-3 rounded-full border-2 flex-shrink-0 ";
    if      (isPassed)          dotClass += "bg-blue-500 border-blue-500";
    else if (isCurrent)         dotClass += "bg-white border-blue-500 ring-2 ring-blue-100 w-3.5 h-3.5";
    else if (isFirst || isLast) dotClass += "bg-blue-700 border-blue-700";
    else                        dotClass += "bg-gray-200 border-gray-300";

    // ETA badge
    const eta = calcETA(stop.distanceFromStartKm, busDistKm);
    let etaBadge = "";
    if (isPassed) {
      etaBadge = `<span class="ml-auto text-xs font-medium px-2 py-0.5 rounded-full bg-gray-100 text-gray-400">Passed</span>`;
    } else if (isCurrent) {
      etaBadge = `<span class="ml-auto text-xs font-medium px-2 py-0.5 rounded-full bg-blue-100 text-blue-700">Here</span>`;
    } else if (eta === "Arriving") {
      etaBadge = `<span class="ml-auto text-xs font-medium px-2 py-0.5 rounded-full bg-yellow-100 text-yellow-700">Arriving</span>`;
    } else if (eta) {
      etaBadge = `<span class="ml-auto text-xs font-medium px-2 py-0.5 rounded-full bg-gray-100 text-gray-600">${eta}</span>`;
    }

    // Mark the passenger's own boarding and destination stops
    const isPassengerSrc  = stop.stopName.trim().toUpperCase() === srcName;
    const isPassengerDest = stop.stopName.trim().toUpperCase() === destName;
    const passengerMarker = isPassengerSrc
      ? `<span class="text-xs text-indigo-500 font-medium ml-1">📍 Your stop</span>`
      : isPassengerDest
        ? `<span class="text-xs text-indigo-500 font-medium ml-1">🏁 Your dest</span>`
        : "";

    const rowBg  = isCurrent ? "bg-blue-50"   : "";
    const nameCl = isPassed  ? "text-gray-400" : isCurrent ? "text-blue-700" : "text-gray-800";
    const metaCl = isPassed  ? "text-gray-300" : "text-gray-400";

    stopsHtml += `
      <div class="flex items-stretch px-4 ${rowBg} ${!isLast ? 'border-b border-gray-50' : ''}">

        <div class="flex flex-col items-center w-7 flex-shrink-0 py-1 relative">
          <div class="flex-1 w-0.5 ${isFirst ? 'invisible' : topLine}"></div>

          ${isCurrent ? `
            <div class="relative flex items-center justify-center">
              <div class="${dotClass}"></div>
              <span class="bus-float absolute -left-4" style="font-size:18px">🚌</span>
            </div>
          ` : `<div class="${dotClass}"></div>`}

          <div class="flex-1 w-0.5 ${isLast ? 'invisible' : botLine}"></div>
        </div>

        <div class="flex-1 py-3 pl-3">
          <div class="flex items-center gap-1 flex-wrap">
            <span class="text-sm font-medium ${nameCl}">${stop.stopName}</span>
            ${passengerMarker}
            ${etaBadge}
          </div>
          <div class="flex gap-3 mt-0.5 text-xs ${metaCl}">
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