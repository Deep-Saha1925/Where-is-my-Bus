let rideId = null;
let watchId = null;
let currentPosition = null;

document.addEventListener("DOMContentLoaded", loadStops);

function loadStops() {
  fetch("data/stops.json")
    .then(response => response.json())
    .then(data => {
      const datalist = document.getElementById("stopsList");
      datalist.innerHTML = "";

      data.stops.forEach(stop => {
        const option = document.createElement("option");
        option.value = stop.toUpperCase(); // keep backend-friendly
        datalist.appendChild(option);
      });
    })
    .catch(error => {
      console.error("Error loading stops:", error);
    });
}

function extractLocation(address) {
  return (
    address.road ||
    address.neighbourhood ||
    address.suburb ||
    address.hamlet ||
    address.village ||
    address.town ||
    address.city ||
    address.county ||
    "Unknown Location"
  );
}

window.onload = () => {
  if (!navigator.geolocation) {
    alert("Geolocation not supported in this device");
    return;
  }

  watchId = navigator.geolocation.watchPosition(
    async pos => {
      currentPosition = pos;

      const lat = position.coords.latitude;
        const lng = position.coords.longitude;
        const accuracy = position.coords.accuracy;

        console.log("Latitude:", lat);
        console.log("Longitude:", lng);
        console.log("Accuracy (meters):", accuracy);

      try {
        const res = await fetch(
                `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}`,
                { headers: { "User-Agent": "WhereIsMyBus" } }
        );
        const data = await res.json();

        const location = extractLocation(data.address);
        document.getElementById("source").value = location;

      } catch (err) {
        console.error(err);
        document.getElementById("source").value = "Unknown Location";
      }
    },
    err => {
      alert("Unable to fetch location");
      console.error(err);
    },
    {
     enableHighAccuracy: true,
     timeout: 15000,
     maximumAge: 0
     }
  );
};

async function startRide() {
  const source = document.getElementById("source").value.trim();
  const destination = document.getElementById("destination").value.trim();

  if (source == destination){
    alert("Source and destination must be different.");
    return;
  }

  if (!destination) {
    alert("Please enter destination");
    return;
  }

  if (!currentPosition) {
    alert("Waiting for GPS signal...");
    return;
  }

  const payload = {
    busNumber: document.getElementById("busNumber").value.trim(), // later make dynamic / logged-in driver
    routeKey: source + "_" + destination,
    latitude: currentPosition.coords.latitude,
    longitude: currentPosition.coords.longitude
  };

  try {
    const res = await fetch("http://localhost:8080/api/ride/start", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });

    if (!res.ok) throw new Error("Failed to start ride");

    const data = await res.json();
    rideId = data.id; // dynamic rideId

    document.getElementById("startBtn").classList.add("hidden");
    document.getElementById("stopBtn").classList.remove("hidden");
    document.getElementById("status").innerText = `Ride Started (ID: ${rideId})`;

    startLocationTracking();

  } catch (err) {
    console.error(err);
    alert("Ride start failed");
  }
}

function startLocationTracking() {
  watchId = navigator.geolocation.watchPosition(
    pos => {
      fetch("http://localhost:8080/api/location/update", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          rideId,
          latitude: pos.coords.latitude,
          longitude: pos.coords.longitude
        })
      });
    },
    err => {
      console.error("Location error:", err);
    },
    {
      enableHighAccuracy: true,
      maximumAge: 0,
      timeout: 5000
    }
  );
}

async function stopRide() {
  if (!rideId) return;

  if (watchId) {
    navigator.geolocation.clearWatch(watchId);
    watchId = null;
  }

  try {
    await fetch(`http://localhost:8080/api/ride/cancel/${rideId}`, {
      method: "PUT"
    });

    document.getElementById("status").innerText = "Ride Stopped";
    document.getElementById("stopBtn").classList.add("hidden");
    document.getElementById("startBtn").classList.remove("hidden");

    rideId = null;

  } catch (err) {
    console.error(err);
    alert("Failed to stop ride");
  }
}