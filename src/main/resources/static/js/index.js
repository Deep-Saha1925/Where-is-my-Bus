document.addEventListener("DOMContentLoaded", loadStops);

function loadStops() {
    fetch("data/stops.json")
        .then(response => response.json())
        .then(data => {
            const sourceSelect = document.getElementById("source");
            const destinationSelect = document.getElementById("destination");

            data.stops.forEach(stop => {
                const option1 = document.createElement("option");
                option1.value = stop;
                option1.textContent = stop;

                const option2 = option1.cloneNode(true);

                sourceSelect.appendChild(option1);
                destinationSelect.appendChild(option2);
            });
        })
        .catch(error => {
            console.error("Error loading stops:", error);
        });
}


async function searchBuses() {
  const source = document.getElementById("source").value.trim().toUpperCase();
  const destination = document.getElementById("destination").value.trim().toUpperCase();

  if (!source || !destination) {
      alert("Please select both source and destination");
      return;
  }

  if (source === destination) {
      alert("Source and destination cannot be the same");
      return;
  }

  const routeKey = `${source}_${destination}`;

  const busList = document.getElementById("busList");
  const loading = document.getElementById("loading");
  const noResults = document.getElementById("noResults");

  busList.innerHTML = "";
  noResults.classList.add("hidden");

  loading.classList.remove("hidden");

  try {
    const res = await fetch(
      `http://localhost:8080/api/ride/active?source=${source}&destination=${destination}`
    );

    const buses = await res.json();
    loading.classList.add("hidden");

    if (!buses.length) {
      noResults.classList.remove("hidden");
      return;
    }

    buses.forEach(bus => {
      const card = document.createElement("div");
      card.className = `
        bg-white rounded-xl shadow-md p-5
        hover:shadow-xl transition transform hover:-translate-y-1
      `;

      let src = "N/A";
      let dest = "N/A";

      if (bus.routeKey && bus.routeKey.includes("_")) {
        [src, dest] = bus.routeKey.split("_");
      }

      card.innerHTML = `
        <div class="flex justify-between items-center mb-3">
          <div class="flex items-center gap-3">
            <i class="fa-solid fa-bus text-indigo-600 text-xl"></i>
            <h4 class="text-lg font-bold text-gray-800">
              ${bus.busNumber}
            </h4>
          </div>

          <span class="text-xs bg-blue-100 text-blue-700 px-2 py-1 rounded-full">
            ETA: ${calculateETAFromDistance(bus.remainingDistanceKm)}
          </span>
        </div>

        <p class="text-sm text-gray-600 mb-2">
          Route: <b>${src}</b> → <b>${dest}</b>
        </p>

        <button
          onclick="track('${routeKey}', ${bus.rideId})"
          class="mt-4 w-full bg-indigo-600 text-white py-2 rounded-lg hover:bg-indigo-700 transition flex items-center justify-center gap-2"
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

function track(routeKey, rideId) {
  window.location.href =
    `track.html?routeKey=${routeKey}&rideId=${rideId}`;
}

function calculateETAFromDistance(distanceKm) {
  if (distanceKm == null) return "Updating";

  const avgSpeedKmph = 30; // realistic bus speed
  const minutes = Math.ceil((distanceKm / avgSpeedKmph) * 60);

  if (minutes <= 1) return "Arriving";
  return `${minutes} min`;
}