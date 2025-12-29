async function loadActiveBuses() {
  try {
    const res = await fetch("http://localhost:8080/api/ride/active/all");
    const rides = await res.json();

    const body = document.getElementById("busTableBody");
    body.innerHTML = "";

    if (!rides.length) {
      body.innerHTML = `
        <tr>
          <td colspan="5" class="p-4 text-center text-gray-500">
            No active buses
          </td>
        </tr>
      `;
      return;
    }

    rides.forEach(ride => {
      const routeKey = ride.routeKey;
      const [source, destination] = routeKey.split("_");

      const row = document.createElement("tr");
      row.className = "border-t hover:bg-gray-50";

      row.innerHTML = `
        <td class="p-3 font-semibold">${ride.busNumber}</td>
        <td class="p-3">${source}</td>
        <td class="p-3">${destination}</td>
        <td class="p-3">
          <span class="text-green-600 font-bold">LIVE</span>
        </td>
        <td class="p-3 text-center">
          <button
            onclick="trackBus('${routeKey}', ${ride.rideId})"
            class="bg-blue-600 text-white px-3 py-1 rounded hover:bg-blue-700"
          >
            View Map
          </button>
        </td>
      `;

      body.appendChild(row);
    });

  } catch (err) {
    console.error("Failed to load active rides", err);
  }
}

function trackBus(routeKey, rideId) {
  window.location.href =
    `http://localhost:8080/track.html?routeKey=${routeKey}&rideId=${rideId}`;
}

// initial load + auto refresh
loadActiveBuses();
setInterval(loadActiveBuses, 5000);