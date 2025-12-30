async function searchBuses() {
  const source = document.getElementById("source").value.trim().toUpperCase();
  const destination = document.getElementById("destination").value.trim().toUpperCase();

  const routeKey = `${source}_${destination}`;

  const busList = document.getElementById("busList");
  const loading = document.getElementById("loading");
  const noResults = document.getElementById("noResults");

  busList.innerHTML = "";
  noResults.classList.add("hidden");

  if (!source || !destination) {
    alert("Please enter both source and destination");
    return;
  }

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

          <span class="text-xs bg-green-100 text-green-700 px-2 py-1 rounded-full">
            LIVE
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
