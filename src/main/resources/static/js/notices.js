/*
 * Where Is My Bus (WIMB)
 * Passenger-side notice banner (index page). Polls /api/notices/active.
 */
(function () {
    var POLL_MS = 60000;
    var STORE_KEY = "wimb_dismissed_notices";
    var COLORS = {
        INFO:     { bar: "#3b82f6", bg: "rgba(59,130,246,0.12)",  icon: "fa-circle-info" },
        WARNING:  { bar: "#f59e0b", bg: "rgba(245,158,11,0.15)",  icon: "fa-triangle-exclamation" },
        CRITICAL: { bar: "#ef4444", bg: "rgba(239,68,68,0.14)",   icon: "fa-circle-exclamation" }
    };

    function esc(v) {
        return String(v == null ? "" : v)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }

    function dismissed() {
        try { return JSON.parse(sessionStorage.getItem(STORE_KEY) || "[]"); } catch (e) { return []; }
    }
    function dismiss(id) {
        try {
            var list = dismissed();
            if (list.indexOf(id) === -1) list.push(id);
            sessionStorage.setItem(STORE_KEY, JSON.stringify(list));
        } catch (e) { /* storage blocked - banner just reappears next poll */ }
    }

    function injectStyle() {
        if (document.getElementById("noticeStyle")) return;
        var s = document.createElement("style");
        s.id = "noticeStyle";
        s.textContent =
            "#noticeBar{max-width:1100px;margin:0 auto;padding:0 16px;}" +
            "#noticeBar:not(:empty){padding-top:12px;}" +
            ".notice-item{display:flex;gap:10px;align-items:flex-start;border-radius:12px;" +
            "padding:10px 12px;margin-bottom:8px;border-left:4px solid;color:var(--text-primary);}" +
            ".notice-item i.n-ico{margin-top:3px;font-size:15px;}" +
            ".notice-body{flex:1;min-width:0;font-size:13.5px;line-height:1.4;}" +
            ".notice-title{font-weight:700;}" +
            ".notice-tag{display:inline-block;font-size:11px;font-weight:600;margin-left:6px;" +
            "padding:1px 7px;border-radius:10px;background:var(--bg-card);color:var(--text-muted);}" +
            ".notice-close{background:none;border:none;color:var(--text-muted);cursor:pointer;" +
            "font-size:16px;line-height:1;padding:2px 4px;}";
        document.head.appendChild(s);
    }

    function render(notices) {
        var bar = document.getElementById("noticeBar");
        if (!bar) return;
        var hidden = dismissed();
        var html = "";
        notices.forEach(function (n) {
            // Critical notices can't be dismissed.
            if (n.severity !== "CRITICAL" && hidden.indexOf(n.id) !== -1) return;
            var c = COLORS[n.severity] || COLORS.INFO;
            var tags = "";
            if (n.routeCode) tags += '<span class="notice-tag">' + esc(n.routeCode) + "</span>";
            if (n.busNumber) tags += '<span class="notice-tag">' + esc(n.busNumber) + "</span>";
            html += '<div class="notice-item" role="alert" style="background:' + c.bg + ";border-color:" + c.bar + '">' +
                '<i class="fa-solid ' + c.icon + ' n-ico" style="color:' + c.bar + '"></i>' +
                '<div class="notice-body"><span class="notice-title">' + esc(n.title) + "</span>" + tags +
                "<div>" + esc(n.message) + "</div></div>" +
                (n.severity === "CRITICAL" ? "" :
                    '<button class="notice-close" aria-label="Dismiss" data-id="' + esc(n.id) + '">&times;</button>') +
                "</div>";
        });
        bar.innerHTML = html;
    }

    var latest = [];
    function load() {
        fetch("/api/notices/active", { cache: "no-store" })
            .then(function (r) { return r.ok ? r.json() : []; })
            .then(function (data) { latest = Array.isArray(data) ? data : []; render(latest); })
            .catch(function () { /* offline: keep whatever is showing */ });
    }

    document.addEventListener("DOMContentLoaded", function () {
        injectStyle();
        var bar = document.getElementById("noticeBar");
        if (bar) {
            bar.addEventListener("click", function (e) {
                var btn = e.target.closest(".notice-close");
                if (!btn) return;
                dismiss(Number(btn.getAttribute("data-id")));
                render(latest);
            });
        }
        load();
        setInterval(load, POLL_MS);
    });
})();