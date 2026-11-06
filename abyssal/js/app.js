/* ═══════════════════════════════════════════════════════════════════════
   ABYSSAL — app
   Owns the one piece of state everything else derives from: depth.

   Every input (drag, wheel, keys, the ruler thumb, the dock, the dive
   button) writes to a *target* depth. A spring moves the rendered depth
   toward it, and that single value is what theme.js turns into a palette,
   what canvas.js turns into a camera, what audio.js turns into a filter
   cutoff, and what the HUD turns into numbers. One source of truth, many
   consumers — which is why all of it stays in sync without those modules
   knowing about each other.
   ═══════════════════════════════════════════════════════════════════════ */
(function () {
  "use strict";

  var D = window.ABYSSAL_DATA;
  var MAX = D.MAX_DEPTH;
  var $ = function (id) { return document.getElementById(id); };
  var root = document.documentElement;

  /* ── elements ─────────────────────────────────────────────────────── */
  var stage = $("stage");
  var vDepth = $("v-depth"), vPressure = $("v-pressure");
  var vTemp = $("v-temp"), vLight = $("v-light");
  var zonecard = $("zonecard");
  var zIndex = $("z-index"), zName = $("z-name"), zAlias = $("z-alias");
  var zRange = $("z-range"), zNote = $("z-note"), zFacts = $("z-facts");
  var rulerTrack = $("ruler-track"), rulerFill = $("ruler-fill");
  var rulerThumb = $("ruler-thumb"), rulerThumbLabel = $("ruler-thumb-label");
  var rulerTicks = $("ruler-ticks");
  var dockZones = $("dock-zones");
  var btnDive = $("btn-dive"), btnDiveLabel = $("btn-dive-label");
  var btnSurface = $("btn-surface");
  var hint = $("hint"), boot = $("boot");
  var floorcard = $("floorcard");
  var help = $("help"), helpList = $("help-list"), helpClose = $("help-close");
  var a11y = $("a11y");

  /* ── the depth spring ─────────────────────────────────────────────── */
  var depth = new Spring.Spring(0, {
    stiffness: 112, damping: 25, precision: 0.02, min: 0, max: MAX
  });

  /* ── depth ⇄ position on the rail ───────────────────────────────────
     Logarithmic, and deliberately so: a linear rail would spend 55% of its
     length on the hadal zone alone and compress the top 200 m — where
     almost everything lives — into a sliver too small to aim at. */
  var LOG_MIN = 0, LOG_MAX = Math.log10(MAX);

  function dToFrac(m) {
    var v = Math.max(1, Math.min(MAX, m));
    return (Math.log10(v) - LOG_MIN) / (LOG_MAX - LOG_MIN);
  }
  function fracToD(f) {
    f = Math.max(0, Math.min(1, f));
    return Math.pow(10, LOG_MIN + f * (LOG_MAX - LOG_MIN));
  }

  /* ── settings ─────────────────────────────────────────────────────── */
  var KEY = "abyssal.v1";
  var settings = { glow: true, snow: true, blur: true, sound: false, lastDepth: 0 };

  function loadSettings() {
    try {
      var raw = window.localStorage.getItem(KEY);
      if (!raw) return;
      var s = JSON.parse(raw);
      if (s && typeof s === "object") {
        for (var k in settings) {
          if (Object.prototype.hasOwnProperty.call(s, k) && typeof s[k] === typeof settings[k]) {
            settings[k] = s[k];
          }
        }
      }
    } catch (e) { /* private mode or corrupt JSON: the defaults are fine */ }
  }

  var saveTimer = null;
  function saveSettings() {
    /* debounced, because this is reachable from the per-frame path */
    if (saveTimer) return;
    saveTimer = setTimeout(function () {
      saveTimer = null;
      try {
        settings.lastDepth = Math.round(depth.value);
        window.localStorage.setItem(KEY, JSON.stringify(settings));
      } catch (e) {}
    }, 700);
  }

  /* ── formatting ───────────────────────────────────────────────────── */
  function group(n) {
    return String(Math.round(n)).replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  }
  function fmtDepth(m) {
    if (m < 10) return m.toFixed(1);
    if (m < 1000) return String(Math.round(m));
    return group(m);
  }
  function fmtRail(m) {
    if (m < 1000) return Math.round(m) + " m";
    return (m / 1000).toFixed(m % 1000 === 0 ? 0 : 1) + " km";
  }

  /* ── announce ─────────────────────────────────────────────────────── */
  var announceTimer = null;
  function announce(msg) {
    /* coalesce: a drag fires dozens of zone changes a second and a screen
       reader would otherwise queue all of them */
    if (announceTimer) clearTimeout(announceTimer);
    announceTimer = setTimeout(function () { a11y.textContent = msg; }, 220);
  }

  /* ── ruler ticks ──────────────────────────────────────────────────── */
  var TICKS = [
    { m: 1, label: "0 m" },
    { m: 200, label: "200 m" },
    { m: 1000, label: "1 km" },
    { m: 4000, label: "4 km" },
    { m: 6000, label: "6 km" },
    { m: MAX, label: "10,935 m" }
  ];

  function buildTicks() {
    var html = "";
    for (var i = 0; i < TICKS.length; i++) {
      html += '<li data-m="' + TICKS[i].m + '" style="top:' +
              (dToFrac(TICKS[i].m) * 100).toFixed(3) + '%"><b>' + TICKS[i].label + "</b></li>";
    }
    rulerTicks.innerHTML = html;
  }

  function markTicks(m) {
    var zi = D.zoneAt(m).index;
    var lis = rulerTicks.querySelectorAll("li");
    for (var i = 0; i < lis.length; i++) {
      /* the tick belonging to the boundary you have just passed lights up */
      var on = i === zi || i === zi + 1;
      lis[i].classList.toggle("is-active", on);
    }
  }

  /* ── dock ─────────────────────────────────────────────────────────── */
  var ROMAN = ["I", "II", "III", "IV", "V"];

  function buildDock() {
    var html = "";
    for (var i = 0; i < D.ZONES.length; i++) {
      var z = D.ZONES[i];
      html += '<button class="zone-btn" type="button" data-index="' + i + '" ' +
              'aria-pressed="false" title="' + z.name + " — " + z.alias +
              " (" + z.from + "–" + z.to + ' m)">' +
              '<span class="long">' + z.name + "</span>" +
              '<span class="rn">' + ROMAN[i] + "</span></button>";
    }
    dockZones.innerHTML = html;
    Array.prototype.forEach.call(dockZones.querySelectorAll(".zone-btn"), function (btn) {
      btn.addEventListener("click", function () {
        var z = D.ZONES[parseInt(btn.getAttribute("data-index"), 10)];
        goTo(zoneLanding(z), "dock");
      });
    });
  }

  /* land a little inside a zone rather than exactly on its boundary, so
     the card and the water both read as "you are in it" */
  function zoneLanding(z) {
    return z.from + Math.min(80, (z.to - z.from) * 0.14);
  }

  function markDock(zi) {
    var btns = dockZones.querySelectorAll(".zone-btn");
    for (var i = 0; i < btns.length; i++) {
      var on = i === zi;
      btns[i].classList.toggle("is-active", on);
      btns[i].setAttribute("aria-pressed", on ? "true" : "false");
    }
  }

  /* ── navigation ───────────────────────────────────────────────────── */
  function goTo(m, why) {
    cancelDive();
    depth.to(Math.max(0, Math.min(MAX, m)));
    hideHint();
    saveSettings();
    if (why) announce("Moving to " + group(Math.max(0, Math.min(MAX, m))) + " metres.");
  }

  /* ── zone card ────────────────────────────────────────────────────── */
  var lastZoneId = null;
  var swapTimer = null;

  function updateZone() {
    var info = D.zoneAt(depth.value);
    var z = info.zone;
    if (z.id === lastZoneId) return;
    lastZoneId = z.id;

    /* restart the crossfade by removing the class, forcing a reflow, then
       re-adding it — otherwise a second change mid-animation does nothing */
    zonecard.classList.remove("is-swapping");
    void zonecard.offsetWidth;
    zonecard.classList.add("is-swapping");
    if (swapTimer) clearTimeout(swapTimer);
    swapTimer = setTimeout(function () { zonecard.classList.remove("is-swapping"); }, 620);

    zIndex.textContent = (info.index + 1 < 10 ? "0" : "") + (info.index + 1);
    zName.textContent = z.name;
    zAlias.textContent = z.alias;
    zRange.textContent = z.from + " – " + group(z.to) + " m";
    zNote.textContent = z.note;

    var html = "";
    for (var i = 0; i < z.facts.length; i++) {
      html += '<span class="fact">' + z.facts[i][0] + " <b>" + z.facts[i][1] + "</b></span>";
    }
    zFacts.innerHTML = html;

    markDock(info.index);
    markTicks(depth.value);
    announce(z.name + " zone. " + z.alias + ". " + z.from + " to " + group(z.to) + " metres.");
  }

  /* ── HUD ──────────────────────────────────────────────────────────── */
  var lastText = {};
  function setText(el, key, value) {
    /* writing textContent every frame dirties layout for no reason */
    if (lastText[key] === value) return;
    lastText[key] = value;
    el.textContent = value;
  }

  function updateHud() {
    var d = depth.value;
    setText(vDepth, "d", fmtDepth(d));
    setText(vPressure, "p", D.pressureAtm(d) < 100
      ? D.pressureAtm(d).toFixed(1) : group(D.pressureAtm(d)));
    setText(vTemp, "t", D.temperatureAt(d).toFixed(1));
    setText(vLight, "l", D.lightLabel(d));

    var f = dToFrac(Math.max(1, d));
    rulerFill.style.height = (f * 100).toFixed(3) + "%";
    rulerThumb.style.top = (f * 100).toFixed(3) + "%";
    var railTxt = fmtRail(d);
    if (lastText.rail !== railTxt) {
      lastText.rail = railTxt;
      rulerThumbLabel.textContent = railTxt;
    }
    rulerThumb.setAttribute("aria-valuenow", Math.round(d));
    rulerThumb.setAttribute("aria-valuetext",
      group(d) + " metres, " + D.zoneAt(d).zone.name + " zone");

    Sheet.updateDepth(d);
  }

  /* ── dragging the water column ──────────────────────────────────────
     Dragging up sinks you, and the mapping is in SCREEN space: the water
     moves with your finger at 1:1 no matter how deep you are. That matters
     because the visible span of the column grows with depth, so a constant
     metres-per-pixel would feel completely different at the surface than
     it does in a trench. */
  function metersPerPixel() {
    return Ocean.visibleSpan().span / Math.max(1, window.innerHeight);
  }

  var drag = { on: false, startY: 0, lastY: 0, lastT: 0, vel: 0, moved: 0, t0: 0, id: null };

  function isUi(target) {
    return target.closest &&
      target.closest("#hud,#dock,#sheet,#scrim,#ruler,#zonecard,#boot,#help,#floorcard,#hint");
  }

  function onPointerDown(e) {
    if (isUi(e.target)) return;
    if (e.button != null && e.button !== 0) return;
    drag.on = true;
    drag.id = e.pointerId;
    drag.startY = drag.lastY = e.clientY;
    drag.lastT = drag.t0 = performance.now();
    drag.vel = 0; drag.moved = 0;
    document.body.classList.add("is-dragging");
    cancelDive();
    hideHint();
  }

  function onPointerMove(e) {
    if (!drag.on) {
      /* specimen hover, pointer devices only */
      if (e.pointerType === "mouse") {
        var hid = Ocean.hitTest(e.clientX, e.clientY);
        Ocean.setHover(hid);
        stage.style.cursor = hid ? "pointer" : "";
      }
      return;
    }
    var y = e.clientY;
    var dy = y - drag.lastY;
    drag.moved += Math.abs(dy);

    /* dragging DOWN moves the camera UP, i.e. shallower */
    depth.nudge(-dy * metersPerPixel());

    var now = performance.now();
    var dt = (now - drag.lastT) / 1000;
    if (dt > 0.001) {
      var v = (-dy * metersPerPixel()) / dt;
      drag.vel = drag.vel * 0.72 + v * 0.28;
      drag.lastT = now;
    }
    drag.lastY = y;
    if (e.cancelable) e.preventDefault();
  }

  function onPointerUp(e) {
    if (!drag.on) return;
    /* a tap that did not travel is a specimen pick, not a drag */
    if (drag.moved < 7 && performance.now() - drag.t0 < 650) {
      var id = Ocean.hitTest(e.clientX, e.clientY);
      if (id) { openSpecimen(id); }
    } else {
      /* carry the flick: release velocity becomes spring velocity, so the
         descent coasts and settles instead of stopping dead */
      var mpp = metersPerPixel();
      var limit = 5200 * mpp;
      depth.velocity = Spring.clamp(drag.vel, -limit, limit);
      /* a decisive flick also pushes the target onward — that is what makes
         a throw travel further than the drag itself did */
      if (Math.abs(drag.vel) > 90) depth.to(depth.target + drag.vel * 0.3);
    }
    drag.on = false;
    document.body.classList.remove("is-dragging");
    saveSettings();
  }

  stage.addEventListener("pointerdown", onPointerDown);
  window.addEventListener("pointermove", onPointerMove, { passive: false });
  window.addEventListener("pointerup", onPointerUp);
  window.addEventListener("pointercancel", onPointerUp);

  /* ── wheel ────────────────────────────────────────────────────────── */
  var wheelTimer = null;
  stage.addEventListener("wheel", function (e) {
    if (isUi(e.target)) return;
    e.preventDefault();
    cancelDive();
    hideHint();
    /* a trackpad sends many tiny deltas, a mouse wheel few large ones;
       sqrt-normalising keeps both controllable at the same rate */
    var mag = Math.sign(e.deltaY) * Math.sqrt(Math.abs(e.deltaY));
    var step = mag * metersPerPixel() * 15;
    depth.nudge(step);
    depth.to(depth.target + step * 0.3);
    if (wheelTimer) clearTimeout(wheelTimer);
    wheelTimer = setTimeout(saveSettings, 200);
  }, { passive: false });

  /* ── ruler ────────────────────────────────────────────────────────── */
  function rulerSet(clientY) {
    var rect = rulerTrack.getBoundingClientRect();
    if (rect.height <= 0) return;
    var f = (clientY - rect.top) / rect.height;
    cancelDive();
    depth.to(fracToD(Math.max(0, Math.min(1, f))));
    hideHint();
  }

  var rulerDrag = false;
  rulerTrack.addEventListener("pointerdown", function (e) {
    rulerDrag = true;
    document.body.classList.add("is-dragging");
    try { rulerTrack.setPointerCapture(e.pointerId); } catch (err) {}
    rulerSet(e.clientY);
    e.preventDefault();
    e.stopPropagation();
  });
  rulerTrack.addEventListener("pointermove", function (e) {
    if (rulerDrag) { rulerSet(e.clientY); e.preventDefault(); }
  });
  function rulerEnd(e) {
    if (!rulerDrag) return;
    rulerDrag = false;
    document.body.classList.remove("is-dragging");
    try { rulerTrack.releasePointerCapture(e.pointerId); } catch (err) {}
    saveSettings();
  }
  rulerTrack.addEventListener("pointerup", rulerEnd);
  rulerTrack.addEventListener("pointercancel", rulerEnd);

  /* the thumb is a real slider: focusable, arrow-key operable, and it
     reports its value in words as well as numbers */
  rulerThumb.addEventListener("keydown", function (e) {
    var f = dToFrac(Math.max(1, depth.target));
    var step = e.shiftKey ? 0.055 : 0.014;
    var k = e.key;
    if (k === "ArrowDown" || k === "ArrowRight") depth.to(fracToD(Math.min(1, f + step)));
    else if (k === "ArrowUp" || k === "ArrowLeft") depth.to(fracToD(Math.max(0, f - step)));
    else if (k === "PageDown") depth.to(fracToD(Math.min(1, f + 0.16)));
    else if (k === "PageUp") depth.to(fracToD(Math.max(0, f - 0.16)));
    else if (k === "Home") depth.to(0);
    else if (k === "End") depth.to(MAX);
    else return;
    cancelDive();
    hideHint();
    saveSettings();
    e.preventDefault();
    e.stopPropagation();
  });

  /* ── the guided descent ─────────────────────────────────────────────
     A slow automatic run to the bottom, so the whole thing can be watched
     as a piece rather than driven. Any input cancels it immediately. */
  var dive = { on: false, t0: 0, from: 0, dur: 1 };

  function startDive() {
    var from = depth.value;
    var dist = MAX - from;
    if (dist < 40) {
      /* already at the floor: run it from the surface instead */
      depth.set(0);
      from = 0;
      dist = MAX;
    }
    dive.on = true;
    dive.from = from;
    dive.t0 = performance.now();
    /* long enough to actually see the zones change, scaled by distance */
    dive.dur = 9000 + (dist / MAX) * 26000;
    btnDive.classList.add("is-live");
    btnDiveLabel.textContent = "Descending…";
    hideHint();
  }

  function cancelDive() {
    if (!dive.on) return;
    dive.on = false;
    btnDive.classList.remove("is-live");
    btnDiveLabel.textContent = "Full descent";
  }

  function stepDive(now) {
    if (!dive.on) return;
    var t = (now - dive.t0) / dive.dur;
    if (t >= 1) {
      depth.to(MAX);
      cancelDive();
      return;
    }
    /* ease in and out in DEPTH space, not fraction space: the rail is
       logarithmic, so easing the fraction would rush the shallow zones */
    var e = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
    depth.to(dive.from + (MAX - dive.from) * e);
  }

  btnDive.addEventListener("click", function () {
    if (dive.on) cancelDive(); else startDive();
  });
  btnSurface.addEventListener("click", function () { goTo(0, "surface"); });

  /* ── keyboard ─────────────────────────────────────────────────────── */
  var CONTROLS = [
    ["Drag the water", "descend and ascend; flick to coast"],
    ["Scroll wheel", "descend, with a little extra throw"],
    ["↑ ↓ ← →", "move by a fraction of the visible column"],
    ["Shift + arrow", "move further"],
    ["Page Up / Down", "jump several hundred metres"],
    ["Home / End", "surface, or the Challenger Deep"],
    ["1 – 5", "jump straight to a zone"],
    ["D", "start or stop the guided descent"],
    ["G / S / M", "bioluminescence / marine snow / sound"],
    ["B", "glass blur, for slower devices"],
    ["?", "show or hide this panel"],
    ["Esc", "close the specimen panel"],
    ["Tap an animal", "open its specimen card"]
  ];

  function buildHelp() {
    var html = "";
    for (var i = 0; i < CONTROLS.length; i++) {
      html += "<div><dt>" + CONTROLS[i][0] + "</dt><dd>" + CONTROLS[i][1] + "</dd></div>";
    }
    helpList.innerHTML = html;
  }

  var helpOn = false;
  function setHelp(on) {
    helpOn = on;
    help.hidden = false;
    /* a frame between unhiding and adding the class, or the transition
       has no starting state to animate from */
    requestAnimationFrame(function () { help.classList.toggle("is-on", on); });
    $("btn-help").setAttribute("aria-pressed", on ? "true" : "false");
    $("btn-help").setAttribute("aria-expanded", on ? "true" : "false");
    if (on) setTimeout(function () { helpClose.focus({ preventScroll: true }); }, 120);
  }

  $("btn-help").addEventListener("click", function () { setHelp(!helpOn); });
  helpClose.addEventListener("click", function () { setHelp(false); });

  window.addEventListener("keydown", function (e) {
    if (e.metaKey || e.ctrlKey || e.altKey) return;
    var tag = e.target.tagName;
    if (tag === "INPUT" || tag === "TEXTAREA" || e.target.isContentEditable) return;
    /* let the sheet and the slider handle their own keys */
    if (e.target.closest && e.target.closest("#sheet,#ruler-thumb")) return;

    var k = e.key;
    var span = Ocean.visibleSpan().span;

    if (k >= "1" && k <= "5") {
      goTo(zoneLanding(D.ZONES[parseInt(k, 10) - 1]), "zone");
      e.preventDefault(); return;
    }
    switch (k) {
      case "ArrowDown": case "ArrowRight":
        cancelDive(); depth.nudge(span * (e.shiftKey ? 0.5 : 0.15)); hideHint(); e.preventDefault(); break;
      case "ArrowUp": case "ArrowLeft":
        cancelDive(); depth.nudge(-span * (e.shiftKey ? 0.5 : 0.15)); hideHint(); e.preventDefault(); break;
      case "PageDown":
        goTo(depth.target + span * 2.4); e.preventDefault(); break;
      case "PageUp":
        goTo(depth.target - span * 2.4); e.preventDefault(); break;
      case "Home": goTo(0); e.preventDefault(); break;
      case "End": goTo(MAX); e.preventDefault(); break;
      case "Escape":
        if (Sheet.isOpen()) Sheet.close();
        else if (helpOn) setHelp(false);
        break;
      case "?": setHelp(!helpOn); e.preventDefault(); break;
      default:
        var c = k.toLowerCase();
        if (c === "g") toggleSetting("glow", "btn-glow");
        else if (c === "s") toggleSetting("snow", "btn-snow");
        else if (c === "m") toggleSetting("sound", "btn-sound");
        else if (c === "b") toggleSetting("blur", "btn-blur");
        else if (c === "d") { if (dive.on) cancelDive(); else startDive(); }
        else return;
        e.preventDefault();
    }
    saveSettings();
  });

  /* ── toggles ──────────────────────────────────────────────────────── */
  function toggleSetting(key, btnId) {
    settings[key] = !settings[key];
    applySettings();
    saveSettings();
  }

  function applySettings() {
    Ocean.setGlow(!!settings.glow);
    Ocean.setSnow(!!settings.snow);
    root.setAttribute("data-blur", settings.blur ? "on" : "off");

    /* audio requires a user gesture, and a click on this button is one */
    if (settings.sound) settings.sound = Ambience.setEnabled(true);
    else Ambience.setEnabled(false);

    press("btn-glow", settings.glow, settings.glow ? "Bioluminescence on" : "Bioluminescence off");
    press("btn-snow", settings.snow, settings.snow ? "Marine snow on" : "Marine snow off");
    press("btn-blur", settings.blur, settings.blur ? "Glass blur on" : "Glass blur off (better performance)");
    press("btn-sound", settings.sound, settings.sound ? "Ambient sound on" : "Ambient sound off");
  }

  function press(id, on, title) {
    var b = $(id);
    if (!b) return;
    b.setAttribute("aria-pressed", on ? "true" : "false");
    if (title) b.title = title;
  }

  ["glow", "snow", "blur", "sound"].forEach(function (key) {
    var id = "btn-" + key;
    var b = $(id);
    if (b) b.addEventListener("click", function () { toggleSetting(key, id); hideHint(); });
  });

  /* ── specimens ────────────────────────────────────────────────────── */
  function openSpecimen(id) {
    var c = null;
    for (var i = 0; i < D.CREATURES.length; i++) {
      if (D.CREATURES[i].id === id) { c = D.CREATURES[i]; break; }
    }
    if (!c) return;
    Sheet.openFor(c, Sheet.HALF, depth.value);
    announce(c.name + ", " + c.latin + ". Lives from " + c.from + " to " + c.to + " metres.");
  }

  Sheet.onCentre(function (c) {
    var mid = (c.from + Math.min(c.to, MAX)) / 2;
    goTo(mid);
    announce("Centred on " + c.name + " at " + group(mid) + " metres.");
  });

  /* clicking the water away from an animal dismisses the panel */
  stage.addEventListener("click", function (e) {
    if (isUi(e.target)) return;
    if (Sheet.isOpen()) Sheet.close();
  });

  /* ── the floor of the ocean ───────────────────────────────────────── */
  var floorShown = false;
  function checkFloor() {
    var at = depth.value > MAX - 2;
    if (at && !floorShown) {
      floorShown = true;
      floorcard.classList.add("is-on");
      announce("Challenger Deep. 10,935 metres. The floor of the ocean.");
    } else if (!at && floorShown && depth.value < MAX - 120) {
      floorShown = false;
      floorcard.classList.remove("is-on");
    }
  }

  /* ── hint ─────────────────────────────────────────────────────────── */
  var hintGone = false;
  function hideHint() {
    if (hintGone) return;
    hintGone = true;
    hint.classList.add("is-gone");
    setTimeout(function () { hint.style.display = "none"; }, 800);
  }

  /* ── sheet geometry ───────────────────────────────────────────────── */
  function sizeSheet() {
    /* one place decides how tall the panel is; sheet.js reads the resolved
       height back, so the two can never disagree */
    var h = Math.round(Math.min(window.innerHeight * 0.86, 700));
    root.style.setProperty("--sheet-h", h + "px");
  }

  /* ── main loop ────────────────────────────────────────────────────── */
  var last = 0;
  function loop(now) {
    var dt = last ? Math.min(0.05, (now - last) / 1000) : 0.016;
    last = now;

    stepDive(now);
    depth.step(dt);
    Ocean.setDepth(depth.value);
    Ambience.update(depth.value);
    updateHud();
    updateZone();
    checkFloor();

    requestAnimationFrame(loop);
  }

  /* ── boot ─────────────────────────────────────────────────────────── */
  function dismissBoot() {
    if (boot.classList.contains("is-done")) return;
    boot.classList.add("is-done");
    boot.setAttribute("aria-hidden", "true");
    setTimeout(function () { boot.style.display = "none"; }, 900);
    /* the opening move: drift down from the surface, so the first thing
       you see is the water changing rather than a static frame */
    setTimeout(function () { goTo(38); }, 500);
  }

  boot.addEventListener("pointerdown", dismissBoot);
  window.addEventListener("keydown", function bootKey(e) {
    if (boot.classList.contains("is-done")) {
      window.removeEventListener("keydown", bootKey, true);
      return;
    }
    if (e.key === "Enter" || e.key === " ") { dismissBoot(); e.preventDefault(); }
  }, true);

  /* ── init ─────────────────────────────────────────────────────────── */
  function init() {
    loadSettings();
    buildTicks();
    buildDock();
    buildHelp();
    sizeSheet();

    Ocean.start();

    /* resume where you left off, if there is somewhere to go */
    if (settings.lastDepth > 8) {
      depth.set(settings.lastDepth);
      lastZoneId = null;
    }

    applySettings();
    updateHud();
    updateZone();
    requestAnimationFrame(loop);

    window.addEventListener("resize", function () { sizeSheet(); }, { passive: true });
    /* a backgrounded tab must not return with one enormous dt */
    document.addEventListener("visibilitychange", function () { last = 0; });

    /* the intro is a moment, not a gate */
    setTimeout(dismissBoot, 2600);
    setTimeout(hideHint, 15000);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
