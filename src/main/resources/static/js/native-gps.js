/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
// One way for the driver page to get GPS, whether it runs in a normal browser tab or inside the
// "WIMB Driver" Android app (a Capacitor wrapper, see driver-app/).
//
//   Browser tab : navigator.geolocation.watchPosition. A phone pauses this when the screen locks
//                 or the tab goes to the background; nothing a web page does can fully prevent that.
//   Android app : the BackgroundGeolocation plugin runs a foreground service (with a visible
//                 notification), so location keeps flowing with the screen off or another app open.
//
// Usage (driver.js):
//   const handle = GpsSource.start(fix => { ... }, err => { ... });
//   handle.stop();
//
//   fix  = { latitude, longitude, accuracy }
//   err  = { message, notAuthorized }
const GpsSource = (() => {

  const NOTIFICATION_TITLE   = "Where Is My Bus: ride active";
  const NOTIFICATION_MESSAGE = "Sharing your bus location with passengers";

  // The plugin only exists when the page runs inside the Capacitor app (it is injected by the app).
  function nativePlugin() {
    const cap = (typeof window !== "undefined") ? window.Capacitor : null;
    if (!cap || typeof cap.isNativePlatform !== "function" || !cap.isNativePlatform()) return null;
    return (cap.Plugins && cap.Plugins.BackgroundGeolocation) || null;
  }

  function isNative() {
    return nativePlugin() !== null;
  }

  function available() {
    return isNative() || !!(typeof navigator !== "undefined" && navigator.geolocation);
  }

  function startNative(plugin, onFix, onError) {
    let watcherId = null;
    let stopped   = false;

    plugin.addWatcher(
      {
        backgroundTitle:   NOTIFICATION_TITLE,
        backgroundMessage: NOTIFICATION_MESSAGE,
        requestPermissions: true,
        stale: false,          // never hand back an old cached position
        distanceFilter: 0      // report every update, the server throttles
      },
      (location, error) => {
        if (error) {
          const notAuthorized = error.code === "NOT_AUTHORIZED";
          onError({
            notAuthorized,
            message: notAuthorized
              ? "Location permission is off for the WIMB Driver app. Open Settings, allow Location (Always), then restart the ride"
              : (error.message || "GPS error")
          });
          return;
        }
        if (!location) return;
        onFix({
          latitude:  location.latitude,
          longitude: location.longitude,
          accuracy:  location.accuracy
        });
      }
    ).then(id => {
      // stop() may have been called while the plugin was still starting
      if (stopped) plugin.removeWatcher({ id });
      else watcherId = id;
    }).catch(err => {
      onError({ notAuthorized: false, message: (err && err.message) || "Could not start background GPS" });
    });

    return {
      native: true,
      stop() {
        stopped = true;
        if (watcherId !== null) {
          plugin.removeWatcher({ id: watcherId });
          watcherId = null;
        }
      }
    };
  }

  function startWeb(onFix, onError) {
    const id = navigator.geolocation.watchPosition(
      pos => onFix({
        latitude:  pos.coords.latitude,
        longitude: pos.coords.longitude,
        accuracy:  pos.coords.accuracy
      }),
      err => onError({ notAuthorized: err.code === 1, message: err.message }),
      { enableHighAccuracy: true, timeout: 20000, maximumAge: 0 }
    );
    return {
      native: false,
      stop() { navigator.geolocation.clearWatch(id); }
    };
  }

  function start(onFix, onError) {
    const plugin = nativePlugin();
    return plugin ? startNative(plugin, onFix, onError) : startWeb(onFix, onError);
  }

  return { isNative, available, start };
})();
