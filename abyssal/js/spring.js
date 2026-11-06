/* ═══════════════════════════════════════════════════════════════════════
   ABYSSAL — spring
   A critically-damped-ish spring integrator, fixed-timestep so it behaves
   identically at 60 Hz and 144 Hz.

   This is the same idea Mochy uses for its player sheet: input moves a
   *target*, and a spring moves the *rendered value* toward it. Dragging
   therefore feels weighty and letting go settles naturally, instead of
   snapping. It also means the descent can carry momentum and overshoot
   very slightly, which is what makes it feel like water.
   ═══════════════════════════════════════════════════════════════════════ */
window.Spring = (function () {
  "use strict";

  function Spring(value, opts) {
    opts = opts || {};
    this.value = value;
    this.target = value;
    this.velocity = 0;
    /* stiffness: how hard it pulls. damping: how fast it stops wobbling. */
    this.stiffness = opts.stiffness != null ? opts.stiffness : 170;
    this.damping = opts.damping != null ? opts.damping : 26;
    this.precision = opts.precision != null ? opts.precision : 0.01;
    this.min = opts.min != null ? opts.min : -Infinity;
    this.max = opts.max != null ? opts.max : Infinity;
  }

  Spring.prototype.set = function (v) {
    this.value = this.target = clamp(v, this.min, this.max);
    this.velocity = 0;
    return this;
  };

  Spring.prototype.to = function (v) {
    this.target = clamp(v, this.min, this.max);
    return this;
  };

  /* nudge the target directly — used while dragging, so the rendered value
     trails the finger by a frame or two and feels like it has mass */
  Spring.prototype.nudge = function (delta) {
    this.target = clamp(this.target + delta, this.min, this.max);
    return this;
  };

  Spring.prototype.isSettled = function () {
    return Math.abs(this.target - this.value) < this.precision &&
           Math.abs(this.velocity) < this.precision;
  };

  /* Semi-implicit Euler with a fixed sub-step. Big dt (a stalled tab, a
     slow frame) is chopped into 1/120 s slices so the integrator cannot
     explode and throw the value past its bounds. */
  Spring.prototype.step = function (dt) {
    var remaining = Math.min(dt, 0.064);   // never simulate more than ~4 frames
    var h = 1 / 120;
    while (remaining > 0) {
      var step = Math.min(h, remaining);
      var force = -this.stiffness * (this.value - this.target);
      var damp = -this.damping * this.velocity;
      this.velocity += (force + damp) * step;
      this.value += this.velocity * step;
      remaining -= step;
    }
    if (this.value < this.min) { this.value = this.min; this.velocity *= -0.28; }
    if (this.value > this.max) { this.value = this.max; this.velocity *= -0.28; }
    if (this.isSettled()) { this.value = this.target; this.velocity = 0; }
    return this.value;
  };

  function clamp(v, lo, hi) { return v < lo ? lo : v > hi ? hi : v; }

  /* smoothstep, for anything that should ease without a spring */
  function smooth(t) { return t * t * (3 - 2 * t); }

  return { Spring: Spring, clamp: clamp, smooth: smooth };
})();
