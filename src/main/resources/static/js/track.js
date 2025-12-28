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

/* ---------------- AUTO SELECT BUS ---------------- */
async function autoSelectRideId() {
  if (rideId || !routeKey) return;

  const [source, destination] = routeKey.split("_");

  try {
    const res = await fetch(
      `http://localhost:8080/api/ride/active?source=${source}&destination=${destination}`
    );

    const buses = await res.json();

    if (buses.length > 0) {
      rideId = buses[0].rideId; // auto pick first bus
      updateLocation();
    }
  } catch (err) {
    console.error("Failed to auto-select bus", err);
  }
}

/* ---------------- LOAD ROUTE STOPS (PARTIAL) ---------------- */
async function loadRouteStops(routeKey, src, dest) {
  const res = await fetch(
    `/api/routes/${routeKey}?source=${src}&destination=${dest}`
  );

  const stops = await res.json();

  // cleanup old markers
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

  const res = await fetch(
    `http://localhost:8080/api/location/last-loc/${rideId}`
  );

  if (!res.ok) return;

  const loc = await res.json();
  if (!loc) return;

  const pos = [loc.latitude, loc.longitude];
  const locationName = await reverseGeocode(loc.latitude, loc.longitude);

  const popup = `<b>Current Location:</b><br>${locationName}`;

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
