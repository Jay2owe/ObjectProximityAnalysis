# V2 findings — envelope coverage and Type I error

Harness: `Cores/opa-core/src/test/java/sc/fiji/opa/core/spatial/EnvelopeCalibrationStudy.java`
Both runs: 1,000 realisations × 200 points, window 1000×1000, radii 10–100, seed 20260820.

**Outcome: one real defect found and fixed, one claim cleared.** The global
p-value was already correctly calibrated and is unchanged. The pointwise band
was labelled 95% and delivered about 93%; `opa-core` 0.3.0 replaces it with a
rank envelope and the measured coverage now lands on nominal.

## After the fix — verification run, 2026-08-20

Same harness, 1,000 realisations, 119 simulations (the count at which a rank
envelope expresses 5% exactly), same seed and window.
Raw report: `Cores/opa-core/validation/v2-after-fix/v2-report.txt`

| Case | Pooled escape, before | after | Type I error, after |
|---|---|---|---|
| K / translation | 0.0690 | **0.0495** | 0.052 |
| K / border | 0.0722 | **0.0543** | 0.047 |
| L / translation | 0.0611 | **0.0448** | 0.045 |
| G / border | 0.0519 | 0.0316 | 0.042 |
| Pair correlation / translation | 0.0664 | **0.0491** | 0.045 |

Nominal is 0.0500 in every row. The four non-saturating cases now sit on it.
Type I error still covers the nominal level everywhere, confirming the fix left
the global test alone.

Two rows out of fifty individual radius checks still flag (K/border at r=80,
L at r=40). At α = 0.05 across fifty tests, about 2.5 flags are expected by
chance, so that is on expectation rather than a residual defect.

**G still under-escapes at 0.0316 and still fails the uniformity test**
(χ² = 20.96 against a 0.05 critical value of 16.919, below the 0.01 value of
21.666). This is the saturation described below, which the envelope fix does
not and should not address: at this density G is already 0.998 by r = 100, the
simulated values tie at 1.0, and a band that has collapsed to a point cannot be
escaped. It fails conservatively. The remedy is radius selection, not
calibration.

### Golden master

The plugin's golden set is declared immutable, so it was rebaselined once,
deliberately, and verified column by column first. Only `Envelope_Upper`
(3,717 cells), `Envelope_Lower` (407) and `Simulations` (546, from the default
change) moved, plus the two new `Envelope_Level` and `Envelope_Rank` columns.
No curve value, distance, p-value or status moved, and there were no structural
anomalies. Recorded in `GoldenMasterTest`'s class documentation.

## The original run, 2026-08-20, before the fix

99 simulations, the shipped default at the time.
Raw report: `Cores/opa-core/validation/v2-envelope-calibration/v2-report.txt`

### What passed

Empirical Type I error of the global maximum-deviation test, against a nominal 0.05:

| Case | Type I error | Wilson 95% CI | Verdict |
|---|---|---|---|
| K / translation | 0.048 | [0.036, 0.063] | PASS |
| K / border | 0.043 | [0.032, 0.057] | PASS |
| L / translation | 0.042 | [0.031, 0.056] | PASS |
| G / border | 0.042 | [0.031, 0.056] | PASS |
| Pair correlation / translation | 0.038 | [0.028, 0.052] | PASS |

All five cover the nominal level. Four of five also pass the binned uniformity test
for the whole p-value distribution. The significance claim a user quotes in a paper
is sound.

This is the outcome that mattered most: had the global test been miscalibrated, the
plugin could not have been released in its current form. It is not.

### What failed

Per-radius escape rate from the pointwise 95% band, pooled across the ten radii:

| Case | Pooled escape rate | Nominal |
|---|---|---|
| K / translation | 0.0690 | 0.0500 |
| K / border | 0.0722 | 0.0500 |
| L / translation | 0.0611 | 0.0500 |
| G / border | 0.0519 | 0.0500 |
| Pair correlation / translation | 0.0664 | 0.0500 |

Every case sits above nominal, and at 1,000 realisations per radius the Wilson
intervals exclude 0.05 at most radii. The band is **anti-conservative**: a user
reading "the observed curve left the 95% envelope at this radius" is running a false
positive rate closer to 7% than 5%.

### Cause — isolated and confirmed

`MonteCarloAnalyzer.summarize` builds the band as the linearly interpolated 2.5th and
97.5th percentiles of the S simulated values:

```java
lower[radiusIndex] = percentile(finite, 0.025);
upper[radiusIndex] = percentile(finite, 0.975);
```

The observed curve is then compared against that band — but it is not part of the S
values the band was built from. Under exchangeability the observed curve's rank among
all S+1 curves is uniform, and the interpolated percentile of S values does not sit at
the rank boundary that a 5% escape rate requires.

Confirmed with 400,000 draws of pure independent Gaussian noise, no spatial statistics
involved at all:

| Simulations S | Interpolated 2.5/97.5 band | Rank envelope, k-th extreme | Exact level 2k/(S+1) |
|---|---|---|---|
| 39 | **0.0957** | k=1 → 0.0503 | 0.0500 |
| 99 | **0.0687** | k=2 → 0.0404 | 0.0400 |
| 199 | **0.0594** | k=5 → 0.0499 | 0.0500 |

The theoretical 0.0687 at S=99 matches the plugin's measured 0.061–0.072 across all
five cases. The estimators are fine; the band construction is the entire effect.

Two consequences worth noting:

- **The default is the worst practical case.** `OPAParameters` defaults to
  `simulations = 99`, so the shipped configuration is exactly the one measured.
- **Fewer simulations make it worse, not just noisier.** At S=39, a setting a user
  might reasonably pick for speed, the band delivers ~90% coverage while still being
  labelled 95%.

### Fix

Replace the interpolated percentile band with a **rank envelope**: the k-th smallest
and k-th largest of the S simulated values, which has exact pointwise level
`2k/(S+1)`. This is what spatstat's `envelope` does, and it is exact by construction
rather than approximately right.

To hit 5% exactly, S+1 must be a multiple of 40 — so S ∈ {39, 79, 119, 159, 199}. The
current default of 99 cannot express 5% at all: k=2 gives 4%, k=3 gives 6%.
Recommend changing the default to **119 simulations with k=3**, which is exact, and
costs 20% more runtime than the present default.

Alongside that, report the **achieved** level next to the band in the results table
and any plot legend, so the figure never states a coverage the construction cannot
deliver.

## Open item — G saturates at the radii tested

G / border shows escape rates collapsing at the top of the radius range: 0.022 at
r=90 and 0.013 at r=100, far *below* nominal, and it is the one case whose uniformity
test failed (chi² = 18.74 against a 0.05 critical value of 16.919, excess in the
top p-value bin).

This is saturation, not a defect. At λ = 2×10⁻⁴ the mean nearest-neighbour distance
is about 35 units, so G(90) ≈ 0.994 and G(100) ≈ 0.998. Most realisations hit exactly
1.0, the simulated values tie, the band degenerates to a point, and nothing can escape
it. `MonteCarloAnalyzer` already documents this behaviour in its `standardize` comment
and handles it conservatively — ties push the p-value toward 1, which is why the
failure is in the safe direction.

The lesson is about defaults, not correctness: G's radius range should be capped
relative to the pattern's mean nearest-neighbour distance rather than to the window
size, and the plugin should warn when a requested radius is past saturation.

## What this changes for release

1. The rank-envelope fix shipped in `opa-core` 0.3.0 and Object Proximity
   Analysis 0.3.0. It was a release blocker and is now closed.
2. The global test needed no change, and this study is the evidence that says so.
3. Both results belong in the family paper's validation section as measured numbers.
   "Correctly sized global test, exactly calibrated pointwise envelope, here is the
   study that establishes it" is a stronger claim than most plugins in this space
   make at all.
4. Re-run this harness after the fix. The pass criteria are already encoded in it.
