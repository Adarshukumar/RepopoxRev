/* ═══════════════════════════════════════════════════════════════════════
   ABYSSAL — theme
   Depth → palette. There is no CSS rule anywhere that hard-codes a water
   colour; this file owns every one of them and rewrites the custom
   properties each frame.

   Two things are interpolated on different curves, deliberately:

     • the water colours follow the zone stops linearly in depth, so a zone
       boundary is a smooth handover rather than a snap;
     • the *interface* colours (text, glass, accent) follow perceived
       brightness, because what the UI needs to do changes qualitatively —
       near the surface the water is bright and the text must go dark, and
       that flip has to happen early enough to stay legible.

   Deriving interface contrast from the background is the same move Mochy
   makes when it themes itself from the album artwork.
   ═══════════════════════════════════════════════════════════════════════ */
window.Theme = (function () {
  "use strict";

  var D = window.ABYSSAL_DATA;
  var ZONES = D.ZONES;

  /* ── colour helpers ───────────────────────────────────────────────── */

  function hexToRgb(hex) {
    var h = hex.replace("#", "");
    if (h.length === 3) h = h[0] + h[0] + h[1] + h[1] + h[2] + h[2];
    var n = parseInt(h, 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  }

  function mix(a, b, t) {
    /* mixing in gamma-encoded sRGB is technically wrong but visually
       smoother for these particular dark-to-darker ramps; doing it in
       linear light makes the mid-blues go muddy */
    return [
      Math.round(a[0] + (b[0] - a[0]) * t),
      Math.round(a[1] + (b[1] - a[1]) * t),
      Math.round(a[2] + (b[2] - a[2]) * t)
    ];
  }

  function css(rgb, alpha) {
    return alpha == null
      ? "rgb(" + rgb[0] + "," + rgb[1] + "," + rgb[2] + ")"
      : "rgba(" + rgb[0] + "," + rgb[1] + "," + rgb[2] + "," + alpha + ")";
  }

  /* relative luminance, the sRGB definition */
  function luminance(rgb) {
    var srgb = rgb.map(function (v) {
      var c = v / 255;
      return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    });
    return 0.2126 * srgb[0] + 0.7152 * srgb[1] + 0.0722 * srgb[2];
  }

  /* Build the list of colour stops across the whole column. Each zone
     contributes its `top` at its upper bound and `bottom` at its lower
     bound; because data.js chains them (zone N's top === zone N-1's
     bottom) the ramp is continuous with no seam. */
  var STOPS = (function () {
    var out = [];
    for (var i = 0; i < ZONES.length; i++) {
      out.push({ d: ZONES[i].from, rgb: hexToRgb(ZONES[i].top) });
      out.push({ d: ZONES[i].to, rgb: hexToRgb(ZONES[i].bottom) });
    }
    out.sort(function (a, b) { return a.d - b.d; });
    /* collapse coincident stops so interpolation always has a real span */
    var dedup = [];
    for (var j = 0; j < out.length; j++) {
      if (dedup.length && dedup[dedup.length - 1].d === out[j].d) continue;
      dedup.push(out[j]);
    }
    return dedup;
  })();

  function waterAt(depth) {
    var s = STOPS;
    if (depth <= s[0].d) return s[0].rgb.slice();
    var last = s[s.length - 1];
    if (depth >= last.d) return last.rgb.slice();
    for (var i = 1; i < s.length; i++) {
      if (depth <= s[i].d) {
        var t = (depth - s[i - 1].d) / (s[i].d - s[i - 1].d);
        return mix(s[i - 1].rgb, s[i].rgb, Spring.smooth(t));
      }
    }
    return last.rgb.slice();
  }

  function accentAt(depth) {
    var z = D.zoneAt(depth);
    var i = z.index;
    var span = ZONES[i].to - ZONES[i].from;
    var t = span > 0 ? (depth - ZONES[i].from) / span : 0;
    t = Math.max(0, Math.min(1, t));
    var a = hexToRgb(ZONES[i].accent);
    var b = hexToRgb(ZONES[Math.min(i + 1, ZONES.length - 1)].accent);
    return mix(a, b, Spring.smooth(t));
  }

  function glowAt(depth) {
    var z = D.zoneAt(depth);
    var i = z.index;
    var span = ZONES[i].to - ZONES[i].from;
    var t = span > 0 ? (depth - ZONES[i].from) / span : 0;
    t = Math.max(0, Math.min(1, t));
    return mix(hexToRgb(ZONES[i].glow),
               hexToRgb(ZONES[Math.min(i + 1, ZONES.length - 1)].glow),
               Spring.smooth(t));
  }

  /* ── applying ─────────────────────────────────────────────────────── */

  var root = document.documentElement;
  var lastKey = "";
  var lightText = true;

  function apply(depth) {
    /* The gradient spans a window around the viewer rather than the whole
       column, so there is always visible falloff above and below — at the
       very bottom, mapping 0..10935 m onto the viewport would leave the
       entire screen a single flat black. */
    var top = waterAt(Math.max(0, depth - 45));
    var bottom = waterAt(Math.min(D.MAX_DEPTH, depth + 340));
    var accent = accentAt(depth);
    var glow = glowAt(depth);
    var lum = luminance(top);

    /* dark text on bright water, light text on dark water, with a wide
       crossfade band so it never snaps */
    var t = Math.max(0, Math.min(1, (0.30 - lum) / 0.22));
    lightText = t > 0.5;

    var text = mix([12, 26, 36], [255, 255, 255], t);
    var muted = mix([40, 66, 80], [255, 255, 255], t);
    var faint = mix([70, 100, 116], [255, 255, 255], t);
    var line = mix([10, 30, 42], [255, 255, 255], t);
    var glass = mix([255, 255, 255], [4, 16, 26], t);

    /* quantise the key so we only touch the DOM when something actually
       changed — writing custom properties every frame invalidates style
       for the whole document and is measurable on a phone */
    var key = [
      depth.toFixed(1), lightText ? 1 : 0, Math.round(t * 24)
    ].join("|");
    if (key === lastKey) return { top: top, bottom: bottom, accent: accent, glow: glow };
    lastKey = key;

    var st = root.style;
    st.setProperty("--c-top", css(top));
    st.setProperty("--c-bottom", css(bottom));
    st.setProperty("--c-accent", css(accent));
    st.setProperty("--c-glow", css(glow));
    st.setProperty("--c-ink", css(mix([8, 22, 32], [255, 255, 255], 1 - t)));
    st.setProperty("--c-text", css(text, 0.94));
    st.setProperty("--c-muted", css(muted, 0.30 + t * 0.36));
    st.setProperty("--c-faint", css(faint, 0.22 + t * 0.30));
    st.setProperty("--c-line", css(line, 0.06 + t * 0.10));
    st.setProperty("--c-glass", css(glass, 0.10 + t * 0.30));
    st.setProperty("--depth01", (depth / D.MAX_DEPTH).toFixed(4));

    return { top: top, bottom: bottom, accent: accent, glow: glow };
  }

  return {
    apply: apply,
    waterAt: waterAt,
    accentAt: accentAt,
    glowAt: glowAt,
    css: css,
    mix: mix,
    hexToRgb: hexToRgb,
    luminance: luminance,
    isLightText: function () { return lightText; }
  };
})();
