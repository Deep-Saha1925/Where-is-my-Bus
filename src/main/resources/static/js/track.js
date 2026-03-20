const params = new URLSearchParams(window.location.search);
let rideId = params.get("rideId");
const routeKey = params.get("routeKey");

const map = L.map("map").setView([26.7271, 88.3953], 13);

L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png")
  .addTo(map);

const busIcon = L.divIcon({
  html: `<i class="fa-solid fa-bus"></i>`,
  className: "bus-marker",
  iconSize: [30, 30],
  iconAnchor: [15, 15],
});

let marker;
let stopMarkers = [];
let routeLine = null;
let routeStops = []; // ← holds Excel stop data for stop-name lookup

/* ---------------- AUTO SELECT BUS ---------------- */
/* ---------------- AUTO SELECT BUS ---------------- */
async function autoSelectRideId() {
  if (rideId || !routeKey) return;

  const [source, destination] = routeKey.split("_");

  try {
    const res = await fetch(
      `/api/ride/active?source=${encodeURIComponent(source)}&destination=${encodeURIComponent(destination)}`
    );

    if (!res.ok) {
      console.error("Failed to fetch active rides:", res.status);
      return;
    }

    const buses = await res.json();
    console.log("Active buses:", buses);

    if (buses.length > 0) {
      rideId = buses[0].rideId;
      console.log("Auto-selected rideId:", rideId);
      updateLocation();
    } else {
      console.warn("No active buses on this route");
    }
  } catch (err) {
    console.error("Failed to auto-select bus", err);
  }
}

/* ---------------- LOAD ROUTE STOPS ---------------- */
async function loadRouteStops(routeKey, src, dest) {
  // FIX: encode spaces and special characters in stop names
  const res = await fetch(
    `/api/routes?source=${encodeURIComponent(src)}&destination=${encodeURIComponent(dest)}`
  );

  if (!res.ok) {
    console.error("Failed to load route stops:", res.status);
    return;
  }

  const stops = await res.json();
  console.log("Loaded routeStops:", stops.length, stops); // debug line

  routeStops = stops;

  stopMarkers.forEach(m => map.removeLayer(m));
  stopMarkers = [];

  const latlngs = [];

  stops.forEach(stop => {
    const latlng = [stop.latitude, stop.longitude];
    latlngs.push(latlng);

    const m = L.circleMarker(latlng, {
      radius: 6,
      color: "#1d4ed8",
      fillColor: "#3b82f6",
      fillOpacity: 0.9
    })
      .addTo(map)
      .bindPopup(`
        <b>${stop.stopName}</b><br>
        Distance: ${stop.distanceFromStartKm} km<br>
        Halt: ${stop.slackTimeMin} min
      `);

    stopMarkers.push(m);
  });

  if (routeLine) map.removeLayer(routeLine);

  routeLine = L.polyline(latlngs, {
    color: "#2563eb",
    weight: 4
  }).addTo(map);

  map.fitBounds(routeLine.getBounds());
  renderETATable(stops);
}

/* ---------------- ETA TABLE ---------------- */
function renderETATable(stops) {
  const body = document.getElementById("etaBody");
  body.innerHTML = "";

  stops.forEach(s => {
    const row = document.createElement("tr");
    row.innerHTML = `
      <td>${s.stopName}</td>
      <td>${s.distanceFromStartKm}</td>
      <td>${s.slackTimeMin}</td>
    `;
    body.appendChild(row);
  });
}

/* ---------------- NEAREST STOP HELPER ---------------- */
function getNearestStopLabel(lat, lng) {
  if (!routeStops || routeStops.length === 0) return null;

  const R = 6371000; // Earth radius in metres
  let nearest = null;
  let minDist = Infinity;

  routeStops.forEach(stop => {
    const dLat = (stop.latitude  - lat) * Math.PI / 180;
    const dLng = (stop.longitude - lng) * Math.PI / 180;
    const a =
      Math.sin(dLat / 2) ** 2 +
      Math.cos(lat * Math.PI / 180) *
      Math.cos(stop.latitude * Math.PI / 180) *
      Math.sin(dLng / 2) ** 2;
    const dist = R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

    if (dist < minDist) {
      minDist = dist;
      nearest = { stop, dist };
    }
  });

  if (!nearest) return null;

  if (nearest.dist <= 100)  return `🚏 ${nearest.stop.stopName}`;           // at stop
  if (nearest.dist <= 500)  return `Near ${nearest.stop.stopName}`;         // close
  return null; // too far — fall back to reverse geocode
}

/* ---------------- REVERSE GEO ---------------- */
const locationCache = {};

async function reverseGeocode(lat, lng) {
  const key = `${lat.toFixed(4)},${lng.toFixed(4)}`;
  if (locationCache[key]) return locationCache[key];

  try {
    const res = await fetch(
      `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}`,
      { headers: { "User-Agent": "WhereIsMyBus" } }
    );
    const data = await res.json();
    const name = data.display_name || "Unknown location";
    locationCache[key] = name;
    return name;
  } catch {
    return "Location unavailable";
  }
}

/* ---------------- LIVE BUS LOCATION ---------------- */
async function updateLocation() {
  if (!rideId) return;

  const res = await fetch(`/api/location/last-loc/${rideId}`);
  if (!res.ok) return;

  const loc = await res.json();
  if (!loc) return;

  const pos = [loc.latitude, loc.longitude];

  // Always get the nearest stop info (no distance limit now)
  const nearestLabel = getNearestStopLabel(loc.latitude, loc.longitude);

  // Always get reverse geocode for road/area context
  const geoName = await reverseGeocode(loc.latitude, loc.longitude);

  // Combine: stop info + road name
  const locationLabel = nearestLabel
    ? `${nearestLabel}, ${geoName}`
    : geoName;

  const popup = `<b>Current Location:</b><br>${locationLabel}`;

  if (!marker) {
    marker = L.marker(pos, { icon: busIcon })
      .addTo(map)
      .bindPopup(popup)
      .openPopup();
  } else {
    marker.setLatLng(pos);
    marker.setPopupContent(popup);
    marker.openPopup();
  }

  map.panTo(pos, { animate: true, duration: 0.5 });
}

/* ---------------- INIT ---------------- */
if (routeKey) {
  const [src, dest] = routeKey.split("_");
  loadRouteStops(routeKey, src, dest);
}

autoSelectRideId();
setInterval(updateLocation, 3000);

function goBack() {
  window.history.back();
}