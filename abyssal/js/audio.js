/* ═══════════════════════════════════════════════════════════════════════
   ABYSSAL — audio
   Everything is synthesised at runtime. There are no audio files, which
   keeps the download tiny and means the sound can respond continuously to
   depth instead of cross-fading between tracks.

   The model
   ---------
   Two detuned oscillators make a low drone; a lowpass sits on top of it
   and its cutoff falls as you descend, so the ocean audibly closes in —
   which is what actually happens to sound propagation and to your own
   perception of it under increasing pressure. A filtered noise bed is the
   surface wash, and it fades out through the first few hundred metres.
   Below ~1,200 m, where no sunlight remains, occasional long tones drift
   through: the acoustic character of the deep is mostly other animals.

   Starts silent. Browsers require a user gesture before an AudioContext
   can make sound, so the context is created on first enable, not on load.
   ═══════════════════════════════════════════════════════════════════════ */
window.Ambience = (function () {
  "use strict";

  var D = window.ABYSSAL_DATA;
  var ctx = null;
  var nodes = null;
  var enabled = false;
  var depth = 0;
  var toneTimer = null;
  var masterTarget = 0;

  function build() {
    if (ctx) return true;
    var AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return false;

    try {
      ctx = new AC();
    } catch (e) {
      return false;
    }

    var master = ctx.createGain();
    master.gain.value = 0;

    /* gentle bus compression so a whale tone cannot spike over the drone */
    var comp = ctx.createDynamicsCompressor();
    comp.threshold.value = -18;
    comp.knee.value = 22;
    comp.ratio.value = 3.5;
    comp.attack.value = 0.012;
    comp.release.value = 0.30;

    master.connect(comp);
    comp.connect(ctx.destination);

    /* ── drone: two detuned oscillators + a sub ─────────────────────── */
    var droneGain = ctx.createGain();
    droneGain.gain.value = 0.30;

    var lp = ctx.createBiquadFilter();
    lp.type = "lowpass";
    lp.frequency.value = 900;
    lp.Q.value = 0.7;

    droneGain.connect(lp);
    lp.connect(master);

    var oscA = ctx.createOscillator();
    oscA.type = "sine";
    oscA.frequency.value = 55;

    var oscB = ctx.createOscillator();
    oscB.type = "triangle";
    oscB.frequency.value = 55 * 1.005;      /* slow beating against A */

    var sub = ctx.createOscillator();
    sub.type = "sine";
    sub.frequency.value = 27.5;

    var subGain = ctx.createGain();
    subGain.gain.value = 0.35;

    var bGain = ctx.createGain();
    bGain.gain.value = 0.45;

    oscA.connect(droneGain);
    oscB.connect(bGain); bGain.connect(droneGain);
    sub.connect(subGain); subGain.connect(droneGain);

    /* ── the drone breathes ─────────────────────────────────────────── */
    var lfo = ctx.createOscillator();
    lfo.type = "sine";
    lfo.frequency.value = 0.055;
    var lfoGain = ctx.createGain();
    lfoGain.gain.value = 0.085;
    lfo.connect(lfoGain);
    lfoGain.connect(droneGain.gain);

    /* and its pitch drifts a few cents, so it never sounds like a test tone */
    var vib = ctx.createOscillator();
    vib.type = "sine";
    vib.frequency.value = 0.031;
    var vibGain = ctx.createGain();
    vibGain.gain.value = 0.7;
    vib.connect(vibGain);
    vibGain.connect(oscA.frequency);
    vibGain.connect(oscB.frequency);

    /* ── surface wash: filtered noise ───────────────────────────────── */
    var noiseBuf = ctx.createBuffer(1, ctx.sampleRate * 3, ctx.sampleRate);
    var ch = noiseBuf.getChannelData(0);
    var lastOut = 0;
    for (var i = 0; i < ch.length; i++) {
      /* brown-ish noise reads as water; white noise reads as hiss */
      var white = Math.random() * 2 - 1;
      lastOut = (lastOut + 0.021 * white) / 1.021;
      ch[i] = lastOut * 3.2;
    }
    var noise = ctx.createBufferSource();
    noise.buffer = noiseBuf;
    noise.loop = true;

    var noiseFilt = ctx.createBiquadFilter();
    noiseFilt.type = "bandpass";
    noiseFilt.frequency.value = 620;
    noiseFilt.Q.value = 0.5;

    var noiseGain = ctx.createGain();
    noiseGain.gain.value = 0;

    noise.connect(noiseFilt);
    noiseFilt.connect(noiseGain);
    noiseGain.connect(master);

    oscA.start(); oscB.start(); sub.start();
    lfo.start(); vib.start(); noise.start();

    nodes = {
      master: master, droneGain: droneGain, lp: lp,
      oscA: oscA, oscB: oscB, sub: sub,
      noiseGain: noiseGain, noiseFilt: noiseFilt
    };
    return true;
  }

  /* A long descending tone — the acoustic signature of the deep, where
     the only sounds are animals and the hull of the water itself. */
  function tone() {
    if (!ctx || !enabled || nodes == null) return;
    var t = ctx.currentTime;
    var deep = Math.max(0, Math.min(1, (depth - 900) / 2200));
    if (deep <= 0.02) return;

    var o = ctx.createOscillator();
    o.type = "sine";
    var base = 180 + Math.random() * 220;
    o.frequency.setValueAtTime(base, t);
    o.frequency.exponentialRampToValueAtTime(base * (0.42 + Math.random() * 0.2), t + 3.4);

    var g = ctx.createGain();
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(0.055 * deep, t + 0.9);
    g.gain.exponentialRampToValueAtTime(0.0001, t + 4.2);

    var f = ctx.createBiquadFilter();
    f.type = "lowpass";
    f.frequency.value = 1100;

    o.connect(g); g.connect(f); f.connect(nodes.master);
    o.start(t);
    o.stop(t + 4.4);
    o.onended = function () { try { o.disconnect(); g.disconnect(); f.disconnect(); } catch (e) {} };
  }

  function scheduleTones() {
    if (toneTimer) { clearTimeout(toneTimer); toneTimer = null; }
    if (!enabled) return;
    (function loop() {
      toneTimer = setTimeout(function () {
        tone();
        loop();
      }, 6000 + Math.random() * 11000);
    })();
  }

  /* Called each frame with the current depth. All parameter changes are
     smoothed with setTargetAtTime rather than assigned directly, because
     an abrupt filter jump is audible as a click. */
  function update(d) {
    depth = d;
    if (!ctx || !enabled || nodes == null) return;

    var t01 = Math.max(0, Math.min(1, d / D.MAX_DEPTH));
    var tc = ctx.currentTime;

    /* the column closes in: cutoff falls roughly exponentially with depth */
    var cutoff = 1050 * Math.exp(-2.05 * t01) + 78;
    nodes.lp.frequency.setTargetAtTime(cutoff, tc, 0.35);

    /* and the pitch drops with it — pressure is audible as weight */
    var f = 55 * (1 - 0.17 * t01);
    nodes.oscA.frequency.setTargetAtTime(f, tc, 0.5);
    nodes.oscB.frequency.setTargetAtTime(f * 1.005, tc, 0.5);
    nodes.sub.frequency.setTargetAtTime(f / 2, tc, 0.5);

    /* surface wash only exists near the top */
    var wash = Math.max(0, 1 - d / 340);
    nodes.noiseGain.gain.setTargetAtTime(0.20 * wash * wash, tc, 0.4);
    nodes.noiseFilt.frequency.setTargetAtTime(420 + 520 * wash, tc, 0.4);

    nodes.droneGain.gain.setTargetAtTime(0.24 + 0.13 * t01, tc, 0.5);
  }

  function setEnabled(on) {
    enabled = !!on;
    if (enabled) {
      if (!build()) { enabled = false; return false; }
      if (ctx.state === "suspended") ctx.resume();
      masterTarget = 0.5;
      nodes.master.gain.setTargetAtTime(masterTarget, ctx.currentTime, 0.9);
      scheduleTones();
    } else {
      masterTarget = 0;
      if (ctx && nodes) nodes.master.gain.setTargetAtTime(0, ctx.currentTime, 0.35);
      if (toneTimer) { clearTimeout(toneTimer); toneTimer = null; }
    }
    return enabled;
  }

  function isEnabled() { return enabled; }

  /* a tab in the background accumulates scheduled parameter changes for
     nothing; suspend and resume with visibility */
  document.addEventListener("visibilitychange", function () {
    if (!ctx) return;
    if (document.hidden) { ctx.suspend(); }
    else if (enabled) { ctx.resume(); }
  });

  return { setEnabled: setEnabled, isEnabled: isEnabled, update: update };
})();
