/* ═══════════════════════════════════════════════════════════════════════
   ABYSSAL — data
   The ocean column, its five zones, and the animals that live in each.
   Depth ranges and figures are the commonly cited ones from the marine
   biology literature; nothing here is invented to look impressive.

   Declared on a single global (ABYSSAL_DATA) rather than exported as a
   module, so the unzipped app also runs straight off file://.
   ═══════════════════════════════════════════════════════════════════════ */
window.ABYSSAL_DATA = (function () {
  "use strict";

  /* Deepest surveyed point in the ocean: the Challenger Deep, Sirena Deep
     basin, Mariana Trench. 10,935 m is the 2021 multibeam figure. */
  var MAX_DEPTH = 10935;

  /* ── the five pelagic zones ───────────────────────────────────────────
     `top` is the colour at the top of the zone, `bottom` at its base.
     theme.js interpolates between consecutive stops, so these are the
     only place a water colour is ever specified.                     */
  var ZONES = [
    {
      id: "epipelagic",
      name: "Epipelagic",
      alias: "The Sunlight Zone",
      from: 0, to: 200,
      top: "#79dce6", bottom: "#0d5f88",
      accent: "#eafcff", glow: "#9ff3ff", ink: "#04141f",
      note: "Everything the ocean is famous for lives in this thin bright skin — and virtually all of its photosynthesis happens here, in the top two percent of its volume.",
      facts: [
        ["Light", "enough for photosynthesis"],
        ["Holds", "~90% of marine life"],
        ["Mixed by", "wind and waves"]
      ]
    },
    {
      id: "mesopelagic",
      name: "Mesopelagic",
      alias: "The Twilight Zone",
      from: 200, to: 1000,
      top: "#0d5f88", bottom: "#03253f",
      accent: "#8fd8ff", glow: "#63d8ff", ink: "#03121e",
      note: "Blue only, and barely that. This is where the largest migration on Earth happens every night — billions of animals rise to feed in the dark and sink before dawn.",
      facts: [
        ["Light", "1% falling to 0.001%"],
        ["Biomass", "possibly 10× all fisheries"],
        ["Nightly", "the deep scattering layer rises"]
      ]
    },
    {
      id: "bathypelagic",
      name: "Bathypelagic",
      alias: "The Midnight Zone",
      from: 1000, to: 4000,
      top: "#03253f", bottom: "#010c18",
      accent: "#7ad4ff", glow: "#59f0d8", ink: "#02101b",
      note: "No sunlight reaches here at all. Every photon in the midnight zone was made by an animal — and most of the light produced is blue-green, because that is the colour seawater absorbs least.",
      facts: [
        ["Light", "none; only bioluminescence"],
        ["Species", "~90% make their own light"],
        ["Food", "marine snow, and each other"]
      ]
    },
    {
      id: "abyssopelagic",
      name: "Abyssopelagic",
      alias: "The Abyssal Zone",
      from: 4000, to: 6000,
      top: "#010c18", bottom: "#00050a",
      accent: "#6fd8c8", glow: "#4ff0c0", ink: "#010c14",
      note: "Near-freezing, utterly dark, and covering more of the planet's surface than every continents combined. The floor is a plain of sediment accumulating at a few millimetres per thousand years.",
      facts: [
        ["Temp", "~2–3 °C, permanently"],
        ["Floor", "abyssal plain, half the planet"],
        ["Snowfall", "~1 mm per 1,000 years"]
      ]
    },
    {
      id: "hadalpelagic",
      name: "Hadalpelagic",
      alias: "The Hadal Zone",
      from: 6000, to: MAX_DEPTH,
      top: "#00050a", bottom: "#000203",
      accent: "#9fb8ff", glow: "#8fb0ff", ink: "#010a12",
      note: "Named for Hades. Only in trenches, and only a fraction of a percent of the ocean floor. The pressure here would be like balancing fifty jumbo jets on a person — and yet things live here.",
      facts: [
        ["Pressure", "over 1,000 atm"],
        ["Floor", "trenches, <0.25% of seabed"],
        ["Named for", "Hades, not abyss"]
      ]
    }
  ];

  /* ── specimens ──────────────────────────────────────────────────────
     form    which procedural drawing routine renders it (see canvas.js)
     from/to real depth range in metres
     glow    bioluminescence colour; null for animals that make no light
     size    drawn scale in pixels at rest
  */
  var CREATURES = [
    // ── Epipelagic ────────────────────────────────────────────────────
    { id: "phyto", name: "Phytoplankton", latin: "various, incl. diatoms",
      from: 0, to: 120, form: "drift", size: 2.4, glow: "#a8f5ff", count: 34,
      note: "Drifting plants and plant-like protists, mostly invisible individually and collectively responsible for **roughly half of all photosynthesis on Earth**. Every breath you take is substantially their work.",
      stats: [["Size", "2–200 µm"], ["Produces", "~50% of O₂"], ["Drifts", "with current"]] },

    { id: "moonjelly", name: "Moon jelly", latin: "Aurelia aurita",
      from: 0, to: 200, form: "jelly", size: 34, glow: "#bfe9ff",
      note: "A bell of 95% water pulsing a muscle older than vertebrates. It has no brain, no blood and no heart — a diffuse nerve net is enough to swim and to feed, and has been for **over 500 million years**.",
      stats: [["Bell", "25–40 cm"], ["Water", "~95%"], ["Age of lineage", "500 Myr"]] },

    { id: "turtle", name: "Green sea turtle", latin: "Chelonia mydas",
      from: 0, to: 100, form: "glider", size: 46, glow: null,
      note: "An air-breathing reptile that must surface to breathe but can sleep on the seabed for hours, slowing its heart to a few beats a minute. It navigates thousands of kilometres home to nest using **the Earth's magnetic field**.",
      stats: [["Length", "1–1.5 m"], ["Breath-hold", "up to 5 h resting"], ["Navigates by", "magnetism"]] },

    { id: "dolphin", name: "Bottlenose dolphin", latin: "Tursiops truncatus",
      from: 0, to: 100, form: "cetacean", size: 62, glow: null,
      note: "It sees with sound. A click train returns from a fish in milliseconds, and the returning image is resolved well enough to detect **a swim bladder through sand**. Dolphins dive past the depth their lungs can survive by collapsing them.",
      stats: [["Dives to", "~300 m"], ["Clicks", "up to 130 dB"], ["Sleeps", "half a brain at a time"]] },

    { id: "manta", name: "Oceanic manta ray", latin: "Mobula birostris",
      from: 0, to: 150, form: "ray", size: 74, glow: null,
      note: "The largest ray alive, gliding on wings that can span seven metres. It has the **biggest brain of any fish** and visits cleaning stations repeatedly, apparently recognising the individuals working there.",
      stats: [["Wingspan", "up to 7 m"], ["Weight", "up to 2 t"], ["Brain", "largest of any fish"]] },

    { id: "sardine", name: "Sardine bait ball", latin: "Sardinops spp.",
      from: 0, to: 80, form: "school", size: 90, glow: null, count: 60,
      note: "A shoal with no leader and no plan. Each fish tracks a handful of neighbours, and from that **a single shimmering body** emerges that a predator cannot pick apart — the flash of the turn arrives as a wave, faster than any one fish could decide.",
      stats: [["Individuals", "thousands"], ["Coordination", "no leader"], ["Turn speed", "a wave, not a decision"]] },

    // ── Mesopelagic ───────────────────────────────────────────────────
    { id: "lanternfish", name: "Lanternfish", latin: "Myctophidae",
      from: 200, to: 1000, form: "fish", size: 20, glow: "#7ce8ff", count: 9,
      note: "Rows of light organs along the belly, each matched to the faint glow filtering down from above — so a predator looking up sees no silhouette. This **counterillumination** is the most common use of bioluminescence in the ocean, and lanternfish may be the most numerous vertebrates on the planet.",
      stats: [["Length", "2–30 cm"], ["Biomass", "possibly 550 Mt"], ["Rises", "every night"]] },

    { id: "hatchetfish", name: "Silver hatchetfish", latin: "Argyropelecus",
      from: 200, to: 1000, form: "fish", size: 17, glow: "#9df0ff", count: 5,
      note: "A body flattened to a blade and mirrored with guanine crystals, so from the side there is almost nothing to see. Its tubular eyes point permanently upward, watching for the **shadow of a predator against the last of the light**.",
      stats: [["Length", "~5 cm"], ["Sides", "mirrored"], ["Eyes", "fixed upward"]] },

    { id: "vampsquid", name: "Vampire squid", latin: "Vampyroteuthis infernalis",
      from: 300, to: 1000, form: "squid", size: 30, glow: "#8fb4ff",
      note: "Neither vampire nor squid, and the only member of its order. It lives in the **oxygen minimum zone** where almost nothing else can, and when threatened it turns itself inside out — webbing its arms over its body into a spined cloak — then ejects a cloud of glowing mucus instead of ink.",
      stats: [["Length", "~30 cm"], ["Habitat", "oxygen minimum zone"], ["Defence", "glowing mucus"]] },

    { id: "swordfish", name: "Swordfish", latin: "Xiphias gladius",
      from: 200, to: 900, form: "fish", size: 88, glow: null,
      note: "It hunts cold and deep, then warms its eyes and brain with a modified muscle so it can still see clearly. The bill is not a rapier — high-speed work suggests it is **swung sideways to stun and injure** rather than stabbed.",
      stats: [["Length", "up to 4.5 m"], ["Speed", "~100 km/h bursts"], ["Warms", "eyes and brain"]] },

    { id: "siphonophore", name: "Siphonophore", latin: "order Siphonophora",
      from: 200, to: 1000, form: "chain", size: 120, glow: "#79e6ff",
      note: "Not an animal but a **colony of clones**, each specialised into swimming, feeding, digesting or reproducing, all descended from a single fertilised egg. One species, *Praya dubia*, reaches 40 metres — longer than a blue whale and the longest animal known.",
      stats: [["Longest", "~40 m (Praya dubia)"], ["Body plan", "a colony, not an individual"], ["Zooids", "clones, specialised"]] },

    { id: "chimaera", name: "Chimaera", latin: "subclass Holocephali",
      from: 200, to: 1000, form: "glider", size: 52, glow: null,
      note: "A shark's cousin that split from it **400 million years ago**, with a single gill cover instead of five and a venomous spine before the dorsal fin. Its upper jaw is fused to the skull, and it crushes shellfish with pavement-like teeth.",
      stats: [["Split from sharks", "400 Myr ago"], ["Gill openings", "one, covered"], ["Defence", "venomous spine"]] },

    // ── Bathypelagic ──────────────────────────────────────────────────
    { id: "anglerfish", name: "Humpback anglerfish", latin: "Melanocetus johnsonii",
      from: 1000, to: 4000, form: "angler", size: 34, glow: "#ffcf6b",
      note: "The lure is not hers. It is a colony of **bioluminescent bacteria** housed in a bulb at the end of a modified dorsal spine, fed and aimed by the fish. The male is a tenth her size, finds her by scent in total darkness, bites on — and fuses to her body, dissolving into a permanent source of sperm.",
      stats: [["Lure", "symbiotic bacteria"], ["Male", "fuses to the female"], ["Jaws", "stomach stretches"]] },

    { id: "gulper", name: "Gulper eel", latin: "Eurypharynx pelecanoides",
      from: 500, to: 3000, form: "eel", size: 66, glow: "#ff8fa3",
      note: "Almost all mouth. The jaws barely meet, but the stomach inflates into a pelican's pouch, letting it swallow prey **larger than its own body**. Its tail ends in a small pink light organ thought to lure the curious close enough to eat.",
      stats: [["Length", "up to 1 m"], ["Mouth", "most of the animal"], ["Tail tip", "a pale lure"]] },

    { id: "giantsquid", name: "Giant squid", latin: "Architeuthis dux",
      from: 300, to: 1000, form: "squid", size: 130, glow: null,
      note: "For centuries known only from beached carcasses and the beaks found in sperm whale stomachs; first filmed alive in its own habitat in **2004**. Its eye is the largest of any animal — up to 27 cm across — which at depth is for detecting the faint displacement of bioluminescent light around an approaching whale.",
      stats: [["Length", "up to 13 m"], ["Eye", "up to 27 cm, largest known"], ["Filmed alive", "2004"]] },

    { id: "dumbo", name: "Dumbo octopus", latin: "genus Grimpoteuthis",
      from: 1000, to: 4800, form: "jelly", size: 30, glow: null,
      note: "The deepest-living octopus known, swimming by flapping two ear-like fins. It has no ink sac — **there is no light down here to hide from** — and it swallows prey whole, having lost the radula its shallow relatives rasp with.",
      stats: [["Depth record", "~7,000 m"], ["Ink sac", "absent"], ["Swims by", "flapping fins"]] },

    { id: "barreleye", name: "Barreleye", latin: "Macropinna microstoma",
      from: 600, to: 2500, form: "fish", size: 22, glow: "#8ef0c8",
      note: "Its head is a **transparent fluid-filled dome**, and its eyes are two green barrels inside it that rotate from pointing forward to pointing straight up through the skull. Found in 1939, the dome was destroyed in every net-caught specimen for decades, so nobody knew it existed until 2004.",
      stats: [["Head", "transparent dome"], ["Eyes", "rotate within it"], ["Dome confirmed", "2004"]] },

    { id: "fangtooth", name: "Fangtooth", latin: "Anoplogaster cornuta",
      from: 500, to: 5000, form: "angler", size: 18, glow: null,
      note: "Proportionally the **largest teeth of any fish in the ocean** — so long that the lower pair slots into specialised sockets in the roof of its skull, letting it close its mouth at all. Juveniles look so different from adults that they were classified as separate species.",
      stats: [["Teeth", "longest relative to body"], ["Closes", "into skull sockets"], ["Young", "once a separate species"]] },

    { id: "swallower", name: "Black swallower", latin: "Chiasmodon niger",
      from: 700, to: 2700, form: "eel", size: 40, glow: null,
      note: "Its stomach is elastic enough to engulf prey several times its own mass, but the meal sometimes proves fatal: specimens have been found floating near the surface with **undigested fish far too large inside them**, the prey's decay gas lifting them up.",
      stats: [["Prey", "up to 10× its mass"], ["Stomach", "vastly distensible"], ["Risk", "sometimes fatal"]] },

    // ── Abyssopelagic ─────────────────────────────────────────────────
    { id: "seapig", name: "Sea pig", latin: "Scotoplanes globosa",
      from: 1000, to: 5000, form: "crawler", size: 22, glow: null, count: 6,
      note: "A sea cucumber that walks. Water-filled tube feet inflate and deflate to carry it across the sediment in **herds of several hundred, all facing the same way** into the current, filtering the fresh-fallen detritus out of the mud.",
      stats: [["Length", "10–15 cm"], ["Walks on", "water-inflated tube feet"], ["Gathers", "in herds"]] },

    { id: "grenadier", name: "Abyssal grenadier", latin: "Coryphaenoides",
      from: 1000, to: 6000, form: "fish", size: 44, glow: null,
      note: "One of the few fish that genuinely lives on the abyssal floor rather than hanging above it. It tapers to a thread-like tail and **produces its own light from a gland around the anus**, pulsed in a rhythm thought to signal to others of its kind.",
      stats: [["Habitat", "on the seafloor"], ["Light organ", "perianal gland"], ["Tail", "tapers to a thread"]] },

    { id: "giantamphipod", name: "Giant amphipod", latin: "Alicella gigantea",
      from: 4000, to: 6000, form: "crawler", size: 30, glow: null,
      note: "A shrimp-relative grown to **34 cm** where its shallow cousins are a few millimetres — deep-sea gigantism, whose cause is still argued but which likely involves cold, slow metabolism and the value of storing food when meals are rare. One was filmed eating a whale fall for weeks.",
      stats: [["Length", "up to 34 cm"], ["Shallow kin", "a few mm"], ["Phenomenon", "deep-sea gigantism"]] },

    { id: "tripodfish", name: "Tripod fish", latin: "Bathypterois grallator",
      from: 2000, to: 6000, form: "crawler", size: 36, glow: null,
      note: "It stands on the sediment on **three elongated fin rays**, facing into the current, and waits — nearly motionless, for hours — for drift to arrive. Its eyes are vestigial and it detects prey largely by touch, which in total darkness is the cheaper sense to maintain.",
      stats: [["Stance", "three fin rays"], ["Eyes", "vestigial"], ["Hunts by", "touch, facing current"]] },

    { id: "seaspider", name: "Giant sea spider", latin: "Colossendeis",
      from: 1000, to: 6000, form: "crawler", size: 34, glow: null,
      note: "Another case of deep-sea gigantism, and a body plan with a problem solved oddly: spiders this large cannot get enough oxygen through their legs alone, so they have **a second, muscular heart** dedicated to pumping blood into them.",
      stats: [["Leg span", "up to 70 cm"], ["Hearts", "two — one for the legs"], ["Gut", "branches into the legs"]] },

    { id: "xenophyophore", name: "Xenophyophore", latin: "class Xenophyophorea",
      from: 2000, to: 6000, form: "sessile", size: 30, glow: null, count: 4,
      note: "A **single-celled organism** that grows to 20 cm — among the largest cells known. It builds a branching test of cemented sediment and mucus, and is so abundant on the abyssal plain that in places it is the dominant habitat, sheltering everything else that lives there.",
      stats: [["Cells", "one"], ["Size", "up to 20 cm"], ["Builds", "a cemented test"]] },

    // ── Hadalpelagic ──────────────────────────────────────────────────
    { id: "snailfish", name: "Hadal snailfish", latin: "Pseudoliparis swirei",
      from: 6000, to: 8200, form: "fish", size: 26, glow: null,
      note: "The deepest fish ever collected, and it looks like nothing so much as a tadpole — translucent, scaleless, gelatinous. Above roughly **8,200 m the chemistry stops working**: stabilising the proteins against that much pressure costs more than the animal can pay, so fish simply cannot exist below it.",
      stats: [["Collected at", "8,178 m"], ["Theoretical limit", "~8,200 m"], ["Body", "translucent, scaleless"]] },

    { id: "hadalamphipod", name: "Hadal amphipod", latin: "Hirondellea gigas",
      from: 6000, to: MAX_DEPTH, form: "crawler", size: 20, glow: null, count: 8,
      note: "Found in virtually every hadal trench, including the bottom of Challenger Deep. It digests sunken wood with an enzyme of its own, and coats itself in a **gel that scavenges aluminium from the seawater** to shield its shell from dissolving under the pressure.",
      stats: [["Length", "~5 cm"], ["Range", "to 10,900 m"], ["Armour", "aluminium gel"]] },

    { id: "hadalcucumber", name: "Hadal sea cucumber", latin: "Myriotrochidae",
      from: 6000, to: 10000, form: "crawler", size: 24, glow: null,
      note: "Among the most abundant large animals in the trenches, swimming weakly above the sediment on rows of papillae. Where a whale carcass lands, they arrive in numbers and **strip the bone clean** — one of the few reliable meals in a food desert.",
      stats: [["Habitat", "trench floors"], ["Locomotion", "weak swimming"], ["Feeds on", "falls and detritus"]] }
  ];

  /* ── physics of the water column ────────────────────────────────────
     Deliberately simple and explicit: these are the models the readouts
     are computed from, and they are all monotonic so the interpolation
     can never produce a nonsense value.                          */

  /* Temperature profile, °C, as (depth, temp) control points. The steep
     drop between 200 m and 1,000 m is the permanent thermocline. The
     slight rise in the hadal zone is real — adiabatic compression warms
     trench water by a degree or two. */
  var TEMP_POINTS = [
    [0, 24.5], [50, 22.0], [200, 18.0], [500, 9.0], [1000, 5.5],
    [2000, 3.6], [4000, 2.4], [6000, 1.9], [9000, 2.4], [MAX_DEPTH, 2.6]
  ];

  function pressureAtm(depth) {
    /* ~1 atm per 10.06 m of seawater, plus the atmosphere we start under.
       At the Challenger Deep this gives ~1,088 atm against a measured
       ~1,086 — close enough that the readout is honest. */
    return 1 + depth / 10.06;
  }

  function temperatureAt(depth) {
    return interp(depth, TEMP_POINTS);
  }

  /* Sunlight as a percentage of surface irradiance. Seawater absorbs red
     within metres and the rest exponentially; an attenuation coefficient of
     0.045/m is a reasonable open-ocean average for the blue-green that
     survives, and it puts ~1% at 100 m, which is the figure usually cited
     for the base of the euphotic zone. */
  function lightPercent(depth) {
    if (depth <= 0) return 100;
    var pct = 100 * Math.exp(-0.045 * depth);
    return pct < 0.0001 ? 0 : pct;
  }

  function lightLabel(depth) {
    var p = lightPercent(depth);
    if (p >= 40) return p.toFixed(0);
    if (p >= 1) return p.toFixed(1);
    if (p > 0) return p.toFixed(3).replace(/0+$/, "").replace(/\.$/, "");
    return "0";
  }

  /* marine snow flux thickens with depth up to a point, then thins as
     there is less left to fall */
  function snowDensity(depth) {
    var t = depth / MAX_DEPTH;
    return 0.30 + 0.70 * Math.sin(Math.PI * Math.min(1, t * 1.35));
  }

  function zoneAt(depth) {
    for (var i = 0; i < ZONES.length; i++) {
      if (depth < ZONES[i].to || i === ZONES.length - 1) return { zone: ZONES[i], index: i };
    }
    return { zone: ZONES[ZONES.length - 1], index: ZONES.length - 1 };
  }

  function creaturesAt(depth) {
    var out = [];
    for (var i = 0; i < CREATURES.length; i++) {
      var c = CREATURES[i];
      if (depth >= c.from && depth <= c.to) out.push(c);
    }
    return out;
  }

  /* generic piecewise-linear interpolation over [[x,y], …] */
  function interp(x, pts) {
    if (x <= pts[0][0]) return pts[0][1];
    var last = pts[pts.length - 1];
    if (x >= last[0]) return last[1];
    for (var i = 1; i < pts.length; i++) {
      if (x <= pts[i][0]) {
        var a = pts[i - 1], b = pts[i];
        var t = (x - a[0]) / (b[0] - a[0]);
        return a[1] + (b[1] - a[1]) * t;
      }
    }
    return last[1];
  }

  return {
    MAX_DEPTH: MAX_DEPTH,
    ZONES: ZONES,
    CREATURES: CREATURES,
    pressureAtm: pressureAtm,
    temperatureAt: temperatureAt,
    lightPercent: lightPercent,
    lightLabel: lightLabel,
    snowDensity: snowDensity,
    zoneAt: zoneAt,
    creaturesAt: creaturesAt,
    interp: interp
  };
})();
