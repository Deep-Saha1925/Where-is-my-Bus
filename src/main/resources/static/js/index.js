const MAX_RECENT = 5;

document.addEventListener("DOMContentLoaded", () => {
    loadStops();
    loadDepots();
    renderRecentChips();
    loadQuickResults();

    document.getElementById("source").addEventListener("keydown", e => {
        if (e.key === "Enter") searchBuses();
    });
    document.getElementById("destination").addEventListener("keydown", e => {
        if (e.key === "Enter") searchBuses();
    });
    document.getElementById("depotSource").addEventListener("keydown", e => {
        if (e.key === "Enter") searchDepotRoutes();
    });
    document.getElementById("depotDestination").addEventListener("keydown", e => {
        if (e.key === "Enter") searchDepotRoutes();
    });
});

/* ─── STOPS ─────────────────────────────────────────────────────── */
let allStops = [];

function loadStops() {
    fetch("/data/stops.json")
        .then(r => r.json())
        .then(data => {
            allStops = (data.stops || []).map(s => s.toUpperCase());
            setupStopAutocomplete("source", "sourceDropdown", allStops);
            setupStopAutocomplete("destination", "destinationDropdown", allStops);
        })
        .catch(err => console.error("Error loading stops:", err));
}

/* ─── DEPOTS (static, non-live "search by depot" tab) ───────────── */
let allDepots = [];

function loadDepots() {
    fetch("/api/driver/depots")
        .then(r => r.json())
        .then(data => {
            allDepots = (data || []).map(s => s.toUpperCase());
            setupStopAutocomplete("depotSource", "depotSourceDropdown", allDepots);
            setupStopAutocomplete("depotDestination", "depotDestinationDropdown", allDepots);
        })
        .catch(err => console.error("Error loading depots:", err));
}

/* ─── CUSTOM DROPDOWN AUTOCOMPLETE ──────────────────────────────────
   Replaces the native <datalist> popup with a styled dropdown that
   matches the app's design, supports keyboard navigation, and lets
   the user click a stop to select it. */
function setupStopAutocomplete(inputId, dropdownId, list) {
    const input    = document.getElementById(inputId);
    const dropdown = document.getElementById(dropdownId);
    const options  = list || [];
    let activeIndex = -1;
    let currentMatches = [];

    function highlight(text, query) {
        if (!query) return text;
        const idx = text.toUpperCase().indexOf(query.toUpperCase());
        if (idx === -1) return text;
        return text.slice(0, idx)
            + "<mark>" + text.slice(idx, idx + query.length) + "</mark>"
            + text.slice(idx + query.length);
    }

    function renderDropdown(query) {
        currentMatches = options.filter(s => s.includes(query.toUpperCase()));
        activeIndex = -1;

        if (!query.trim()) {
            dropdown.classList.remove("open");
            dropdown.innerHTML = "";
            return;
        }

        if (!currentMatches.length) {
            dropdown.innerHTML = `<div class="stop-dropdown-empty">No matching stops</div>`;
            dropdown.classList.add("open");
            return;
        }

        dropdown.innerHTML = currentMatches.slice(0, 20).map((stop, i) => `
      <div class="stop-option" data-index="${i}">
        <i class="fa-solid fa-location-dot"></i>
        <span>${highlight(stop, query)}</span>
      </div>
    `).join("");
        dropdown.classList.add("open");

        dropdown.querySelectorAll(".stop-option").forEach(el => {
            el.addEventListener("mousedown", e => {
                e.preventDefault(); // keep focus, avoid blur closing dropdown first
                const i = Number(el.dataset.index);
                selectStop(currentMatches[i]);
            });
        });
    }

    function selectStop(stop) {
        input.value = stop;
        dropdown.classList.remove("open");
        dropdown.innerHTML = "";
        activeIndex = -1;
    }

    function updateActive() {
        dropdown.querySelectorAll(".stop-option").forEach((el, i) => {
            el.classList.toggle("active", i === activeIndex);
        });
        const activeEl = dropdown.querySelector(".stop-option.active");
        if (activeEl) activeEl.scrollIntoView({ block: "nearest" });
    }

    input.addEventListener("input", () => renderDropdown(input.value));

    input.addEventListener("focus", () => {
        if (input.value.trim()) renderDropdown(input.value);
    });

    input.addEventListener("keydown", e => {
        const open = dropdown.classList.contains("open") && currentMatches.length;
        if (!open) return; // let the existing Enter-to-search listener handle it

        if (e.key === "ArrowDown") {
            e.preventDefault();
            activeIndex = Math.min(activeIndex + 1, Math.min(currentMatches.length, 20) - 1);
            updateActive();
        } else if (e.key === "ArrowUp") {
            e.preventDefault();
            activeIndex = Math.max(activeIndex - 1, 0);
            updateActive();
        } else if (e.key === "Enter") {
            if (activeIndex >= 0) {
                e.preventDefault();
                selectStop(currentMatches[activeIndex]);
            }
            // if nothing highlighted, Enter falls through to the search listener
        } else if (e.key === "Escape") {
            dropdown.classList.remove("open");
        }
    });

    document.addEventListener("click", e => {
        if (!input.contains(e.target) && !dropdown.contains(e.target)) {
            dropdown.classList.remove("open");
        }
    });
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
           onclick="track('${routeKey}', ${bus.rideId}, '${bus.routeCode || ""}')">
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

      <button class="track-btn" onclick="track('${routeKey}', ${bus.rideId}, '${bus.routeCode || ""}')">
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
function track(routeKey, rideId, routeCode) {
    const routeParam = routeCode ? `&routeCode=${encodeURIComponent(routeCode)}` : "";
    window.location.href = `track.html?routeKey=${routeKey}&rideId=${rideId}${routeParam}`;
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

function swapDepots() {
    const src  = document.getElementById("depotSource");
    const dest = document.getElementById("depotDestination");
    const temp = src.value;
    src.value  = dest.value;
    dest.value = temp;
}

/* ─── SEARCH TABS: Live Buses vs Depot Routes ───────────────────── */
function switchSearchTab(tab) {
    const isLive = tab === "live";

    document.getElementById("liveSearchPanel").style.display  = isLive ? "" : "none";
    document.getElementById("depotSearchPanel").style.display = isLive ? "none" : "";
    document.getElementById("tabBtnLive").classList.toggle("active", isLive);
    document.getElementById("tabBtnDepot").classList.toggle("active", !isLive);

    if (isLive) {
        document.getElementById("depotResultsSection").style.display = "none";
        renderRecentChips();
        loadQuickResults();
    } else {
        document.getElementById("recentSection").style.display  = "none";
        document.getElementById("quickResults").style.display   = "none";
        document.getElementById("resultsHeader").style.display   = "none";
        document.getElementById("noResults").style.display       = "none";
        document.getElementById("busList").innerHTML             = "";
    }
}

/* ─── DEPOT-TO-DEPOT SEARCH (static — not live GPS) ─────────────── */
/* Best-effort parser for admin-entered free-text times (e.g. "06:00",
 * "6:00 AM") into minutes-since-midnight, purely for sorting the timetable
 * chronologically. Unparseable entries just sort to the end. */
function timeSortKey(t) {
    if (!t) return Infinity;
    const match = t.trim().match(/^(\d{1,2}):(\d{2})\s*(AM|PM|am|pm)?$/);
    if (!match) return Infinity;
    let hours = parseInt(match[1], 10);
    const minutes = parseInt(match[2], 10);
    const meridiem = match[3] ? match[3].toUpperCase() : null;
    if (meridiem === "PM" && hours !== 12) hours += 12;
    if (meridiem === "AM" && hours === 12) hours = 0;
    return hours * 60 + minutes;
}

async function searchDepotRoutes() {
    const source      = document.getElementById("depotSource").value.trim();
    const destination  = document.getElementById("depotDestination").value.trim();

    if (!source || !destination) {
        alert("Please select both depots");
        return;
    }
    if (source === destination) {
        alert("Source and destination depots cannot be the same");
        return;
    }

    const section        = document.getElementById("depotResultsSection");
    const loading         = document.getElementById("depotLoading");
    const noResults       = document.getElementById("depotNoResults");
    const resultsHeader   = document.getElementById("depotResultsHeader");
    const resultsCount    = document.getElementById("depotResultsCount");
    const routeList       = document.getElementById("depotRouteList");

    section.style.display       = "block";
    routeList.innerHTML         = "";
    noResults.style.display     = "none";
    resultsHeader.style.display = "none";
    loading.style.display       = "block";

    try {
        const res = await fetch(
            `/api/routes/by-depots?source=${encodeURIComponent(source)}&destination=${encodeURIComponent(destination)}`
        );
        const matches = await res.json();

        loading.style.display = "none";

        if (!matches.length) {
            noResults.style.display = "block";
            return;
        }

        // Flatten route matches into one timetable row per scheduled
        // departure time — same idea as a train timetable: one row per
        // service, not one card per route. Routes with no departure times
        // registered yet fall back to a single "no timetable" row (using
        // the bus roster as soft context if one exists).
        const rows = [];
        matches.forEach(m => {
            if (m.departureTimes && m.departureTimes.length) {
                m.departureTimes.forEach(time => rows.push({ ...m, time }));
            } else {
                rows.push({ ...m, time: null });
            }
        });
        rows.sort((a, b) => timeSortKey(a.time) - timeSortKey(b.time));

        resultsHeader.style.display = "flex";
        resultsCount.textContent    = `${rows.length} departure${rows.length > 1 ? "s" : ""} found`;

        routeList.innerHTML = rows.map((r, i) => {
            if (r.time) {
                return `
                  <div class="depot-route-card timetable-row" style="margin-bottom:10px; animation-delay:${i * 60}ms;">
                    <div class="timetable-time">${r.time}</div>
                    <div class="timetable-info">
                      <div class="timetable-route-name">${r.routeName}</div>
                      <div class="timetable-meta">${r.sourceDepot} → ${r.destinationDepot} &middot; ${r.stopsBetween} stop${r.stopsBetween > 1 ? "s" : ""} &middot; ${r.distanceKm.toFixed(1)} km</div>
                    </div>
                  </div>
                `;
            }

            const busNote = (r.busNumbers && r.busNumbers.length)
                ? `Buses: ${r.busNumbers.join(", ")}`
                : "buses run periodically";

            return `
              <div class="depot-route-card timetable-row timetable-row--fallback" style="margin-bottom:10px; animation-delay:${i * 60}ms;">
                <div class="timetable-time"><i class="fa-solid fa-clock"></i></div>
                <div class="timetable-info">
                  <div class="timetable-route-name">${r.routeName}</div>
                  <div class="timetable-meta">${r.sourceDepot} → ${r.destinationDepot} &middot; no fixed timetable registered yet &middot; ${busNote}</div>
                </div>
              </div>
            `;
        }).join("");

    } catch (err) {
        loading.style.display = "none";
        alert("Failed to fetch routes");
        console.error(err);
    }
}