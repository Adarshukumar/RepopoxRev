/* ═══════════════════════════════════════════════════════════════════════
   ABYSSAL — sheet
   The specimen panel. Drag the grab handle or anywhere on the sheet and it
   follows your finger; let go and it springs to the nearest stop instead of
   snapping there.

   This is the component most directly modelled on Mochy's
   BottomSheetPlayer, and the parts worth carrying over are:

     • input moves a TARGET, a spring moves the rendered value — so the
       sheet has apparent mass and can settle with a slight overshoot;
     • a flick is judged by VELOCITY, not by distance travelled, so a short
       fast drag still throws it to the next stop;
     • overscroll past the ends is resisted rather than blocked;
     • no CSS transition while dragging (that would fight the spring), and
       the spring is what animates the settle.

   Geometry: the sheet has one fixed height (--sheet-h) and the stops are
   fractions of it revealed, so the spring only ever animates a translateY.
   Animating height as well would make the settle feel rubbery.
   ═══════════════════════════════════════════════════════════════════════ */
window.Sheet = (function () {
  "use strict";

  var D = window.ABYSSAL_DATA;

  var el = document.getElementById("sheet");
  var scrim = document.getElementById("scrim");
  var grab = document.getElementById("sheet-grab");
  var body = document.getElementById("sheet-body");

  /* revealed fraction of the sheet: 0 hidden, 1 fully up */
  var STOPS = [0, 0.55, 1];
  var CLOSED = 0, HALF = 1, FULL = 2;

  var spring = new Spring.Spring(0, { stiffness: 205, damping: 30, precision: 0.0009 });

  var stopIndex = CLOSED;
  var open = false;
  var creature = null;
  var onSettleFn = null;

  var dragging = false;
  var pointerId = null;
  var startY = 0, startVal = 0;
  var lastY = 0, lastT = 0, velocity = 0, moved = 0;
  var rafId = null;

  /* ── geometry ─────────────────────────────────────────────────────── */

  function sheetPx() {
    /* read the resolved height rather than duplicating the CSS rule here,
       so a media-query change cannot desync the two */
    var h = el.offsetHeight;
    if (h > 0) return h;
    var v = getComputedStyle(document.documentElement).getPropertyValue("--sheet-h");
    var n = parseFloat(v);
    return isFinite(n) && n > 0 ? n : Math.round(window.innerHeight * 0.84);
  }

  function apply() {
    var revealed = spring.value;                 // 0..1
    var ty = (1 - revealed) * 100;
    el.style.transform = "translate(-50%," + ty.toFixed(3) + "%)";
    var a = Math.max(0, Math.min(1, (revealed - 0.06) / 0.5));
    scrim.style.opacity = (a * 0.9).toFixed(3);
    var vis = revealed > 0.02;
    scrim.classList.toggle("is-on", vis);
    scrim.hidden = !vis;
    el.classList.toggle("is-open", vis);
    el.setAttribute("aria-hidden", vis ? "false" : "true");
    document.body.classList.toggle("sheet-open", vis);
    /* keep the panel out of the tab order while it is down */
    body.querySelectorAll("button,[href],[tabindex]").forEach(function (n) {
      n.tabIndex = vis ? 0 : -1;
    });
  }

  function tick() {
    spring.step(1 / 60);
    apply();
    if (!dragging && spring.isSettled()) { rafId = null; return; }
    rafId = requestAnimationFrame(tick);
  }

  function animate() { if (rafId == null) rafId = requestAnimationFrame(tick); }

  function setStop(i, immediate) {
    stopIndex = Math.max(0, Math.min(STOPS.length - 1, i));
    spring.to(STOPS[stopIndex]);
    if (immediate) spring.set(STOPS[stopIndex]);
    animate();
    var nowOpen = stopIndex > CLOSED;
    if (nowOpen !== open) {
      open = nowOpen;
      if (open && creature) {
        /* focus the panel once it is actually on screen, not before */
        setTimeout(function () { if (open) grab.focus({ preventScroll: true }); }, 260);
      }
    }
    if (typeof onSettleFn === "function") onSettleFn(open, creature);
  }

  /* ── content ──────────────────────────────────────────────────────── */

  /* The source data is written with **bold** and *italic* so the copy reads
     naturally in the file; a tiny pass turns it into markup. Everything is
     escaped first — the data is ours, but this keeps innerHTML honest. */
  function rich(text) {
    return String(text)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
      .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
      .replace(/\*([^*]+)\*/g, "<em>$1</em>")
      .replace(/\n\n+/g, "</p><p>");
  }

  function fmt(m) {
    m = Math.min(m, D.MAX_DEPTH);
    return m >= 1000
      ? (m / 1000).toFixed(m % 1000 === 0 ? 0 : 1) + " km"
      : Math.round(m) + " m";
  }

  /* same log mapping the ruler uses, so the two never disagree about where
     a given depth sits — a linear band would make every shallow animal a
     sliver and every deep one the whole track */
  var LOG_MIN = 0, LOG_MAX = Math.log10(D.MAX_DEPTH);
  function bandFrac(m) {
    var v = Math.max(1, m);
    return (Math.log10(v) - LOG_MIN) / (LOG_MAX - LOG_MIN);
  }

  function render(c, currentDepth) {
    creature = c;
    var colour = c.glow || D.zoneAt((c.from + Math.min(c.to, D.MAX_DEPTH)) / 2).zone.accent;
    el.style.setProperty("--spec-c", colour);

    var lo = bandFrac(Math.max(1, c.from));
    var hi = bandFrac(c.to);
    /* a minimum width so a narrow range is still visible and clickable */
    var w = Math.max(0.035, hi - lo);
    var you = currentDepth != null ? bandFrac(Math.max(1, currentDepth)) : null;
    var youIn = you != null && you >= lo - 0.02 && you <= hi + 0.02;

    var statsHtml = "";
    for (var i = 0; i < c.stats.length; i++) {
      statsHtml += '<div class="spec-stat"><span>' + rich(c.stats[i][0]) +
                   "</span><b>" + rich(c.stats[i][1]) + "</b></div>";
    }

    body.innerHTML =
      '<div class="spec-stagger">' +

        '<div class="spec-head">' +
          '<div class="spec-orb" aria-hidden="true"><i></i></div>' +
          '<div class="spec-title">' +
            "<h2>" + rich(c.name) + "</h2>" +
            '<p class="spec-latin">' + rich(c.latin) + "</p>" +
            '<p class="spec-depthline">' + fmt(c.from) + " – " + fmt(c.to) +
              " &middot; " + (c.glow ? "bioluminescent" : "no light of its own") + "</p>" +
          "</div>" +
        "</div>" +

        '<div class="spec-band">' +
          '<div class="spec-band-label"><span>Surface</span><span>Where it lives</span>' +
            "<span>" + fmt(D.MAX_DEPTH) + "</span></div>" +
          '<div class="spec-band-track">' +
            '<div class="spec-band-range" style="left:' + (lo * 100).toFixed(3) +
              "%;width:" + (w * 100).toFixed(3) + '%"></div>' +
            (you != null
              ? '<div class="spec-band-you" style="left:' + (you * 100).toFixed(3) +
                '%"' + (youIn ? "" : ' title="outside this animal\'s range"') + "></div>"
              : "") +
          "</div>" +
        "</div>" +

        '<p class="spec-note">' + rich(c.note) + "</p>" +

        '<div class="spec-stats">' + statsHtml + "</div>" +

        '<div class="spec-actions">' +
          '<button class="pill" type="button" data-act="centre">Centre on it</button>' +
          '<button class="pill ghost" type="button" data-act="close">Close</button>' +
        "</div>" +

      "</div>";

    body.scrollTop = 0;
    apply();

    body.querySelector('[data-act="close"]').addEventListener("click", close);
    body.querySelector('[data-act="centre"]').addEventListener("click", function () {
      if (typeof onCentre === "function" && creature) onCentre(creature);
    });
  }

  var onCentre = null;

  /* ── pointer handling ─────────────────────────────────────────────── */

  function onDown(e) {
    if (e.target.closest && e.target.closest("[data-act]")) return;
    /* when the sheet is down, only the grab handle is a drag target —
       otherwise a tap on the water would be swallowed by the panel */
    if (!open && !(grab === e.target || grab.contains(e.target))) return;
    if (e.button != null && e.button !== 0) return;

    dragging = true;
    moved = 0;
    pointerId = e.pointerId;
    startY = lastY = e.clientY;
    startVal = spring.value;
    lastT = performance.now();
    velocity = 0;
    document.body.classList.add("is-dragging");
    if (el.setPointerCapture && e.pointerId != null) {
      try { el.setPointerCapture(e.pointerId); } catch (err) {}
    }
    e.preventDefault();
  }

  function onMove(e) {
    if (!dragging) return;
    var y = e.clientY;
    var dy = y - startY;
    moved = Math.max(moved, Math.abs(dy));

    var h = sheetPx();
    /* dragging DOWN reveals less; convert pixels to the spring's units */
    var next = startVal - dy / h;

    /* resist overscroll past both ends: it still moves, at a third of the
       rate, so you feel the boundary without hitting a wall */
    if (next > STOPS[FULL]) next = STOPS[FULL] + (next - STOPS[FULL]) * 0.28;
    if (next < STOPS[CLOSED]) next = STOPS[CLOSED] + (next - STOPS[CLOSED]) * 0.28;

    var now = performance.now();
    var dt = (now - lastT) / 1000;
    if (dt > 0.0008) {
      var v = ((y - lastY) / h) / dt;
      velocity = velocity * 0.7 + -v * 0.3;
      lastT = now;
      lastY = y;
    }

    spring.value = next;
    spring.target = next;
    apply();
    animate();
    if (e.cancelable) e.preventDefault();
  }

  function onUp() {
    if (!dragging) return;
    dragging = false;
    document.body.classList.remove("is-dragging");
    if (el.releasePointerCapture && pointerId != null) {
      try { el.releasePointerCapture(pointerId); } catch (err) {}
    }
    pointerId = null;

    /* Velocity wins when the flick is decisive; otherwise go to whichever
       stop is nearest. Both thresholds were tuned by feel. */
    var FLICK = 1.05;
    var target;
    if (velocity > FLICK) target = Math.min(STOPS.length - 1, stopIndex + 1);
    else if (velocity < -FLICK) target = Math.max(0, stopIndex - 1);
    else {
      var best = 0, bestD = Infinity;
      for (var i = 0; i < STOPS.length; i++) {
        var d = Math.abs(STOPS[i] - spring.value);
        if (d < bestD) { bestD = d; best = i; }
      }
      target = best;
    }
    /* carry the flick into the spring so the throw looks continuous */
    spring.velocity = velocity * 2.6;
    setStop(target);
  }

  el.addEventListener("pointerdown", onDown);
  window.addEventListener("pointermove", onMove, { passive: false });
  window.addEventListener("pointerup", onUp);
  window.addEventListener("pointercancel", onUp);
  scrim.addEventListener("pointerdown", close);

  /* ── keyboard ─────────────────────────────────────────────────────── */
  el.addEventListener("keydown", function (e) {
    if (e.key === "Escape" && open) { close(); e.preventDefault(); return; }
    if (document.activeElement !== grab) return;
    if (e.key === "ArrowUp" || e.key === "PageUp") { setStop(stopIndex + 1); e.preventDefault(); }
    else if (e.key === "ArrowDown" || e.key === "PageDown") { setStop(stopIndex - 1); e.preventDefault(); }
    else if (e.key === "Home") { setStop(CLOSED); e.preventDefault(); }
    else if (e.key === "End") { setStop(FULL); e.preventDefault(); }
    else if (e.key === "Enter" || e.key === " ") { setStop(open ? CLOSED : HALF); e.preventDefault(); }
  });

  grab.setAttribute("role", "button");
  grab.setAttribute("tabindex", "0");
  grab.setAttribute("aria-label", "Specimen panel. Drag, or press arrow keys to resize.");
  grab.setAttribute("aria-expanded", "false");

  window.addEventListener("resize", function () { apply(); });

  /* ── public API ───────────────────────────────────────────────────── */
  function openFor(c, stop, currentDepth) {
    render(c, currentDepth);
    setStop(stop == null ? HALF : stop);
    grab.setAttribute("aria-expanded", "true");
  }

  function close() {
    setStop(CLOSED);
    grab.setAttribute("aria-expanded", "false");
  }

  /* re-draw the "you are here" marker as the viewer keeps descending */
  function updateDepth(d) {
    if (!creature || !open) return;
    var marker = body.querySelector(".spec-band-you");
    if (!marker) return;
    var you = bandFrac(Math.max(1, d));
    marker.style.left = (you * 100).toFixed(3) + "%";
  }

  return {
    openFor: openFor,
    close: close,
    isOpen: function () { return open; },
    creature: function () { return creature; },
    updateDepth: updateDepth,
    setStop: setStop,
    onSettle: function (fn) { onSettleFn = fn; },
    onCentre: function (fn) { onCentre = fn; },
    HALF: HALF,
    FULL: FULL,
    CLOSED: CLOSED
  };
})();
