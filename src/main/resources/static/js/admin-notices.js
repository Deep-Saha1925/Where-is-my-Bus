/*
 * Where Is My Bus (WIMB)
 * Admin "Notices" tab: publish / hide / delete passenger notices.
 */
(function () {
    function esc(v) {
        return String(v == null ? "" : v)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }
    function csrf() {
        var el = document.getElementById("csrfToken");
        return el ? el.value : "";
    }
    function setMsg(text, color) {
        var el = document.getElementById("noticeStatus");
        if (!el) return;
        el.textContent = text;
        el.style.color = color || "";
    }
    var SEV_LABEL = { INFO: "ℹ️ Info", WARNING: "⚠️ Warning", CRITICAL: "🚨 Critical" };

    window.loadNotices = async function () {
        var tbody = document.getElementById("noticeTableBody");
        var sub = document.getElementById("noticeCountSub");
        if (!tbody) return;
        try {
            var res = await fetch("/admin/notices", { cache: "no-store" });
            if (!res.ok) throw new Error("HTTP " + res.status);
            var list = await res.json();
            var now = Date.now();
            if (sub) sub.textContent = list.length + " notice" + (list.length === 1 ? "" : "s");
            if (!list.length) {
                tbody.innerHTML = '<tr><td colspan="5" class="table-empty">No notices yet</td></tr>';
                return;
            }
            tbody.innerHTML = list.map(function (n) {
                var expired = n.expiresAt && new Date(n.expiresAt).getTime() <= now;
                var state = !n.active ? "Hidden" : (expired ? "Expired" : "Live");
                var color = state === "Live" ? "#22c55e" : "var(--text-faint)";
                var scope = [n.routeCode, n.busNumber].filter(Boolean).map(esc).join(" · ") || "All";
                return "<tr>" +
                    "<td><b>" + esc(n.title) + "</b><div style='font-size:12px;color:var(--text-muted)'>" + esc(n.message) + "</div></td>" +
                    "<td>" + (SEV_LABEL[n.severity] || esc(n.severity)) + "</td>" +
                    "<td>" + scope + "</td>" +
                    "<td style='color:" + color + ";font-weight:600'>" + state +
                    (n.expiresAt ? "<div style='font-size:11px;font-weight:400;color:var(--text-faint)'>until " +
                        esc(new Date(n.expiresAt).toLocaleString()) + "</div>" : "") + "</td>" +
                    "<td style='white-space:nowrap'>" +
                    "<button class='row-update-btn' onclick='toggleNotice(" + Number(n.id) + "," + (!n.active) + ")'>" +
                    (n.active ? "Hide" : "Show") + "</button> " +
                    "<button class='row-delete-btn' onclick='deleteNotice(" + Number(n.id) + ")'>Delete</button></td></tr>";
            }).join("");
        } catch (e) {
            tbody.innerHTML = '<tr><td colspan="5" class="table-empty" style="color:#ef4444;">Failed to load notices</td></tr>';
        }
    };

    window.publishNotice = async function () {
        var fd = new FormData();
        fd.append("_csrf", csrf());
        fd.append("title", document.getElementById("noticeTitle").value);
        fd.append("message", document.getElementById("noticeMessage").value);
        fd.append("severity", document.getElementById("noticeSeverity").value);
        fd.append("routeCode", document.getElementById("noticeRoute").value);
        fd.append("busNumber", document.getElementById("noticeBus").value);
        var hrs = document.getElementById("noticeExpiry").value;
        if (hrs) fd.append("expiresInHours", hrs);

        setMsg("Publishing...", "var(--text-muted)");
        try {
            var res = await fetch("/admin/notices", { method: "POST", body: fd });
            var data = await res.json().catch(function () { return {}; });
            if (!res.ok) { setMsg("❌ " + (data.error || "Failed to publish"), "#ef4444"); return; }
            setMsg("✅ " + data.message, "#22c55e");
            ["noticeTitle", "noticeMessage", "noticeRoute", "noticeBus"].forEach(function (id) {
                document.getElementById(id).value = "";
            });
            loadNotices();
        } catch (e) {
            setMsg("❌ Network error", "#ef4444");
        }
    };

    window.toggleNotice = async function (id, value) {
        var res = await fetch("/admin/notices/" + id + "/active?value=" + value + "&_csrf=" + encodeURIComponent(csrf()),
            { method: "PUT" });
        if (!res.ok) setMsg("❌ Could not update notice", "#ef4444");
        loadNotices();
    };

    window.deleteNotice = async function (id) {
        if (!confirm("Delete this notice permanently?")) return;
        var res = await fetch("/admin/notices/" + id + "?_csrf=" + encodeURIComponent(csrf()), { method: "DELETE" });
        if (!res.ok) setMsg("❌ Could not delete notice", "#ef4444");
        loadNotices();
    };

    document.addEventListener("DOMContentLoaded", loadNotices);
})();
