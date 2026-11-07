/*
 * Where Is My Bus (WIMB)
 * Passenger "Report a problem" bottom sheet.
 *
 * Self-contained: injects its own CSS + modal the first time it is opened.
 * Open it from anywhere with:
 *     <button type="button" data-report-open>Report a problem</button>
 * or from code:  window.openPassengerReport({ bus: "WB-23A-1245", route: "Siliguri to Falakata" })
 *
 * On the tracking page (track.html) the bus number and route are pre-filled from
 * #busNumberDisplay and #routeTitle. Works with both pages' theme variables.
 */
(function () {
    "use strict";

    var TYPES = [
        ["LOCATION_WRONG", "📍 Bus location looks wrong"],
        ["BUS_NOT_RUNNING", "🚫 Bus is not running / cancelled"],
        ["CONDUCT", "👤 Driver or staff behaviour"],
        ["APP_PROBLEM", "🛠️ Problem with this app"],
        ["OTHER", "💬 Something else"]
    ];

    var built = false, lastFocus = null, draft = "";

    function t(k) { return typeof window.tr === "function" ? window.tr(k) : k; }
    function esc(v) {
        return String(v == null ? "" : v)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }
    function $(id) { return document.getElementById(id); }

    /* Bus + route from the tracking page, if we are on it */
    function pageContext() {
        var bus = "", route = "";
        var b = $("busNumberDisplay");
        if (b) bus = (b.textContent || "").trim();
        if (bus === "—" || bus === "-") bus = "";
        var r = $("routeTitle");
        if (r) {
            route = (r.textContent || "").trim();
            if (/^loading/i.test(route)) route = "";
        }
        return { bus: bus, route: route };
    }

    function injectStyle() {
        var s = document.createElement("style");
        s.textContent =
            "html.pr-lock,html.pr-lock body{overflow:hidden !important;}" +
            "#prModal{position:fixed;inset:0;z-index:100;background:rgba(0,0,0,.5);display:none;" +
            "align-items:flex-end;justify-content:center;}" +
            "#prModal.open{display:flex;}" +
            "#prBox{background:var(--bg-sheet,var(--bg-card,#fff));color:var(--text-primary,#0f172a);" +
            "width:100%;max-width:520px;max-height:92vh;max-height:92dvh;overflow-y:auto;" +
            "overscroll-behavior:contain;-webkit-overflow-scrolling:touch;border-radius:20px 20px 0 0;" +
            "padding:16px 16px calc(18px + env(safe-area-inset-bottom,0px));" +
            "font-family:inherit;animation:prUp .22s ease-out;}" +
            "@keyframes prUp{from{transform:translateY(40px);opacity:.4}to{transform:none;opacity:1}}" +
            "@media (min-width:600px){#prModal{align-items:center}#prBox{border-radius:20px}}" +
            "@media (prefers-reduced-motion:reduce){#prBox{animation:none}}" +
            ".pr-head{display:flex;justify-content:space-between;align-items:center;gap:10px;margin-bottom:12px;}" +
            ".pr-title{font-size:17px;font-weight:700;margin:0;}" +
            ".pr-x{width:44px;height:44px;margin:-6px -8px -6px 0;border:0;background:transparent;" +
            "color:var(--text-muted,#64748b);font-size:26px;line-height:1;cursor:pointer;border-radius:50%;}" +
            ".pr-lbl{display:block;font-size:13px;font-weight:600;color:var(--text-muted,#64748b);margin:12px 0 5px;}" +
            ".pr-in{width:100%;min-height:46px;padding:11px 13px;font:inherit;font-size:16px;" + /* 16px stops iOS zoom */
            "background:var(--bg-input,var(--bg-sheet-in,#f8fafc));color:var(--text-primary,#0f172a);" +
            "border:1px solid var(--border,#e2e8f0);border-radius:12px;outline:none;box-sizing:border-box;}" +
            ".pr-in:focus{border-color:#3b82f6;box-shadow:0 0 0 3px rgba(59,130,246,.2);}" +
            "textarea.pr-in{resize:vertical;min-height:104px;}" +
            ".pr-hint{font-size:12px;color:var(--text-faint,#94a3b8);margin-top:4px;line-height:1.35;}" +
            ".pr-row{display:flex;justify-content:space-between;gap:10px;}" +
            ".pr-send{width:100%;min-height:50px;margin-top:16px;border:0;border-radius:14px;" +
            "background:#1d4ed8;color:#fff;font:inherit;font-size:16px;font-weight:700;cursor:pointer;}" +
            ".pr-send:disabled{opacity:.6;cursor:progress;}" +
            ".pr-send:active{transform:scale(.98);}" +
            ".pr-msg{font-size:13.5px;min-height:20px;margin:10px 0 0;line-height:1.4;}" +
            ".pr-done{text-align:center;padding:18px 4px 6px;}" +
            ".pr-done .pr-big{font-size:44px;line-height:1;}" +
            ".pr-done p{margin:10px 0 0;font-size:15px;line-height:1.45;}" +
            ".pr-hp{position:absolute !important;left:-9999px !important;width:1px;height:1px;opacity:0;" +
            "pointer-events:none;}";
        document.head.appendChild(s);
    }

    function build() {
        if (built) return;
        built = true;
        injectStyle();

        var m = document.createElement("div");
        m.id = "prModal";
        m.setAttribute("role", "dialog");
        m.setAttribute("aria-modal", "true");
        m.setAttribute("aria-labelledby", "prTitle");
        m.innerHTML =
            '<div id="prBox">' +
            '<div class="pr-head"><h2 class="pr-title" id="prTitle">' + esc(t("Report a problem")) + '</h2>' +
            '<button type="button" class="pr-x" id="prClose" aria-label="' + esc(t("Close")) + '">&times;</button></div>' +

            '<div id="prForm">' +
            '<label class="pr-lbl" for="prType">' + esc(t("What is the problem?")) + '</label>' +
            '<select id="prType" class="pr-in">' +
            TYPES.map(function (x) { return '<option value="' + x[0] + '">' + esc(t(x[1])) + '</option>'; }).join("") +
            '</select>' +

            '<label class="pr-lbl" for="prBus">' + esc(t("Bus number (optional)")) + '</label>' +
            '<input id="prBus" class="pr-in" maxlength="40" autocomplete="off" autocapitalize="characters" placeholder="WB-23A-1245"/>' +

            '<label class="pr-lbl" for="prPlace">' + esc(t("Route / place (optional)")) + '</label>' +
            '<input id="prPlace" class="pr-in" maxlength="150" autocomplete="off"/>' +

            '<div class="pr-row"><label class="pr-lbl" for="prDesc" style="margin-bottom:5px;">' + esc(t("Details")) + '</label>' +
            '<span class="pr-hint" id="prCount" style="margin-top:12px;">0/1000</span></div>' +
            '<textarea id="prDesc" class="pr-in" maxlength="1000" rows="4" placeholder="' + esc(t("Describe what happened")) + '"></textarea>' +

            '<label class="pr-lbl" for="prContact">' + esc(t("Phone or email (optional)")) + '</label>' +
            '<input id="prContact" class="pr-in" maxlength="100" autocomplete="off" inputmode="email"/>' +
            '<div class="pr-hint">' + esc(t("Only the admin sees this. Add it if you want a reply.")) + '</div>' +

            /* honeypot: invisible to people, tempting to bots */
            '<input id="prWebsite" class="pr-hp" type="text" name="website" tabindex="-1" autocomplete="off" aria-hidden="true"/>' +

            '<button type="button" class="pr-send" id="prSend">' + esc(t("Send report")) + '</button>' +
            '<p class="pr-msg" id="prMsg" role="status" aria-live="polite"></p>' +
            '</div>' +

            '<div id="prDone" class="pr-done" style="display:none;">' +
            '<div class="pr-big">✅</div>' +
            '<p>' + esc(t("Thank you! Your report was sent to the admin.")) + '</p>' +
            '<button type="button" class="pr-send" id="prDoneBtn">' + esc(t("Close")) + '</button>' +
            '</div>' +
            '</div>';
        document.body.appendChild(m);

        $("prClose").onclick = close;
        $("prDoneBtn").onclick = close;
        $("prSend").onclick = send;
        m.addEventListener("click", function (e) { if (e.target === m) close(); });
        $("prDesc").addEventListener("input", function () {
            draft = this.value;
            $("prCount").textContent = this.value.length + "/1000";
        });
        // keep the focused field above the on-screen keyboard
        m.addEventListener("focusin", function (e) {
            var el = e.target;
            if (el && /^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName)) {
                setTimeout(function () { try { el.scrollIntoView({ block: "center", behavior: "smooth" }); } catch (_) {} }, 300);
            }
        });
        document.addEventListener("keydown", function (e) {
            if (!$("prModal").classList.contains("open")) return;
            if (e.key === "Escape") { close(); return; }
            if (e.key === "Tab") trapFocus(e);
        });
    }

    function trapFocus(e) {
        var box = $("prBox");
        var items = box.querySelectorAll("button, select, input:not(.pr-hp), textarea");
        var vis = [];
        for (var i = 0; i < items.length; i++) { if (items[i].offsetParent !== null) vis.push(items[i]); }
        if (!vis.length) return;
        var first = vis[0], last = vis[vis.length - 1];
        if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
        else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
    }

    function open(opts) {
        build();
        opts = opts || {};
        var ctx = pageContext();
        lastFocus = document.activeElement;

        $("prForm").style.display = "";
        $("prDone").style.display = "none";
        $("prMsg").textContent = "";
        $("prSend").disabled = false;
        $("prBus").value = opts.bus || ctx.bus || $("prBus").value || "";
        $("prPlace").value = opts.route || ctx.route || $("prPlace").value || "";
        $("prDesc").value = draft;
        $("prCount").textContent = draft.length + "/1000";
        $("prWebsite").value = "";

        $("prModal").classList.add("open");
        document.documentElement.classList.add("pr-lock");
        setTimeout(function () { $("prDesc").focus({ preventScroll: true }); }, 50);
    }

    function close() {
        var m = $("prModal");
        if (!m) return;
        m.classList.remove("open");
        document.documentElement.classList.remove("pr-lock");
        if (lastFocus && lastFocus.focus) { try { lastFocus.focus({ preventScroll: true }); } catch (_) {} }
    }

    function msg(text, color) {
        var el = $("prMsg");
        el.textContent = text;
        el.style.color = color || "var(--text-muted,#64748b)";
    }

    async function send() {
        var desc = $("prDesc").value.trim();
        if (desc.length < 5) { msg(t("Please describe the problem (at least 5 characters)"), "#dc2626"); $("prDesc").focus(); return; }

        var btn = $("prSend");
        btn.disabled = true;
        msg(t("Sending..."));
        try {
            var res = await fetch("/api/passenger/reports", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    type: $("prType").value,
                    busNumber: $("prBus").value,
                    place: $("prPlace").value,
                    description: desc,
                    contact: $("prContact").value,
                    website: $("prWebsite").value
                })
            });
            var data = await res.json().catch(function () { return {}; });
            if (!res.ok) {
                msg("❌ " + (data.error ? t(data.error) : t("Could not send. Please try again.")), "#dc2626");
                btn.disabled = false;
                return;
            }
            draft = "";
            $("prDesc").value = "";
            $("prForm").style.display = "none";
            $("prDone").style.display = "";
        } catch (e) {
            msg("❌ " + t("Network error. Please try again."), "#dc2626");
            btn.disabled = false;
        }
    }

    // Any element with data-report-open opens the sheet (no inline onclick needed).
    document.addEventListener("click", function (e) {
        var el = e.target.closest && e.target.closest("[data-report-open]");
        if (!el) return;
        e.preventDefault();
        open();
    });

    window.openPassengerReport = open;
})();
