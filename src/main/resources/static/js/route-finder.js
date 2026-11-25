/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */

/*
 * Route Finder: search routes "from A to B", open one, see its stops on a small map sketch and update
 * its details and stops. One component shared by the admin page and the depot dashboard.
 *
 *   WIMBRouteFinder.mount(rootElement, {
 *     base:             "/admin/route-finder"          // or "/depot/api/route-finder"
 *     csrf:             () => ({ header, token }),     // read at call time, so a refreshed token is picked up
 *     refreshCsrf:      async () => {},                // optional: called once when a change is rejected with 403
 *     canChangeSource:  true | false,                  // only the admin may change where a route starts
 *     fileUrl:          code => url | null             // optional "Download Excel" link
 *   });
 *
 * Everything that comes from the server is put on the page with textContent, never innerHTML.
 * Avoid the two-character sequences "[" "[" and "[" "(" in this file only if it is ever inlined into a
 * Thymeleaf template (it is not: pages load it as a plain script).
 */
(function (global) {
  'use strict';

  var CSS = [
    '.rf { --rf-card: var(--bg-card, var(--card, #ffffff)); --rf-card2: var(--card-2, var(--bg-page, #f6f8f6));',
    '  --rf-text: var(--text-primary, var(--text, #171a21)); --rf-muted: var(--text-muted, var(--muted, #667085));',
    '  --rf-faint: var(--text-faint, var(--muted, #8a978d)); --rf-border: var(--border, #e3e7ee);',
    '  --rf-accent: var(--accent, #2563eb); --rf-ok: var(--ok, #16a34a); --rf-warn: var(--warn, #d97706); --rf-bad: var(--bad, #dc2626);',
    '  color: var(--rf-text); font-family: inherit; }',
    '.rf *, .rf-overlay * { box-sizing: border-box; }',
    '.rf-search { display:flex; gap:8px; flex-wrap:wrap; align-items:flex-end; margin-bottom:12px; }',
    '.rf-field { display:flex; flex-direction:column; gap:4px; flex:1 1 180px; min-width:150px; }',
    '.rf-field label { font-size:12px; font-weight:700; color:var(--rf-muted); }',
    '.rf-input { width:100%; padding:9px 11px; font:inherit; font-size:14px; border-radius:10px; border:1px solid var(--rf-border); background:var(--rf-card); color:var(--rf-text); }',
    '.rf-input:focus { outline:2px solid var(--rf-accent); outline-offset:-1px; }',
    '.rf-input[readonly], .rf-input:disabled { background:var(--rf-card2); color:var(--rf-muted); }',
    '.rf-input.bad { border-color:var(--rf-bad); outline:1px solid var(--rf-bad); }',
    '.rf-btn { font:inherit; font-size:13px; font-weight:700; padding:9px 15px; border-radius:10px; border:1px solid var(--rf-border); background:var(--rf-card); color:var(--rf-text); cursor:pointer; white-space:nowrap; }',
    '.rf-btn:hover { background:var(--rf-card2); }',
    '.rf-btn.primary { background:var(--rf-accent); border-color:var(--rf-accent); color:#fff; }',
    '.rf-btn.primary:hover { filter:brightness(1.08); background:var(--rf-accent); }',
    '.rf-btn.small { padding:5px 9px; font-size:12px; border-radius:8px; }',
    '.rf-btn.danger { color:var(--rf-bad); }',
    '.rf-btn[disabled] { opacity:.5; cursor:default; }',
    '.rf-hint { font-size:13px; color:var(--rf-muted); margin:2px 0 12px; }',
    '.rf-list { display:grid; grid-template-columns:repeat(auto-fill, minmax(320px, 1fr)); gap:12px; }',
    '.rf-card { background:var(--rf-card); border:1px solid var(--rf-border); border-radius:14px; padding:14px 16px; display:flex; flex-direction:column; gap:8px; }',
    '.rf-card-top { display:flex; justify-content:space-between; gap:10px; align-items:flex-start; }',
    '.rf-name { font-weight:700; font-size:15.5px; overflow-wrap:anywhere; }',
    '.rf-sub { color:var(--rf-muted); font-size:12.5px; overflow-wrap:anywhere; }',
    '.rf-match { font-size:13px; font-weight:600; }',
    '.rf-match.direct { color:var(--rf-ok); } .rf-match.via { color:var(--rf-accent); } .rf-match.opposite { color:var(--rf-warn); } .rf-match.touches { color:var(--rf-muted); }',
    '.rf-pill { font-size:11px; font-weight:700; padding:3px 9px; border-radius:999px; background:var(--rf-card2); border:1px solid var(--rf-border); color:var(--rf-muted); white-space:nowrap; }',
    '.rf-pill.edit { background:var(--rf-accent); border-color:var(--rf-accent); color:#fff; }',
    '.rf-row { display:flex; gap:8px; flex-wrap:wrap; align-items:center; font-size:12.5px; color:var(--rf-muted); }',
    '.rf-empty { grid-column:1/-1; text-align:center; color:var(--rf-muted); border:1px dashed var(--rf-border); border-radius:14px; padding:30px 16px; line-height:1.5; }',

    '.rf-overlay { position:fixed; inset:0; background:rgba(8,12,20,.55); z-index:1000; display:flex; align-items:center; justify-content:center; padding:12px; }',
    '.rf-overlay[hidden] { display:none; }',
    '.rf-dialog { background:var(--rf-card); color:var(--rf-text); width:min(980px, 100%); max-height:94vh; border-radius:16px; border:1px solid var(--rf-border); display:flex; flex-direction:column; box-shadow:0 20px 50px rgba(0,0,0,.4); font-family:inherit; }',
    '.rf-dhead { display:flex; justify-content:space-between; gap:12px; align-items:flex-start; padding:16px 18px; border-bottom:1px solid var(--rf-border); }',
    '.rf-dhead h2 { margin:0 0 3px; font-size:18px; overflow-wrap:anywhere; }',
    '.rf-dbody { overflow-y:auto; padding:16px 18px; display:flex; flex-direction:column; gap:18px; }',
    '.rf-dfoot { display:flex; gap:10px; flex-wrap:wrap; align-items:center; justify-content:space-between; padding:12px 18px; border-top:1px solid var(--rf-border); background:var(--rf-card2); border-radius:0 0 16px 16px; }',
    '.rf-sec h3 { margin:0 0 8px; font-size:12.5px; text-transform:uppercase; letter-spacing:.6px; color:var(--rf-muted); }',
    '.rf-grid { display:grid; grid-template-columns:repeat(auto-fit, minmax(210px, 1fr)); gap:10px; }',
    '.rf-banner { padding:9px 12px; border-radius:10px; font-size:13px; font-weight:600; background:var(--rf-card2); border:1px solid var(--rf-border); }',
    '.rf-banner.warn { color:var(--rf-warn); border-color:var(--rf-warn); }',
    '.rf-banner.err { color:var(--rf-bad); border-color:var(--rf-bad); }',
    '.rf-banner.ok { color:var(--rf-ok); border-color:var(--rf-ok); }',
    '.rf-svg { width:100%; height:170px; border:1px solid var(--rf-border); border-radius:12px; background:var(--rf-card2); display:block; }',
    '.rf-tablewrap { overflow-x:auto; border:1px solid var(--rf-border); border-radius:12px; }',
    '.rf-table { width:100%; border-collapse:collapse; font-size:13px; min-width:720px; }',
    '.rf-table th { text-align:left; font-size:11.5px; text-transform:uppercase; letter-spacing:.5px; color:var(--rf-muted); padding:8px 6px; background:var(--rf-card2); position:sticky; top:0; }',
    '.rf-table td { padding:4px 4px; border-top:1px solid var(--rf-border); vertical-align:middle; }',
    '.rf-table .num { width:34px; text-align:center; color:var(--rf-muted); font-weight:700; }',
    '.rf-table .acts { white-space:nowrap; width:128px; }',
    '.rf-table .rf-input { padding:6px 8px; font-size:13px; border-radius:8px; }',
    '.rf-tools { display:flex; gap:8px; flex-wrap:wrap; margin-top:10px; }',
    '.rf-chk { display:flex; gap:8px; align-items:flex-start; font-size:13px; line-height:1.4; max-width:520px; }',
    '.rf-chk input { margin-top:3px; }',
    '.rf-toast { position:fixed; left:50%; bottom:22px; transform:translate(-50%, 20px); background:#1f2937; color:#fff; padding:10px 18px; border-radius:999px; font-size:13.5px; font-weight:600; opacity:0; pointer-events:none; transition:opacity .2s, transform .2s; z-index:1100; max-width:92vw; text-align:center; }',
    '.rf-toast.show { opacity:1; transform:translate(-50%, 0); }',
    '@media (max-width:700px) { .rf-overlay { padding:0; align-items:flex-end; } .rf-dialog { max-height:96vh; border-radius:16px 16px 0 0; } }'
  ].join('\n');

  var cssDone = false;
  function injectCss() {
    if (cssDone) return;
    var s = document.createElement('style');
    s.textContent = CSS;
    document.head.appendChild(s);
    cssDone = true;
  }

  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text !== undefined && text !== null) e.textContent = text;
    return e;
  }
  function svgEl(tag, attrs) {
    var e = document.createElementNS('http://www.w3.org/2000/svg', tag);
    Object.keys(attrs || {}).forEach(function (k) { e.setAttribute(k, attrs[k]); });
    return e;
  }
  function field(label, input) {
    var d = el('div', 'rf-field');
    d.appendChild(el('label', null, label));
    d.appendChild(input);
    return d;
  }
  function input(type, value, extra) {
    var i = el('input', 'rf-input');
    i.type = type;
    i.value = value === undefined || value === null ? '' : value;
    if (type === 'number') { i.step = 'any'; }
    Object.keys(extra || {}).forEach(function (k) { i[k] = extra[k]; });
    return i;
  }
  function splitList(text) {
    return text.split(',').map(function (x) { return x.trim(); }).filter(function (x) { return x; });
  }
  function num(v) { return (v === '' || v === null || v === undefined) ? NaN : Number(v); }
  function round3(v) { return Math.round(v * 1000) / 1000; }
  function haversineKm(a, b) {
    var R = 6371, toRad = Math.PI / 180;
    var dLat = (b.lat - a.lat) * toRad, dLon = (b.lon - a.lon) * toRad;
    var h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(a.lat * toRad) * Math.cos(b.lat * toRad) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return 2 * R * Math.asin(Math.sqrt(h));
  }

  /** Same rules as RouteFinderLogic.validateStops on the server; returns {index, message} or null. */
  function validateStops(stops) {
    if (stops.length < 2) return { index: -1, message: 'A route needs at least 2 stops.' };
    var prev = -1;
    for (var i = 0; i < stops.length; i++) {
      var s = stops[i];
      var label = 'Stop ' + (i + 1) + (s.name.trim() ? ' (' + s.name.trim() + ')' : '');
      var lat = num(s.lat), lon = num(s.lon), d = num(s.dist), sl = num(s.slack);
      if (!s.name.trim()) return { index: i, message: label + ': the name is empty.' };
      if (!isFinite(lat) || !isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180 || (lat === 0 && lon === 0)) {
        return { index: i, message: label + ': latitude / longitude is not valid.' };
      }
      if (!isFinite(d) || d < 0) return { index: i, message: label + ': the distance must be 0 or more.' };
      if (i > 0 && d < prev) return { index: i, message: label + ': the distance (' + d + ' km) is less than the previous stop\u2019s (' + prev + ' km).' };
      prev = d;
      if (!isFinite(sl) || sl < 0 || sl > 180) return { index: i, message: label + ': the halt must be between 0 and 180 minutes.' };
    }
    return null;
  }

  var MATCH_CLASS = { DIRECT: 'direct', VIA: 'via', OPPOSITE: 'opposite', TOUCHES: 'touches' };

  function mount(root, cfg) {
    injectCss();
    root.classList.add('rf');
    root.replaceChildren();

    var lastQuery = { from: '', to: '' };
    var ed = null;                 // the route open in the editor
    var overlay = null, dialogBody = null, dialogHead = null, dialogFoot = null;
    var toastEl = el('div', 'rf-toast');
    toastEl.setAttribute('role', 'status');
    document.body.appendChild(toastEl);

    function toast(text, ms) {
      toastEl.textContent = text;
      toastEl.classList.add('show');
      clearTimeout(toast.t);
      toast.t = setTimeout(function () { toastEl.classList.remove('show'); }, ms || 3500);
    }

    async function api(path, opts) {
      opts = opts || {};
      var method = opts.method || 'GET';
      for (var attempt = 0; attempt < 2; attempt++) {
        var headers = {};
        if (method !== 'GET') {
          var c = cfg.csrf();
          headers[c.header] = c.token;
          headers['Content-Type'] = 'application/json';
        }
        var r = await fetch(cfg.base + path, { method: method, headers: headers, body: opts.body, cache: 'no-store', credentials: 'same-origin' });
        if (r.status === 403 && method !== 'GET' && attempt === 0 && cfg.refreshCsrf) {
          await cfg.refreshCsrf();       // the token may simply be stale: refresh once and retry
          continue;
        }
        if (r.redirected || r.status === 401) { location.href = '/login.html'; throw new Error('Signed out'); }
        var data = null;
        try { data = await r.json(); } catch (e) { data = null; }
        if (!r.ok) {
          var err = new Error((data && data.error) || ('Request failed (' + r.status + ')'));
          err.status = r.status;
          throw err;
        }
        return data;
      }
    }

    /* ───────────── search ───────────── */
    var form = el('form', 'rf-search');
    var fromIn = input('search', '', { placeholder: 'e.g. Alipurduar', autocomplete: 'off' });
    var toIn = input('search', '', { placeholder: 'e.g. Falakata', autocomplete: 'off' });
    var swapBtn = el('button', 'rf-btn', '\u21C4');
    swapBtn.type = 'button';
    swapBtn.title = 'Swap From and To';
    swapBtn.setAttribute('aria-label', 'Swap From and To');
    var goBtn = el('button', 'rf-btn primary', 'Search');
    goBtn.type = 'submit';
    var allBtn = el('button', 'rf-btn', 'Show all');
    allBtn.type = 'button';
    form.append(field('From', fromIn), swapBtn, field('To', toIn), goBtn, allBtn);

    var hint = el('div', 'rf-hint');
    var list = el('div', 'rf-list');
    root.append(form, hint, list);

    swapBtn.addEventListener('click', function () {
      var t = fromIn.value; fromIn.value = toIn.value; toIn.value = t;
      if (fromIn.value.trim() || toIn.value.trim()) runSearch();
    });
    allBtn.addEventListener('click', function () { fromIn.value = ''; toIn.value = ''; runSearch(); });
    form.addEventListener('submit', function (e) { e.preventDefault(); runSearch(); });

    async function runSearch(quiet) {
      lastQuery = { from: fromIn.value.trim(), to: toIn.value.trim() };
      if (!quiet) hint.textContent = 'Searching\u2026';
      try {
        var q = '?from=' + encodeURIComponent(lastQuery.from) + '&to=' + encodeURIComponent(lastQuery.to);
        var hits = await api('/search' + q);
        renderResults(hits);
      } catch (e) {
        list.replaceChildren(el('div', 'rf-empty', 'Could not search: ' + e.message));
        hint.textContent = '';
      }
    }

    function describeMatch(h) {
      var f = lastQuery.from, t = lastQuery.to;
      switch (h.match) {
        case 'DIRECT': return '\u2705 Direct route ' + (h.sourceDepot || '?') + ' \u2192 ' + (h.destinationDepot || '?');
        case 'VIA': return '\u2705 Passes through both: ' + (h.fromStop || f) + ' \u2192 ' + (h.toStop || t);
        case 'OPPOSITE': return '\u21A9 Runs the other way: ' + (h.toStop || t) + ' \u2192 ' + (h.fromStop || f) +
          (f && t ? ' (this is the way back for ' + f + ' \u2192 ' + t + ')' : '');
        default:
          if (h.fromStop) return 'Serves ' + h.fromStop;
          if (h.toStop) return 'Serves ' + h.toStop;
          return '';
      }
    }

    function renderResults(hits) {
      var f = lastQuery.from, t = lastQuery.to;
      if (f && t) hint.textContent = hits.length + (hits.length === 1 ? ' route' : ' routes') + ' for ' + f + ' \u2192 ' + t;
      else if (f || t) hint.textContent = hits.length + (hits.length === 1 ? ' route' : ' routes') + ' serving ' + (f || t);
      else hint.textContent = hits.length + (hits.length === 1 ? ' route' : ' routes') + ' in total';
      list.replaceChildren();
      if (!hits.length) {
        list.append(el('div', 'rf-empty',
          (f || t) ? 'No route found for that search. Check the spelling, or try just one of the two places.' : 'No routes yet.'));
        return;
      }
      hits.forEach(function (h) {
        var card = el('div', 'rf-card');
        var top = el('div', 'rf-card-top');
        var left = el('div');
        left.append(el('div', 'rf-name', h.routeName || h.routeCode),
          el('div', 'rf-sub', (h.sourceDepot || '?') + ' \u2192 ' + (h.destinationDepot || '?') + ' \u00B7 ' + h.stopCount + ' stops \u00B7 ' + h.routeCode));
        top.append(left, el('span', h.canEdit ? 'rf-pill edit' : 'rf-pill', h.canEdit ? 'Can edit' : 'View only'));
        card.append(top);
        var m = describeMatch(h);
        if (m) card.append(el('div', 'rf-match ' + (MATCH_CLASS[h.match] || ''), m));
        var row = el('div', 'rf-row');
        row.append(el('span', null, h.busNumbers.length + (h.busNumbers.length === 1 ? ' bus' : ' buses')),
          el('span', null, '\u00B7'),
          el('span', null, h.departureTimes.length + (h.departureTimes.length === 1 ? ' departure' : ' departures')));
        if (h.ownerDepot) { row.append(el('span', null, '\u00B7'), el('span', null, 'added by depot ' + h.ownerDepot)); }
        card.append(row);
        var open = el('button', 'rf-btn small', h.canEdit ? '\u270F\uFE0F View / edit' : '\uD83D\uDC41 View');
        open.type = 'button';
        open.style.alignSelf = 'flex-start';
        open.addEventListener('click', function () { openEditor(h.routeCode, open); });
        card.append(open);
        list.append(card);
      });
    }

    /* ───────────── editor ───────────── */
    function buildOverlay() {
      overlay = el('div', 'rf-overlay');
      overlay.hidden = true;
      var dlg = el('div', 'rf-dialog');
      dlg.setAttribute('role', 'dialog');
      dlg.setAttribute('aria-modal', 'true');
      dlg.setAttribute('aria-label', 'Route details');
      dialogHead = el('div', 'rf-dhead');
      dialogBody = el('div', 'rf-dbody');
      dialogFoot = el('div', 'rf-dfoot');
      dlg.append(dialogHead, dialogBody, dialogFoot);
      overlay.appendChild(dlg);
      document.body.appendChild(overlay);
      document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && overlay && !overlay.hidden) closeEditor();
      });
    }

    function snapshot() {
      if (!ed) return '';
      return JSON.stringify([ed.stops, ed.name.value, ed.source.value, ed.dest.value, ed.buses.value, ed.times.value]);
    }
    function dirty() { return !!ed && ed.canEdit && snapshot() !== ed.initial; }

    function closeEditor(force) {
      if (!overlay || overlay.hidden) return;
      if (!force && dirty() && !confirm('You have unsaved changes. Close without saving?')) return;
      overlay.hidden = true;
      document.body.style.overflow = '';
      var back = ed && ed.trigger;
      ed = null;
      if (back && document.body.contains(back)) back.focus();
    }

    async function openEditor(code, trigger) {
      if (!overlay) buildOverlay();
      overlay.hidden = false;
      document.body.style.overflow = 'hidden';
      dialogHead.replaceChildren(el('h2', null, 'Loading\u2026'));
      dialogBody.replaceChildren();
      dialogFoot.replaceChildren();
      try {
        var d = await api('/' + encodeURIComponent(code) + '/stops');
        buildEditor(d, trigger);
      } catch (e) {
        dialogHead.replaceChildren(el('h2', null, 'Could not open the route'));
        dialogBody.replaceChildren(el('div', 'rf-banner err', e.message));
        var close = el('button', 'rf-btn', 'Close');
        close.type = 'button';
        close.addEventListener('click', function () { closeEditor(true); });
        dialogFoot.replaceChildren(close);
      }
    }

    function buildEditor(d, trigger) {
      var canEdit = !!d.canEdit;
      ed = {
        code: d.routeCode, canEdit: canEdit, trigger: trigger || (ed && ed.trigger) || null, meta: d,
        stops: d.stops.map(function (s) {
          return { name: s.stopName || '', lat: String(s.latitude), lon: String(s.longitude), dist: String(s.distanceFromStartKm), slack: String(s.slackTimeMin) };
        })
      };

      /* header */
      var title = el('div');
      title.append(el('h2', null, d.routeName || d.routeCode),
        el('div', 'rf-sub', (d.sourceDepot || '?') + ' \u2192 ' + (d.destinationDepot || '?') + ' \u00B7 ' + d.routeCode +
          (d.ownerDepot ? ' \u00B7 added by depot ' + d.ownerDepot : '')));
      var x = el('button', 'rf-btn small', '\u2715');
      x.type = 'button';
      x.setAttribute('aria-label', 'Close');
      x.addEventListener('click', function () { closeEditor(); });
      dialogHead.replaceChildren(title, x);

      var body = [];
      if (d.running) body.push(el('div', 'rf-banner warn', 'A bus is running on this route right now. Stops can be changed again after the trip ends; details can still be edited.'));
      if (!canEdit) body.push(el('div', 'rf-banner', 'View only. This route is not yours to change. Use \u201CRequest / report\u201D to ask the admin for a change.'));

      /* details */
      ed.name = input('text', d.routeName, { maxLength: 100, readOnly: !canEdit });
      ed.source = input('text', d.sourceDepot || '', { maxLength: 100, readOnly: !canEdit || !cfg.canChangeSource });
      ed.dest = input('text', d.destinationDepot || '', { maxLength: 100, readOnly: !canEdit });
      ed.buses = input('text', d.busNumbers.join(', '), { readOnly: !canEdit, placeholder: 'e.g. WB-23A-1245, WB-23A-1290' });
      ed.times = input('text', d.departureTimes.join(', '), { readOnly: !canEdit, placeholder: 'e.g. 06:00, 09:30, 14:15' });
      var secD = el('div', 'rf-sec');
      secD.appendChild(el('h3', null, 'Details'));
      var g = el('div', 'rf-grid');
      g.append(field('Route name', ed.name), field('Starts at', ed.source), field('Ends at', ed.dest),
        field('Buses (comma separated)', ed.buses), field('Departure times (comma separated)', ed.times));
      secD.appendChild(g);
      if (canEdit) {
        var saveD = el('button', 'rf-btn', 'Save details');
        saveD.type = 'button';
        saveD.style.marginTop = '10px';
        saveD.addEventListener('click', function () { saveDetails(saveD); });
        secD.appendChild(saveD);
      }
      body.push(secD);

      /* stops */
      var secS = el('div', 'rf-sec');
      secS.appendChild(el('h3', null, 'Stops (' + d.stopCount + ')'));
      ed.svg = svgEl('svg', { viewBox: '0 0 600 170', class: 'rf-svg', role: 'img', 'aria-label': 'Sketch of the route from its coordinates' });
      secS.appendChild(ed.svg);
      var wrap = el('div', 'rf-tablewrap');
      wrap.style.marginTop = '10px';
      ed.table = el('table', 'rf-table');
      wrap.appendChild(ed.table);
      secS.appendChild(wrap);
      ed.msg = el('div', 'rf-banner');
      ed.msg.hidden = true;
      ed.msg.style.marginTop = '10px';
      secS.appendChild(ed.msg);
      if (canEdit) {
        var tools = el('div', 'rf-tools');
        var add = el('button', 'rf-btn small', '\uFF0B Add stop at end');
        add.type = 'button';
        add.addEventListener('click', function () { insertStop(ed.stops.length - 1); });
        var spread = el('button', 'rf-btn small', 'Spread distances by coordinates');
        spread.type = 'button';
        spread.title = 'Keeps the last stop\u2019s distance and shares it between the stops in proportion to the straight-line gaps';
        spread.addEventListener('click', spreadDistances);
        tools.append(add, spread);
        secS.appendChild(tools);
      }
      body.push(secS);
      dialogBody.replaceChildren.apply(dialogBody, body);

      /* footer */
      var foot = [];
      var left = el('div');
      left.style.display = 'flex'; left.style.gap = '12px'; left.style.flexWrap = 'wrap'; left.style.alignItems = 'center';
      if (cfg.fileUrl) {
        var url = cfg.fileUrl(d.routeCode);
        if (url) {
          var dl = el('a', 'rf-btn small', '\u2B07 Excel');
          dl.href = url;
          dl.style.textDecoration = 'none';
          left.appendChild(dl);
        }
      }
      if (canEdit && d.opposite && d.opposite.canEdit) {
        var lab = el('label', 'rf-chk');
        ed.alsoReturn = el('input');
        ed.alsoReturn.type = 'checkbox';
        lab.append(ed.alsoReturn, el('span', null, 'Also update the return route ' + d.opposite.routeCode +
          ' (its stops and distances are rebuilt in reverse; its name, buses and timetable stay).'));
        left.appendChild(lab);
      } else if (canEdit && d.opposite) {
        left.appendChild(el('span', 'rf-sub', 'Return route ' + d.opposite.routeCode + ' is not yours to edit.'));
      }
      foot.push(left);
      var right = el('div');
      right.style.display = 'flex'; right.style.gap = '8px';
      var closeB = el('button', 'rf-btn', 'Close');
      closeB.type = 'button';
      closeB.addEventListener('click', function () { closeEditor(); });
      right.appendChild(closeB);
      if (canEdit) {
        ed.saveStopsBtn = el('button', 'rf-btn primary', 'Save stops');
        ed.saveStopsBtn.type = 'button';
        ed.saveStopsBtn.disabled = !!d.running;
        ed.saveStopsBtn.addEventListener('click', saveStops);
        right.appendChild(ed.saveStopsBtn);
      }
      foot.push(right);
      dialogFoot.replaceChildren.apply(dialogFoot, foot);

      renderTable();
      drawPreview();
      ed.initial = snapshot();
      closeBtnFocus(x);
    }
    function closeBtnFocus(btn) { try { btn.focus(); } catch (e) { /* ignore */ } }

    /* ── stops table ── */
    function renderTable() {
      var t = ed.table;
      t.replaceChildren();
      var head = el('tr');
      ['#', 'Stop name', 'Latitude', 'Longitude', 'Distance (km)', 'Halt (min)', ''].forEach(function (h) { head.appendChild(el('th', null, h)); });
      t.appendChild(el('thead')).appendChild(head);
      var tb = el('tbody');
      ed.stops.forEach(function (s, i) {
        var tr = el('tr');
        tr.appendChild(el('td', 'num', String(i + 1)));
        function cell(key, type, extra) {
          var inp = input(type, s[key], Object.assign({ readOnly: !ed.canEdit }, extra || {}));
          inp.addEventListener('input', function () { s[key] = inp.value; onStopsChanged(); });
          var td = el('td');
          td.appendChild(inp);
          tr.appendChild(td);
          return inp;
        }
        cell('name', 'text', { maxLength: 100 });
        cell('lat', 'number'); cell('lon', 'number'); cell('dist', 'number', { min: 0 });
        cell('slack', 'number', { min: 0, max: 180, step: 1 });
        var acts = el('td', 'acts');
        if (ed.canEdit) {
          function btn(label, title, fn, danger) {
            var b = el('button', 'rf-btn small' + (danger ? ' danger' : ''), label);
            b.type = 'button'; b.title = title; b.setAttribute('aria-label', title);
            b.addEventListener('click', fn);
            acts.appendChild(b);
            return b;
          }
          btn('\u2191', 'Move up', function () { moveStop(i, -1); }).disabled = i === 0;
          btn('\u2193', 'Move down', function () { moveStop(i, 1); }).disabled = i === ed.stops.length - 1;
          btn('\uFF0B', 'Insert a stop below', function () { insertStop(i); });
          btn('\u2715', 'Remove this stop', function () { removeStop(i); }, true).disabled = ed.stops.length <= 2;
        }
        tr.appendChild(acts);
        tb.appendChild(tr);
      });
      t.appendChild(tb);
      markInvalid();
    }

    function onStopsChanged() { markInvalid(); drawPreview(); }

    function markInvalid() {
      if (!ed) return;
      var rows = ed.table.querySelectorAll('tbody tr');
      rows.forEach(function (r) { r.querySelectorAll('.rf-input').forEach(function (i) { i.classList.remove('bad'); }); });
      var v = validateStops(ed.stops);
      if (v && v.index >= 0 && rows[v.index]) rows[v.index].querySelectorAll('.rf-input').forEach(function (i) { i.classList.add('bad'); });
      if (ed.canEdit && v) { ed.msg.hidden = false; ed.msg.className = 'rf-banner err'; ed.msg.textContent = v.message; }
      else { ed.msg.hidden = true; }
    }

    function moveStop(i, dir) {
      var j = i + dir;
      if (j < 0 || j >= ed.stops.length) return;
      var tmp = ed.stops[i]; ed.stops[i] = ed.stops[j]; ed.stops[j] = tmp;
      renderTable(); drawPreview();
    }
    function removeStop(i) {
      if (ed.stops.length <= 2) return;
      ed.stops.splice(i, 1);
      renderTable(); drawPreview();
    }
    function insertStop(after) {
      var a = ed.stops[after], b = ed.stops[after + 1];
      var lat = '', lon = '', dist = a ? a.dist : '0';
      if (a && b) {   // between two stops: start from their midpoint so the new row is already valid
        lat = String(Math.round((num(a.lat) + num(b.lat)) / 2 * 1e6) / 1e6);
        lon = String(Math.round((num(a.lon) + num(b.lon)) / 2 * 1e6) / 1e6);
        dist = String(round3((num(a.dist) + num(b.dist)) / 2));
      } else if (a) { lat = a.lat; lon = a.lon; }
      ed.stops.splice(after + 1, 0, { name: '', lat: lat, lon: lon, dist: dist, slack: '2' });
      renderTable(); drawPreview();
      var rows = ed.table.querySelectorAll('tbody tr');
      var focus = rows[after + 1] && rows[after + 1].querySelector('input');
      if (focus) focus.focus();
    }
    function spreadDistances() {
      var pts = ed.stops.map(function (s) { return { lat: num(s.lat), lon: num(s.lon) }; });
      if (pts.some(function (p) { return !isFinite(p.lat) || !isFinite(p.lon); })) { toast('Fill in every stop\u2019s coordinates first.'); return; }
      var total = num(ed.stops[ed.stops.length - 1].dist);
      if (!isFinite(total) || total <= 0) { toast('Set the last stop\u2019s distance first; it is kept as the trip length.'); return; }
      var cum = [0];
      for (var i = 1; i < pts.length; i++) cum.push(cum[i - 1] + haversineKm(pts[i - 1], pts[i]));
      var span = cum[cum.length - 1];
      if (span <= 0) { toast('All the stops are at the same place.'); return; }
      ed.stops.forEach(function (s, k) { s.dist = String(round3(total * cum[k] / span)); });
      renderTable(); drawPreview();
      toast('Distances spread by coordinates. Check them, then save.');
    }

    /* ── sketch of the route ── */
    function drawPreview() {
      if (!ed || !ed.svg) return;
      var svg = ed.svg;
      svg.replaceChildren();
      var pts = [];
      ed.stops.forEach(function (s, i) {
        var lat = num(s.lat), lon = num(s.lon);
        if (isFinite(lat) && isFinite(lon) && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180 && !(lat === 0 && lon === 0)) {
          pts.push({ lat: lat, lon: lon, i: i, name: s.name });
        }
      });
      if (pts.length < 2) {
        var tx = svgEl('text', { x: 300, y: 88, 'text-anchor': 'middle', fill: 'currentColor', 'font-size': 13 });
        tx.textContent = 'Enter coordinates to see the route sketch';
        svg.appendChild(tx);
        return;
      }
      var minLat = Infinity, maxLat = -Infinity, minLon = Infinity, maxLon = -Infinity;
      pts.forEach(function (p) {
        minLat = Math.min(minLat, p.lat); maxLat = Math.max(maxLat, p.lat);
        minLon = Math.min(minLon, p.lon); maxLon = Math.max(maxLon, p.lon);
      });
      var k = Math.cos(((minLat + maxLat) / 2) * Math.PI / 180);
      var w = Math.max((maxLon - minLon) * k, 1e-6), h = Math.max(maxLat - minLat, 1e-6);
      var pad = 22, W = 600 - pad * 2, H = 170 - pad * 2;
      var sc = Math.min(W / w, H / h);
      var ox = pad + (W - w * sc) / 2, oy = pad + (H - h * sc) / 2;
      pts.forEach(function (p) { p.x = ox + (p.lon - minLon) * k * sc; p.y = oy + (maxLat - p.lat) * sc; });

      var path = svgEl('polyline', {
        points: pts.map(function (p) { return p.x.toFixed(1) + ',' + p.y.toFixed(1); }).join(' '),
        fill: 'none', stroke: 'var(--rf-accent)', 'stroke-width': 2.5, 'stroke-linejoin': 'round', 'stroke-linecap': 'round'
      });
      svg.appendChild(path);
      var last = pts.length - 1;
      pts.forEach(function (p, idx) {
        var end = idx === 0 || idx === last;
        var c = svgEl('circle', { cx: p.x.toFixed(1), cy: p.y.toFixed(1), r: end ? 6 : 3.5, fill: end ? 'var(--rf-ok)' : 'var(--rf-card)', stroke: 'var(--rf-accent)', 'stroke-width': 2 });
        var title = svgEl('title');
        title.textContent = (p.i + 1) + '. ' + (p.name || '(no name)');
        c.appendChild(title);
        svg.appendChild(c);
        if (end) {
          var lab = svgEl('text', { x: (p.x + (p.x > 300 ? -10 : 10)).toFixed(1), y: (p.y - 9).toFixed(1), 'text-anchor': p.x > 300 ? 'end' : 'start', fill: 'currentColor', 'font-size': 12, 'font-weight': 700 });
          lab.textContent = p.name || String(p.i + 1);
          svg.appendChild(lab);
        }
      });
    }

    /* ── saving ── */
    async function saveStops() {
      var v = validateStops(ed.stops);
      if (v) { markInvalid(); toast(v.message, 5000); return; }
      var payload = {
        alsoReturn: !!(ed.alsoReturn && ed.alsoReturn.checked),
        stops: ed.stops.map(function (s) {
          return { stopName: s.name.trim(), latitude: num(s.lat), longitude: num(s.lon), distanceFromStartKm: num(s.dist), slackTimeMin: num(s.slack) };
        })
      };
      var btn = ed.saveStopsBtn, code = ed.code;
      btn.disabled = true;
      try {
        var res = await api('/' + encodeURIComponent(code) + '/stops', { method: 'PUT', body: JSON.stringify(payload) });
        var note = (res.message || 'Saved') + ' \u00B7 ' + res.stopCount + ' stops';
        if (res.returnRoute && res.returnRoute.note) note += ' \u00B7 ' + res.returnRoute.note;
        toast(note, res.returnRoute && !res.returnRoute.updated ? 8000 : 4000);
        await reloadEditor(code);
        runSearch(true);
      } catch (e) {
        ed.msg.hidden = false; ed.msg.className = 'rf-banner err'; ed.msg.textContent = e.message;
        btn.disabled = false;
      }
    }

    async function saveDetails(btn) {
      var code = ed.code;
      btn.disabled = true;
      try {
        await api('/' + encodeURIComponent(code) + '/details', {
          method: 'PUT',
          body: JSON.stringify({
            routeName: ed.name.value.trim(),
            sourceDepot: ed.source.value.trim(),
            destinationDepot: ed.dest.value.trim(),
            busNumbers: splitList(ed.buses.value),
            departureTimes: splitList(ed.times.value)
          })
        });
        toast('Details saved');
        await reloadEditor(code);
        runSearch(true);
      } catch (e) {
        toast(e.message, 6000);
        btn.disabled = false;
      }
    }

    async function reloadEditor(code) {
      var trigger = ed && ed.trigger;
      var d = await api('/' + encodeURIComponent(code) + '/stops');
      buildEditor(d, trigger);
    }

    runSearch(true);
    return { search: runSearch, reload: function () { return runSearch(true); } };
  }

  global.WIMBRouteFinder = { mount: mount };
})(window);
