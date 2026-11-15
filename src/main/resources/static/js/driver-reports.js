/*
 * Where Is My Bus (WIMB)
 * Driver "Report an issue" button + form + list of the driver's own reports, with the status AND
 * the admin's reply. A red count on the button flags replies the driver has not read yet.
 * Self-contained: injects its own button/modal and reads the global
 * `driverToken` that driver.js already maintains (null when logged out).
 */
(function () {
    var TYPE_LABEL = {
        BREAKDOWN: "🔧 Breakdown / bus issue",
        ROAD_BLOCKAGE: "🚧 Road blockage / diversion",
        ROUTE_REQUEST: "🗺️ New route / stop request",
        OTHER: "💬 Other"
    };
    var STATUS = {
        NEW:          { text: "Sent",         color: "#5c6b62" },
        UNDER_REVIEW: { text: "Under review", color: "#e2892f" },
        NEED_INFO:    { text: "Admin needs more info. Please contact your depot.", color: "#c2410c" },
        APPROVED:     { text: "Approved",     color: "#1f6f5c" },
        REJECTED:     { text: "Rejected",     color: "#b91c1c" }
    };

    function t(k) { return typeof window.tr === "function" ? window.tr(k) : k; }
    function esc(v) {
        return String(v == null ? "" : v)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }
    function token() { try { return driverToken; } catch (e) { return null; } }

    // Which replies the driver has already seen: { reportId: repliedAt }, kept in this browser only.
    var SEEN_KEY = "wimb_rpt_seen";
    function seenMap() {
        try { return JSON.parse(localStorage.getItem(SEEN_KEY) || "{}") || {}; } catch (e) { return {}; }
    }
    function isUnseen(r, seen) { return !!r.adminReply && seen[r.id] !== r.repliedAt; }
    function markSeen(list) {
        var next = {};                                   // only the reports still in the list, so it never grows
        list.forEach(function (r) { if (r.adminReply) next[r.id] = r.repliedAt; });
        try { localStorage.setItem(SEEN_KEY, JSON.stringify(next)); } catch (e) {}
    }
    function setBadge(n) {
        var b = document.getElementById("rptBadge");
        if (!b) return;
        b.textContent = n > 9 ? "9+" : String(n);
        b.style.display = n > 0 ? "inline-block" : "none";
    }
    async function fetchMine() {
        try {
            var res = await fetch("/api/driver/reports", { headers: { "X-Driver-Token": token() || "" } });
            return res.ok ? await res.json() : null;
        } catch (e) { return null; }
    }
    // Count unread replies without opening the form.
    async function refreshBadge() {
        var list = await fetchMine();
        if (!list) return;
        var seen = seenMap();
        setBadge(list.filter(function (r) { return isUnseen(r, seen); }).length);
    }

    function build() {
        var s = document.createElement("style");
        s.textContent =
            "#rptFab{position:fixed;right:14px;bottom:calc(14px + env(safe-area-inset-bottom));z-index:60;" +
            "display:none;background:var(--accent);color:#fff;border:none;border-radius:999px;" +
            "padding:11px 16px;font-size:14px;font-weight:700;box-shadow:var(--shadow);cursor:pointer;}" +
            "#rptModal{position:fixed;inset:0;z-index:80;background:rgba(0,0,0,.45);display:none;" +
            "align-items:flex-end;justify-content:center;}" +
            "#rptModal.open{display:flex;}" +
            "#rptBox{background:var(--bg-card);width:100%;max-width:480px;max-height:92vh;overflow-y:auto;" +
            "border-radius:20px 20px 0 0;padding:18px;}" +
            ".rpt-item{border:1px solid var(--border);border-radius:12px;padding:10px 12px;margin-top:8px;font-size:13px;}" +
            ".rpt-item b{font-size:13.5px;}" +
            ".rpt-item.has-new{border-color:#1f6f5c;box-shadow:0 0 0 2px rgba(31,111,92,.18);}" +
            ".rpt-reply{background:rgba(31,111,92,.09);border-left:3px solid #1f6f5c;border-radius:8px;padding:8px 10px;margin:6px 0 4px;}" +
            ".rpt-reply-h{font-size:12px;font-weight:700;color:#1f6f5c;margin-bottom:2px;}" +
            ".rpt-reply-b{white-space:pre-wrap;overflow-wrap:anywhere;color:var(--text);}" +
            ".rpt-reply-t{font-size:11px;color:var(--text-faint);margin-top:3px;}" +
            ".rpt-new{background:#dc2626;color:#fff;border-radius:8px;padding:1px 7px;font-size:10px;font-weight:700;margin-left:6px;}" +
            "#rptBadge{display:none;background:#dc2626;color:#fff;border:2px solid #fff;border-radius:999px;min-width:18px;height:18px;" +
            "line-height:14px;box-sizing:content-box;padding:0 4px;font-size:11px;font-weight:700;margin-left:7px;text-align:center;}";
        document.head.appendChild(s);

        var fab = document.createElement("button");
        fab.id = "rptFab"; fab.type = "button";
        fab.innerHTML = "<span>⚠️ " + esc(t("Report an issue")) + '</span><span id="rptBadge"></span>';
        fab.onclick = openModal;
        document.body.appendChild(fab);

        var m = document.createElement("div");
        m.id = "rptModal";
        m.innerHTML =
            '<div id="rptBox">' +
            '<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">' +
            '<p class="eyebrow" style="font-size:16px;margin:0;">' + esc(t("Report an issue")) + '</p>' +
            '<button type="button" id="rptClose" style="background:none;border:none;font-size:22px;cursor:pointer;color:var(--text-muted);">&times;</button></div>' +
            '<label class="brand-label">' + esc(t("What are you reporting?")) + '</label>' +
            '<select id="rptType" class="brand-select mb-4">' +
            Object.keys(TYPE_LABEL).map(function (k) { return '<option value="' + k + '">' + esc(TYPE_LABEL[k]) + '</option>'; }).join("") +
            '</select>' +
            '<label class="brand-label">' + esc(t("Place / route (optional)")) + '</label>' +
            '<input id="rptPlace" class="brand-input mb-4" maxlength="150" placeholder="e.g. NH31 near Falakata"/>' +
            '<label class="brand-label">' + esc(t("Details")) + '</label>' +
            '<textarea id="rptDesc" class="brand-input mb-4" rows="4" maxlength="1000" placeholder="' + esc(t("Describe what happened")) + '"></textarea>' +
            '<button type="button" id="rptSend" class="brand-btn btn-amber">' + esc(t("Send to admin")) + '</button>' +
            '<p id="rptMsg" style="font-size:13px;margin:8px 0 0;min-height:18px;"></p>' +
            '<p class="eyebrow" style="font-size:13.5px;margin:14px 0 0;">' + esc(t("Your recent reports")) + '</p>' +
            '<div id="rptList"></div></div>';
        document.body.appendChild(m);

        document.getElementById("rptClose").onclick = closeModal;
        m.addEventListener("click", function (e) { if (e.target === m) closeModal(); });
        document.getElementById("rptSend").onclick = send;
    }

    function openModal() {
        document.getElementById("rptModal").classList.add("open");
        document.getElementById("rptMsg").textContent = "";
        loadMine();
    }
    function closeModal() { document.getElementById("rptModal").classList.remove("open"); }
    function msg(text, color) {
        var el = document.getElementById("rptMsg");
        el.textContent = text; el.style.color = color || "var(--text-muted)";
    }

    async function send() {
        var btn = document.getElementById("rptSend");
        var body = {
            type: document.getElementById("rptType").value,
            place: document.getElementById("rptPlace").value,
            description: document.getElementById("rptDesc").value
        };
        btn.disabled = true; msg(t("Sending..."));
        try {
            var res = await fetch("/api/driver/reports", {
                method: "POST",
                headers: { "Content-Type": "application/json", "X-Driver-Token": token() || "" },
                body: JSON.stringify(body)
            });
            var data = await res.json().catch(function () { return {}; });
            if (!res.ok) { msg("❌ " + (data.error || t("Could not send")), "#b91c1c"); return; }
            msg("✅ " + t("Report sent to admin"), "#1f6f5c");
            document.getElementById("rptPlace").value = "";
            document.getElementById("rptDesc").value = "";
            loadMine();
        } catch (e) {
            msg("❌ " + t("Network error"), "#b91c1c");
        } finally {
            btn.disabled = false;
        }
    }

    async function loadMine() {
        var box = document.getElementById("rptList");
        var list = await fetchMine();
        if (!list) { box.innerHTML = ""; return; }
        if (!list.length) { box.innerHTML = '<p style="font-size:13px;color:var(--text-faint);margin-top:8px;">' + esc(t("No reports yet")) + '</p>'; return; }
        var seen = seenMap();                         // read BEFORE marking, so the NEW chips can show
        box.innerHTML = list.map(function (r) {
            var st = STATUS[r.status] || { text: r.status, color: "#5c6b62" };
            // With a written reply the generic "contact your depot" hint is no longer needed.
            var statusText = (r.status === "NEED_INFO" && r.adminReply) ? t("Admin needs more info") : st.text;
            var fresh = isUnseen(r, seen);
            var reply = r.adminReply
                ? '<div class="rpt-reply"><div class="rpt-reply-h">💬 ' + esc(t("Reply from admin")) +
                (fresh ? '<span class="rpt-new">' + esc(t("NEW")) + '</span>' : '') + '</div>' +
                '<div class="rpt-reply-b">' + esc(r.adminReply) + '</div>' +
                (r.repliedAt ? '<div class="rpt-reply-t">' + esc(new Date(r.repliedAt).toLocaleString()) + '</div>' : '') +
                '</div>'
                : '';
            return '<div class="rpt-item' + (fresh ? ' has-new' : '') + '"><b>' + esc(TYPE_LABEL[r.type] || r.type) + '</b>' +
                (r.place ? ' · ' + esc(r.place) : '') +
                '<div style="color:var(--text-muted);margin:3px 0;">' + esc(r.description) + '</div>' +
                '<div style="font-weight:700;color:' + st.color + ';">' + esc(statusText) + '</div>' +
                reply +
                '<div style="font-size:11.5px;color:var(--text-faint);">' + esc(new Date(r.createdAt).toLocaleString()) + '</div></div>';
        }).join("");
        markSeen(list);                               // the driver has now seen every reply
        setBadge(0);
    }

    document.addEventListener("DOMContentLoaded", function () {
        build();
        // Button only while a driver is logged in (driverToken is null otherwise).
        var hadToken = false;
        setInterval(function () {
            var has = !!token();
            document.getElementById("rptFab").style.display = has ? "block" : "none";
            if (has && !hadToken) refreshBadge();         // just logged in: any replies waiting?
            hadToken = has;
        }, 1000);
        // New replies while the driver is on the road: check once a minute, only while the app is
        // visible and the form is closed (opening the form already loads the list).
        setInterval(function () {
            if (!token() || document.hidden) return;
            if (document.getElementById("rptModal").classList.contains("open")) return;
            refreshBadge();
        }, 60000);
    });
})();