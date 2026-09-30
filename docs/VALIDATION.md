# Validation summary

What was tested, what the numbers were, and what was deliberately left for
later. Each section links to the full findings, which record seeds, harnesses
and raw reports so every number can be reproduced.

## V1: estimators converge to their analytic expectations

1,000 complete-spatial-randomness patterns per configuration (n = 50, 150 and
400 on square and 4:1 windows). Every estimator converges to its known
expectation: K, cross-K and pair correlation to πr² and 1, L to r after the
Jensen term, G and cross-G to the exact expectation of the uncorrected
estimator. The one systematic departure is border-corrected univariate K,
which runs low by about 1/n (−2.4% at n = 50), a property of the
reduced-sample method and the reason translation correction is the default.
[Full findings](validation/V1_FINDINGS.md).

## V2: the envelope and the p-value are calibrated

1,000 patterns, each run through the plugin's own Monte Carlo test. The global
maximum-deviation p-value was correctly sized from the start (Type I error
0.038 to 0.048 against a nominal 0.05). The pointwise band was labelled 95%
but delivered about 93%; it was replaced by a rank envelope in opa-core 0.3.0
and now escapes 0.045 to 0.054 of the time at 119 simulations.
[Full findings](validation/V2_FINDINGS.md).

## V3: agreement with spatstat

Twelve fixed patterns and 116 curves against R's spatstat. K, L, L(r)−r,
cross-K, cross-L and pair correlation agree to within 1e-14 relative under
translation and no correction; G and cross-G are bit-identical. Border
correction differs by design: this plugin divides by n−1 rather than n, which
is unbiased for the fixed-count null it simulates (1.06% against 2.99% mean
bias at 50 objects). The last cross-K border gap is spatstat's own binning and
falls to 2e-16 on a 200-fold finer grid.
[Full findings](validation/V3_FINDINGS.md).

## V4: distances against DiAna

Anisotropic 3D label images (0.2 × 0.2 × 0.5 µm) in both tools. Centre-centre
agrees to floating-point noise and centre-edge to within half a voxel
diagonal; both recover the analytic ball geometry. Edge-edge and contact
differ by definition (surface at the voxel face here, at voxel centres in
DiAna; contact as area here, as a voxel count there), and each difference is
traced. **Deferred:** the comparison on a real dataset moves to the methods
paper, because none was available to this release's build environment.
[Full findings](validation/V4_FINDINGS.md).

## V5: known non-random patterns

A Thomas cluster process is recovered to within 1.8% of its closed-form K and
its parameters to within 2%; a Matérn cluster process shows its excess up to
2R; a Matérn II hard core gives K = 0 below the hard-core distance in every
realisation; and the global test's power rises steadily with effect size in
both directions. [Full findings](validation/V5_FINDINGS.md).

## V6: runtime

Measured on seeded synthetic scenes from 200 to 10,000 objects; see the
runtime table in the [README](../README.md#how-long-a-run-takes). opa-core
0.4.0 made the same analyses 4 to 66 times faster than 0.3.0 with every
output identical bit for bit, checked by oracle tests in opa-core, the
plugin's 672 golden outputs and a SHA-256 of every benchmark case's full
output.

## Plan

The scope and pass criteria set before any of this ran:
[validation plan](validation/VALIDATION_PLAN.md).
