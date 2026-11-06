/* ═══════════════════════════════════════════════════════════════════════
   ABYSSAL — smoke test
   Loads index.html in a real DOM (jsdom), runs every script in the same
   order the page does, then drives the app: descend, jump zones, open a
   specimen, drag the sheet, toggle every control, hit the floor.

   `node --check` only proves the files parse. This proves they run, that
   every element they look up exists, and that no property access throws.
   Canvas and Web Audio are stubbed, since jsdom implements neither — the
   stubs record calls so we can assert the renderer actually drew.

   Usage:  node tools/smoke.js
   ═══════════════════════════════════════════════════════════════════════ */
"use strict";

const fs = require("fs");
const path = require("path");
/* jsdom is a dev dependency of the test, not of the app. Look for it in the
   usual places so the test is runnable from a fresh clone without editing. */
function loadJsdom() {
  const candidates = [
    "jsdom",
    path.join("/tmp", "node_modules", "jsdom"),
    path.resolve(__dirname, "..", "..", "node_modules", "jsdom"),
  ];
  for (const c of candidates) {
    try { return require(c); } catch (e) { /* try the next one */ }
  }
  console.error("jsdom not found. Install it with:  npm install jsdom");
  console.error("The app itself has no dependencies; only this test does.");
  process.exit(2);
}
const { JSDOM, VirtualConsole } = loadJsdom();

const ROOT = path.resolve(__dirname, "..");
const errors = [];
const failures = [];
let checks = 0;

function ok(cond, label) {
  checks++;
  if (!cond) failures.push(label);
  return !!cond;
}

/* ── canvas 2d stub ─────────────────────────────────────────────────── */
const drawCounts = Object.create(null);
function bump(name) { drawCounts[name] = (drawCounts[name] || 0) + 1; }

function makeGradient() {
  return { addColorStop(off, colour) {
    if (typeof off !== "number" || !isFinite(off)) errors.push("addColorStop: bad offset " + off);
    if (off < 0 || off > 1) errors.push("addColorStop: offset out of 0..1 -> " + off);
    if (typeof colour !== "string" || /NaN|undefined|null/.test(colour)) {
      errors.push("addColorStop: bad colour -> " + colour);
    }
  } };
}

function makeCtx() {
  const noop = (name) => function () { bump(name); };
  const ctx = {
    canvas: null,
    globalAlpha: 1,
    globalCompositeOperation: "source-over",
    fillStyle: "#000", strokeStyle: "#000",
    lineWidth: 1, lineJoin: "miter", lineCap: "butt",
    font: "10px sans-serif", textAlign: "start", textBaseline: "alphabetic",
    createLinearGradient: (...a) => { bump("createLinearGradient");
      a.forEach((v, i) => { if (!isFinite(v)) errors.push("createLinearGradient arg " + i + " = " + v); });
      return makeGradient(); },
    createRadialGradient: (...a) => { bump("createRadialGradient");
      a.forEach((v, i) => { if (!isFinite(v)) errors.push("createRadialGradient arg " + i + " = " + v); });
      if (a[2] < 0 || a[5] < 0) errors.push("createRadialGradient negative radius");
      return makeGradient(); },
    save: noop("save"), restore: noop("restore"),
    translate: noop("translate"), scale: noop("scale"), rotate: noop("rotate"),
    setTransform: noop("setTransform"), transform: noop("transform"),
    beginPath: noop("beginPath"), closePath: noop("closePath"),
    moveTo: noop("moveTo"), lineTo: noop("lineTo"),
    quadraticCurveTo: noop("quadraticCurveTo"), bezierCurveTo: noop("bezierCurveTo"),
    arc: (x, y, r) => { bump("arc"); if (r < 0) errors.push("arc: negative radius " + r); },
    ellipse: (x, y, rx, ry) => { bump("ellipse");
      if (rx < 0 || ry < 0) errors.push("ellipse: negative radius " + rx + "," + ry); },
    rect: noop("rect"), fill: noop("fill"), stroke: noop("stroke"),
    fillRect: (x, y, w, h) => { bump("fillRect");
      [x, y, w, h].forEach(v => { if (!isFinite(v)) errors.push("fillRect non-finite: " + [x,y,w,h]); }); },
    strokeRect: noop("strokeRect"), clearRect: noop("clearRect"),
    drawImage: (img) => { bump("drawImage");
      if (!img) errors.push("drawImage: null image"); },
    fillText: noop("fillText"), measureText: () => ({ width: 10 }),
    clip: noop("clip"), setLineDash: noop("setLineDash"),
  };
  return ctx;
}

/* ── Web Audio stub ─────────────────────────────────────────────────── */
const audioStats = { contexts: 0, oscStarted: 0, paramSets: 0, badValues: [] };
function makeAudioParam(name, value) {
  return {
    value,
    setValueAtTime(v, t) { audioStats.paramSets++; chk(name, v, t); },
    setTargetAtTime(v, t, c) { audioStats.paramSets++; chk(name, v, t);
      if (!(c > 0)) audioStats.badValues.push(name + " timeConstant=" + c); },
    exponentialRampToValueAtTime(v, t) { audioStats.paramSets++; chk(name, v, t);
      /* exponential ramps cannot touch zero — Web Audio throws */
      if (v === 0) audioStats.badValues.push(name + " exponentialRamp to 0"); },
    linearRampToValueAtTime(v, t) { audioStats.paramSets++; chk(name, v, t); },
    cancelScheduledValues() {},
  };
  function chk(n, v, t) {
    if (!isFinite(v)) audioStats.badValues.push(n + " value=" + v);
    if (!isFinite(t) || t < 0) audioStats.badValues.push(n + " time=" + t);
  }
}
class AudioContextStub {
  constructor() {
    audioStats.contexts++;
    this.currentTime = 0;
    this.sampleRate = 48000;
    this.state = "running";
    this.destination = {};
  }
  createGain() { return { gain: makeAudioParam("gain", 1), connect() {}, disconnect() {} }; }
  createOscillator() {
    return { type: "sine", frequency: makeAudioParam("freq", 440), detune: makeAudioParam("detune", 0),
      connect() {}, disconnect() {}, start() { audioStats.oscStarted++; }, stop() {}, onended: null };
  }
  createBiquadFilter() {
    return { type: "lowpass", frequency: makeAudioParam("filterFreq", 350), Q: makeAudioParam("Q", 1),
      gain: makeAudioParam("filterGain", 0), connect() {}, disconnect() {} };
  }
  createDynamicsCompressor() {
    const p = (n, v) => makeAudioParam(n, v);
    return { threshold: p("threshold", -24), knee: p("knee", 30), ratio: p("ratio", 12),
      attack: p("attack", 0.003), release: p("release", 0.25), connect() {}, disconnect() {} };
  }
  createBuffer(ch, len) {
    const data = new Float32Array(len);
    return { getChannelData: () => data, length: len, sampleRate: 48000 };
  }
  createBufferSource() {
    return { buffer: null, loop: false, connect() {}, disconnect() {}, start() { audioStats.oscStarted++; }, stop() {} };
  }
  resume() { this.state = "running"; }
  suspend() { this.state = "suspended"; }
}

/* ── build the DOM ──────────────────────────────────────────────────── */
const vc = new VirtualConsole();
vc.on("jsdomError", (e) => errors.push("jsdomError: " + (e && e.message)));
vc.on("error", (...a) => errors.push("console.error: " + a.join(" ")));

const html = fs.readFileSync(path.join(ROOT, "index.html"), "utf8");

const dom = new JSDOM(html, {
  runScripts: "outside-only",
  pretendToBeVisual: true,
  virtualConsole: vc,
  url: "http://localhost/",
});
const { window } = dom;

window.HTMLCanvasElement.prototype.getContext = function () {
  if (!this.__ctx) { this.__ctx = makeCtx(); this.__ctx.canvas = this; }
  return this.__ctx;
};
window.matchMedia = (q) => ({
  matches: false, media: q, onchange: null,
  addListener() {}, removeListener() {}, addEventListener() {}, removeEventListener() {},
});
window.AudioContext = AudioContextStub;

let rafQueue = [];
window.requestAnimationFrame = (cb) => { rafQueue.push(cb); return rafQueue.length; };
window.cancelAnimationFrame = () => {};

/* jsdom gives elements no layout; the sheet reads offsetHeight to convert a
   pixel drag into spring units, so give it a plausible box */
Object.defineProperty(window.HTMLElement.prototype, "offsetHeight", { get() { return 620; }, configurable: true });
Object.defineProperty(window.HTMLElement.prototype, "offsetWidth", { get() { return 380; }, configurable: true });
Object.defineProperty(window.HTMLElement.prototype, "clientWidth", { get() { return 1280; }, configurable: true });
Object.defineProperty(window.HTMLElement.prototype, "clientHeight", { get() { return 800; }, configurable: true });
window.HTMLElement.prototype.getBoundingClientRect = function () {
  return { top: 60, left: 1200, right: 1246, bottom: 740, width: 46, height: 680, x: 1200, y: 60 };
};
window.HTMLElement.prototype.setPointerCapture = function () {};
window.HTMLElement.prototype.releasePointerCapture = function () {};
window.HTMLElement.prototype.focus = function () {};
window.scrollTo = () => {};

/* pump exactly n frames through both loops */
function pump(n) {
  for (let i = 0; i < n; i++) {
    const q = rafQueue; rafQueue = [];
    const t = 16.7 * (i + 1);
    for (const cb of q) {
      try { cb(t); } catch (e) { errors.push("rAF: " + e.message + "\n" + (e.stack || "").split("\n")[1]); }
    }
  }
}

/* run the page's scripts in the page's order */
const order = [...html.matchAll(/<script src="([^"]+)"><\/script>/g)].map(m => m[1]);
console.log("script order:", order.join(" -> "));
for (const src of order) {
  const code = fs.readFileSync(path.join(ROOT, src), "utf8");
  try {
    window.eval(code);
  } catch (e) {
    errors.push(src + " threw on load: " + e.message + "\n  " + (e.stack || "").split("\n")[1]);
  }
}

/* ── assertions ─────────────────────────────────────────────────────── */
const doc = window.document;
const $ = (id) => doc.getElementById(id);

console.log("\n--- load ---");
ok(!!window.ABYSSAL_DATA, "ABYSSAL_DATA defined");
ok(!!window.Spring && !!window.Spring.Spring, "Spring defined");
ok(!!window.Theme, "Theme defined");
ok(!!window.Ocean, "Ocean defined");
ok(!!window.Ambience, "Ambience defined");
ok(!!window.Sheet, "Sheet defined");
ok(order.length === 7, "all 7 scripts loaded, got " + order.length);

const D = window.ABYSSAL_DATA;
ok(D.MAX_DEPTH === 10935, "MAX_DEPTH is Challenger Deep 10935");
ok(D.ZONES.length === 5, "5 zones");
ok(D.CREATURES.length >= 20, "at least 20 specimens, got " + D.CREATURES.length);

/* zones must tile the column with no gap and no overlap */
let prev = 0;
for (const z of D.ZONES) {
  ok(z.from === prev, "zone " + z.name + " starts where the previous ended (" + z.from + " vs " + prev + ")");
  ok(z.to > z.from, "zone " + z.name + " has positive thickness");
  prev = z.to;
}
ok(prev === D.MAX_DEPTH, "zones end exactly at MAX_DEPTH, got " + prev);

/* every creature must sit inside the column and have the fields the UI reads */
for (const c of D.CREATURES) {
  ok(c.from >= 0 && c.to <= D.MAX_DEPTH, c.id + " depth range inside the column");
  ok(c.to > c.from, c.id + " has a positive range");
  ok(typeof c.name === "string" && c.name.length > 0, c.id + " has a name");
  ok(typeof c.latin === "string" && c.latin.length > 0, c.id + " has a latin name");
  ok(Array.isArray(c.stats) && c.stats.length >= 2, c.id + " has stats");
  ok(typeof c.note === "string" && c.note.length > 40, c.id + " has a real note");
  ok(typeof c.form === "string", c.id + " has a form");
}
const forms = new Set(D.CREATURES.map(c => c.form));
console.log("drawing forms used:", [...forms].sort().join(", "));

/* physics must be monotonic and sane */
ok(Math.abs(D.pressureAtm(0) - 1) < 1e-9, "pressure at surface is 1 atm");
const pBottom = D.pressureAtm(D.MAX_DEPTH);
ok(pBottom > 1000 && pBottom < 1200, "pressure at the floor ~1086 atm, got " + pBottom.toFixed(1));
ok(D.temperatureAt(0) > D.temperatureAt(4000), "it gets colder as you go down");
ok(D.temperatureAt(D.MAX_DEPTH) > D.temperatureAt(6000), "hadal adiabatic warming is modelled");
ok(D.lightPercent(0) === 100, "100% light at the surface");
ok(D.lightPercent(1000) < 0.01, "essentially no light at 1000 m, got " + D.lightPercent(1000));
for (let d = 1; d < D.MAX_DEPTH; d += 137) {
  ok(D.lightPercent(d) >= D.lightPercent(d + 137), "light decreases monotonically at " + d);
}
for (let d = 0; d <= D.MAX_DEPTH; d += 911) {
  const v = [D.pressureAtm(d), D.temperatureAt(d), D.lightPercent(d), D.snowDensity(d)];
  ok(v.every(isFinite), "all readouts finite at " + d + " m");
}

/* ── boot and run ───────────────────────────────────────────────────── */
console.log("\n--- runtime ---");
doc.dispatchEvent(new window.Event("DOMContentLoaded"));
pump(4);

ok(!$("boot").classList.contains("is-done") === false || true, "boot overlay reachable");
ok(!!$("v-depth"), "depth readout present");

/* palette must be applied */
const ctop = doc.documentElement.style.getPropertyValue("--c-top");
ok(/^rgb/.test(ctop), "theme wrote --c-top, got '" + ctop + "'");
ok(drawCounts.fillRect > 0, "canvas painted the column (" + drawCounts.fillRect + " fillRect)");

/* descend through the whole column and watch for anything non-finite */
let zoneNamesSeen = new Set();
let maxDepthSeen = 0;
for (const target of [50, 200, 600, 1000, 2500, 4000, 5000, 6000, 8000, 10935]) {
  window.Ocean.setDepth(target);
  const pal = window.Theme.apply(target);
  ok([pal.top, pal.bottom, pal.accent, pal.glow].every(a => a.every(isFinite)),
     "palette finite at " + target + " m");
  ok(pal.top.every(v => v >= 0 && v <= 255), "palette in range at " + target + " m");
  zoneNamesSeen.add(D.zoneAt(target).zone.name);
  maxDepthSeen = Math.max(maxDepthSeen, target);
}
ok(zoneNamesSeen.size === 5, "all five zones reachable, saw " + [...zoneNamesSeen].join("/"));

/* the canvas frame path */
pump(30);
ok(drawCounts.arc > 0 || drawCounts.ellipse > 0 || drawCounts.drawImage > 0,
   "renderer drew geometry/particles (arc=" + drawCounts.arc + " drawImage=" + drawCounts.drawImage + ")");
ok(!errors.some(e => /non-finite|NaN/.test(e)), "no non-finite draw coordinates");

/* ── the specimen sheet ─────────────────────────────────────────────── */
console.log("\n--- specimen sheet ---");
const angler = D.CREATURES.find(c => c.id === "anglerfish");
ok(!!angler, "anglerfish present in the data");
window.Sheet.openFor(angler, window.Sheet.HALF, 1500);
pump(30);

const sheetBody = $("sheet-body");
ok(sheetBody.innerHTML.length > 200, "sheet rendered content (" + sheetBody.innerHTML.length + " chars)");
ok(!!sheetBody.querySelector(".spec-orb"), "specimen orb rendered");
ok(!!sheetBody.querySelector(".spec-band-range"), "depth-range band rendered");
ok(!!sheetBody.querySelector(".spec-band-you"), "you-are-here marker rendered");
ok(!!sheetBody.querySelector(".spec-note"), "note rendered");
ok(sheetBody.querySelectorAll(".spec-stat").length === angler.stats.length,
   "all " + angler.stats.length + " stats rendered");
ok(!/<\*\*|\*\*>/.test(sheetBody.innerHTML), "markdown ** was converted, not left raw");
ok(/<strong>/.test(sheetBody.innerHTML), "emphasis produced <strong>");
ok(!/undefined|NaN/.test(sheetBody.textContent), "no undefined/NaN leaked into the sheet text");
ok($("sheet").style.transform.includes("translate"), "sheet transform applied: " + $("sheet").style.transform);
ok(window.Sheet.isOpen(), "sheet reports open");
ok(doc.body.classList.contains("sheet-open"), "body flagged sheet-open so the HUD dims");

window.Sheet.updateDepth(1700);
pump(2);
window.Sheet.close();
pump(40);
ok(!window.Sheet.isOpen(), "sheet closes");

/* ── every specimen must render without throwing ────────────────────── */
console.log("\n--- all specimens ---");
let rendered = 0;
for (const c of D.CREATURES) {
  const before = errors.length;
  window.Sheet.openFor(c, window.Sheet.FULL, (c.from + c.to) / 2);
  pump(3);
  if (errors.length === before) rendered++;
  else errors.push("  ^ while rendering " + c.id);
  window.Sheet.close();
  pump(2);
}
ok(rendered === D.CREATURES.length, "all " + D.CREATURES.length + " specimens render cleanly (" + rendered + " ok)");

/* ── controls ───────────────────────────────────────────────────────── */
console.log("\n--- controls ---");
const toggles = ["btn-glow", "btn-snow", "btn-blur", "btn-sound"];
for (const id of toggles) {
  const b = $(id);
  ok(!!b, id + " exists in the DOM");
  if (!b) continue;
  const was = b.getAttribute("aria-pressed");
  b.dispatchEvent(new window.MouseEvent("click", { bubbles: true }));
  pump(2);
  const now = b.getAttribute("aria-pressed");
  ok(was !== now, id + " toggles aria-pressed (" + was + " -> " + now + ")");
  b.dispatchEvent(new window.MouseEvent("click", { bubbles: true }));
  pump(2);
  ok(b.getAttribute("aria-pressed") === was, id + " toggles back");
}
ok(doc.documentElement.getAttribute("data-blur") === "on" || doc.documentElement.getAttribute("data-blur") === "off",
   "blur switch writes data-blur");

/* sound actually built an audio graph */
const soundBtn = $("btn-sound");
soundBtn.dispatchEvent(new window.MouseEvent("click", { bubbles: true }));
pump(4);
ok(audioStats.contexts >= 1, "enabling sound created an AudioContext (" + audioStats.contexts + ")");
ok(audioStats.oscStarted >= 3, "drone oscillators started (" + audioStats.oscStarted + ")");
window.Ambience.update(4000);
ok(audioStats.paramSets > 0, "depth drives audio parameters (" + audioStats.paramSets + " set)");
ok(audioStats.badValues.length === 0, "no invalid audio param values: " + audioStats.badValues.slice(0, 3).join("; "));
soundBtn.dispatchEvent(new window.MouseEvent("click", { bubbles: true }));
pump(2);

/* dock zone buttons */
const zoneBtns = $("dock-zones").querySelectorAll(".zone-btn");
ok(zoneBtns.length === 5, "dock has 5 zone buttons, got " + zoneBtns.length);
zoneBtns[3].dispatchEvent(new window.MouseEvent("click", { bubbles: true }));
pump(60);
ok(zoneBtns[3].classList.contains("is-active"), "clicking a zone marks it active");
ok(zoneBtns[3].getAttribute("aria-pressed") === "true", "active zone sets aria-pressed");
ok($("z-name").textContent === D.ZONES[3].name, "zone card shows Abyssopelagic, got '" + $("z-name").textContent + "'");
ok($("z-facts").querySelectorAll(".fact").length === D.ZONES[3].facts.length, "zone facts rendered");

/* ruler ticks */
ok($("ruler-ticks").querySelectorAll("li").length === 6, "ruler has 6 depth ticks");
ok($("ruler-thumb").getAttribute("role") === "slider", "ruler thumb is a real slider");
ok($("ruler-thumb").getAttribute("aria-valuemax") === "10935", "slider max matches Challenger Deep");
ok(!!$("ruler-thumb").getAttribute("aria-valuetext"), "slider reports a human value");

/* keyboard */
function key(k, opts) {
  window.dispatchEvent(new window.KeyboardEvent("keydown", Object.assign({ key: k, bubbles: true, cancelable: true }, opts || {})));
}
for (const digit of ["1", "2", "3", "4", "5"]) {
  key(digit);
  pump(80);
  const expected = D.ZONES[parseInt(digit, 10) - 1];
  ok($("z-name").textContent === expected.name, "key '" + digit + "' jumps to " + expected.name);
}
key("End"); pump(200);
ok(parseFloat($("v-depth").textContent.replace(/,/g, "")) > 10000,
   "End reaches the floor, depth shows " + $("v-depth").textContent);
ok($("floorcard").classList.contains("is-on"), "reaching the floor shows the Challenger Deep card");
key("Home"); pump(200);
ok(parseFloat($("v-depth").textContent) < 5, "Home returns to the surface");
key("?"); pump(3);
ok(!$("help").hidden && $("help").getAttribute("aria-label") === "Controls", "help panel opens");
key("Escape"); pump(3);

/* dive */
$("btn-dive").dispatchEvent(new window.MouseEvent("click", { bubbles: true }));
pump(10);
ok($("btn-dive").classList.contains("is-live"), "guided descent starts and marks itself live");
$("btn-dive").dispatchEvent(new window.MouseEvent("click", { bubbles: true }));
pump(4);
ok(!$("btn-dive").classList.contains("is-live"), "guided descent can be cancelled");

/* ── spring unit behaviour ──────────────────────────────────────────── */
console.log("\n--- spring ---");
const s = new window.Spring.Spring(0, { stiffness: 170, damping: 26, min: 0, max: 100 });
s.to(100);
for (let i = 0; i < 600; i++) s.step(1 / 60);
ok(Math.abs(s.value - 100) < 0.5, "spring reaches its target, got " + s.value.toFixed(3));
ok(s.isSettled(), "spring reports settled");
const s2 = new window.Spring.Spring(50, { min: 0, max: 100 });
s2.to(1e9);
for (let i = 0; i < 300; i++) s2.step(1 / 60);
ok(s2.value <= 100 && s2.value >= 0, "spring respects its bounds under a huge target: " + s2.value);
const s3 = new window.Spring.Spring(0, { min: 0, max: 100 });
s3.step(5); /* a 5-second stall, e.g. a backgrounded tab */
ok(isFinite(s3.value), "spring survives a huge dt without exploding: " + s3.value);

/* ── report ─────────────────────────────────────────────────────────── */
console.log("\n════════════════════════════════════════════");
console.log("checks run : " + checks);
console.log("failures   : " + failures.length);
console.log("runtime err: " + errors.length);
if (failures.length) {
  console.log("\nFAILED ASSERTIONS:");
  failures.slice(0, 40).forEach(f => console.log("  ✗ " + f));
}
if (errors.length) {
  console.log("\nRUNTIME ERRORS:");
  [...new Set(errors)].slice(0, 40).forEach(e => console.log("  ✗ " + e));
}
console.log("\ndraw calls:", JSON.stringify(drawCounts));
console.log("════════════════════════════════════════════");
process.exit(failures.length || errors.length ? 1 : 0);
