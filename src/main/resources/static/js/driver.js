//let rideId = null;
//let previewWatchId = null;   // for initial GPS preview
//let rideWatchId = null;      // for ride tracking
//let currentPosition = null;
//let lastGeoCodeTime = 0;
//
//document.addEventListener("DOMContentLoaded", () => {
//  loadStops();
//  initPreviewGPS();
//});
//
///* ------------------ STOPS ------------------ */
//
//function loadStops() {
//  fetch("data/stops.json")
//    .then(res => res.json())
//    .then(data => {
//      const datalist = document.getElementById("stopsList");
//      datalist.innerHTML = "";
//
//      data.stops.forEach(stop => {
//        const option = document.createElement("option");
//        option.value = stop.toUpperCase();
//        datalist.appendChild(option);
//      });
//    })
//    .catch(err => console.error("Error loading stops:", err));
//}
//
///* ------------------ LOCATION HELPERS ------------------ */
//
//function extractLocation(address) {
//  return (
//    address.road ||
//    address.neighbourhood ||
//    address.suburb ||
//    address.village ||
//    address.town ||
//    address.city ||
//    "Unknown Location"
//  );
//}
//
///* ------------------ PREVIEW GPS (before ride) ------------------ */
//
//function initPreviewGPS() {
//  if (!navigator.geolocation) {
//    alert("Geolocation not supported");
//    return;
//  }
//
//  // Warm-up call
//  navigator.geolocation.getCurrentPosition(() => {}, () => {}, {
//    enableHighAccuracy: false
//  });
//
//  previewWatchId = navigator.geolocation.watchPosition(
//    pos => {
//      currentPosition = pos;
//      console.log("GPS OK:", pos.coords.latitude, pos.coords.longitude);
//    },
//    err => {
//      if (err.code === err.TIMEOUT) {
//        console.warn("GPS timeout, retrying...");
//        return;
//      }
//      console.error("Preview GPS error:", err);
//    },
//    {
//      enableHighAccuracy: false,
//      timeout: 60000,
//      maximumAge: 10000
//    }
//  );
//}
//
///* ------------------ START RIDE ------------------ */
//
//async function startRide() {
//  const source = document.getElementById("source").value.trim();
//  const destination = document.getElementById("destination").value.trim();
//
//  if (!source || !destination) {
//    alert("Please enter source and destination");
//    return;
//  }
//
//  if (source === destination) {
//    alert("Source and destination must be different");
//    return;
//  }
//
//  if (!currentPosition) {
//    alert("Waiting for GPS signal...");
//    return;
//  }
//
//  const payload = {
//    busNumber: document.getElementById("busNumber").value.trim(),
//    routeKey: `${source}_${destination}`,
//    latitude: currentPosition.coords.latitude,
//    longitude: currentPosition.coords.longitude
//  };
//
//  try {
//    const res = await fetch("http://localhost:8080/api/ride/start", {
//      method: "POST",
//      headers: { "Content-Type": "application/json" },
//      body: JSON.stringify(payload)
//    });
//
//    if (!res.ok) throw new Error("Ride start failed");
//
//    const data = await res.json();
//    rideId = data.id;
//
//    // stop preview GPS
//    if (previewWatchId) {
//      navigator.geolocation.clearWatch(previewWatchId);
//      previewWatchId = null;
//    }
//
//    document.getElementById("startBtn").classList.add("hidden");
//    document.getElementById("stopBtn").classList.remove("hidden");
//    document.getElementById("status").innerText = `Ride Started (ID: ${rideId})`;
//
//    startRideTracking();
//
//  } catch (err) {
//    console.error(err);
//    alert("Ride start failed");
//  }
//}
//
///* ------------------ LIVE RIDE TRACKING ------------------ */
//
//function startRideTracking() {
//  if (rideWatchId) return;
//
//  rideWatchId = navigator.geolocation.watchPosition(
//    pos => {
//      fetch("http://localhost:8080/api/location/update", {
//        method: "POST",
//        headers: { "Content-Type": "application/json" },
//        body: JSON.stringify({
//          rideId,
//          latitude: pos.coords.latitude,
//          longitude: pos.coords.longitude,
//          accuracy: pos.coords.accuracy
//        })
//      });
//    },
//    err => console.error("Ride GPS error:", err),
//    {
//      enableHighAccuracy: true,
//      timeout: 10000,
//      maximumAge: 0
//    }
//  );
//}
//
///* ------------------ STOP RIDE ------------------ */
//
//async function stopRide() {
//  if (!rideId) return;
//
//  if (rideWatchId) {
//    navigator.geolocation.clearWatch(rideWatchId);
//    rideWatchId = null;
//  }
//
//  try {
//    await fetch(`http://localhost:8080/api/ride/cancel/${rideId}`, {
//      method: "PUT"
//    });
//
//    document.getElementById("status").innerText = "Ride Stopped";
//    document.getElementById("stopBtn").classList.add("hidden");
//    document.getElementById("startBtn").classList.remove("hidden");
//
//    rideId = null;
//
//    // restart preview GPS
//    initPreviewGPS();
//
//  } catch (err) {
//    console.error(err);
//    alert("Failed to stop ride");
//  }
//}













let rideId = null;
let previewWatchId = null;
let rideWatchId = null;
let currentPosition = null;

document.addEventListener("DOMContentLoaded", () => {
  loadStops();
  initPreviewGPS(); // still runs in background as fallback
});

/* ------------------ STOPS ------------------ */
function loadStops() {
  fetch("/data/stops.json")
    .then(res => res.json())
    .then(data => {
      const datalist = document.getElementById("stopsList");
      datalist.innerHTML = "";
      data.stops.forEach(stop => {
        const option = document.createElement("option");
        option.value = stop.toUpperCase();
        datalist.appendChild(option);
      });
    })
    .catch(err => console.error("Error loading stops:", err));
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
// Fetches the route stops and returns lat/lng of the source stop by name
async function getCoordsFromRoute(source, destination) {
  try {
    const res = await fetch(`/api/routes?source=${source}&destination=${destination}`);
    if (!res.ok) throw new Error("Route fetch failed");

    const stops = await res.json();

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

  document.getElementById("newRideSection").classList.add("hidden");
  document.getElementById("activeRideSection").classList.remove("hidden");
  document.getElementById("activeRouteKey").innerText =
    `${source} → ${destination}`;
  document.getElementById("activeRideId").innerText = rideId;

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
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });

    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`Server error: ${errText}`);
    }

    const data = await res.json();
    rideId = data.id;

    // Stop preview GPS watcher
    if (previewWatchId !== null) {
      navigator.geolocation.clearWatch(previewWatchId);
      previewWatchId = null;
    }

    document.getElementById("startBtn").classList.add("hidden");
    document.getElementById("stopBtn").classList.remove("hidden");
    statusEl.innerText = `Ride Started ✅ (ID: ${rideId})`;

    startRideTracking(); // live GPS tracking begins after ride starts

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
        headers: { "Content-Type": "application/json" },
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
    await fetch(`/api/ride/cancel/${rideId}`, { method: "PUT" });

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


/* ------------------ CHECK BUS (after entering bus number) ------------------ */
async function checkBus() {
  const busNumber = document.getElementById("busNumber").value.trim();
  if (!busNumber) {
    alert("Please enter a bus number");
    return;
  }

  document.getElementById("status").innerText = "Checking...";

  try {
    // Fetch all active rides and find one matching this bus number
    const res = await fetch("/api/ride/active/all");
    if (!res.ok) throw new Error("Failed to fetch rides");

    const rides = await res.json();
    const existing = rides.find(
      r => r.busNumber.trim().toUpperCase() === busNumber.toUpperCase()
    );

    document.getElementById("status").innerText = "";

    if (existing) {
      // Active ride found — show resume prompt
      document.getElementById("savedRouteKey").innerText =
        existing.routeKey.replace("_", " → ");
      document.getElementById("savedRideId").innerText = existing.rideId;
      document.getElementById("activeBusNumber").innerText = busNumber;

      // Store for resumeRide() to use
      rideId = existing.rideId;

      document.getElementById("busNumberSection").classList.add("hidden");
      document.getElementById("resumeSection").classList.remove("hidden");
    } else {
      // No active ride — go straight to new ride form
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