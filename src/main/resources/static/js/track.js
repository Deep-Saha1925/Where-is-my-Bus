//const params = new URLSearchParams(window.location.search);
//let rideId = params.get("rideId");
//const routeKey = params.get("routeKey");
//
//const map = L.map("map").setView([26.7271, 88.3953], 13);
//
//L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png")
//  .addTo(map);
//
//const busIcon = L.divIcon({
//  html: `<i class="fa-solid fa-bus"></i>`,
//  className: "bus-marker",
//  iconSize: [30, 30],
//  iconAnchor: [15, 15],
//});
//
//let marker;
//let stopMarkers = [];
//let routeLine = null;
//let routeStops = []; // ← holds Excel stop data for stop-name lookup
//
///* ---------------- AUTO SELECT BUS ---------------- */
///* ---------------- AUTO SELECT BUS ---------------- */
//async function autoSelectRideId() {
//  if (rideId || !routeKey) return;
//
//  const [source, destination] = routeKey.split("_");
//
//  try {
//    const res = await fetch(
//      `/api/ride/active?source=${encodeURIComponent(source)}&destination=${encodeURIComponent(destination)}`
//    );
//
//    if (!res.ok) {
//      console.error("Failed to fetch active rides:", res.status);
//      return;
//    }
//
//    const buses = await res.json();
//    console.log("Active buses:", buses);
//
//    if (buses.length > 0) {
//      rideId = buses[0].rideId;
//      console.log("Auto-selected rideId:", rideId);
//      updateLocation();
//    } else {
//      console.warn("No active buses on this route");
//    }
//  } catch (err) {
//    console.error("Failed to auto-select bus", err);
//  }
//}
//
///* ---------------- LOAD ROUTE STOPS ---------------- */
//async function loadRouteStops(routeKey, src, dest) {
//  // FIX: encode spaces and special characters in stop names
//  const res = await fetch(
//    `/api/routes?source=${encodeURIComponent(src)}&destination=${encodeURIComponent(dest)}`
//  );
//
//  if (!res.ok) {
//    console.error("Failed to load route stops:", res.status);
//    return;
//  }
//
//  const stops = await res.json();
//  console.log("Loaded routeStops:", stops.length, stops); // debug line
//
//  routeStops = stops;
//
//  stopMarkers.forEach(m => map.removeLayer(m));
//  stopMarkers = [];
//
//  const latlngs = [];
//
//  stops.forEach(stop => {
//    const latlng = [stop.latitude, stop.longitude];
//    latlngs.push(latlng);
//
//    const m = L.circleMarker(latlng, {
//      radius: 6,
//      color: "#1d4ed8",
//      fillColor: "#3b82f6",
//      fillOpacity: 0.9
//    })
//      .addTo(map)
//      .bindPopup(`
//        <b>${stop.stopName}</b><br>
//        Distance: ${stop.distanceFromStartKm} km<br>
//        Halt: ${stop.slackTimeMin} min
//      `);
//
//    stopMarkers.push(m);
//  });
//
//  if (routeLine) map.removeLayer(routeLine);
//
//  routeLine = L.polyline(latlngs, {
//    color: "#2563eb",
//    weight: 4
//  }).addTo(map);
//
//  map.fitBounds(routeLine.getBounds());
//  renderETATable(stops);
//}
//
///* ---------------- ETA TABLE ---------------- */
//function renderETATable(stops) {
//  const body = document.getElementById("etaBody");
//  body.innerHTML = "";
//
//  stops.forEach(s => {
//    const row = document.createElement("tr");
//    row.innerHTML = `
//      <td>${s.stopName}</td>
//      <td>${s.distanceFromStartKm}</td>
//      <td>${s.slackTimeMin}</td>
//    `;
//    body.appendChild(row);
//  });
//}
//
///* ---------------- NEAREST STOP HELPER ---------------- */
//function getNearestStopLabel(lat, lng) {
//  if (!routeStops || routeStops.length === 0) return null;
//
//  const R = 6371000; // Earth radius in metres
//  let nearest = null;
//  let minDist = Infinity;
//
//  routeStops.forEach(stop => {
//    const dLat = (stop.latitude  - lat) * Math.PI / 180;
//    const dLng = (stop.longitude - lng) * Math.PI / 180;
//    const a =
//      Math.sin(dLat / 2) ** 2 +
//      Math.cos(lat * Math.PI / 180) *
//      Math.cos(stop.latitude * Math.PI / 180) *
//      Math.sin(dLng / 2) ** 2;
//    const dist = R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
//
//    if (dist < minDist) {
//      minDist = dist;
//      nearest = { stop, dist };
//    }
//  });
//
//  if (!nearest) return null;
//
//  if (nearest.dist <= 100)  return `🚏 ${nearest.stop.stopName}`;           // at stop
//  if (nearest.dist <= 500)  return `Near ${nearest.stop.stopName}`;         // close
//  return null; // too far — fall back to reverse geocode
//}
//
///* ---------------- REVERSE GEO ---------------- */
//const locationCache = {};
//
//async function reverseGeocode(lat, lng) {
//  const key = `${lat.toFixed(4)},${lng.toFixed(4)}`;
//  if (locationCache[key]) return locationCache[key];
//
//  try {
//    const res = await fetch(
//      `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}`,
//      { headers: { "User-Agent": "WhereIsMyBus" } }
//    );
//    const data = await res.json();
//    const name = data.display_name || "Unknown location";
//    locationCache[key] = name;
//    return name;
//  } catch {
//    return "Location unavailable";
//  }
//}
//
///* ---------------- LIVE BUS LOCATION ---------------- */
//async function updateLocation() {
//  if (!rideId) return;
//
//  const res = await fetch(`/api/location/last-loc/${rideId}`);
//  if (!res.ok) return;
//
//  const loc = await res.json();
//  if (!loc) return;
//
//  const pos = [loc.latitude, loc.longitude];
//
//  // Always get the nearest stop info (no distance limit now)
//  const nearestLabel = getNearestStopLabel(loc.latitude, loc.longitude);
//
//  // Always get reverse geocode for road/area context
//  const geoName = await reverseGeocode(loc.latitude, loc.longitude);
//
//  // Combine: stop info + road name
//  const locationLabel = nearestLabel
//    ? `${nearestLabel}, ${geoName}`
//    : geoName;
//
//  const popup = `<b>Current Location:</b><br>${locationLabel}`;
//
//  if (!marker) {
//    marker = L.marker(pos, { icon: busIcon })
//      .addTo(map)
//      .bindPopup(popup)
//      .openPopup();
//  } else {
//    marker.setLatLng(pos);
//    marker.setPopupContent(popup);
//    marker.openPopup();
//  }
//
//  map.panTo(pos, { animate: true, duration: 0.5 });
//}
//
///* ---------------- INIT ---------------- */
//if (routeKey) {
//  const [src, dest] = routeKey.split("_");
//  loadRouteStops(routeKey, src, dest);
//}
//
//autoSelectRideId();
//setInterval(updateLocation, 3000);
//
//function goBack() {
//  window.history.back();
//}











const params    = new URLSearchParams(window.location.search);
let rideId      = params.get("rideId");
const routeKey  = params.get("routeKey");

let routeStops  = [];
let busLocation = null;
let rideInfo    = null;

/* ─── INIT ─────────────────────────────────────────────────────── */
if (routeKey) {
  const [src, dest] = routeKey.split("_");
  document.getElementById("routeTitle").innerText =
    `${src} → ${dest}`;
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
    const res = await fetch(
      `/api/routes?source=${encodeURIComponent(src)}&destination=${encodeURIComponent(dest)}`
    );
    routeStops = await res.json();
    renderTimeline();
  } catch (err) {
    console.error("Failed to load route:", err);
  }
}

/* ─── AUTO SELECT RIDE ──────────────────────────────────────────── */
async function autoSelectRide() {
  if (!routeKey) return;
  const [src, dest] = routeKey.split("_");
  try {
    const res = await fetch(
      `/api/ride/active?source=${encodeURIComponent(src)}&destination=${encodeURIComponent(dest)}`
    );
    const buses = await res.json();
    if (buses.length > 0) {
      rideId = buses[0].rideId;
      fetchRideInfo();
      tick();
    }
  } catch (err) {
    console.error("Auto-select failed:", err);
  }
}

/* ─── FETCH RIDE INFO (bus number etc) ──────────────────────────── */
async function fetchRideInfo() {
  try {
    const res  = await fetch("/api/ride/active/all");
    const rides = await res.json();
    rideInfo = rides.find(r => String(r.rideId) === String(rideId));
    if (rideInfo) {
      document.getElementById("busNumberDisplay").innerText =
        rideInfo.busNumber || "—";
    }
  } catch (err) {
    console.error("Ride info failed:", err);
  }
}

/* ─── TICK (every 3s) ───────────────────────────────────────────── */
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

/* ─── NEAREST STOP INDEX ────────────────────────────────────────── */
function getNearestStopIndex(lat, lng) {
  if (!routeStops.length) return -1;
  const R = 6371000;
  let minDist = Infinity, nearest = 0;

  routeStops.forEach((stop, i) => {
    const dLat = (stop.latitude  - lat) * Math.PI / 180;
    const dLng = (stop.longitude - lng) * Math.PI / 180;
    const a =
      Math.sin(dLat/2)**2 +
      Math.cos(lat * Math.PI/180) *
      Math.cos(stop.latitude * Math.PI/180) *
      Math.sin(dLng/2)**2;
    const dist = R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    if (dist < minDist) { minDist = dist; nearest = i; }
  });
  return nearest;
}

/* ─── ETA CALC ──────────────────────────────────────────────────── */
function calcETA(stopDistKm, busDistKm) {
  const diff = stopDistKm - busDistKm;
  if (diff <= 0) return null; // passed
  const mins = Math.ceil((diff / 30) * 60);
  if (mins <= 1)  return "Arriving";
  if (mins < 60)  return `${mins} min`;
  return `${Math.round(mins/60)}h ${mins%60}m`;
}

/* ─── RENDER TIMELINE ───────────────────────────────────────────── */
function renderTimeline() {
  const container = document.getElementById("stopList");
  if (!routeStops.length) {
    container.innerHTML =
      `<div class="px-4 py-8 text-center text-sm text-gray-400">Loading stops...</div>`;
    return;
  }

  let currentIdx = -1;
  let busDistKm  = 0;

  if (busLocation) {
    currentIdx = getNearestStopIndex(busLocation.latitude, busLocation.longitude);
    // Estimate bus distance along route using nearest stop's distance
    if (currentIdx >= 0) {
      busDistKm = routeStops[currentIdx].distanceFromStartKm;
    }
  }

  const totalDist = routeStops[routeStops.length - 1].distanceFromStartKm;
  const progress  = totalDist > 0
    ? Math.min(100, Math.round((busDistKm / totalDist) * 100))
    : 0;

  document.getElementById("progressBar").style.width = progress + "%";
  document.getElementById("remainingDisplay").innerText =
    totalDist > 0
      ? `${(totalDist - busDistKm).toFixed(1)} km`
      : "—";

  if (currentIdx >= 0) {
    document.getElementById("currentStopDisplay").innerText =
      routeStops[currentIdx].stopName;
  }

  let html = "";

  routeStops.forEach((stop, i) => {
    const isFirst   = i === 0;
    const isLast    = i === routeStops.length - 1;
    const isPassed  = currentIdx >= 0 && i < currentIdx;
    const isCurrent = i === currentIdx;
    const eta       = calcETA(stop.distanceFromStartKm, busDistKm);

    // Line segment colors
    const topLine   = (isPassed || isCurrent) ? "bg-blue-500" : "bg-gray-200";
    const botLine   = isPassed                ? "bg-blue-500" : "bg-gray-200";

    // Dot style
    let dotClass = "w-3 h-3 rounded-full border-2 ";
    if (isPassed)      dotClass += "bg-blue-500 border-blue-500";
    else if (isCurrent) dotClass += "bg-white border-blue-500 ring-2 ring-blue-100 w-3.5 h-3.5";
    else if (isFirst || isLast) dotClass += "bg-blue-700 border-blue-700";
    else               dotClass += "bg-gray-200 border-gray-300";

    // ETA badge
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

    // Row highlight for current
    const rowBg = isCurrent ? "bg-blue-50" : "";

    html += `
    <div class="flex items-stretch px-4 ${rowBg} ${i < routeStops.length-1 ? 'border-b border-gray-50' : ''}">

      <!-- Timeline column -->
      <div class="flex flex-col items-center w-7 flex-shrink-0 py-1 relative">
        <div class="flex-1 w-0.5 ${isFirst ? 'invisible' : topLine}"></div>

        ${isCurrent ? `
          <div class="relative flex items-center justify-center">
            <div class="${dotClass}"></div>
            <span class="bus-float absolute -left-4 text-lg" style="font-size:18px">🚌</span>
          </div>
        ` : `<div class="${dotClass}"></div>`}

        <div class="flex-1 w-0.5 ${isLast ? 'invisible' : botLine}"></div>
      </div>

      <!-- Stop info -->
      <div class="flex-1 py-3 pl-3">
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium ${isPassed ? 'text-gray-400' : isCurrent ? 'text-blue-700' : 'text-gray-800'}">
            ${stop.stopName}
          </span>
          ${etaBadge}
        </div>
        <div class="flex gap-3 mt-0.5 text-xs ${isPassed ? 'text-gray-300' : 'text-gray-400'}">
          <span>${stop.distanceFromStartKm} km</span>
          ${stop.slackTimeMin > 0 ? `<span>Halt ${stop.slackTimeMin} min</span>` : ""}
        </div>
      </div>
    </div>`;
  });

  container.innerHTML = html;
}

/* ─── HELPERS ───────────────────────────────────────────────────── */
function timeAgo(ts) {
  if (!ts) return "just now";
  const diff = Math.floor((Date.now() - new Date(ts).getTime()) / 1000);
  if (diff < 10)  return "just now";
  if (diff < 60)  return `${diff}s ago`;
  return `${Math.floor(diff/60)}m ago`;
}

function goBack() {
  window.history.back();
}