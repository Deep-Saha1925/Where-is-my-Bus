/*
 * Where Is My Bus (WIMB)
 * "Upcoming scheduled buses" box for the home page's Live Buses search.
 *
 * When a passenger searches A -> D and no bus is running right now (or even when some are),
 * this asks GET /api/routes/upcoming and lists the buses the timetable says are coming:
 *   - DUE       leaves within ~5 minutes
 *   - UPCOMING  leaves later
 *   - LATE      scheduled time has passed and nothing is live on that route yet
 *
 * It builds its own container right after #busList, so index.html needs no markup change.
 *   UpcomingBuses.show(source, destination, liveBuses)   after a live search
 *   UpcomingBuses.clear()                                when results are wiped / tab switches
 */
(function () {
    "use strict";

    var REFRESH_MS = 60000;
    var timer = null;
    var seq = 0;              // newest request wins; older responses are ignored
    var current = null;       // { source, destination, liveCodes }

    function t(k, vars) { return typeof window.tr === "function" ? window.tr(k, vars) : fill(k, vars); }
    function fill(s, vars) {
        if (!vars) return s;
        return s.replace(/\{(\w+)\}/g, function (_, k) { return vars[k] != null ? vars[k] : "{" + k + "}"; });
    }
    function esc(v) {
        return String(v == null ? "" : v)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }

    /* "13:05" -> "1:05 PM" */
    function clock12(hhmm) {
        var m = /^(\d{1,2}):(\d{2})$/.exec(hhmm || "");
        if (!m) return hhmm || "";
        var h = parseInt(m[1], 10), ap = h >= 12 ? "PM" : "AM";
        h = h % 12; if (h === 0) h = 12;
        return h + ":" + m[2] + " " + ap;
    }

    function duration(mins) {
        mins = Math.abs(mins);
        if (mins < 60) return t("{n} min", { n: mins });
        var h = Math.floor(mins / 60), m = mins % 60;
        return m ? t("{h}h {m}m", { h: h, m: m }) : t("{h}h", { h: h });
    }

    function injectStyle() {
        if (document.getElementById("upStyle")) return;
        var s = document.createElement("style");
        s.id = "upStyle";
        s.textContent =
            "#upcomingBox{margin-top:18px;}" +
            ".up-head{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin-bottom:4px;}" +
            ".up-title{font-size:15px;font-weight:700;color:var(--text-primary);}" +
            ".up-sub{font-size:12px;line-height:1.4;color:var(--text-muted);margin-bottom:12px;}" +
            ".up-card{display:flex;gap:12px;align-items:stretch;background:var(--bg-card);" +
            "border:1px solid var(--border);border-radius:14px;padding:12px;margin-bottom:10px;" +
            "box-shadow:var(--shadow);}" +
            ".up-time{flex-shrink:0;min-width:74px;text-align:center;display:flex;flex-direction:column;" +
            "justify-content:center;border-radius:10px;padding:6px 8px;background:rgba(99,102,241,.10);color:#4f46e5;}" +
            ".dark-mode .up-time{color:#a5b4fc;background:rgba(129,140,248,.14);}" +
            ".up-time b{font-size:17px;line-height:1.15;white-space:nowrap;}" +
            ".up-time span{font-size:10.5px;font-weight:600;opacity:.85;}" +
            ".up-card.due .up-time{background:rgba(16,185,129,.14);color:#047857;}" +
            ".dark-mode .up-card.due .up-time{color:#6ee7b7;}" +
            ".up-card.late .up-time{background:rgba(245,158,11,.16);color:#b45309;}" +
            ".dark-mode .up-card.late .up-time{color:#fbbf24;}" +
            ".up-body{flex:1;min-width:0;}" +
            ".up-route{font-size:14px;font-weight:700;color:var(--text-primary);overflow-wrap:anywhere;}" +
            ".up-meta{font-size:12px;line-height:1.45;color:var(--text-muted);margin-top:2px;}" +
            ".up-badge{display:inline-block;font-size:11.5px;font-weight:700;border-radius:999px;" +
            "padding:2px 9px;margin-top:6px;background:rgba(99,102,241,.12);color:#4f46e5;}" +
            ".dark-mode .up-badge{color:#a5b4fc;}" +
            ".up-card.due .up-badge{background:rgba(16,185,129,.14);color:#047857;}" +
            ".dark-mode .up-card.due .up-badge{color:#6ee7b7;}" +
            ".up-card.late .up-badge{background:rgba(245,158,11,.16);color:#b45309;}" +
            ".dark-mode .up-card.late .up-badge{color:#fbbf24;}" +
            "@media (max-width:380px){.up-card{gap:10px;padding:10px;}.up-time{min-width:64px;}.up-time b{font-size:15px;}}";
        document.head.appendChild(s);
    }

    function box() {
        var el = document.getElementById("upcomingBox");
        if (el) return el;
        var anchor = document.getElementById("busList");
        if (!anchor || !anchor.parentNode) return null;
        injectStyle();
        el = document.createElement("div");
        el.id = "upcomingBox";
        el.style.display = "none";
        anchor.parentNode.insertBefore(el, anchor.nextSibling);
        return el;
    }

    function badgeText(b) {
        if (b.status === "LATE") return t("Scheduled {t} ago, not live yet", { t: duration(b.minutesUntil) });
        if (b.status === "DUE") return b.minutesUntil <= 0 ? t("Due now") : t("Due in {t}", { t: duration(b.minutesUntil) });
        return t("In {t}", { t: duration(b.minutesUntil) });
    }

    function card(b, source, destination) {
        var cls = b.status === "DUE" ? "due" : (b.status === "LATE" ? "late" : "");
        var approx = b.approximate ? "~" : "";
        var meta = [];
        meta.push(esc(source) + " → " + esc(destination));
        meta.push(t("arrives about {time}", { time: clock12(b.atDestination) }));
        if (b.approximate) {
            meta.push(t("leaves {stop} at {time}", { stop: esc(b.startStop), time: clock12(b.departsStart) }));
        }
        var buses = (b.busNumbers && b.busNumbers.length)
            ? '<div class="up-meta">' + esc(t("Buses on this route")) + ": " + b.busNumbers.slice(0, 6).map(esc).join(", ") +
              (b.busNumbers.length > 6 ? " +" + (b.busNumbers.length - 6) : "") + "</div>"
            : "";
        return '<div class="up-card ' + cls + '">' +
            '<div class="up-time"><b>' + approx + esc(clock12(b.atSource)) + "</b>" +
            "<span>" + esc(b.nextDay ? t("Tomorrow") : t("Today")) + "</span></div>" +
            '<div class="up-body"><div class="up-route">' + esc(b.routeName) + "</div>" +
            '<div class="up-meta">' + meta.join(" · ") + "</div>" + buses +
            '<span class="up-badge">' + esc(badgeText(b)) + "</span></div></div>";
    }

    function render(list, source, destination) {
        var el = box();
        if (!el) return;
        if (!list.length) { el.style.display = "none"; el.innerHTML = ""; return; }
        el.innerHTML =
            '<div class="up-head"><span class="up-title">🕒 ' + esc(t("Upcoming scheduled buses")) + "</span></div>" +
            '<div class="up-sub">' + esc(t("From the timetable. Times at your stop are estimates; live tracking starts when the driver begins the trip.")) + "</div>" +
            list.map(function (b) { return card(b, source, destination); }).join("");
        el.style.display = "block";
    }

    async function load() {
        if (!current) return;
        var my = ++seq;
        var c = current;
        try {
            var url = "/api/routes/upcoming?source=" + encodeURIComponent(c.source) +
                "&destination=" + encodeURIComponent(c.destination) +
                (c.liveCodes.length ? "&liveRoutes=" + encodeURIComponent(c.liveCodes.join(",")) : "");
            var res = await fetch(url, { cache: "no-store" });
            if (!res.ok) throw new Error("HTTP " + res.status);
            var list = await res.json();
            if (my !== seq || !current) return;   // a newer search replaced this one
            c.count = Array.isArray(list) ? list.length : 0;
            render(Array.isArray(list) ? list : [], c.source, c.destination);
            if (typeof c.onResult === "function") c.onResult(Array.isArray(list) ? list : []);
        } catch (e) {
            // Suggestions are a bonus: never break the search if this fails.
            if (my === seq) { var el = box(); if (el) { el.style.display = "none"; el.innerHTML = ""; } }
        }
    }

    function show(source, destination, liveBuses, onResult) {
        clear();
        var codes = [];
        (liveBuses || []).forEach(function (b) {
            if (b && b.routeCode && codes.indexOf(b.routeCode) === -1) codes.push(b.routeCode);
        });
        current = { source: source, destination: destination, liveCodes: codes, onResult: onResult };
        load();
        timer = setInterval(load, REFRESH_MS);   // keeps "in 5 min" honest while the page stays open
    }

    function clear() {
        seq++;
        current = null;
        if (timer) { clearInterval(timer); timer = null; }
        var el = document.getElementById("upcomingBox");
        if (el) { el.style.display = "none"; el.innerHTML = ""; }
    }

    window.UpcomingBuses = { show: show, clear: clear };
})();
