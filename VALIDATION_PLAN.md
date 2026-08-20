# Object Proximity Analysis — scientific validation scope

Written: 2026-08-20
Purpose: close the first bullet of `PUBLISHING_AUDIT.md` — *"Complete independent
scientific validation against analytic examples and an established
spatial-statistics implementation"* — which is the release blocker for v0.3.0 and
the gating item for the family paper.

Engine under test: `Cores/opa-core`, packages `sc.fiji.opa.core.geometry`
(distances, surface contact) and `sc.fiji.opa.core.spatial` (point-pattern
functions, edge corrections, Monte Carlo envelopes).

## What is already covered

The existing suite is stronger than the one-line audit entry suggests. Five test
classes in `opa-core` plus fourteen in the plugin already cover:

- exact hand-computed Ripley K for a known point pair, and exact G and cross-G;
- translation correction against rectangular overlap, and border correction
  anchor eligibility;
- pair correlation equal to 1 for a CSR-expected K curve;
- L and L(r)−r kept as distinct quantities;
- determinism of Monte Carlo envelopes given a recorded seed, and preservation of
  both point counts in the bivariate case;
- undefined/incomplete-envelope bookkeeping, and suppression of significance
  claims when a curve is entirely undefined;
- exact calibrated distances, overlap-is-zero, k-th neighbour ranking, exact
  versus thresholded surface contact, 3D calibrated face area, and non-dyadic
  pixel sizes;
- rejection of fractional, negative, non-finite and out-of-range labels.

These are **deterministic unit tests on hand-computable configurations**. What is
missing is everything **statistical**: whether the estimators converge to their
known expectations, whether the envelopes have the coverage they claim, and
whether an independent implementation agrees.

## The five gaps, in dependency order

### V1 — Convergence to analytic Poisson expectations
*No external dependency. Estimated 1 day.*

For a homogeneous Poisson process of intensity λ on a rectangular window, the
exact expectations in 2D are:

| Quantity | Expectation under complete spatial randomness |
|---|---|
| K(r) | πr² |
| L(r) | r |
| L(r) − r | 0 |
| g(r) | 1 |
| G(r) | 1 − exp(−λπr²) |
| Cross-K₁₂(r), independent processes | πr² |
| Cross-G₁₂(r), independent processes | 1 − exp(−λ₂πr²) |

Simulate M = 1,000 independent realisations at several intensities and window
aspect ratios, average each estimated curve across realisations, and compare to
the analytic curve.

**Pass criterion**: the mean estimate lies within ±3 standard errors of the
analytic value at every radius, for both translation and border corrections.

**Known caveat that must be handled, not failed**: OPA's G and cross-G are
*uncorrected* by design, so under CSR the observed Ĝ is biased downward near the
window edge. Run the G checks with r ≤ 0.1 × min(window side) and report the
residual bias as a measured number rather than treating it as a defect. This
matches the limitation already documented in `README.md`.

### V2 — Envelope coverage and Type I error
*No external dependency. **Harness built and running, 2026-08-20.***

Implemented as `opa-core/src/test/java/sc/fiji/opa/core/spatial/EnvelopeCalibrationStudy.java`.
Skipped in a normal build; enable with `-Dopa.calibration=true`, and tune with
`-Dopa.calibration.realisations`, `.simulations`, `.points`, `.seed` and `.out`.
Covers K/translation, K/border, L/translation, G/border and pair
correlation/translation. Writes `validation/v2-envelope-calibration/v2-report.txt`
(generated output, git-ignored).

Two design points emerged from reading `MonteCarloAnalyzer` that the first draft of
this plan had wrong:

- The global p-value is `(1 + #{simulated >= observed}) / (S + 1)`, so under the
  null it is uniform on the **discrete** set {1/(S+1), …, 1}. A continuous
  Kolmogorov–Smirnov test against U(0,1) would reject spuriously. Uniformity is
  therefore assessed with a binned chi-square (10 bins, df 9) instead.
- Coverage is judged with **Wilson score** intervals, not the usual
  `p ± 1.96·√(pq/n)`. At a nominal 5% the Wald interval collapses to zero width
  whenever a radius happens to record no escapes, which manufactures failures that
  are artefacts of the interval rather than the plugin.

One reassuring finding from reading the code: `exchangeableScales` pools the observed
curve together with all simulated curves when computing the per-radius scale, so
exchangeability between observed and simulated is preserved. Computing that scale
from the simulations alone would have biased the p-value, and it does not.

**Status: complete, 2026-08-20. Results in [`V2_FINDINGS.md`](V2_FINDINGS.md).**

The global maximum-deviation test is correctly sized — empirical Type I error 0.038
to 0.048 across all five cases, every Wilson interval covering the nominal 0.05. The
significance value a user quotes is trustworthy and needs no change.

The pointwise band is not. It is labelled 95% and delivers about 93%, because
`MonteCarloAnalyzer` builds it from the interpolated 2.5/97.5 percentiles of the S
simulated curves rather than from their ranks. Confirmed against 400,000 draws of
pure Gaussian noise with no spatial statistics involved: that construction escapes
6.87% of the time at S=99, matching the 6.1–7.2% measured in the plugin. Fix is a
rank envelope with exact level 2k/(S+1), which makes the default of 99 simulations
unable to express 5% and argues for 119.

That fix is a release blocker for v0.3.0, and re-running this harness is its exit
gate.

The plugin's central scientific claim is the Monte Carlo envelope and the global
maximum-deviation p-value, and neither was tested for calibration before this stage.
The study draws 1,000 independent complete-spatial-randomness patterns, runs the
plugin's own envelope with 99 simulations against each, counts how often the
observed curve escapes the pointwise 95% band, and collects the distribution of the
global p-value.

**Pass criterion**: at every radius the Wilson 95% interval for the escape rate
covers 0.05; binned global p-values pass the chi-square uniformity test at df 9;
the Wilson interval for empirical Type I error covers the nominal 0.05.

**If this fails, the plugin cannot be released as-is** — every significance claim
it makes would be miscalibrated. This is why it runs before V3–V5.

### V3 — Agreement with spatstat
***Complete, 2026-08-20. Results in [`V3_FINDINGS.md`](V3_FINDINGS.md).***

R 4.6.1 with spatstat.explore 3.8.2. Twelve fixed patterns, 116 curves.
Everything agrees to floating-point noise (1e-15 or better) except the border
correction, and G and cross-G are bit-identical. The border difference is a
documented intensity-estimator choice — spatstat uses n/|W|, OPA uses
(n−1)/|W| — and over 3,000 realisations of the fixed-count null this plugin
actually simulates, OPA's is roughly 2.5× less biased. No code change; it needs
a README note so nobody comparing the two tools thinks one is broken.

`spatstat` (Baddeley, Rubak & Turner) is the reference implementation reviewers
will name. It provides a 1:1 match for every function OPA implements: `Kest`,
`Lest`, `Gest`, `pcf`, `Kcross`, `Lcross`, `Gcross`, and `envelope`, including
translation and border corrections.

Neither R nor a Python substitute is currently installed on this machine
(`pointpats`/`libpysal` also absent). Install R plus spatstat — roughly 20
minutes — rather than using `pointpats`, because spatstat is the authority a
reviewer recognises and it covers the cross-type functions that `pointpats` does
not.

Export ~20 fixed point patterns (CSR, clustered, regular, bivariate; varying n
and window shape) as CSV from the Java engine, compute both implementations'
curves on identical inputs, and compare pointwise.

**Pass criterion**: maximum absolute relative difference below 1e-6 for
translation-corrected K, L and cross-K; below 1e-6 for uncorrected G and cross-G;
documented and explained agreement for the pair correlation function, where
kernel-smoothing choices legitimately differ between implementations and exact
agreement should **not** be expected.

### V4 — Distance modes against an independent tool
*Needs Fiji plus DiAna. Estimated 1 day.*

DiAna (Gilles et al. 2017) already computes centre–centre, centre–edge,
edge–centre and surface-in-contact in 3D, so it is the natural cross-check for
the geometry engine — and it is the incumbent the family paper positions against,
which makes the comparison worth having in print either way.

Run both tools on the same 3D label stacks: synthetic spheres at known
separations (where the answer is also analytic), plus one real dataset.

**Pass criterion**: agreement within one voxel diagonal for centre–centre and
centre–edge; documented and explained differences for edge–edge and surface
contact, where the two tools' surface definitions genuinely differ. Any
disagreement larger than a voxel must be traced to a definitional difference and
written down, not averaged away.

### V5 — Behaviour on known non-random patterns
*No external dependency. Estimated 0.5 day.*

The current test only asserts that a clustered pattern has more small-scale pairs
than a regular one. Replace with quantitative checks against processes whose
theoretical curves are known: a Matérn cluster process and a Matérn hard-core
process.

**Pass criterion**: estimated K recovers the theoretical cluster/inhibition
signature at the correct spatial scale; the global test rejects CSR at the
expected rate as the effect size increases.

### V6 — Scale benchmark
*No external dependency. Estimated 0.5 day. Closes a separate audit bullet.*

Time and memory for 999-simulation envelopes across object counts spanning
10²–10⁴, plus the O(n²) surface-to-surface distance path flagged in `README.md`.
Produce the runtime table that belongs in the paper and in the README, so users
can size a run before starting it.

## Total

Roughly **6–7 working days**, of which V1 and V2 are the scientifically load-bearing
half and need no software that is not already installed.

## Recommended order

1. ~~**V2** first, despite V1 being simpler — it is the only stage that can
   invalidate the design, so failing it early saves the rest.~~ **Done 2026-08-20.**
   It earned its place: it found a real miscalibration in the pointwise band that
   no other stage would have caught, and cleared the global test that the whole
   plugin's credibility rests on.
2. V1, which shares all its simulation scaffolding with V2.
3. ~~V3, once V1/V2 confirm the estimators are self-consistent.~~ **Done 2026-08-20.**
4. V5 and V6 in parallel with V3.
5. V4 last; it needs a Fiji session and is the least likely to surface a defect.

## Deliverables

- `src/test/java/.../ValidationAnalyticTest.java` — V1, V5 as repeatable tests.
- `validation/` folder holding the V2 coverage study, the V3 spatstat scripts and
  exported patterns, the V4 comparison tables, and the V6 benchmark — with seeds
  recorded so every number is reproducible.
- One results section, reusable verbatim as the family paper's validation figure
  and as the plugin's `docs/VALIDATION.md`.
