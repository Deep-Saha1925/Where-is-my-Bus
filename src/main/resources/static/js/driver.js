let rideId = null;
let previewWatchId = null;
let rideWatchId = null;
let currentPosition = null;
let driverToken = null;
let selectedRouteCode = null; // set once the driver picks a route in newRideSection

document.addEventListener("DOMContentLoaded", () => {
  loadRoutes();
  loadDepots();
  initPreviewGPS(); // still runs in background as fallback
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

// Called when the driver picks a route — loads that route's stops into the
// Start Location / Destination datalist and enables those fields.
async function onRouteChange() {
  const routeSelect = document.getElementById("routeCode");
  const sourceInput = document.getElementById("source");
  const destInput = document.getElementById("destination");
  const datalist = document.getElementById("stopsList");

  selectedRouteCode = routeSelect.value;

  // Reset downstream fields whenever the route changes
  sourceInput.value = "";
  destInput.value = "";
  sourceInput.disabled = true;
  destInput.disabled = true;
  sourceInput.placeholder = "Loading stops...";
  destInput.placeholder = "Loading stops...";
  datalist.innerHTML = "";

  if (!selectedRouteCode) return;

  try {
    const res = await fetch(`/api/routes/stops?routeCode=${encodeURIComponent(selectedRouteCode)}`);
    const stops = await safeJson(res); // full ordered stop list for this route

    stops.forEach(stop => {
      const option = document.createElement("option");
      option.value = stop.stopName.toUpperCase();
      datalist.appendChild(option);
    });

    sourceInput.disabled = false;
    destInput.disabled = false;
    sourceInput.placeholder = "Type source stop";
    destInput.placeholder = "Type destination stop";
  } catch (err) {
    console.error("Failed to load stops for route:", err);
    sourceInput.placeholder = "Could not load stops";
    destInput.placeholder = "Could not load stops";
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
// Fetches the route stops (scoped to the driver's selected route) and
// returns lat/lng of the source stop by name
async function getCoordsFromRoute(source, destination) {
  try {
    const routeParam = selectedRouteCode ? `&routeCode=${encodeURIComponent(selectedRouteCode)}` : "";
    const res = await fetch(`/api/routes?source=${source}&destination=${destination}${routeParam}`);
    if (!res.ok) throw new Error("Route fetch failed");

    const stops = await safeJson(res);

    // Find the stop whose name matches the source (case-insensitive)
    const match = stops.find(
        s => s.stopName.trim().toUpperCase() === source.trim().toUpperCase()
    );

    if (match) {
      console.log(`Using Excel coords for "${match.stopName}":`, match.latitude, match.longitude);
      return { latitude: match.latitude, longitude: match.longitude };
    }

    console.warn("Source stop not found in route Excel data:", source);
    return null;

  } catch (err) {
    console.error("Failed to fetch route coords:", err);
    return null;
  }
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
  let coords = await getCoordsFromRoute(source, destination);

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


    document.getElementById("newRideSection").classList.add("hidden");
    document.getElementById("activeRideSection").classList.remove("hidden");
    document.getElementById("activeRouteKey").innerText = `${source} → ${destination}`;
    document.getElementById("activeRideId").innerText = rideId;
    document.getElementById("activeBusNumber").innerText = busNumber;
    statusEl.innerText = `Ride Started ✅ (ID: ${rideId})`;

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

  rideWatchId = navigator.geolocation.watchPosition(
      pos => {
        fetch("/api/ride/location", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "X-Driver-Token": driverToken
          },
          body: JSON.stringify({
            rideId,
            latitude:  pos.coords.latitude,
            longitude: pos.coords.longitude,
            accuracy:  pos.coords.accuracy
          })
        }).catch(err => console.error("Location update failed:", err));
      },
      err => console.error("Ride GPS error:", err.message),
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
  );
}

/* ------------------ STOP RIDE ------------------ */
async function stopRide() {
  if (!rideId) return;

  if (rideWatchId !== null) {
    navigator.geolocation.clearWatch(rideWatchId);
    rideWatchId = null;
  }

  try {
    await fetch(`/api/ride/cancel/${rideId}`, {
      method: "PUT",
      headers: { "X-Driver-Token": driverToken }
    });

    document.getElementById("status").innerText = "Ride Stopped ⛔";
    document.getElementById("stopBtn").classList.add("hidden");
    document.getElementById("startBtn").classList.remove("hidden");

    // Go back to bus number entry
    document.getElementById("activeRideSection").classList.add("hidden");
    document.getElementById("busNumberSection").classList.remove("hidden");
    document.getElementById("busNumber").value = "";
    document.getElementById("busNumber").disabled = false;

    rideId = null;
    currentPosition = null;

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
    const storedToken = localStorage.getItem("wimb_driver_token");
    const storedBus   = localStorage.getItem("wimb_driver_bus");

    if (storedToken && storedBus && storedBus === busNumber.toUpperCase()) {
      // Already verified earlier this shift — skip the depot code check
      driverToken = storedToken;
    } else {
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
    }

    // ── everything below is your original checkBus() logic, unchanged ──
    const res = await fetch("/api/ride/active/all");
    if (!res.ok) throw new Error("Failed to fetch rides");

    const rides = await safeJson(res);
    const existing = rides.find(
        r => r.busNumber.trim().toUpperCase() === busNumber.toUpperCase()
    );

    document.getElementById("status").innerText = "";

    if (existing) {
      document.getElementById("savedRouteKey").innerText =
          existing.routeKey.replace("_", " → ");
      document.getElementById("savedRideId").innerText = existing.rideId;
      document.getElementById("activeBusNumber").innerText = busNumber;

      rideId = existing.rideId;

      document.getElementById("busNumberSection").classList.add("hidden");
      document.getElementById("resumeSection").classList.remove("hidden");
    } else {
      showNewRideForm();
    }

  } catch (err) {
    console.error(err);
    document.getElementById("status").innerText = "";
    alert("Could not check bus status: " + err.message);
  }
}

/* ------------------ RESUME RIDE ------------------ */
function resumeRide() {
  // rideId already set in checkBus()
  const routeKey = document.getElementById("savedRouteKey").innerText;

  document.getElementById("resumeSection").classList.add("hidden");
  document.getElementById("activeRideSection").classList.remove("hidden");
  document.getElementById("activeRouteKey").innerText = routeKey;
  document.getElementById("activeRideId").innerText = rideId;
  document.getElementById("status").innerText = `Ride Resumed ✅`;

  startRideTracking();
}

/* ------------------ SHOW NEW RIDE FORM ------------------ */
function showNewRideForm() {
  rideId = null;
  document.getElementById("busNumberSection").classList.add("hidden");
  document.getElementById("resumeSection").classList.add("hidden");
  document.getElementById("newRideSection").classList.remove("hidden");
}