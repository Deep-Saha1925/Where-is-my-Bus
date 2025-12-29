const map = L.map("map").setView([26.7271, 88.3953], 13);

L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
  maxZoom: 19
}).addTo(map);

const busIcon = L.divIcon({
  html: `<i class="fa-solid fa-bus"></i>`,
  className: "bus-marker",
  iconSize: [30, 30],
  iconAnchor: [15, 15]
});

const busMarkers = new Map(); // rideId → marker

async function loadAllActiveBuses() {
  try {
    const res = await fetch("http://localhost:8080/api/ride/active/all");
    if (!res.ok) return;

    const buses = await res.json();

    buses.forEach(bus => {
      if (!bus.latitude || !bus.longitude) return;

      const pos = [bus.latitude, bus.longitude];

      const popup = `
        <b>Bus:</b> ${bus.busNumber}<br>
        <b>Route:</b> ${bus.routeKey}<br>
        <b>Ride ID:</b> ${bus.rideId}
      `;

      if (!busMarkers.has(bus.rideId)) {
        const marker = L.marker(pos, { icon: busIcon })
          .addTo(map)
          .bindPopup(popup)
          .openPopup();

        busMarkers.set(bus.rideId, marker);
      } else {
        const marker = busMarkers.get(bus.rideId);
        marker.setLatLng(pos);
        marker.setPopupContent(popup);
      }
    });

  } catch (err) {
    console.error("Failed to load active buses", err);
  }
}

// initial load
loadAllActiveBuses();

// refresh every 3 seconds
setInterval(loadAllActiveBuses, 3000);
