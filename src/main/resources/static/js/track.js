const params = new URLSearchParams(window.location.search);
const rideId = params.get("rideId");

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


async function updateLocation() {
  const res = await fetch(
    `http://localhost:8080/api/location/last-loc/${rideId}`
  );

  if (!res.ok) return;

  const loc = await res.json();
  if (!loc) return;

  const pos = [loc.latitude, loc.longitude];

  const locationName = await reverseGeocode(loc.latitude, loc.longitude);

  if (!marker) {
      marker = L.marker(pos, { icon: busIcon })
        .addTo(map)
        .bindPopup(`<b>Current Location:</b><br>${locationName}`);
    } else {
      marker.setLatLng(pos);
      marker.setPopupContent(`<b>Current Location:</b><br>${locationName}`);
    }

  map.setView(pos);
}

function goBack() {
  window.history.back();
}


updateLocation();
setInterval(updateLocation, 3000);