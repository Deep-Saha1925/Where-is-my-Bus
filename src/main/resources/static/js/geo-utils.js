/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
// Location helpers shared by the passenger pages (index.html and track.html).
//
// Privacy: the passenger's position is only ever used inside this browser tab. It is never sent to
// the server, never stored, and the browser asks for permission first.
const Geo = (() => {

  const EARTH_RADIUS_M = 6371000;

  function distanceM(lat1, lon1, lat2, lon2) {
    const rad  = Math.PI / 180;
    const dLat = (lat2 - lat1) * rad;
    const dLon = (lon2 - lon1) * rad;
    const a = Math.sin(dLat / 2) ** 2 +
        Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLon / 2) ** 2;
    return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
  }

  // The point closest to (lat, lng). Points need latitude and longitude; anything else is returned as given.
  // Returns { point, index, distanceM } or null when there is nothing to compare against.
  function nearest(lat, lng, points) {
    let best = null;
    (points || []).forEach((point, index) => {
      if (!point || typeof point.latitude !== "number" || typeof point.longitude !== "number") return;
      const d = distanceM(lat, lng, point.latitude, point.longitude);
      if (best === null || d < best.distanceM) best = { point, index, distanceM: d };
    });
    return best;
  }

  function formatDistance(meters) {
    if (!(meters >= 0)) return "—";
    if (meters < 1000) return `${Math.round(meters / 10) * 10 || Math.round(meters)} m`;
    return `${(meters / 1000).toFixed(meters < 10000 ? 1 : 0)} km`;
  }

  // A plain-language reason for a failed location request, with what to do about it.
  function explainError(err) {
    const code = err && (err.code !== undefined ? err.code : null);
    if (code === "UNSUPPORTED") return "This browser can't share its location.";
    if (code === "INSECURE") return "Location only works on a secure (https) connection.";
    if (code === 1) return "Location is turned off for this site. Allow it in your browser (lock icon next to the address, then Location) and tap again.";
    if (code === 2) return "Your location couldn't be found. Check that location/GPS is turned on, then try again.";
    if (code === 3) return "Finding your location took too long. Try again, ideally with a clear view of the sky.";
    return "Couldn't get your location. Please try again.";
  }

  // Asks the browser for the current position (this is what shows the permission prompt).
  function request(options) {
    return new Promise((resolve, reject) => {
      if (typeof window !== "undefined" && window.isSecureContext === false) {
        reject({ code: "INSECURE" });
        return;
      }
      if (typeof navigator === "undefined" || !navigator.geolocation) {
        reject({ code: "UNSUPPORTED" });
        return;
      }
      navigator.geolocation.getCurrentPosition(
        pos => resolve({
          latitude:  pos.coords.latitude,
          longitude: pos.coords.longitude,
          accuracy:  pos.coords.accuracy
        }),
        err => reject(err),
        Object.assign({ enableHighAccuracy: true, timeout: 15000, maximumAge: 30000 }, options || {})
      );
    });
  }

  return { distanceM, nearest, formatDistance, explainError, request };
})();
