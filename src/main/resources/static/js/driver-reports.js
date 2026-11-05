/*
 * Where Is My Bus (WIMB)
 * Driver "Report an issue" button + form + status list.
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
            ".rpt-item b{font-size:13.5px;}";
        document.head.appendChild(s);

        var fab = document.createElement("button");
        fab.id = "rptFab"; fab.type = "button"; fab.textContent = "⚠️ " + t("Report an issue");
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
        try {
            var res = await fetch("/api/driver/reports", { headers: { "X-Driver-Token": token() || "" } });
            if (!res.ok) { box.innerHTML = ""; return; }
            var list = await res.json();
            if (!list.length) { box.innerHTML = '<p style="font-size:13px;color:var(--text-faint);margin-top:8px;">' + esc(t("No reports yet")) + '</p>'; return; }
            box.innerHTML = list.map(function (r) {
                var st = STATUS[r.status] || { text: r.status, color: "#5c6b62" };
                return '<div class="rpt-item"><b>' + esc(TYPE_LABEL[r.type] || r.type) + '</b>' +
                    (r.place ? ' · ' + esc(r.place) : '') +
                    '<div style="color:var(--text-muted);margin:3px 0;">' + esc(r.description) + '</div>' +
                    '<div style="font-weight:700;color:' + st.color + ';">' + esc(st.text) + '</div>' +
                    '<div style="font-size:11.5px;color:var(--text-faint);">' + esc(new Date(r.createdAt).toLocaleString()) + '</div></div>';
            }).join("");
        } catch (e) { box.innerHTML = ""; }
    }

    document.addEventListener("DOMContentLoaded", function () {
        build();
        // Button only while a driver is logged in (driverToken is null otherwise).
        setInterval(function () {
            document.getElementById("rptFab").style.display = token() ? "block" : "none";
        }, 1000);
    });
})();
