/* ═══════════════════════════════════════════════════════════════════════
   ABYSSAL — canvas
   Draws the water column: the surface, light shafts, marine snow,
   bioluminescent motes, and the specimens themselves.

   Camera model
   ------------
   `depth` is where the viewer is. A specimen at world depth `d` is on
   screen at  y = (d - depth + span/2) / span * height, where `span` is
   how many metres of column the viewport currently shows. Span grows
   with depth (sqrt, not linear) so the surface frames tens of metres and
   the hadal zone frames hundreds — otherwise the deep zones would be
   empty or a wall of overlapping animals.

   Performance
   -----------
   Glows are the expensive part: `shadowBlur` per draw call is very slow.
   Instead each glow colour is pre-rendered once into a small offscreen
   canvas and stamped with drawImage, which is an order of magnitude
   cheaper and lets a few hundred glints run at 60 fps on a phone.
   Particle budgets scale with viewport area and device pixel ratio is
   capped at 2.
   ═══════════════════════════════════════════════════════════════════════ */
window.Ocean = (function () {
  "use strict";

  var D = window.ABYSSAL_DATA;
  var T = window.Theme;
  var TAU = Math.PI * 2;

  var cv = document.getElementById("water");
  var ctx = cv.getContext("2d", { alpha: false });

  var W = 0, H = 0, dpr = 1;
  var reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* ── state ────────────────────────────────────────────────────────── */
  var state = {
    depth: 0,
    glowOn: true,
    snowOn: true,
    time: 0,
    palette: { top: [0, 0, 0], bottom: [0, 0, 0], accent: [150, 230, 255], glow: [120, 230, 255] },
    hoverId: null
  };

  /* ── glow sprite cache ────────────────────────────────────────────── */
  var SPRITE = 64;
  var spriteCache = Object.create(null);

  function glowSprite(rgb) {
    var key = rgb[0] + "," + rgb[1] + "," + rgb[2];
    var hit = spriteCache[key];
    if (hit) return hit;
    var c = document.createElement("canvas");
    c.width = c.height = SPRITE;
    var g = c.getContext("2d");
    var grd = g.createRadialGradient(SPRITE / 2, SPRITE / 2, 0, SPRITE / 2, SPRITE / 2, SPRITE / 2);
    grd.addColorStop(0.00, "rgba(" + key + ",1)");
    grd.addColorStop(0.22, "rgba(" + key + ",0.55)");
    grd.addColorStop(0.55, "rgba(" + key + ",0.14)");
    grd.addColorStop(1.00, "rgba(" + key + ",0)");
    g.fillStyle = grd;
    g.fillRect(0, 0, SPRITE, SPRITE);
    spriteCache[key] = c;
    /* bounded: a long session cycling zones could otherwise accumulate */
    if (Object.keys(spriteCache).length > 48) {
      delete spriteCache[Object.keys(spriteCache)[0]];
    }
    return c;
  }

  function stamp(rgb, x, y, r, alpha) {
    if (alpha <= 0.004 || r <= 0) return;
    /* multiply into whatever alpha the caller already set, and put it back
       afterwards -- drawForm stamps glows from inside a faded context, and
       resetting to 1 here would undo the edge fade for the rest of the body */
    var prev = ctx.globalAlpha;
    ctx.globalAlpha = prev * Math.min(1, alpha);
    ctx.drawImage(glowSprite(rgb), x - r, y - r, r * 2, r * 2);
    ctx.globalAlpha = prev;
  }

  /* ── sizing ───────────────────────────────────────────────────────── */
  function resize() {
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    W = cv.clientWidth || window.innerWidth;
    H = cv.clientHeight || window.innerHeight;
    cv.width = Math.round(W * dpr);
    cv.height = Math.round(H * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    seedSnow();
    seedMotes();
    seedShafts();
  }

  /* ── camera ───────────────────────────────────────────────────────── */
  function span() {
    /* sqrt so the surface is intimate and the trenches are vast */
    return 42 + Math.sqrt(Math.max(0, state.depth)) * 6.4;
  }

  function depthToY(d) {
    var s = span();
    return ((d - state.depth) / s + 0.5) * H;
  }

  function visibleSpan() {
    var s = span();
    return { from: state.depth - s / 2, to: state.depth + s / 2, span: s };
  }

  /* ── marine snow ──────────────────────────────────────────────────── */
  var snow = [];
  function seedSnow() {
    var area = W * H;
    var n = Math.round(Math.max(40, Math.min(340, area / 5200)));
    if (reduced) n = Math.round(n * 0.4);
    snow = [];
    for (var i = 0; i < n; i++) {
      snow.push({
        x: Math.random(), y: Math.random(),
        /* parallax layer: near flakes are bigger, faster and blurrier */
        z: Math.random(),
        r: 0.4 + Math.random() * 1.7,
        vy: 0.006 + Math.random() * 0.02,
        vx: (Math.random() - 0.5) * 0.006,
        ph: Math.random() * TAU
      });
    }
  }

  function drawSnow(dt) {
    if (!state.snowOn) return;
    var density = D.snowDensity(state.depth);
    var lum = T.luminance(state.palette.top);
    /* flakes read as bright against dark water and dark against bright */
    var dark = lum > 0.34;
    var shown = Math.floor(snow.length * (0.45 + density * 0.55));
    for (var i = 0; i < shown; i++) {
      var f = snow[i];
      if (!reduced) {
        f.y += f.vy * f.z * dt * 0.9;
        f.x += (f.vx + Math.sin(state.time * 0.4 + f.ph) * 0.0016) * f.z * dt * 60;
        if (f.y > 1.05) { f.y = -0.05; f.x = Math.random(); }
        if (f.x > 1.05) f.x = -0.05; else if (f.x < -0.05) f.x = 1.05;
      }
      var a = (0.10 + f.z * 0.42) * (0.5 + density * 0.5);
      ctx.globalAlpha = Math.min(1, a);
      ctx.fillStyle = dark ? "#06202e" : "#eaf7ff";
      var r = f.r * (0.55 + f.z * 0.85);
      ctx.beginPath();
      ctx.arc(f.x * W, f.y * H, r, 0, TAU);
      ctx.fill();
    }
    ctx.globalAlpha = 1;
  }

  /* ── bioluminescent motes ─────────────────────────────────────────── */
  var motes = [];
  function seedMotes() {
    var n = Math.round(Math.max(26, Math.min(190, (W * H) / 9000)));
    if (reduced) n = Math.round(n * 0.45);
    motes = [];
    for (var i = 0; i < n; i++) {
      motes.push({
        x: Math.random(), y: Math.random(), z: Math.random(),
        r: 0.7 + Math.random() * 2.1,
        ph: Math.random() * TAU,
        sp: 0.25 + Math.random() * 0.9,
        vy: (Math.random() - 0.5) * 0.012,
        vx: (Math.random() - 0.5) * 0.008,
        hue: Math.random()
      });
    }
  }

  function drawMotes(dt) {
    if (!state.glowOn) return;
    /* no sunlight below ~1000 m, so the glints only start to matter as the
       water goes dark; they fade in across the twilight zone */
    var emerge = Math.max(0, Math.min(1, (state.depth - 90) / 700));
    if (emerge <= 0.01) return;
    var base = T.hexToRgb("#63d8ff");
    var alt = T.hexToRgb("#59f0d8");
    var warm = T.hexToRgb("#ffcf6b");
    for (var i = 0; i < motes.length; i++) {
      var m = motes[i];
      if (!reduced) {
        m.x += m.vx * dt; m.y += m.vy * dt;
        if (m.x > 1.04) m.x = -0.04; else if (m.x < -0.04) m.x = 1.04;
        if (m.y > 1.04) m.y = -0.04; else if (m.y < -0.04) m.y = 1.04;
      }
      /* each mote breathes on its own phase, so the field twinkles rather
         than pulsing in unison */
      var pulse = 0.5 + 0.5 * Math.sin(state.time * m.sp + m.ph);
      pulse = pulse * pulse;
      var a = emerge * (0.12 + pulse * 0.7) * (0.35 + m.z * 0.65);
      if (a < 0.01) continue;
      var rgb = m.hue < 0.12 ? warm : (m.hue < 0.5 ? base : alt);
      stamp(rgb, m.x * W, m.y * H, m.r * (3 + pulse * 5) * (0.6 + m.z), a);
      ctx.globalAlpha = Math.min(1, a * 1.25);
      ctx.fillStyle = "#ffffff";
      ctx.beginPath();
      ctx.arc(m.x * W, m.y * H, m.r * 0.34 * (0.6 + pulse * 0.6), 0, TAU);
      ctx.fill();
      ctx.globalAlpha = 1;
    }
  }

  /* ── light shafts ─────────────────────────────────────────────────── */
  var shafts = [];
  function seedShafts() {
    shafts = [];
    var n = 7;
    for (var i = 0; i < n; i++) {
      shafts.push({
        x: (i + 0.5) / n + (Math.random() - 0.5) * 0.07,
        w: 0.05 + Math.random() * 0.09,
        ph: Math.random() * TAU,
        sp: 0.10 + Math.random() * 0.16,
        skew: (Math.random() - 0.5) * 0.34
      });
    }
  }

  function drawShafts() {
    var light = D.lightPercent(state.depth) / 100;
    if (light < 0.004) return;                    /* nothing left to shaft */
    /* shafts belong to the water ABOVE the viewer, so they fade as you sink
       and are drawn from the top of the viewport only near the surface */
    var a = Math.pow(light, 0.55) * 0.5;
    if (a < 0.006) return;
    var topY = depthToY(0);
    if (topY > H * 1.2) return;
    ctx.save();
    ctx.globalCompositeOperation = "lighter";
    for (var i = 0; i < shafts.length; i++) {
      var s = shafts[i];
      var sway = reduced ? 0 : Math.sin(state.time * s.sp + s.ph);
      var cx = (s.x + sway * 0.02) * W;
      var w = s.w * W * (0.75 + 0.25 * (0.5 + 0.5 * sway));
      var bottom = Math.max(H, topY + H * 1.15);
      var g = ctx.createLinearGradient(cx, topY, cx + s.skew * W, bottom);
      g.addColorStop(0, "rgba(226,250,255," + (a * 0.85).toFixed(4) + ")");
      g.addColorStop(0.35, "rgba(190,240,255," + (a * 0.30).toFixed(4) + ")");
      g.addColorStop(1, "rgba(150,220,255,0)");
      ctx.fillStyle = g;
      ctx.beginPath();
      ctx.moveTo(cx - w * 0.42, topY);
      ctx.lineTo(cx + w * 0.42, topY);
      ctx.lineTo(cx + s.skew * W + w * 1.5, bottom);
      ctx.lineTo(cx + s.skew * W - w * 1.5, bottom);
      ctx.closePath();
      ctx.fill();
    }
    ctx.restore();
  }

  /* ── the surface ──────────────────────────────────────────────────── */
  function drawSurface() {
    var y = depthToY(0);
    if (y < -80 || y > H + 80) return;
    ctx.save();
    /* sky above the waterline */
    var sky = ctx.createLinearGradient(0, y - H * 0.42, 0, y);
    sky.addColorStop(0, "#dff4fb");
    sky.addColorStop(0.55, "#a9e4f2");
    sky.addColorStop(1, "#8adcec");
    ctx.fillStyle = sky;
    ctx.fillRect(0, y - H * 0.45, W, H * 0.45);

    /* two overlapping sine bands read as swell without a simulation */
    ctx.beginPath();
    ctx.moveTo(0, y);
    for (var x = 0; x <= W; x += 8) {
      var t = reduced ? 0 : state.time;
      var yy = y
        + Math.sin(x * 0.011 + t * 1.15) * 5.5
        + Math.sin(x * 0.026 - t * 0.85) * 2.6
        + Math.sin(x * 0.004 + t * 0.5) * 3.4;
      ctx.lineTo(x, yy);
    }
    ctx.lineTo(W, y + 40); ctx.lineTo(0, y + 40); ctx.closePath();
    ctx.fillStyle = "rgba(150,228,240,0.55)";
    ctx.fill();

    /* the bright line where water meets air */
    ctx.beginPath();
    for (var x2 = 0; x2 <= W; x2 += 8) {
      var t2 = reduced ? 0 : state.time;
      var y2 = y
        + Math.sin(x2 * 0.011 + t2 * 1.15) * 5.5
        + Math.sin(x2 * 0.026 - t2 * 0.85) * 2.6
        + Math.sin(x2 * 0.004 + t2 * 0.5) * 3.4;
      if (x2 === 0) ctx.moveTo(x2, y2); else ctx.lineTo(x2, y2);
    }
    ctx.strokeStyle = "rgba(255,255,255,0.85)";
    ctx.lineWidth = 1.6;
    ctx.stroke();
    ctx.restore();
  }

  /* ── specimens ──────────────────────────────────────────────────────
     Instances are spread across each species' real depth range once, then
     recycled: anything that falls far behind the camera is re-seeded ahead
     of it, so a long descent never runs out of animals and memory stays
     flat regardless of how far you travel.                        */
  var instances = [];

  function seedInstances() {
    instances = [];
    for (var i = 0; i < D.CREATURES.length; i++) {
      var c = D.CREATURES[i];
      var n = c.count || 3;
      for (var k = 0; k < n; k++) {
        instances.push({
          c: c,
          d: c.from + (c.to - c.from) * (n === 1 ? 0.5 : k / (n - 1)) + (Math.random() - 0.5) * 8,
          x: 0.10 + Math.random() * 0.80,
          ph: Math.random() * TAU,
          sp: 0.35 + Math.random() * 0.7,
          dir: Math.random() < 0.5 ? -1 : 1,
          drift: (Math.random() - 0.5) * 0.012,
          wob: Math.random() * TAU
        });
      }
    }
  }

  function recycle() {
    var v = visibleSpan();
    var ahead = v.to + v.span * 0.6;
    for (var i = 0; i < instances.length; i++) {
      var it = instances[i];
      /* only recycle things well outside the view, so nothing pops */
      if (it.d < v.from - v.span * 1.4) {
        it.d = Math.min(it.c.to, ahead + Math.random() * v.span);
        it.x = 0.10 + Math.random() * 0.80;
      } else if (it.d > ahead + v.span * 1.6) {
        it.d = Math.max(it.c.from, v.from - v.span * 0.5 - Math.random() * v.span);
        it.x = 0.10 + Math.random() * 0.80;
      }
      /* keep each animal inside its own species' real depth range */
      if (it.d < it.c.from) it.d = it.c.from;
      if (it.d > it.c.to) it.d = it.c.to;
    }
  }

  /* hit targets for pointer picking, rebuilt each frame */
  var hitTargets = [];

  function drawCreatures(dt) {
    hitTargets.length = 0;
    var v = visibleSpan();
    var lum = T.luminance(state.palette.top);
    var silhouette = lum > 0.16;   /* bright water: animals read as dark shapes */

    for (var i = 0; i < instances.length; i++) {
      var it = instances[i];
      if (it.d < v.from - 40 || it.d > v.to + 40) continue;

      var y = depthToY(it.d);
      if (y < -140 || y > H + 140) continue;

      /* fade in at the edges of the view so nothing pops into existence */
      var edge = Math.min(1, Math.min(y + 140, H - y + 140) / 190);
      if (edge <= 0.01) continue;

      /* nearer animals (shallower in the span) are larger: a cheap depth cue */
      var rel = (it.d - v.from) / v.span;
      var scale = (0.62 + 0.62 * (1 - rel)) * (H / 760) * (it.c.size / 26);
      scale = Math.max(0.22, Math.min(2.6, scale));

      if (!reduced) {
        it.x += it.drift * dt * it.dir;
        if (it.x > 0.94) { it.x = 0.94; it.dir = -1; it.drift = -it.drift; }
        if (it.x < 0.06) { it.x = 0.06; it.dir = 1; it.drift = -it.drift; }
      }
      var x = it.x * W;
      var bob = reduced ? 0 : Math.sin(state.time * it.sp + it.ph) * 5 * scale;

      var glowRgb = it.c.glow ? T.hexToRgb(it.c.glow) : null;
      var bodyCol = silhouette
        ? "rgba(6,24,36," + (0.42 + 0.4 * edge).toFixed(3) + ")"
        : "rgba(226,246,255," + (0.16 + 0.5 * edge).toFixed(3) + ")";
      var lineCol = silhouette
        ? "rgba(4,20,30," + (0.55 * edge).toFixed(3) + ")"
        : "rgba(200,240,255," + (0.55 * edge).toFixed(3) + ")";

      var hovered = state.hoverId === it.c.id;
      var a = edge * (hovered ? 1 : 0.92);

      ctx.save();
      ctx.globalAlpha = a;
      ctx.translate(x, y + bob);
      ctx.scale(scale * it.dir, scale);

      if (glowRgb && state.glowOn) {
        stamp(glowRgb, 0, 0, 34 + (hovered ? 16 : 0),
              (0.16 + 0.20 * edge) * (hovered ? 1.5 : 1));
      }
      drawForm(it.c.form, bodyCol, lineCol, glowRgb, it, hovered);
      ctx.restore();
      ctx.globalAlpha = 1;

      /* screen-space hit box, in unscaled pixels */
      hitTargets.push({
        id: it.c.id, x: x, y: y + bob,
        r: Math.max(20, it.c.size * scale * 1.25)
      });
    }
  }

  /* ── the procedural animal forms ────────────────────────────────────
     At depth you see shapes and light rather than detail, so these are
     stylised silhouettes. Each animates on its own cycle.           */
  function drawForm(form, body, line, glow, it, hovered) {
    var t = reduced ? 0 : state.time;
    ctx.fillStyle = body;
    ctx.strokeStyle = line;
    ctx.lineWidth = 1.15;
    ctx.lineJoin = "round";
    ctx.lineCap = "round";

    switch (form) {

      case "drift": {
        /* phytoplankton: a loose cluster of specks */
        for (var i = 0; i < 9; i++) {
          var ang = (i / 9) * TAU + t * 0.18 + it.ph;
          var rad = 7 + Math.sin(t * 0.6 + i) * 2.4;
          ctx.beginPath();
          ctx.arc(Math.cos(ang) * rad, Math.sin(ang) * rad, 1.25, 0, TAU);
          ctx.fill();
        }
        ctx.beginPath(); ctx.arc(0, 0, 1.9, 0, TAU); ctx.fill();
        break;
      }

      case "jelly": {
        /* bell contracts on a cycle, tentacles trail and lag behind it */
        var pulse = 0.5 + 0.5 * Math.sin(t * 1.5 + it.ph);
        var bw = 15 - pulse * 3.2, bh = 12 + pulse * 4.4;
        ctx.beginPath();
        ctx.moveTo(-bw, 0);
        ctx.quadraticCurveTo(-bw * 0.9, -bh * 1.5, 0, -bh * 1.5);
        ctx.quadraticCurveTo(bw * 0.9, -bh * 1.5, bw, 0);
        ctx.quadraticCurveTo(0, bh * 0.42, -bw, 0);
        ctx.fill(); ctx.stroke();
        for (var k = 0; k < 7; k++) {
          var tx = -bw + (k / 6) * bw * 2;
          ctx.beginPath();
          ctx.moveTo(tx, bh * 0.2);
          var len = 15 + Math.sin(t * 1.5 + it.ph + k) * 5;
          ctx.quadraticCurveTo(tx + Math.sin(t * 1.1 + k) * 4, bh * 0.2 + len * 0.6,
                               tx + Math.sin(t * 0.9 + k * 0.7) * 6, bh * 0.2 + len);
          ctx.stroke();
        }
        break;
      }

      case "fish": {
        var wag = Math.sin(t * 3.4 + it.ph) * 0.30;
        ctx.beginPath();
        ctx.moveTo(-13, 0);
        ctx.quadraticCurveTo(-4, -6.2, 8, -2.6);
        ctx.quadraticCurveTo(12, -1, 13, 0);
        ctx.quadraticCurveTo(12, 1, 8, 2.6);
        ctx.quadraticCurveTo(-4, 6.2, -13, 0);
        ctx.fill(); ctx.stroke();
        /* caudal fin, swinging */
        ctx.save(); ctx.translate(-13, 0); ctx.rotate(wag);
        ctx.beginPath(); ctx.moveTo(0, 0);
        ctx.lineTo(-7.5, -5.4); ctx.lineTo(-5.6, 0); ctx.lineTo(-7.5, 5.4);
        ctx.closePath(); ctx.fill(); ctx.stroke(); ctx.restore();
        /* dorsal + pectoral */
        ctx.beginPath(); ctx.moveTo(-2, -4.6); ctx.lineTo(2, -8.4); ctx.lineTo(5, -3.4);
        ctx.closePath(); ctx.fill();
        ctx.beginPath(); ctx.moveTo(1, 2.4); ctx.lineTo(4, 6.4); ctx.lineTo(-1.5, 3.6);
        ctx.closePath(); ctx.fill();
        if (glow && state.glowOn) {
          /* photophores along the belly — counterillumination */
          for (var p = 0; p < 5; p++) {
            var px = -8 + p * 3.6;
            var blink = 0.45 + 0.55 * Math.sin(t * 1.9 + p * 0.8 + it.ph);
            stamp(glow, px, 3.4, 4.6, 0.5 * blink);
          }
        }
        break;
      }

      case "school": {
        /* a bait ball: individuals orbit a shared centre */
        var n = 26;
        for (var s = 0; s < n; s++) {
          var a1 = (s / n) * TAU + t * 0.5 + it.ph;
          var rr = 13 + Math.sin(a1 * 3 + t * 1.3) * 5 + (s % 3) * 2.6;
          var sx = Math.cos(a1) * rr, sy = Math.sin(a1) * rr * 0.62;
          ctx.save(); ctx.translate(sx, sy); ctx.rotate(a1 + Math.PI / 2);
          ctx.beginPath(); ctx.ellipse(0, 0, 1.15, 2.9, 0, 0, TAU);
          ctx.fill(); ctx.restore();
        }
        break;
      }

      case "ray": {
        var flap = Math.sin(t * 1.15 + it.ph) * 0.30;
        ctx.beginPath();
        ctx.moveTo(0, -7);
        ctx.quadraticCurveTo(-14, -12 - flap * 9, -25, -2 - flap * 13);
        ctx.quadraticCurveTo(-13, 5, 0, 7);
        ctx.quadraticCurveTo(13, 5, 25, -2 - flap * 13);
        ctx.quadraticCurveTo(14, -12 - flap * 9, 0, -7);
        ctx.fill(); ctx.stroke();
        /* cephalic fins and the tail */
        ctx.beginPath(); ctx.moveTo(-3.4, -6.4); ctx.lineTo(-4.6, -12);
        ctx.moveTo(3.4, -6.4); ctx.lineTo(4.6, -12); ctx.stroke();
        ctx.beginPath(); ctx.moveTo(0, 7); ctx.lineTo(0, 21); ctx.stroke();
        break;
      }

      case "cetacean": {
        var und = Math.sin(t * 1.5 + it.ph) * 0.16;
        ctx.beginPath();
        ctx.moveTo(-26, 0);
        ctx.quadraticCurveTo(-13, -8.5, 6, -6.2);
        ctx.quadraticCurveTo(19, -4.4, 25, -1.2);
        ctx.quadraticCurveTo(19, 4.6, 6, 6.4);
        ctx.quadraticCurveTo(-13, 8.6, -26, 0);
        ctx.fill(); ctx.stroke();
        ctx.save(); ctx.translate(-26, 0); ctx.rotate(und);
        ctx.beginPath(); ctx.moveTo(0, 0);
        ctx.lineTo(-9, -7.2); ctx.lineTo(-5.6, 0); ctx.lineTo(-9, 7.2);
        ctx.closePath(); ctx.fill(); ctx.stroke(); ctx.restore();
        ctx.beginPath(); ctx.moveTo(2, 5.4); ctx.lineTo(7, 12); ctx.lineTo(-2, 7);
        ctx.closePath(); ctx.fill();
        break;
      }

      case "glider": {
        /* turtle / chimaera: broad carapace with slow paddle strokes */
        var pad = Math.sin(t * 0.95 + it.ph) * 0.42;
        ctx.beginPath(); ctx.ellipse(0, 0, 13, 9.4, 0, 0, TAU);
        ctx.fill(); ctx.stroke();
        ctx.beginPath(); ctx.ellipse(13.5, -0.6, 4.6, 3.5, 0, 0, TAU);
        ctx.fill(); ctx.stroke();
        ctx.save(); ctx.rotate(pad * 0.5);
        ctx.beginPath(); ctx.moveTo(-3, -7); ctx.lineTo(-12, -15); ctx.lineTo(-1, -10);
        ctx.closePath(); ctx.fill(); ctx.restore();
        ctx.save(); ctx.rotate(-pad * 0.5);
        ctx.beginPath(); ctx.moveTo(-3, 7); ctx.lineTo(-12, 15); ctx.lineTo(-1, 10);
        ctx.closePath(); ctx.fill(); ctx.restore();
        ctx.beginPath(); ctx.moveTo(-13, 0); ctx.lineTo(-20, -3.6);
        ctx.lineTo(-20, 3.6); ctx.closePath(); ctx.fill();
        break;
      }

      case "squid": {
        var jet = 0.5 + 0.5 * Math.sin(t * 1.7 + it.ph);
        ctx.beginPath();
        ctx.moveTo(0, -17 - jet * 2);
        ctx.quadraticCurveTo(8.6, -6, 6.4, 5);
        ctx.quadraticCurveTo(0, 8.6, -6.4, 5);
        ctx.quadraticCurveTo(-8.6, -6, 0, -17 - jet * 2);
        ctx.fill(); ctx.stroke();
        /* fins along the mantle */
        ctx.beginPath(); ctx.moveTo(-6.6, -9); ctx.lineTo(-12, -13); ctx.lineTo(-5.4, -3.4);
        ctx.closePath(); ctx.fill();
        ctx.beginPath(); ctx.moveTo(6.6, -9); ctx.lineTo(12, -13); ctx.lineTo(5.4, -3.4);
        ctx.closePath(); ctx.fill();
        for (var q = 0; q < 8; q++) {
          var ax = -5.6 + (q / 7) * 11.2;
          var len2 = 13 + Math.sin(t * 1.25 + q * 0.6 + it.ph) * 5;
          ctx.beginPath(); ctx.moveTo(ax, 6);
          ctx.quadraticCurveTo(ax + Math.sin(t * 0.9 + q) * 4.6, 6 + len2 * 0.6,
                               ax + Math.sin(t * 0.7 + q * 0.9) * 7, 6 + len2);
          ctx.stroke();
        }
        break;
      }

      case "chain": {
        /* siphonophore: a column of cloned bells, each pulsing slightly
           out of phase so the wave travels down the colony */
        var seg = 15;
        for (var c2 = 0; c2 < seg; c2++) {
          var ph2 = t * 1.25 - c2 * 0.42 + it.ph;
          var px2 = Math.sin(ph2 * 0.8) * (2.4 + c2 * 0.34);
          var py2 = -34 + c2 * 4.6;
          var rr2 = 3.6 - c2 * 0.13 + Math.sin(ph2) * 0.7;
          if (rr2 < 0.7) rr2 = 0.7;
          ctx.beginPath(); ctx.arc(px2, py2, rr2, 0, TAU); ctx.fill();
          if (glow && state.glowOn) {
            var bl = 0.4 + 0.6 * Math.max(0, Math.sin(ph2));
            stamp(glow, px2, py2, rr2 * 3.6, 0.34 * bl);
          }
        }
        ctx.beginPath(); ctx.moveTo(0, -34); ctx.lineTo(0, 36); ctx.stroke();
        break;
      }

      case "angler": {
        /* round body, enormous gape, and a lure on a rod that is the only
           bright thing for hundreds of metres */
        ctx.beginPath();
        ctx.moveTo(-11, 0);
        ctx.quadraticCurveTo(-6, -9.6, 4, -8.4);
        ctx.quadraticCurveTo(11, -6, 12, 0);
        ctx.quadraticCurveTo(11, 6.6, 4, 8.4);
        ctx.quadraticCurveTo(-6, 9.6, -11, 0);
        ctx.fill(); ctx.stroke();
        /* gape */
        ctx.beginPath(); ctx.moveTo(12, 0); ctx.lineTo(3.4, -3.4); ctx.lineTo(3.4, 3.4);
        ctx.closePath();
        ctx.fillStyle = "rgba(2,10,16,0.82)"; ctx.fill();
        ctx.fillStyle = body;
        /* teeth */
        ctx.strokeStyle = "rgba(235,250,255,0.75)"; ctx.lineWidth = 0.8;
        for (var z = 0; z < 4; z++) {
          var ty = -2.6 + z * 1.7;
          ctx.beginPath(); ctx.moveTo(11 - z * 1.1, ty); ctx.lineTo(8.4 - z * 1.1, ty + 0.9);
          ctx.stroke();
        }
        ctx.strokeStyle = line; ctx.lineWidth = 1.15;
        /* illicium + esca */
        var sw = reduced ? 0 : Math.sin(t * 1.05 + it.ph) * 0.30;
        ctx.beginPath(); ctx.moveTo(2, -8);
        ctx.quadraticCurveTo(-3 + sw * 7, -19, 1 + sw * 11, -24);
        ctx.stroke();
        var lx = 1 + sw * 11, ly = -24;
        if (glow && state.glowOn) {
          var beat = 0.55 + 0.45 * Math.sin(t * 2.6 + it.ph);
          stamp(glow, lx, ly, 15 * (0.8 + beat * 0.5), 0.5 + beat * 0.45);
          ctx.fillStyle = "#fffbe8";
          ctx.beginPath(); ctx.arc(lx, ly, 1.9 + beat * 0.9, 0, TAU); ctx.fill();
          ctx.fillStyle = body;
        }
        break;
      }

      case "eel": {
        /* a single travelling sine, tapered — no rigid body at all */
        var segs = 26, amp = 4.6;
        ctx.beginPath();
        for (var e = 0; e <= segs; e++) {
          var u = e / segs;
          var ex = -26 + u * 52;
          var ey = Math.sin(u * 5.2 - t * 2.5 + it.ph) * amp * (0.25 + u);
          if (e === 0) ctx.moveTo(ex, ey); else ctx.lineTo(ex, ey);
        }
        ctx.lineWidth = 5.4; ctx.strokeStyle = body; ctx.stroke();
        ctx.lineWidth = 1.1; ctx.strokeStyle = line; ctx.stroke();
        /* gape at the head end */
        ctx.beginPath();
        ctx.moveTo(26, Math.sin(5.2 - t * 2.5 + it.ph) * amp * 1.25);
        ctx.lineTo(34, -5.6); ctx.lineTo(34, 5.6); ctx.closePath();
        ctx.fillStyle = body; ctx.fill(); ctx.stroke();
        ctx.lineWidth = 1.15;
        break;
      }

      case "crawler": {
        /* sea pig / amphipod / tripod: a low body on jointed legs that
           step in a metachronal wave */
        ctx.beginPath(); ctx.ellipse(0, -1.4, 11, 6.4, 0, 0, TAU);
        ctx.fill(); ctx.stroke();
        for (var L = 0; L < 6; L++) {
          var lx2 = -8.4 + L * 3.4;
          var step = Math.sin(t * 2.1 + L * 0.9 + it.ph) * 2.2;
          ctx.beginPath(); ctx.moveTo(lx2, 3.4);
          ctx.lineTo(lx2 + step, 9.4); ctx.stroke();
        }
        /* the two front papillae sea pigs are drawn with */
        ctx.beginPath(); ctx.moveTo(-9, -4); ctx.lineTo(-12, -8.4);
        ctx.moveTo(9, -4); ctx.lineTo(12, -8.4); ctx.stroke();
        break;
      }

      case "sessile": {
        /* xenophyophore: a branching cemented test, essentially static */
        ctx.beginPath(); ctx.ellipse(0, 0, 13, 7.4, 0, 0, TAU);
        ctx.fill(); ctx.stroke();
        for (var b = 0; b < 9; b++) {
          var ba = (b / 9) * TAU;
          var br = 6 + Math.sin(b * 2.3) * 3;
          ctx.beginPath(); ctx.moveTo(0, 0);
          ctx.quadraticCurveTo(Math.cos(ba) * br * 0.6, Math.sin(ba) * br * 0.5,
                               Math.cos(ba) * br, Math.sin(ba) * br * 0.7);
          ctx.stroke();
        }
        break;
      }
    }

    if (hovered) {
      ctx.strokeStyle = T.css(state.palette.accent, 0.85);
      ctx.lineWidth = 1.2;
      ctx.beginPath(); ctx.arc(0, 0, 26, 0, TAU); ctx.stroke();
    }
  }

  /* ── frame ────────────────────────────────────────────────────────── */
  var last = 0;

  function frame(now) {
    var dt = last ? Math.min(0.05, (now - last) / 1000) : 0.016;
    last = now;
    if (!reduced) state.time += dt;

    var pal = Theme.apply(state.depth);
    state.palette = pal;

    /* opaque context: paint the column ourselves rather than letting CSS
       show through, which avoids a compositing pass over the whole screen */
    var g = ctx.createLinearGradient(0, 0, 0, H);
    g.addColorStop(0, T.css(pal.top));
    g.addColorStop(1, T.css(pal.bottom));
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, W, H);

    drawShafts();
    drawSurface();
    recycle();
    drawCreatures(dt);
    if (state.snowOn) drawSnow(dt);
    drawMotes(dt);

    /* a faint depth-fog band across the middle sells the volume */
    var fog = ctx.createLinearGradient(0, H * 0.25, 0, H * 0.95);
    fog.addColorStop(0, T.css(pal.bottom, 0));
    fog.addColorStop(1, T.css(pal.bottom, 0.42));
    ctx.fillStyle = fog;
    ctx.fillRect(0, H * 0.25, W, H * 0.75);

    requestAnimationFrame(frame);
  }

  /* ── public API ───────────────────────────────────────────────────── */
  function hitTest(px, py) {
    var best = null, bestD = Infinity;
    for (var i = 0; i < hitTargets.length; i++) {
      var h = hitTargets[i];
      var d = Math.hypot(h.x - px, h.y - py);
      if (d < h.r && d < bestD) { best = h; bestD = d; }
    }
    return best ? best.id : null;
  }

  function start() {
    resize();
    seedInstances();
    window.addEventListener("resize", resize, { passive: true });
    window.addEventListener("orientationchange", function () { setTimeout(resize, 180); });
    requestAnimationFrame(frame);
  }

  return {
    start: start,
    setDepth: function (d) { state.depth = d; },
    setGlow: function (on) { state.glowOn = on; },
    setSnow: function (on) { state.snowOn = on; },
    setHover: function (id) { state.hoverId = id; },
    hitTest: hitTest,
    visibleSpan: visibleSpan,
    depthToY: depthToY,
    reducedMotion: function () { return reduced; }
  };
})();
