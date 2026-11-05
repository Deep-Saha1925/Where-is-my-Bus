/*
 * Where Is My Bus (WIMB)
 * Turns a plain <select> into a type-to-search box. The <select> stays in the
 * page (hidden) and remains the source of truth, so existing code that reads
 * select.value / listens to onchange keeps working unchanged.
 */
(function () {
    var STYLE_ID = "ssStyle";

    function t(key) { return typeof window.tr === "function" ? window.tr(key) : key; }

    function injectStyle() {
        if (document.getElementById(STYLE_ID)) return;
        var s = document.createElement("style");
        s.id = STYLE_ID;
        s.textContent =
            ".ss-wrap{position:relative;}" +
            ".ss-list{position:absolute;left:0;right:0;top:calc(100% + 4px);z-index:50;max-height:240px;" +
            "overflow-y:auto;background:var(--bg-card);border:1px solid var(--border);border-radius:11px;" +
            "box-shadow:var(--shadow);display:none;-webkit-overflow-scrolling:touch;}" +
            ".ss-list.open{display:block;}" +
            ".ss-item{padding:11px 14px;font-size:14.5px;cursor:pointer;color:var(--text-primary);}" +
            ".ss-item.active{background:var(--accent-soft);}" +
            ".ss-item.selected{font-weight:700;}" +
            ".ss-empty{padding:11px 14px;font-size:13.5px;color:var(--text-faint);}";
        document.head.appendChild(s);
    }

    function makeSearchable(selectId) {
        var select = document.getElementById(selectId);
        if (!select || select.dataset.ss === "1") return;
        select.dataset.ss = "1";
        injectStyle();

        var wrap = document.createElement("div");
        wrap.className = "ss-wrap";
        wrap.style.marginBottom = getComputedStyle(select).marginBottom;

        var input = document.createElement("input");
        input.type = "text";
        input.className = "brand-input";
        input.autocomplete = "off";
        input.autocapitalize = "off";
        input.spellcheck = false;
        input.setAttribute("role", "combobox");
        input.setAttribute("aria-expanded", "false");

        var list = document.createElement("div");
        list.className = "ss-list";
        list.setAttribute("role", "listbox");

        select.parentNode.insertBefore(wrap, select);
        wrap.appendChild(input);
        wrap.appendChild(list);
        wrap.appendChild(select);
        select.style.display = "none";

        var shown = [];      // options currently listed
        var activeIdx = -1;

        function realOptions() {
            return Array.prototype.filter.call(select.options, function (o) { return o.value !== ""; });
        }
        function placeholderText() {
            var p = Array.prototype.find.call(select.options, function (o) { return o.value === ""; });
            return p ? p.textContent : "";
        }
        function selectedText() {
            var o = select.selectedOptions && select.selectedOptions[0];
            return o && o.value !== "" ? o.textContent : "";
        }
        function sync() {
            input.value = selectedText();
            input.placeholder = placeholderText();
            input.disabled = select.disabled;
        }

        function render(filter) {
            var tokens = (filter || "").toLowerCase().split(/\s+/).filter(Boolean);
            shown = realOptions().filter(function (o) {
                var txt = o.textContent.toLowerCase();
                return tokens.every(function (tk) { return txt.indexOf(tk) !== -1; });
            });
            activeIdx = shown.length ? 0 : -1;
            list.innerHTML = "";
            if (!shown.length) {
                var e = document.createElement("div");
                e.className = "ss-empty";
                e.textContent = t("No match found");
                list.appendChild(e);
                return;
            }
            shown.forEach(function (o, i) {
                var d = document.createElement("div");
                d.className = "ss-item" + (o.value === select.value ? " selected" : "") + (i === activeIdx ? " active" : "");
                d.textContent = o.textContent;
                d.setAttribute("role", "option");
                // pointerdown + preventDefault: choose before the input loses focus
                d.addEventListener("mousedown", function (ev) { ev.preventDefault(); choose(o); });
                d.addEventListener("touchstart", function () { d._moved = false; }, { passive: true });
                d.addEventListener("touchmove", function () { d._moved = true; }, { passive: true });
                d.addEventListener("touchend", function (ev) {
                    if (d._moved) return;          // it was a scroll, not a tap
                    ev.preventDefault(); choose(o);
                });
                list.appendChild(d);
            });
        }

        function open(filter) {
            if (input.disabled) return;
            render(filter);
            list.classList.add("open");
            input.setAttribute("aria-expanded", "true");
            var sel = list.querySelector(".ss-item.selected");
            if (sel && !filter) sel.scrollIntoView({ block: "nearest" });
        }
        function close() {
            list.classList.remove("open");
            input.setAttribute("aria-expanded", "false");
        }
        function choose(o) {
            select.value = o.value;
            select.dispatchEvent(new Event("change", { bubbles: true }));
            sync();
            close();
            input.blur();
        }
        function highlight(i) {
            var items = list.querySelectorAll(".ss-item");
            if (!items.length) return;
            activeIdx = (i + items.length) % items.length;
            items.forEach(function (el, k) { el.classList.toggle("active", k === activeIdx); });
            items[activeIdx].scrollIntoView({ block: "nearest" });
        }

        // Tap = show the full list (scrolling still works); typing narrows it down.
        input.addEventListener("focus", function () { input.select(); open(""); });
        input.addEventListener("click", function () { if (!list.classList.contains("open")) open(""); });
        input.addEventListener("input", function () { open(input.value); });
        input.addEventListener("keydown", function (ev) {
            if (ev.key === "ArrowDown") { ev.preventDefault(); if (!list.classList.contains("open")) open(input.value); else highlight(activeIdx + 1); }
            else if (ev.key === "ArrowUp") { ev.preventDefault(); highlight(activeIdx - 1); }
            else if (ev.key === "Enter") {
                if (list.classList.contains("open") && shown[activeIdx]) { ev.preventDefault(); choose(shown[activeIdx]); }
            }
            else if (ev.key === "Escape") { close(); sync(); }
        });
        input.addEventListener("blur", function () { close(); sync(); });

        // Options get filled in later by loadRoutes(); keep the box in step.
        new MutationObserver(sync).observe(select, { childList: true, subtree: true, attributes: true, attributeFilter: ["disabled"] });

        select._ssSync = sync;
        sync();
    }

    window.makeSearchable = makeSearchable;
    // Call after setting select.value from code (programmatic changes fire no event).
    window.syncSearchable = function (id) {
        var el = document.getElementById(id);
        if (el && el._ssSync) el._ssSync();
    };

    document.addEventListener("DOMContentLoaded", function () { makeSearchable("routeCode"); });
})();
