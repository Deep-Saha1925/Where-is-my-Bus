let rideId = null;
let previewWatchId = null;
let rideWatchId = null;
let currentPosition = null;
let driverToken = null;
let selectedRouteCode = null; // set once the driver picks a route in newRideSection
let routeStopsCache = [];    // ordered stops of the selected route

// ---- live location sending ----
const SEND_MIN_INTERVAL_MS = 3000;   // never post more often than this (GPS can fire several times a second)
const SIM_MIN_INTERVAL_MS  = 1000;    // simulated moves (slider / Next stop) may post this often
const HEARTBEAT_MS         = 10000;  // re-send the last fix this often even if the bus hasn't moved
let lastFix        = null;   // latest { latitude, longitude, accuracy } from the device
let lastSentAt     = 0;
let heartbeatTimer = null;
let wakeLock       = null;
let sendFailures   = 0;
let trailingSendTimer = null;  // pending "send the newest position" after a throttled update

// ---- simulated location (test panel) ----
let locationMode   = "live";  // "live" | "sim"
let simOverride    = null;    // {latitude, longitude} while simulating; real GPS is ignored then
let simStops       = [];      // ordered stops of the active ride's route
let activeRouteCode = null;   // routeCode of the ride currently running

/* ------------------ SCREEN SECTIONS ------------------ */
// Exactly one step of the flow is visible at a time. Every transition goes
// through here so two panels can never show together.
const SECTION_IDS = ["loadingSection", "busNumberSection", "resumeSection", "newRideSection", "activeRideSection"];
function showSection(name) {
  SECTION_IDS.forEach(id => {
    const el = document.getElementById(id);
    if (el) el.classList.toggle("hidden", id !== name);
  });
}

document.addEventListener("DOMContentLoaded", async () => {
  initPreviewGPS(); // still runs in background as fallback

  // Show a neutral "Loading…" panel until the depot / route lists are ready,
  // then always start at the login step (bus number + depot code).
  showSection("loadingSection");
  const timeout = new Promise(resolve => setTimeout(resolve, 8000)); // never hang on a slow server
  await Promise.race([Promise.allSettled([loadDepots(), loadRoutes()]), timeout]);
  showSection("busNumberSection");
});

// Coming back to the tab (unlocking the phone etc.): re-grab the wake lock
// and push a fresh location straight away.
document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible" && rideId) {
    requestWakeLock();
    sendLocation(true);
  }
});

/* ------------------ DEPOTS ------------------ */
async function loadDepots() {
  const select = document.getElementById("depotName");
  try {
    const res = await fetch("/api/driver/depots");
    const depots = await safeJson(res);

    select.innerHTML = "";
    const placeholder = document.createElement("option");
    placeholder.value = "";
    placeholder.disabled = true;
    placeholder.textContent = "Select your depot";
    select.appendChild(placeholder);

    depots.forEach(depot => {
      const option = document.createElement("option");
      option.value = depot;
      option.textContent = depot.charAt(0) + depot.slice(1).toLowerCase();
      select.appendChild(option);
    });

    const savedDepot = localStorage.getItem("wimb_driver_depot");
    if (savedDepot && depots.includes(savedDepot)) {
      select.value = savedDepot;
    } else {
      placeholder.selected = true;
    }
  } catch (err) {
    console.error("Failed to load depots:", err);
    select.innerHTML = `<option value="" disabled selected>Could not load depots</option>`;
  }
}

/* ------------------ SAFE JSON HELPER ------------------ */
// Wraps fetch responses so a non-JSON reply (HTML error page, wrong port,
// server not started, etc.) throws a clear message instead of the cryptic
// "Unexpected token '<', "<!DOCTYPE "... is not valid JSON" parser error.
async function safeJson(res) {
  const contentType = res.headers.get("content-type") || "";

  if (!contentType.includes("application/json")) {
    const bodyPreview = (await res.text()).slice(0, 120).trim();
    const looksLikeHtml = bodyPreview.startsWith("<");

    throw new Error(
        looksLikeHtml
            ? `Server returned a web page instead of data (status ${res.status}). ` +
            `Make sure you're opening this page as http://localhost:8080/driver.html ` +
            `served by the Spring Boot app — not a separate dev server or a local file.`
            : `Unexpected response (status ${res.status}): ${bodyPreview}`
    );
  }

  return res.json();
}

/* ------------------ ROUTES ------------------ */
async function loadRoutes() {
  const select = document.getElementById("routeCode");
  try {
    const res = await fetch("/api/routes/list");
    const routes = await safeJson(res); // [{ routeCode, routeName, stopCount }]

    select.innerHTML = "";
    const placeholder = document.createElement("option");
    placeholder.value = "";
    placeholder.disabled = true;
    placeholder.selected = true;
    placeholder.textContent = routes.length ? "Select your route" : "No routes available";
    select.appendChild(placeholder);

    routes.forEach(route => {
      const option = document.createElement("option");
      option.value = route.routeCode;
      option.textContent = `${route.routeName} (${route.stopCount} stops)`;
      select.appendChild(option);
    });
  } catch (err) {
    console.error("Failed to load routes:", err);
    select.innerHTML = `<option value="" disabled selected>Could not load routes</option>`;
  }
}

// Fills a <select> with a placeholder + the given stops.
function fillStopSelect(select, stops, placeholderText) {
  select.innerHTML = "";
  const placeholder = document.createElement("option");
  placeholder.value = "";
  placeholder.disabled = true;
  placeholder.selected = true;
  placeholder.textContent = placeholderText;
  select.appendChild(placeholder);

  stops.forEach(stop => {
    const option = document.createElement("option");
    const name = stop.stopName.trim().toUpperCase();
    option.value = name;
    option.textContent = name;
    select.appendChild(option);
  });
}

// Called when the driver picks a route — loads that route's stops into the
// Start Location / Destination dropdowns and enables them.
async function onRouteChange() {
  const routeSelect = document.getElementById("routeCode");
  const sourceSelect = document.getElementById("source");
  const destSelect = document.getElementById("destination");

  selectedRouteCode = routeSelect.value;
  routeStopsCache = [];

  // Reset downstream fields whenever the route changes
  sourceSelect.disabled = true;
  destSelect.disabled = true;
  fillStopSelect(sourceSelect, [], selectedRouteCode ? "Loading stops..." : "Pick a route first");
  fillStopSelect(destSelect, [], selectedRouteCode ? "Loading stops..." : "Pick a route first");

  if (!selectedRouteCode) return;

  const requestedRoute = selectedRouteCode;
  try {
    const res = await fetch(`/api/routes/stops?routeCode=${encodeURIComponent(requestedRoute)}`);
    const stops = await safeJson(res);
    if (!res.ok || !Array.isArray(stops)) {
      throw new Error((stops && stops.error) || `Server error ${res.status}`);
    }
    // Ignore a stale response if the driver already switched to another route
    if (requestedRoute !== selectedRouteCode) return;

    routeStopsCache = stops;
    fillStopSelect(sourceSelect, stops, stops.length ? "Select start stop" : "No stops on this route");
    fillStopSelect(destSelect, stops, stops.length ? "Select destination stop" : "No stops on this route");
    sourceSelect.disabled = stops.length === 0;
    destSelect.disabled = stops.length === 0;
  } catch (err) {
    console.error("Failed to load stops for route:", err);
    fillStopSelect(sourceSelect, [], "Could not load stops");
    fillStopSelect(destSelect, [], "Could not load stops");
  }
}

/* ------------------ PREVIEW GPS (background fallback) ------------------ */
function initPreviewGPS() {
  if (!navigator.geolocation) return;

  navigator.geolocation.getCurrentPosition(() => {}, () => {}, {
    enableHighAccuracy: false
  });

  previewWatchId = navigator.geolocation.watchPosition(
      pos => {
        currentPosition = pos;
        console.log("GPS preview OK:", pos.coords.latitude, pos.coords.longitude);
      },
      err => {
        if (err.code === err.TIMEOUT) return;
        console.error("Preview GPS error:", err.message);
      },
      { enableHighAccuracy: false, timeout: 60000, maximumAge: 10000 }
  );
}

/* ------------------ GET COORDS FROM EXCEL ROUTE DATA ------------------ */
// Looks the source stop up in the already-loaded stops of the selected route.
function getCoordsFromRoute(source) {
  const match = routeStopsCache.find(
      s => s.stopName.trim().toUpperCase() === source.trim().toUpperCase()
  );
  if (match) {
    return { latitude: match.latitude, longitude: match.longitude };
  }
  console.warn("Source stop not found in route data:", source);
  return null;
}

/* ------------------ START RIDE ------------------ */
async function startRide() {
  const busNumber   = document.getElementById("busNumber").value.trim();
  const source      = document.getElementById("source").value.trim().toUpperCase();
  const destination = document.getElementById("destination").value.trim().toUpperCase();
  const statusEl    = document.getElementById("status");

  if (!selectedRouteCode) {
    alert("Please select a route first");
    return;
  }

  if (!busNumber || !source || !destination) {
    alert("Please fill in Bus Number, Source, and Destination");
    return;
  }

  if (source === destination) {
    alert("Source and destination must be different");
    return;
  }

  statusEl.innerText = "Fetching start location...";

  // STEP 1: Try to get coords from Excel route data first
  let coords = getCoordsFromRoute(source);

  // STEP 2: If Excel lookup failed, fall back to GPS
  if (!coords) {
    statusEl.innerText = "Stop not in route data, trying GPS...";
    try {
      const pos = await getPositionFromGPS();
      coords = {
        latitude:  pos.coords.latitude,
        longitude: pos.coords.longitude
      };
    } catch (err) {
      statusEl.innerText = "";
      alert("Could not get location: " + err.message);
      return;
    }
  }

  const payload = {
    busNumber,
    routeKey: `${source}_${destination}`,
    routeCode: selectedRouteCode || null,
    latitude:  coords.latitude,
    longitude: coords.longitude
  };

  try {
    statusEl.innerText = "Starting ride...";

    const res = await fetch("/api/ride/start", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Driver-Token": driverToken
      },
      body: JSON.stringify(payload)
    });

    if (!res.ok) {
      if (res.status === 403) {
        localStorage.removeItem("wimb_driver_token");
        localStorage.removeItem("wimb_driver_bus");
        driverToken = null;
        // Session expired — send the driver back to re-verify with the depot code
        showSection("busNumberSection");
      }
      const errText = await res.text();
      throw new Error(`Server error: ${errText}`);
    }

    const data = await safeJson(res);
    rideId = data.id;

    // Stop preview GPS watcher
    if (previewWatchId !== null) {
      navigator.geolocation.clearWatch(previewWatchId);
      previewWatchId = null;
    }


    showSection("activeRideSection");
    document.getElementById("activeRouteKey").innerText = `${source} → ${destination}`;
    document.getElementById("activeRideId").innerText = rideId;
    document.getElementById("activeBusNumber").innerText = busNumber;
    statusEl.innerText = `Ride Started ✅ (ID: ${rideId})`;

    // Remember the ride so a page refresh / tab kill doesn't silently end tracking
    activeRouteCode = selectedRouteCode;

    startRideTracking();

  } catch (err) {
    console.error(err);
    statusEl.innerText = "";
    alert("Ride start failed: " + err.message);
  }
}

/* ------------------ GPS FALLBACK (Promise wrapper) ------------------ */
function getPositionFromGPS() {
  return new Promise((resolve, reject) => {
    if (currentPosition) {
      resolve(currentPosition);
      return;
    }
    navigator.geolocation.getCurrentPosition(
        pos => resolve(pos),
        err => reject(err),
        { enableHighAccuracy: true, timeout: 15000, maximumAge: 30000 }
    );
  });
}

/* ------------------ LIVE RIDE TRACKING (GPS after ride starts) ------------------ */
function startRideTracking() {
  if (rideWatchId !== null) return;
  if (!navigator.geolocation) {
    alert("This device/browser does not support GPS, so live tracking cannot start.");
    return;
  }

  requestWakeLock();
  updateCurrentStop();
  loadSimStops().then(updateCurrentStop);

  rideWatchId = navigator.geolocation.watchPosition(
      pos => {
        if (simOverride) return; // simulating: ignore real GPS
        lastFix = {
          latitude:  pos.coords.latitude,
          longitude: pos.coords.longitude,
          accuracy:  pos.coords.accuracy
        };
        updateCurrentStop();
        sendLocation(false);
      },
      err => {
        console.error("Ride GPS error:", err.message);
        setTrackingStatus("⚠️ GPS problem: " + err.message + " — check location permission");
      },
      { enableHighAccuracy: true, timeout: 20000, maximumAge: 0 }
  );

  // Heartbeat: watchPosition only fires when the position changes, so a bus
  // waiting at a stop (or a laptop on a desk) would go silent and look
  // "offline" to passengers. Re-send the last known fix regularly.
  heartbeatTimer = setInterval(() => sendLocation(true), HEARTBEAT_MS);
}

function stopRideTracking() {
  if (rideWatchId !== null) {
    navigator.geolocation.clearWatch(rideWatchId);
    rideWatchId = null;
  }
  if (heartbeatTimer !== null) {
    clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }
  releaseWakeLock();
  lastFix = null;
  lastSentAt = 0;
  sendFailures = 0;
  if (trailingSendTimer) { clearTimeout(trailingSendTimer); trailingSendTimer = null; }
  simOverride = null;
  simStops = [];
  activeRouteCode = null;
  const simSel = document.getElementById("simStop");
  if (simSel) simSel.innerHTML = `<option value="" disabled selected>Loading stops...</option>`;
  setLocationMode("live");
  updateCurrentStop();
}

async function sendLocation(isHeartbeat, force = false) {
  if (!rideId || !lastFix || !driverToken) return;

  const now = Date.now();
  // Throttle real GPS bursts; heartbeats only fire if nothing was sent recently.
  const minGap = isHeartbeat ? HEARTBEAT_MS - 1000
                             : (simOverride ? SIM_MIN_INTERVAL_MS : SEND_MIN_INTERVAL_MS);
  if (!force && now - lastSentAt < minGap) {
    // Don't DROP the newest position -- previously a fix that arrived inside the
    // throttle window was simply lost, and passengers only saw it after the next
    // 10s heartbeat (or never, if the bus then stopped). Send it as soon as the
    // window is over instead. Heartbeats don't need this, they repeat anyway.
    if (!isHeartbeat && !trailingSendTimer) {
      trailingSendTimer = setTimeout(() => {
        trailingSendTimer = null;
        sendLocation(false, true);   // sends whatever lastFix is *now*
      }, Math.max(50, minGap - (now - lastSentAt)));
    }
    return;
  }
  lastSentAt = now;

  if (trailingSendTimer && force) { clearTimeout(trailingSendTimer); trailingSendTimer = null; }

  try {
    const res = await fetch("/api/ride/location", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Driver-Token": driverToken
      },
      body: JSON.stringify({
        rideId,
        latitude:  lastFix.latitude,
        longitude: lastFix.longitude,
        accuracy:  lastFix.accuracy
      })
    });

    if (!res.ok) {
      if (res.status === 403) {
        setTrackingStatus("❌ Session expired or ride ended — passengers can't see you. Stop the ride and start again.");
      } else {
        setTrackingStatus(`⚠️ Server error ${res.status} while sending location — retrying`);
      }
      sendFailures++;
      return;
    }

    sendFailures = 0;
    const t = new Date().toLocaleTimeString();
    const acc = lastFix.accuracy != null ? ` (GPS ±${Math.round(lastFix.accuracy)} m)` : "";
    setTrackingStatus(simOverride
        ? `🧪 Sending SIMULATED location — last sent ${t}`
        : `📡 Sharing live location — last sent ${t}${acc}`);
  } catch (err) {
    sendFailures++;
    console.error("Location update failed:", err);
    setTrackingStatus("⚠️ No internet — location not reaching passengers, retrying…");
  }
}

function setTrackingStatus(text) {
  const el = document.getElementById("status");
  if (el) el.innerText = text;
}

/* ------------------ SIMULATE LOCATION (test panel) ------------------ */
// Lets you test the whole passenger flow from a desk: choose where along the
// route the bus "is" and that position is posted exactly like a real GPS fix.

function setLocationMode(mode) {
  locationMode = mode;
  const tabLive = document.getElementById("tabLive");
  const tabSim  = document.getElementById("tabSim");
  const panel   = document.getElementById("simPanel");
  if (!tabLive || !tabSim || !panel) return;

  tabLive.classList.toggle("active", mode === "live");
  tabSim.classList.toggle("active", mode === "sim");
  panel.classList.toggle("hidden", mode !== "sim");

  if (mode === "sim") {
    loadSimStops().then(() => onSimChange(false));
  } else if (simOverride) {
    // Back to real GPS: drop the fake position so the next real fix takes over
    simOverride = null;
    lastFix = null;
    setTrackingStatus("📡 Back on live GPS — waiting for the next fix…");
    updateCurrentStop();
  }
}

async function loadSimStops() {
  const select = document.getElementById("simStop");
  if (!simStops.length) {
    // Right after starting a ride the stops are already loaded for the selected route
    if (routeStopsCache.length && (!activeRouteCode || activeRouteCode === selectedRouteCode)) {
      simStops = routeStopsCache;
    } else if (activeRouteCode) {
      try {
        const res = await fetch(`/api/routes/stops?routeCode=${encodeURIComponent(activeRouteCode)}`);
        const stops = await safeJson(res);
        if (res.ok && Array.isArray(stops)) simStops = stops;
      } catch (err) {
        console.error("Could not load route stops:", err);
      }
    }
  }

  if (select && select.options.length <= 1) {
    select.innerHTML = "";
    if (!simStops.length) {
      select.innerHTML = `<option value="" disabled selected>Could not load stops for this route</option>`;
    } else {
      simStops.forEach((stop, i) => {
        const opt = document.createElement("option");
        opt.value = String(i);
        opt.textContent = `${i + 1}. ${stop.stopName.trim().toUpperCase()}`;
        select.appendChild(opt);
      });
      select.value = "0";
    }
  }
}

/* ------------------ CURRENT STOP DISPLAY ------------------ */
function distanceMeters(lat1, lon1, lat2, lon2) {
  const R = 6371000, rad = Math.PI / 180;
  const dLat = (lat2 - lat1) * rad, dLon = (lon2 - lon1) * rad;
  const a = Math.sin(dLat / 2) ** 2 +
      Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

// Updates the "Current stop position" card. In sim mode it shows exactly what the
// slider says; in live mode it works out the nearest stop from the GPS fix.
function updateCurrentStop() {
  const main = document.getElementById("posMain");
  if (!main) return;
  const sub = document.getElementById("posSub");
  const bar = document.getElementById("posBar");
  const badge = document.getElementById("posMode");
  const n = simStops.length;

  badge.innerText = simOverride ? "🧪 Simulated" : "📡 Live";

  if (!n || !lastFix) {
    main.innerText = n ? "Waiting for location…" : "Loading route…";
    sub.innerText = "—";
    bar.style.width = "0%";
    return;
  }

  const name = s => s.stopName.trim().toUpperCase();

  if (simOverride) {
    const idx = Math.max(0, parseInt(document.getElementById("simStop").value || "0", 10));
    const t = simStops[idx + 1] ? Number(document.getElementById("simSlider").value) / 100 : 0;
    if (t === 0) {
      main.innerText = `📍 At ${name(simStops[idx])}`;
      sub.innerText = `Stop ${idx + 1} of ${n}` + (simStops[idx + 1] ? ` · next: ${name(simStops[idx + 1])}` : " · end of route");
    } else if (t === 1) {
      main.innerText = `📍 At ${name(simStops[idx + 1])}`;
      sub.innerText = `Stop ${idx + 2} of ${n}`;
    } else {
      main.innerText = `🚌 ${name(simStops[idx])} → ${name(simStops[idx + 1])}`;
      sub.innerText = `${Math.round(t * 100)}% of the way · stop ${idx + 1} of ${n}`;
    }
    bar.style.width = (n > 1 ? ((idx + t) / (n - 1)) * 100 : 100) + "%";
    return;
  }

  // Live GPS: nearest stop on the route
  let best = 0, bestD = Infinity;
  simStops.forEach((s, i) => {
    const d = distanceMeters(lastFix.latitude, lastFix.longitude, s.latitude, s.longitude);
    if (d < bestD) { bestD = d; best = i; }
  });
  const AT_STOP_M = 150;
  const distText = bestD >= 1000 ? `${(bestD / 1000).toFixed(1)} km` : `${Math.round(bestD)} m`;
  main.innerText = bestD <= AT_STOP_M ? `📍 At ${name(simStops[best])}` : `🚌 Near ${name(simStops[best])}`;
  sub.innerText = `Stop ${best + 1} of ${n}` + (bestD <= AT_STOP_M ? "" : ` · ${distText} away`);
  bar.style.width = (n > 1 ? (best / (n - 1)) * 100 : 100) + "%";
}

// resetSlider = true when the stop dropdown changed (bus sits exactly on that stop)
function onSimChange(resetSlider) {
  if (!simStops.length) return;
  const stopSel = document.getElementById("simStop");
  const slider  = document.getElementById("simSlider");
  const idx     = Math.max(0, parseInt(stopSel.value || "0", 10));
  const from    = simStops[idx];
  const to      = simStops[idx + 1] || null; // last stop has nothing after it

  if (resetSlider) slider.value = 0;
  slider.disabled = !to;
  if (!to) slider.value = 0;

  const t = to ? Number(slider.value) / 100 : 0;
  const latitude  = to ? from.latitude  + (to.latitude  - from.latitude)  * t : from.latitude;
  const longitude = to ? from.longitude + (to.longitude - from.longitude) * t : from.longitude;

  document.getElementById("simFromLabel").innerText = from.stopName.trim().toUpperCase();
  document.getElementById("simToLabel").innerText   = to ? to.stopName.trim().toUpperCase() : "(end of route)";
  document.getElementById("simPctLabel").innerText  = `${Math.round(t * 100)}%`;
  document.getElementById("simCoords").innerText    = `${latitude.toFixed(5)}, ${longitude.toFixed(5)}`;

  // From here on the simulated point replaces real GPS, incl. the 10 s heartbeat
  simOverride = { latitude, longitude };
  lastFix = { latitude, longitude, accuracy: 10 };
  updateCurrentStop();
  sendLocation(false, false); // normal throttle while dragging the slider
}

function simStep(delta) {
  if (!simStops.length) return;
  const stopSel = document.getElementById("simStop");
  const next = Math.min(simStops.length - 1, Math.max(0, parseInt(stopSel.value || "0", 10) + delta));
  stopSel.value = String(next);
  onSimChange(true);
  sendSimNow();
}

function sendSimNow() {
  if (!simOverride) onSimChange(false);
  sendLocation(false, true); // bypass throttle
}

/* ------------------ SCREEN WAKE LOCK ------------------ */
// Phones throttle GPS and pause the page when the screen turns off, which is
// the most common reason a driver "stops updating". Keep the screen on.
async function requestWakeLock() {
  try {
    if ("wakeLock" in navigator && !wakeLock) {
      wakeLock = await navigator.wakeLock.request("screen");
      wakeLock.addEventListener("release", () => { wakeLock = null; });
    }
  } catch (err) {
    console.warn("Wake lock unavailable:", err.message);
  }
}

function releaseWakeLock() {
  if (wakeLock) {
    wakeLock.release().catch(() => {});
    wakeLock = null;
  }
}

/* ------------------ STOP RIDE ------------------ */
async function stopRide() {
  if (!rideId) return;

  try {
    const cancelRes = await fetch(`/api/ride/cancel/${rideId}`, {
      method: "PUT",
      headers: { "X-Driver-Token": driverToken }
    });
    if (!cancelRes.ok) {
      // The ride is still running on the server — keep sharing location
      throw new Error(`Server refused to end the ride (status ${cancelRes.status})`);
    }

    stopRideTracking();

    document.getElementById("status").innerText = "Ride Stopped ⛔";

    // Go back to bus number entry
    showSection("busNumberSection");
    document.getElementById("busNumber").value = "";
    document.getElementById("busNumber").disabled = false;

    rideId = null;
    currentPosition = null;

    // Reset the new-ride form so the next ride starts clean
    document.getElementById("routeCode").value = "";
    onRouteChange();

    initPreviewGPS();

  } catch (err) {
    console.error(err);
    alert("Failed to stop ride: " + err.message);
  }
}


/* ------------------ TOGGLE DRIVER CODE VISIBILITY ------------------ */
function toggleDriverCodeVisibility() {
  const input   = document.getElementById("driverCode");
  const eye     = document.getElementById("eyeIcon");
  const eyeOff  = document.getElementById("eyeOffIcon");
  const btn     = document.getElementById("toggleCodeBtn");

  const isHidden = input.type === "password";
  input.type = isHidden ? "text" : "password";

  eye.classList.toggle("hidden", isHidden);
  eyeOff.classList.toggle("hidden", !isHidden);

  btn.setAttribute("aria-label", isHidden ? "Hide driver code" : "Show driver code");
  btn.setAttribute("aria-pressed", isHidden ? "true" : "false");
}

/* ------------------ CHECK BUS (after entering bus number) ------------------ */
async function checkBus() {
  const busNumber = document.getElementById("busNumber").value.trim();
  const depotName = document.getElementById("depotName").value;
  const code = document.getElementById("driverCode").value.trim();

  if (!busNumber) {
    alert("Please enter a bus number");
    return;
  }

  document.getElementById("status").innerText = "Checking...";

  try {
    if (!depotName) {
      document.getElementById("status").innerText = "";
      alert("Please select your depot");
      return;
    }
    if (!code) {
      document.getElementById("status").innerText = "";
      alert("Please enter your depot code");
      return;
    }

    const verifyRes = await fetch("/api/driver/verify", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ depotName, code, busNumber })
    });

    if (!verifyRes.ok) {
      document.getElementById("status").innerText = "";
      // Try to read a real error message; fall back if the body isn't JSON
      let msg = "Invalid depot or code";
      try {
        const errData = await safeJson(verifyRes);
        msg = errData.error || msg;
      } catch (_) { /* keep default msg */ }
      alert(msg);
      return;
    }

    const verifyData = await safeJson(verifyRes);
    driverToken = verifyData.token;
    localStorage.setItem("wimb_driver_token", driverToken);
    localStorage.setItem("wimb_driver_bus", busNumber.toUpperCase());
    localStorage.setItem("wimb_driver_depot", depotName);

    // ── everything below is your original checkBus() logic, unchanged ──
    const res = await fetch("/api/ride/active/all");
    if (!res.ok) throw new Error("Failed to fetch rides");

    const rides = await safeJson(res);
    const existing = rides.find(
        r => r.busNumber.trim().toUpperCase() === busNumber.toUpperCase()
    );

    document.getElementById("status").innerText = "";

    if (existing) {
      document.getElementById("savedRouteKey").innerText = routeLabel(existing);
      document.getElementById("savedRideId").innerText = existing.rideId;
      document.getElementById("activeBusNumber").innerText = busNumber;

      rideId = existing.rideId;
      activeRouteCode = existing.routeCode || null;

      showSection("resumeSection");
    } else {
      showNewRideForm();
    }

  } catch (err) {
    console.error(err);
    document.getElementById("status").innerText = "";
    alert("Could not check bus status: " + err.message);
  }
}

function routeLabel(ride) {
  if (ride.sourceName && ride.destinationName) return `${ride.sourceName} → ${ride.destinationName}`;
  return (ride.routeKey || "").replace("_", " → ");
}

/* ------------------ RESUME RIDE ------------------ */
function resumeRide() {
  // rideId already set in checkBus()
  const routeKey = document.getElementById("savedRouteKey").innerText;

  showSection("activeRideSection");
  document.getElementById("activeRouteKey").innerText = routeKey;
  document.getElementById("activeRideId").innerText = rideId;
  document.getElementById("status").innerText = `Ride Resumed ✅`;

  startRideTracking();
}

/* ------------------ SHOW NEW RIDE FORM ------------------ */
function showNewRideForm() {
  rideId = null;
  showSection("newRideSection");
}