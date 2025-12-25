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

async function updateLocation() {
  const res = await fetch(
    `http://localhost:8080/api/location/last-loc/${rideId}`
  );

  if (!res.ok) return;

  const loc = await res.json();
  if (!loc) return;

  const pos = [loc.latitude, loc.longitude];

  if (!marker) {
    marker = L.marker(pos, {icon: busIcon}).addTo(map);
  } else {
    marker.setLatLng(pos);
  }

  map.setView(pos);
}

updateLocation();
setInterval(updateLocation, 3000);