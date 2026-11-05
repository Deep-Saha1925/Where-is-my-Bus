/*
 * Where Is My Bus (WIMB)
 * Admin "Driver Reports" tab: review, update status, add notes, publish as notice.
 */
(function () {
    var TYPE_LABEL = {
        BREAKDOWN: "🔧 Breakdown / bus issue",
        ROAD_BLOCKAGE: "🚧 Road blockage / diversion",
        ROUTE_REQUEST: "🗺️ New route / stop request",
        OTHER: "💬 Other"
    };
    var STATUS_LABEL = { NEW: "New", UNDER_REVIEW: "Under review", NEED_INFO: "Need info", APPROVED: "Approved", REJECTED: "Rejected" };
    var STATUS_COLOR = { NEW: "#3b82f6", UNDER_REVIEW: "#f59e0b", NEED_INFO: "#ea580c", APPROVED: "#22c55e", REJECTED: "#ef4444" };
    var DEFAULT_SEV = { BREAKDOWN: "WARNING", ROAD_BLOCKAGE: "WARNING", ROUTE_REQUEST: "INFO", OTHER: "INFO" };
    var cache = {};

    function esc(v) {
        return String(v == null ? "" : v)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }
    function csrf() { var el = document.getElementById("csrfToken"); return el ? el.value : ""; }
    function msg(text, color) {
        var el = document.getElementById("reportStatusMsg");
        if (el) { el.textContent = text; el.style.color = color || ""; }
    }

    window.loadReports = async function () {
        var box = document.getElementById("reportList");
        var filter = document.getElementById("reportFilter");
        if (!box) return;
        var q = filter && filter.value ? "?status=" + encodeURIComponent(filter.value) : "";
        try {
            var res = await fetch("/admin/reports" + q, { cache: "no-store" });
            if (!res.ok) throw new Error("HTTP " + res.status);
            var list = await res.json();
            cache = {};
            list.forEach(function (r) { cache[r.id] = r; });
            updateBadge(q ? null : list);
            document.getElementById("reportCountSub").textContent = list.length + " report" + (list.length === 1 ? "" : "s");
            if (!list.length) { box.innerHTML = '<p class="table-empty">No reports</p>'; return; }
            box.innerHTML = list.map(card).join("");
        } catch (e) {
            box.innerHTML = '<p class="table-empty" style="color:#ef4444;">Failed to load reports</p>';
        }
    };

    // Red count on the tab button = reports still waiting for a first look.
    function updateBadge(all) {
        var btn = document.getElementById("tabBtnReports");
        if (!btn) return;
        if (all) {
            var n = all.filter(function (r) { return r.status === "NEW"; }).length;
            btn.textContent = "📥 Driver Reports" + (n ? " (" + n + " new)" : "");
        }
    }

    function card(r) {
        var opts = Object.keys(STATUS_LABEL).map(function (k) {
            return '<option value="' + k + '"' + (k === r.status ? " selected" : "") + ">" + STATUS_LABEL[k] + "</option>";
        }).join("");
        var published = r.publishedNoticeId
            ? '<span style="font-size:12px;color:#22c55e;font-weight:600;">📢 Published as notice #' + Number(r.publishedNoticeId) + "</span>"
            : '<button class="row-update-btn" onclick="toggleReportPublish(' + Number(r.id) + ')">📢 Publish as notice</button>';
        return '<div class="panel-card" style="margin-bottom:12px;"><div class="panel-card-body">' +
            '<div style="display:flex;justify-content:space-between;gap:8px;flex-wrap:wrap;">' +
            "<div><b>" + esc(TYPE_LABEL[r.type] || r.type) + "</b> · Bus <b>" + esc(r.busNumber) + "</b>" +
            (r.place ? " · " + esc(r.place) : "") + "</div>" +
            '<span style="font-weight:700;color:' + (STATUS_COLOR[r.status] || "") + '">' + esc(STATUS_LABEL[r.status] || r.status) + "</span></div>" +
            '<div style="margin:6px 0;white-space:pre-wrap;">' + esc(r.description) + "</div>" +
            '<div style="font-size:11.5px;color:var(--text-faint);">Sent ' + esc(new Date(r.createdAt).toLocaleString()) + "</div>" +
            '<div style="display:flex;gap:8px;flex-wrap:wrap;margin-top:10px;align-items:center;">' +
            '<select id="rs-' + r.id + '" class="pick-select">' + opts + "</select>" +
            '<input id="rn-' + r.id + '" class="pick-text" maxlength="500" placeholder="Internal note (driver cannot see this)" value="' + esc(r.adminNote || "") + '" style="flex:1;min-width:180px;"/>' +
            '<button class="row-update-btn" onclick="saveReport(' + Number(r.id) + ')">Save</button>' +
            published +
            '<button class="row-delete-btn" onclick="deleteReport(' + Number(r.id) + ')">Delete</button></div>' +
            '<div id="rp-' + r.id + '" style="display:none;margin-top:10px;border-top:1px dashed var(--border);padding-top:10px;"></div>' +
            "</div></div>";
    }

    window.saveReport = async function (id) {
        var fd = new URLSearchParams();
        fd.append("status", document.getElementById("rs-" + id).value);
        fd.append("adminNote", document.getElementById("rn-" + id).value);
        var res = await fetch("/admin/reports/" + id + "?_csrf=" + encodeURIComponent(csrf()), { method: "PUT", body: fd });
        var data = await res.json().catch(function () { return {}; });
        msg(res.ok ? "✅ " + data.message : "❌ " + (data.error || "Failed"), res.ok ? "#22c55e" : "#ef4444");
        loadReports();
    };

    window.deleteReport = async function (id) {
        if (!confirm("Delete this report permanently?")) return;
        var res = await fetch("/admin/reports/" + id + "?_csrf=" + encodeURIComponent(csrf()), { method: "DELETE" });
        msg(res.ok ? "✅ Report deleted" : "❌ Could not delete", res.ok ? "#22c55e" : "#ef4444");
        loadReports();
    };

    // Prefilled form: driver text is only a draft, admin edits it before it goes public.
    window.toggleReportPublish = function (id) {
        var box = document.getElementById("rp-" + id);
        var r = cache[id];
        if (!box || !r) return;
        if (box.style.display === "block") { box.style.display = "none"; return; }
        var sev = DEFAULT_SEV[r.type] || "INFO";
        var title = (TYPE_LABEL[r.type] || "Notice").replace(/^\S+\s/, "") + (r.place ? ": " + r.place : "");
        box.innerHTML =
            '<input id="pt-' + id + '" class="pick-text" maxlength="100" style="width:100%;display:block;margin-bottom:6px;" value="' + esc(title.substring(0, 100)) + '"/>' +
            '<textarea id="pm-' + id + '" class="pick-text" maxlength="500" rows="3" style="width:100%;display:block;margin-bottom:6px;">' + esc(r.description.substring(0, 500)) + "</textarea>" +
            '<select id="pv-' + id + '" class="pick-select"><option value="INFO"' + (sev === "INFO" ? " selected" : "") + '>ℹ️ Info</option><option value="WARNING"' + (sev === "WARNING" ? " selected" : "") + '>⚠️ Warning</option><option value="CRITICAL">🚨 Critical</option></select> ' +
            '<select id="pe-' + id + '" class="pick-select"><option value="">Never expires</option><option value="6">6 hours</option><option value="24" selected>1 day</option><option value="72">3 days</option><option value="168">7 days</option></select> ' +
            '<button class="upload-btn" style="display:inline-block;width:auto;" onclick="confirmReportPublish(' + id + ')">Publish now</button>';
        box.style.display = "block";
    };

    window.confirmReportPublish = async function (id) {
        var fd = new URLSearchParams();
        fd.append("title", document.getElementById("pt-" + id).value);
        fd.append("message", document.getElementById("pm-" + id).value);
        fd.append("severity", document.getElementById("pv-" + id).value);
        var exp = document.getElementById("pe-" + id).value;
        if (exp) fd.append("expiresInHours", exp);
        var res = await fetch("/admin/reports/" + id + "/publish?_csrf=" + encodeURIComponent(csrf()), { method: "POST", body: fd });
        var data = await res.json().catch(function () { return {}; });
        msg(res.ok ? "✅ " + data.message : "❌ " + (data.error || "Failed"), res.ok ? "#22c55e" : "#ef4444");
        loadReports();
        if (res.ok && window.loadNotices) loadNotices();
    };

    document.addEventListener("DOMContentLoaded", function () {
        loadReports();
        setInterval(function () {
            var f = document.getElementById("reportFilter");
            var list = document.getElementById("reportList");
            var editing = list && (list.contains(document.activeElement) ||
                          list.querySelector("[id^='rp-'][style*='block']"));
            // keep the "new" badge fresh, but never wipe what the admin is typing
            if ((!f || !f.value) && !editing) loadReports();
        }, 60000);
    });
})();
