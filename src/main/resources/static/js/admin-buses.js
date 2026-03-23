async function loadActiveBuses() {
  try {
    const res   = await fetch("/api/ride/active/all");
    const rides = await res.json();

    const grid = document.getElementById("busGrid");

    // Update stats
    const uniqueRoutes = new Set(rides.map(r => r.routeKey)).size;
    document.getElementById("statCount").textContent  = rides.length;
    document.getElementById("statRoutes").textContent = uniqueRoutes;
    document.getElementById("lastRefreshed").textContent =
      "Last updated " + new Date().toLocaleTimeString();

    if (!rides.length) {
      grid.innerHTML = `
        <div class="empty-state">
          <div class="empty-icon">🚌</div>
          <div class="empty-title">No active buses</div>
          <div class="empty-sub">No rides are currently in progress</div>
        </div>`;
      return;
    }

    grid.innerHTML = rides.map((ride, i) => {
      const [source, destination] = ride.routeKey
        ? ride.routeKey.split("_") : ["—", "—"];

      return `
      <div class="bus-card" style="animation-delay:${i * 60}ms">
        <div class="bus-card-header">
          <div class="bus-number">
            <div class="bus-icon-circle">🚌</div>
            ${ride.busNumber}
          </div>
          <div class="status-badge">
            <span class="status-dot"></span> Active
          </div>
        </div>

        <div class="route-row">
          <span class="route-src">${source}</span>
          <span class="route-arr">→</span>
          <span class="route-dest">${destination}</span>
        </div>

        <div class="meta-row">
          <span class="meta-chip">Ride #${ride.rideId}</span>
          ${ride.latitude && ride.longitude
            ? `<span class="meta-chip">📍 ${Number(ride.latitude).toFixed(4)}, ${Number(ride.longitude).toFixed(4)}</span>`
            : `<span class="meta-chip">📍 Location updating...</span>`
          }
        </div>

        <button class="view-btn" onclick="trackBus('${ride.routeKey}', ${ride.rideId})">
          <span>📍</span> View on Map
        </button>
      </div>`;
    }).join("");

  } catch (err) {
    console.error("Failed to load active rides:", err);
    document.getElementById("busGrid").innerHTML = `
      <div class="empty-state">
        <div class="empty-icon">⚠️</div>
        <div class="empty-title">Failed to load</div>
        <div class="empty-sub">Could not fetch active rides. Retrying...</div>
      </div>`;
  }
}

function trackBus(routeKey, rideId) {
  window.location.href =
    `/track.html?routeKey=${routeKey}&rideId=${rideId}`;
}

loadActiveBuses();
setInterval(loadActiveBuses, 5000);