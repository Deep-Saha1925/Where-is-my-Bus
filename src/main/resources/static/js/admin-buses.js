/* ── SEARCH / FILTER ── */
let allRides = []; // store full list for filtering

function filterBuses() {
  const query = document.getElementById("searchInput").value.trim().toLowerCase();
  const clearBtn  = document.getElementById("clearBtn");
  const searchMeta = document.getElementById("searchMeta");

  clearBtn.style.display = query ? "block" : "none";

  if (!query) {
    renderCards(allRides);
    searchMeta.textContent = "";
    return;
  }

  const filtered = allRides.filter(ride => {
    const [src, dest] = ride.routeKey ? ride.routeKey.split("_") : ["", ""];
    return (
      ride.busNumber?.toLowerCase().includes(query) ||
      src.toLowerCase().includes(query) ||
      dest.toLowerCase().includes(query)
    );
  });

  renderCards(filtered);

  searchMeta.textContent = filtered.length === 0
    ? `No buses found for "${query}"`
    : `${filtered.length} bus${filtered.length > 1 ? "es" : ""} found for "${query}"`;
}

function clearSearch() {
  document.getElementById("searchInput").value = "";
  document.getElementById("clearBtn").style.display = "none";
  document.getElementById("searchMeta").textContent = "";
  renderCards(allRides);
}
/* ── RENDER CARDS (used by both load and filter) ── */
function renderCards(rides) {
  const grid = document.getElementById("busGrid");

  if (!rides.length) {
    const query = document.getElementById("searchInput").value.trim();
    grid.innerHTML = `
      <div class="empty-state">
        <div class="empty-icon">${query ? "🔍" : "🚌"}</div>
        <div class="empty-title">${query ? `No results for "${query}"` : "No active buses"}</div>
        <div class="empty-sub">${query ? "Try a different bus number, source or destination" : "No rides are currently in progress"}</div>
      </div>`;
    return;
  }

  grid.innerHTML = rides.map((ride, i) => {
    const [source, destination] = ride.routeKey
      ? ride.routeKey.split("_") : ["—", "—"];

    // Highlight matching text
    const query = document.getElementById("searchInput").value.trim().toLowerCase();

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

      <button class="view-btn" onclick="trackBus('${ride.routeKey}', ${ride.rideId}, '${ride.routeCode || ""}')">
        <span>📍</span> View on Map
      </button>
    </div>`;
  }).join("");
}

/* ── LOAD (fetches + caches + renders) ── */
async function loadActiveBuses() {
  try {
    const res   = await fetch("/api/ride/active/all");
    const rides = await res.json();

    allRides = rides; // cache for search filtering

    const uniqueRoutes = new Set(rides.map(r => r.routeKey)).size;
    document.getElementById("statCount").textContent  = rides.length;
    document.getElementById("statRoutes").textContent = uniqueRoutes;
    document.getElementById("lastRefreshed").textContent =
      "Last updated " + new Date().toLocaleTimeString();

    // Only re-render if not currently searching
    const query = document.getElementById("searchInput")?.value.trim();
    if (query) {
      filterBuses(); // re-apply filter on fresh data
    } else {
      renderCards(rides);
    }

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

function trackBus(routeKey, rideId, routeCode) {
  const routeParam = routeCode ? `&routeCode=${encodeURIComponent(routeCode)}` : "";
  window.location.href = `/track.html?routeKey=${routeKey}&rideId=${rideId}${routeParam}`;
}

loadActiveBuses();
setInterval(loadActiveBuses, 5000);