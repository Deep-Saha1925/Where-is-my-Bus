const MAX_RECENT = 5;

document.addEventListener("DOMContentLoaded", () => {
  loadStops();
  renderRecentChips();
  loadQuickResults();

  document.getElementById("source").addEventListener("keydown", e => {
    if (e.key === "Enter") searchBuses();
  });
  document.getElementById("destination").addEventListener("keydown", e => {
    if (e.key === "Enter") searchBuses();
  });
});

/* ─── STOPS ─────────────────────────────────────────────────────── */
function loadStops() {
  fetch("/data/stops.json")
    .then(r => r.json())
    .then(data => {
      const datalist = document.getElementById("stopsList");
      datalist.innerHTML = "";
      data.stops.forEach(stop => {
        const opt = document.createElement("option");
        opt.value = stop.toUpperCase();
        datalist.appendChild(opt);
      });
    })
    .catch(err => console.error("Error loading stops:", err));
}

/* ─── RECENT SEARCHES ────────────────────────────────────────────── */
function getRecent() {
  try { return JSON.parse(localStorage.getItem("wimb_passenger_recent") || "[]"); }
  catch { return []; }
}

function saveRecent(source, destination) {
  if (!source || !destination) return;
  const entry = { source, destination, label: `${source} → ${destination}` };
  let recent  = getRecent().filter(
    r => !(r.source === source && r.destination === destination)
  );
  recent.unshift(entry);
  recent = recent.slice(0, MAX_RECENT);
  localStorage.setItem("wimb_passenger_recent", JSON.stringify(recent));
  renderRecentChips();
}

function removeRecent(index) {
  const recent = getRecent();
  recent.splice(index, 1);
  localStorage.setItem("wimb_passenger_recent", JSON.stringify(recent));
  renderRecentChips();
  loadQuickResults();
}

function clearAllRecent() {
  localStorage.removeItem("wimb_passenger_recent");
  renderRecentChips();
  document.getElementById("quickResults").style.display = "none";
}

function applyRecent(source, destination) {
  document.getElementById("source").value      = source;
  document.getElementById("destination").value = destination;
  searchBuses();
}

function renderRecentChips() {
  const recent  = getRecent();
  const section = document.getElementById("recentSection");
  const chips   = document.getElementById("recentChips");

  if (!recent.length) {
    section.style.display = "none";
    return;
  }

  section.style.display = "block";
  chips.innerHTML = recent.map((r, i) => `
    <div class="recent-chip"
         onclick="applyRecent('${r.source}', '${r.destination}')">
      <i class="fa-solid fa-clock-rotate-left" style="font-size:11px; opacity:0.7"></i>
      ${r.source} → ${r.destination}
      <span class="remove-chip"
            onclick="event.stopPropagation(); removeRecent(${i})">✕</span>
    </div>
  `).join("");
}

/* ─── QUICK RESULTS ─────────────────────────────────────────────── */
async function loadQuickResults() {
  const recent = getRecent();
  if (!recent.length) return;

  const { source, destination } = recent[0];

  try {
    const res   = await fetch(
      `/api/ride/active?source=${encodeURIComponent(source)}&destination=${encodeURIComponent(destination)}`
    );
    const buses = await res.json();

    const quickResults = document.getElementById("quickResults");
    const quickList    = document.getElementById("quickList");
    const quickCount   = document.getElementById("quickCount");
    const quickRoute   = document.getElementById("quickRoute");

    if (!buses.length) {
      quickResults.style.display = "none";
      return;
    }

    quickResults.style.display = "block";
    quickCount.textContent     = `${buses.length} active`;
    quickRoute.textContent     = `${source} → ${destination}`;

    const routeKey = `${source}_${destination}`;
    quickList.innerHTML = buses.map((bus, i) => `
      <div class="quick-card fade-up"
           style="animation-delay:${i * 80}ms"
           onclick="track('${routeKey}', ${bus.rideId})">
        <div class="bus-icon-wrap">🚌</div>
        <div style="flex:1; min-width:0;">
          <div style="font-size:14px; font-weight:700; color:var(--text-primary);">
            ${bus.busNumber}
          </div>
          <div style="font-size:12px; color:var(--text-muted); margin-top:2px;">
            <span class="route-src">${source}</span>
            <i class="fa-solid fa-arrow-right"
               style="margin:0 4px; font-size:10px; color:var(--text-faint);"></i>
            <span class="route-dest">${destination}</span>
          </div>
        </div>
        <div style="text-align:right; flex-shrink:0;">
          <div class="eta-badge">
            ${calculateETAFromDistance(bus.remainingDistanceKm)}
          </div>
          <div style="font-size:11px; color:#10b981; margin-top:4px;
                      display:flex; align-items:center;
                      justify-content:flex-end; gap:4px;">
            <span class="live-dot-sm"></span> Live
          </div>
        </div>
      </div>
    `).join("");

  } catch (err) {
    console.error("Quick results failed:", err);
  }
}

function renderBusCards(buses, routeKey) {
  const busList       = document.getElementById("busList");
  const resultsHeader = document.getElementById("resultsHeader");
  const resultsCount  = document.getElementById("resultsCount");

  resultsHeader.style.display = "flex";
  resultsCount.textContent    = `${buses.length} bus${buses.length > 1 ? "es" : ""} found`;
  busList.innerHTML           = "";

  buses.forEach((bus, i) => {
    let src = "N/A", dest = "N/A";
    if (bus.routeKey?.includes("_")) [src, dest] = bus.routeKey.split("_");

    const card = document.createElement("div");
    card.className        = "bus-card";
    card.style.animationDelay = `${i * 80}ms`;

    card.innerHTML = `
      <div style="display:flex; justify-content:space-between; align-items:flex-start;">
        <div style="display:flex; align-items:center; gap:12px;">
          <div class="bus-icon-wrap">🚌</div>
          <div>
            <div style="font-size:16px; font-weight:700; color:var(--text-primary);">
              ${bus.busNumber}
            </div>
            <div style="font-size:11px; color:var(--text-faint); margin-top:1px;">
              Ride #${bus.rideId}
            </div>
          </div>
        </div>
        <div style="text-align:right;">
          <div class="eta-badge">
            ${calculateETAFromDistance(bus.remainingDistanceKm)}
          </div>
          <div style="font-size:11px; color:#10b981; margin-top:5px;
                      display:flex; align-items:center;
                      justify-content:flex-end; gap:4px;">
            <span class="live-dot-sm"></span> Live
          </div>
        </div>
      </div>

      <div class="route-pill">
        <span class="route-src">${src}</span>
        <i class="fa-solid fa-arrow-right"
           style="font-size:10px; color:var(--text-faint); margin:0 2px;"></i>
        <span class="route-dest">${dest}</span>
      </div>

      <button class="track-btn" onclick="track('${routeKey}', ${bus.rideId})">
        <i class="fa-solid fa-location-dot"></i>
        Track Bus
      </button>
    `;

    busList.appendChild(card);
  });
}

/* ─── MAIN SEARCH ────────────────────────────────────────────────── */
async function searchBuses() {
  const source      = document.getElementById("source").value.trim();
  const destination = document.getElementById("destination").value.trim();

  if (!source || !destination) {
    alert("Please select both source and destination");
    return;
  }
  if (source === destination) {
    alert("Source and destination cannot be the same");
    return;
  }

  const routeKey      = `${source}_${destination}`;
  const busList       = document.getElementById("busList");
  const loading       = document.getElementById("loading");
  const noResults     = document.getElementById("noResults");
  const resultsHeader = document.getElementById("resultsHeader");
  const quickResults  = document.getElementById("quickResults");

  busList.innerHTML           = "";
  noResults.style.display     = "none";
  resultsHeader.style.display = "none";
  quickResults.style.display  = "none";
  loading.style.display       = "block";

  try {
    const res   = await fetch(
      `/api/ride/active?source=${encodeURIComponent(source)}&destination=${encodeURIComponent(destination)}`
    );
    const buses = await res.json();

    loading.style.display = "none";
    saveRecent(source, destination);

    if (!buses.length) {
      noResults.style.display = "block";
      return;
    }

    renderBusCards(buses, routeKey);

  } catch (err) {
    loading.style.display = "none";
    alert("Failed to fetch buses");
    console.error(err);
  }
}

/* ─── HELPERS ────────────────────────────────────────────────────── */
function track(routeKey, rideId) {
  window.location.href = `track.html?routeKey=${routeKey}&rideId=${rideId}`;
}

function calculateETAFromDistance(distanceKm) {
  if (distanceKm == null) return "Updating";
  const mins = Math.ceil((distanceKm / 30) * 60);
  if (mins <= 1) return "Arriving";
  return `${mins} min`;
}

/* ─── SWAP STOPS ─────────────────────────────────────────────────── */
function swapStops() {
  const src  = document.getElementById("source");
  const dest = document.getElementById("destination");
  const temp = src.value;
  src.value  = dest.value;
  dest.value = temp;
}