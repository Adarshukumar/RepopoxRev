# ABYSSAL

**An interactive descent through the five depth zones of the ocean** — from the sunlit
surface to the Challenger Deep at 10,935 m.

You drag the water to sink. The colour of the ocean, the interface wrapped around it,
the animals drifting past, the readouts on the instruments and the sound of the place
all change continuously with depth. Tap an animal to open its specimen card.

No images. No audio files. No dependencies, no build step, no network. Everything you
see is drawn procedurally on a canvas and everything you hear is synthesised at
runtime, from a palette and a set of depth ranges that live in one data file.

---

## Running it

Unzip and open `index.html`. That is the whole instruction.

```bash
unzip abyssal.zip && cd abyssal && open index.html      # macOS
unzip abyssal.zip && cd abyssal && xdg-open index.html  # Linux
```

It works straight off `file://` — deliberately. The scripts are classic
`<script src>` tags rather than ES modules, because modules are CORS-blocked on
`file://` and would leave you with a blank page. All data is inline in
`js/data.js` rather than fetched from JSON, for the same reason. Fonts are system
stacks, so nothing is pulled from a CDN.

If you would rather serve it:

```bash
cd abyssal && python3 -m http.server 8080
```

---

## Controls

| Input | Action |
| --- | --- |
| Drag the water | descend and ascend; flick to coast |
| Scroll wheel | descend, with a little extra throw |
| `↑ ↓ ← →` | move by a fraction of the visible column |
| `Shift` + arrow | move further |
| `Page Up` / `Page Down` | jump several hundred metres |
| `Home` / `End` | surface, or the Challenger Deep |
| `1`–`5` | jump straight to a zone |
| `D` | start or stop the guided descent |
| `G` / `S` / `M` | bioluminescence / marine snow / sound |
| `B` | glass blur, for slower devices |
| `?` | show the controls panel |
| `Esc` | close the specimen panel |
| Tap an animal | open its specimen card |
| Ruler thumb | scrub the whole column — it is a real slider |

---

## The animation vocabulary

This app was built as an exercise in reusing the animation language of
[Mochy Music](https://github.com/realidkroo/Mochy-Music) — the Android player whose
source is vendored in this repository at `../Mochy-Music`, analysed in
`../learning-build`. The interesting thing about Mochy's motion is that almost none of
it is decorative: each animation is driven by a real value in the app's state. The
same is true here, and the mapping is close to one-to-one.

| Mochy Music | ABYSSAL | The shared idea |
| --- | --- | --- |
| `ui/theme` derives the whole palette from the album artwork | `js/theme.js` derives the whole palette from **depth** | Interface colour is a function of content, never a hard-coded constant |
| `BottomSheetPlayer.kt` — draggable, spring-settled, three stops | `js/sheet.js` — the specimen panel | Input moves a *target*; a spring moves the *rendered value* |
| `LyricsGlowLine.kt` — the active line glows | bioluminescent lure, photophores and glints | Light as an annotation of what matters right now |
| `MorphingCover.kt` — artwork morphs between tracks | zone crossfades and the specimen orb | State change as a continuous transition, never a cut |
| Staggered list reveals | `.spec-stagger`, boot sequence, HUD entrances | Sequence the elements so the eye can follow |
| Progressive vignette and blur behind the player | `#vignette` tightens as the light dies | The chrome reacts to the content behind it |
| Blur is user-disableable, because Mochy's own FAQ says so | `B` / the blur button, `html[data-blur="off"]` | A performance escape hatch is a feature, not a failure |

Two of those are worth a sentence each.

**Depth-derived palette.** There is no CSS rule anywhere in `abyssal.css` that
hard-codes a water colour. `theme.js` interpolates the zone stops and writes
`--c-top`, `--c-bottom`, `--c-accent`, `--c-glow`, `--c-text`, `--c-glass`,
`--c-line` and `--depth01` as custom properties. The interface recolours itself as
you sink, including the direction of the text contrast: near the surface the water is
bright and the text goes dark, and that flip is driven by the *relative luminance* of
the water behind it rather than by the zone index, so it always happens early enough
to stay legible.

**The spring.** `js/spring.js` is a fixed-timestep integrator, semi-implicit Euler
sub-stepped at 1/120 s so it behaves identically at 60 Hz and 144 Hz and cannot
explode after a stalled tab. Every moving part of the app is one of these: the depth
itself, and the sheet's position. A flick is judged by *velocity* rather than by
distance travelled, which is what makes the descent coast and settle like something
with mass instead of stopping where your finger stopped.

---

## Structure

```
abyssal/
├── index.html            the DOM, and the script order that matters
├── css/abyssal.css       every rule; no water colour is hard-coded in here
├── js/
│   ├── data.js           the science: 5 zones, 28 specimens, 4 physics models
│   ├── spring.js         fixed-timestep spring integrator
│   ├── theme.js          depth → palette → CSS custom properties
│   ├── canvas.js         the renderer: water, light, snow, animals
│   ├── audio.js          procedural depth-reactive ambience
│   ├── sheet.js          draggable specimen panel with snap stops
│   └── app.js            owns depth; wires every input to it
└── tools/smoke.js        runs the whole app in jsdom and asserts on it
```

The dependency graph is a straight line and each module has exactly one job:

```
data.js ──> spring.js ──> theme.js ──> canvas.js ──> audio.js ──> sheet.js ──> app.js
```

`app.js` owns the single source of truth — **depth**. Every input writes to a target
depth; a spring moves the rendered depth toward it; and that one number is what
`theme.js` turns into a palette, `canvas.js` into a camera, `audio.js` into a filter
cutoff, and the HUD into numbers. Nothing downstream knows about anything else
downstream, which is why it all stays in sync without coordination.

### Loading order is load-bearing

The scripts are plain `<script>` tags and run in document order, so `spring.js` must
come before `theme.js` (which calls `Spring.smooth`) and `theme.js` before `canvas.js`
(which calls `Theme.apply`). There is no module system to catch a reordering. This is
the one real cost of making the app work from `file://`, and it is why the order is
asserted by the smoke test rather than left to memory.

---

## The science

The numbers are the commonly cited ones from the marine biology literature, not
invented to look impressive. `js/data.js` is the only place any of them live.

**The five zones** tile the column exactly, with no gaps and no overlaps — the smoke
test asserts this rather than trusting it:

| Zone | Range | |
| --- | --- | --- |
| Epipelagic | 0–200 m | the sunlight zone; ~90% of marine life |
| Mesopelagic | 200–1,000 m | the twilight zone; the nightly vertical migration |
| Bathypelagic | 1,000–4,000 m | the midnight zone; no sunlight at all |
| Abyssopelagic | 4,000–6,000 m | the abyssal plain; more surface than all land |
| Hadalpelagic | 6,000–10,935 m | trenches only; named for Hades |

**The physics**, all four models explicit and monotonic:

- **Pressure** — `1 + d/10.06` atm. At the Challenger Deep this gives ~1,086 atm
  against a measured ~1,086, so the readout is honest rather than decorative.
- **Temperature** — piecewise-linear through ten control points: 24.5 °C at the
  surface, down the permanent thermocline to 1.9 °C at 6,000 m, then *rising* slightly
  to 2.6 °C at the floor. That last rise is real — adiabatic compression warms trench
  water — and it is the detail most casual treatments of the deep get wrong.
- **Light** — `100 · e^(−0.045d)`, so ~64% at 10 m, ~10% at 50 m, ~1% at 100 m and
  indistinguishable from zero below 1,000 m. It drives the light shafts, the surface glow, and whether animals are
  drawn as lit bodies or as silhouettes.
- **Marine snow** — flux that thickens with depth and then thins as there is less left
  to fall.

**28 specimens**, each with a real depth range, a real binomial, and a note about the
thing that is actually interesting about it. The forms are drawn procedurally — 13
distinct drawing routines (`drift`, `jelly`, `fish`, `school`, `ray`, `cetacean`,
`glider`, `squid`, `chain`, `angler`, `eel`, `crawler`, `sessile`) — so the anglerfish
lure beats, the siphonophore's pulse travels down the colony, the eel is a single
tapered travelling sine with no rigid body at all, and the bait ball turns as a wave
rather than as a decision.

A few of the things in there: the barrel-eye's transparent dome, confirmed only in
2004 because every net-caught specimen since 1939 had destroyed it. The vampire
squid, which ejects glowing mucus instead of ink. The male anglerfish, which fuses to
the female and dissolves. *Praya dubia*, a colony of clones at 40 m — longer than a
blue whale, and the longest animal known. And the hadal snailfish, which stops
existing below ~8,200 m because stabilising proteins against that much pressure
eventually costs more than the animal can pay.

---

## Performance

The expensive thing in a canvas full of glowing animals is the glow. `shadowBlur`
costs a blur pass per draw call, and a few hundred of them per frame will not hold 60
fps on a phone.

So every glow colour is pre-rendered **once** into a 64×64 offscreen canvas and then
stamped with `drawImage`, scaled. The smoke test records the call counts from a full
run through the column: **11 `createRadialGradient` calls against 109,472
`drawImage` calls**. One gradient per colour, then pure bitmap blits forever after.

The rest of the budget:

- Device pixel ratio capped at 2. Beyond that the extra fill rate buys nothing
  visible on a phone panel.
- Particle counts scale with viewport area, clamped to sane bounds, and drop to ~40%
  under `prefers-reduced-motion`.
- Specimens are recycled rather than regenerated: anything that falls well behind the
  camera is re-seeded ahead of it, so memory stays flat no matter how far you descend
  and a long run never runs out of animals.
- `theme.js` quantises its output key and skips the DOM writes entirely when nothing
  changed. Custom properties invalidate style for the whole document, so writing them
  unconditionally every frame is measurable.
- The HUD's `textContent` writes are guarded the same way, so a steady depth does not
  dirty layout 60 times a second.
- The canvas context is `{ alpha: false }` and paints its own gradient, avoiding a
  compositing pass over the full screen.
- The audio context suspends when the tab is hidden, and the frame loop zeroes its
  `last` timestamp on visibility change so a backgrounded tab does not return with one
  enormous `dt`.

---

## Accessibility

- The ruler thumb is a real `role="slider"` with `aria-valuemin`, `aria-valuemax`,
  `aria-valuenow` and a spoken `aria-valuetext` (`"4,000 metres, Abyssopelagic zone"`),
  and it is fully keyboard-operable.
- Zone buttons carry `aria-pressed`; the four toggles carry `aria-pressed` and a
  `title` that states the current condition rather than the action.
- A visually-hidden `aria-live="polite"` region announces zone changes, with a
  coalescing timer — a drag would otherwise queue dozens of announcements a second.
- Focus moves into the specimen panel when it opens and the panel leaves the tab order
  when it is down.
- Every focusable element has a `:focus-visible` ring.
- `@media (prefers-reduced-motion: reduce)` collapses all animation and transition
  durations, hides the film grain, and is read by the JS too, so the canvas stops
  drifting particles and the zone card stops crossfading.
- Glass blur is a user-facing switch, not a hidden one.

---

## Verification

```bash
node tools/smoke.js
```

The app has no dependencies; the test has one (`npm install jsdom`).

`node --check` only proves the files parse. This loads `index.html` in a real DOM,
runs all seven scripts in the page's own order, stubs canvas and Web Audio (recording
every call), and then *drives the app*: it descends the full column, opens all 28
specimen cards, toggles all four controls both ways, jumps to all five zones by
keyboard, hits the floor and checks the Challenger Deep card appears, starts and
cancels the guided descent, and unit-tests the spring's bounds and its behaviour under
a five-second stall.

Current result: **399 checks, 0 failures, 0 runtime errors.**

Things it has caught that a syntax check could not:

- a specimen whose depth range extended to 11,000 m — deeper than the ocean's floor;
- a `stamp()` helper that reset `globalAlpha` to 1 on exit, silently undoing the
  edge-fade of every animal body drawn after its own glow;
- controls wired up in JS to elements that did not exist in the HTML;
- classes written by JS with no rule in the stylesheet, and rules in the stylesheet
  with nothing to produce them;
- non-finite gradient stops and negative radii, which canvas accepts quietly and
  renders as nothing.

The audit that runs alongside it is also in the test: every ID the JS looks up must
exist in the HTML, and every class the JS writes must exist in the CSS. That check is
what found the missing elements — the HTML, CSS and JS were written at different times
and had drifted apart, and nothing but cross-referencing all three would have shown it.

---

## Notes on the design

**Why logarithmic depth.** Both the ruler and the specimen card's range band map depth
to position logarithmically. Linear would spend 55% of the rail on the hadal zone
alone and compress the top 200 m — where almost all marine life lives — into a sliver
too small to aim at. The two use the same mapping function, so they can never disagree
about where a given depth sits.

**Why the visible span grows with depth.** The camera shows `42 + √depth · 6.4` metres
of column. Square root, not linear: near the surface you are framing tens of metres
and the animals are close and large, while in a trench you are framing hundreds and
they are small and sparse. That matches how the water actually feels, and it stops the
deep zones from being either empty or an unreadable pile of overlapping animals.

**Why the gradient window is local.** `theme.js` paints from `depth − 45 m` to
`depth + 340 m` rather than from the surface to the floor. Mapping the whole 10,935 m
column onto one viewport would leave the entire screen a single flat black at depth,
with no falloff to read — and the falloff is most of the sense of descending.

**Why the colour ramps chain.** Each zone's `top` is the previous zone's `bottom`, so
the interpolation has no seam at a boundary. Blending in gamma-encoded sRGB is
technically wrong and is done anyway: in linear light these particular dark-to-darker
ramps go muddy in the mid-blues.

---

## Licence

MIT. The specimen data and physics models are summaries of published marine biology;
the prose is original. See `../Mochy-Music-VENDORED.md` for the licence position of the
vendored Mochy Music source, which is GPL-3.0 and belongs to its own authors.

ABYSSAL shares no code with Mochy Music. What it borrows is the approach: bind every
animation to a real value in the app's state, and the motion stops being decoration.
