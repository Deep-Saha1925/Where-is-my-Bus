const MAX_RECENT = 5;

document.addEventListener("DOMContentLoaded", () => {
  loadStops();
  renderRecentChips();
  loadQuickResults();

  // Search on Enter key in either input
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
  document.getElementById("quickResults").classList.add("hidden");
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
    section.classList.add("hidden");
    return;
  }

  section.classList.remove("hidden");
  chips.innerHTML = recent.map((r, i) => `
    <div class="recent-chip" onclick="applyRecent('${r.source}', '${r.destination}')">
      <i class="fa-solid fa-clock-rotate-left" style="font-size:11px; opacity:0.7"></i>
      ${r.source} → ${r.destination}
      <span class="remove-chip" onclick="event.stopPropagation(); removeRecent(${i})">✕</span>
    </div>
  `).join("");
}

/* ─── QUICK RESULTS (from recent searches, shown on load) ────────── */
async function loadQuickResults() {
  const recent = getRecent();
  if (!recent.length) return;

  // Use the most recent search to show quick results
  const { source, destination } = recent[0];

  try {
    const res   = await fetch(
      `/api/ride/active?source=${encodeURIComponent(source)}&destination=${encodeURIComponent(destination)}`
    );
    const buses = await res.json();

    const quickResults = document.getElementById("quickResults");
    const quickList    = document.getElementById("quickList");
    const quickCount   = document.getElementById("quickCount");

    if (!buses.length) {
      quickResults.classList.add("hidden");
      return;
    }

    quickResults.classList.remove("hidden");
    quickCount.textContent = `${buses.length} bus${buses.length > 1 ? "es" : ""} active`;

    const routeKey = `${source}_${destination}`;
    quickList.innerHTML = buses.map((bus, i) => `
      <div class="bg-white rounded-xl border border-indigo-100 shadow-sm p-4 fade-up"
           style="animation-delay:${i * 80}ms">
        <div class="flex justify-between items-center mb-2">
          <div class="flex items-center gap-2">
            <div class="w-8 h-8 rounded-full bg-indigo-50 flex items-center justify-center">
              <i class="fa-solid fa-bus text-indigo-600 text-sm"></i>
            </div>
            <span class="font-bold text-gray-800 text-sm">${bus.busNumber}</span>
          </div>
          <span class="text-xs bg-indigo-50 text-indigo-600 px-2 py-1 rounded-full font-medium">
            ${calculateETAFromDistance(bus.remainingDistanceKm)}
          </span>
        </div>
        <p class="text-xs text-gray-500 mb-3">
          <span class="font-medium text-indigo-600">${source}</span>
          <i class="fa-solid fa-arrow-right mx-1 text-gray-300"></i>
          <span class="font-medium text-green-600">${destination}</span>
        </p>
        <button onclick="track('${routeKey}', ${bus.rideId})"
                class="w-full bg-indigo-600 text-white py-1.5 rounded-lg hover:bg-indigo-700
                       transition text-xs font-semibold flex items-center justify-center gap-1">
          <i class="fa-solid fa-location-dot"></i> Track
        </button>
      </div>
    `).join("");

  } catch (err) {
    console.error("Quick results failed:", err);
  }
}

/* ─── MAIN SEARCH ────────────────────────────────────────────────── */
async function searchBuses() {
  const source      = document.getElementById("source").value.trim().toUpperCase();
  const destination = document.getElementById("destination").value.trim().toUpperCase();

  if (!source || !destination) {
    alert("Please select both source and destination");
    return;
  }
  if (source === destination) {
    alert("Source and destination cannot be the same");
    return;
  }

  const routeKey     = `${source}_${destination}`;
  const busList      = document.getElementById("busList");
  const loading      = document.getElementById("loading");
  const noResults    = document.getElementById("noResults");
  const resultsHeader= document.getElementById("resultsHeader");
  const resultsCount = document.getElementById("resultsCount");
  const quickResults = document.getElementById("quickResults");

  busList.innerHTML = "";
  noResults.classList.add("hidden");
  resultsHeader.classList.add("hidden");
  quickResults.classList.add("hidden"); // hide quick results during full search
  loading.classList.remove("hidden");

  try {
    const res   = await fetch(
      `/api/ride/active?source=${encodeURIComponent(source)}&destination=${encodeURIComponent(destination)}`
    );
    const buses = await res.json();

    loading.classList.add("hidden");

    // Save to recent only on successful search
    saveRecent(source, destination);

    if (!buses.length) {
      noResults.classList.remove("hidden");
      return;
    }

    resultsHeader.classList.remove("hidden");
    resultsCount.textContent = `${buses.length} bus${buses.length > 1 ? "es" : ""} found`;

    buses.forEach((bus, i) => {
      const card = document.createElement("div");
      card.className = "bg-white rounded-xl shadow-md p-5 hover:shadow-xl transition transform hover:-translate-y-1 fade-up";
      card.style.animationDelay = `${i * 80}ms`;

      let src = "N/A", dest = "N/A";
      if (bus.routeKey?.includes("_")) [src, dest] = bus.routeKey.split("_");

      card.innerHTML = `
        <div class="flex justify-between items-center mb-3">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-full bg-indigo-50 flex items-center justify-center">
              <i class="fa-solid fa-bus text-indigo-600 text-lg"></i>
            </div>
            <div>
              <h4 class="text-base font-bold text-gray-800">${bus.busNumber}</h4>
              <p class="text-xs text-gray-400">Ride #${bus.rideId}</p>
            </div>
          </div>
          <div class="text-right">
            <span class="text-xs bg-blue-100 text-blue-700 px-2 py-1 rounded-full font-medium block">
              ${calculateETAFromDistance(bus.remainingDistanceKm)}
            </span>
            <span class="text-xs text-green-600 font-medium mt-1 flex items-center justify-end gap-1">
              <span class="w-1.5 h-1.5 bg-green-500 rounded-full inline-block animate-pulse"></span>
              Live
            </span>
          </div>
        </div>

        <div class="flex items-center gap-2 bg-gray-50 rounded-lg px-3 py-2 mb-4 text-sm">
          <span class="font-semibold text-indigo-600">${src}</span>
          <i class="fa-solid fa-arrow-right text-gray-300 text-xs"></i>
          <span class="font-semibold text-green-600">${dest}</span>
        </div>

        <button
          onclick="track('${routeKey}', ${bus.rideId})"
          class="w-full bg-indigo-600 text-white py-2 rounded-lg hover:bg-indigo-700
                 transition flex items-center justify-center gap-2 text-sm font-semibold"
        >
          <i class="fa-solid fa-location-dot"></i>
          Track Bus
        </button>
      `;

      busList.appendChild(card);
    });

  } catch (err) {
    loading.classList.add("hidden");
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