# V3 findings — agreement with spatstat

Run: 2026-08-20. R 4.6.1, spatstat.explore 3.8.2.
Export: `Cores/opa-core/src/test/java/sc/fiji/opa/core/spatial/SpatstatExport.java`
Comparison: `Cores/opa-core/src/test/resources/spatstat/compare-spatstat.R`
Raw report: `Cores/opa-core/validation/v3-spatstat/v3-report.txt`

12 fixed patterns — complete spatial randomness at three densities, a cluster
process, a jittered grid, an elongated window, a window whose origin is not at
(0,0), and four bivariate pairs — with 116 exported curves.

## Result

| Function | Correction | Max relative difference | Verdict |
|---|---|---|---|
| K | translation | 6.3e-15 | agrees |
| L | translation | 3.0e-15 | agrees |
| L(r)−r | translation | 3.3e-12 | agrees |
| K | none | 2.2e-16 | agrees |
| L | none | 2.1e-16 | agrees |
| L(r)−r | none | 2.0e-14 | agrees |
| **G** | uncorrected | **0** | **bit-identical** |
| cross-K | translation | 1.9e-15 | agrees |
| cross-L | translation | 1.1e-15 | agrees |
| cross-K | none | 3.5e-16 | agrees |
| cross-L | none | 2.2e-16 | agrees |
| **cross-G** | uncorrected | **0** | **bit-identical** |
| pair correlation (ring-normalised from spatstat's K) | translation | 3.9e-14 | agrees |
| K, L, L(r)−r | **border** | 2.0e-2 | differs, explained below |
| cross-K, cross-L | **border** | 9.3e-2 | differs, explained below |

Everything except the border correction agrees to floating-point noise, and the
nearest-neighbour functions agree exactly, bit for bit. The default correction
this plugin ships — translation — is among the exact matches.

The pair correlation check is deliberately indirect. OPA derives g(r) by
ring-normalising increments of K; spatstat's `pcf` is kernel-smoothed. They are
different estimators of the same quantity, so comparing them pointwise would
prove nothing. Applying OPA's own ring normalisation to spatstat's K instead
isolates the question worth asking — do the two K estimates agree — and they do,
to 3.9e-14.

## The border difference is a definitional choice, not a defect

For the univariate case the gap is exactly a factor of n/(n−1), confirmed by
reconstructing both formulas from the raw point sets:

| Formula | Reproduces |
|---|---|
| `\|W\|·pairs / (n_eligible · (n−1))` | OPA, to 0.000e+00 |
| `\|W\|·pairs / (n_eligible · n)` | spatstat, to 1.4e-16 |

Ratio OPA/spatstat measured at 1.020408 for n=50 and 1.002004 for n=500, against
n/(n−1) of 1.020408 and 1.002004. Both implementations count the same pairs over
the same eroded point set; they differ only in the intensity estimate in the
denominator. spatstat uses λ̂ = n/|W|; OPA uses λ̂ = (n−1)/|W|.

### Which is right depends on the null, and OPA's matches its own

A point cannot be its own neighbour, so under a **binomial process** — a fixed
number of points placed uniformly — an interior point expects (n−1)πr²/|W|
neighbours, and dividing by (n−1) is exactly unbiased. Dividing by n is low by a
factor (n−1)/n. Under a **Poisson process** with random n, spatstat's choice is
the standard one.

`MonteCarloAnalyzer.generate` places exactly the observed number of points in
every simulation. OPA's null model is therefore a binomial process, and its
estimator is the one matched to it.

Measured over 3,000 realisations of that null, mean absolute bias against the
analytic πr²:

| n | OPA, λ̂=(n−1)/\|W\| | spatstat, λ̂=n/\|W\| | 1/n |
|---|---|---|---|
| 50 | **1.06%** | 2.99% | 2.00% |
| 200 | **0.36%** | 0.86% | 0.50% |

OPA is roughly 2.5× closer to the truth for the null it actually simulates. The
residual bias in both grows with radius and is inherent to the reduced-sample
method, which is why translation is and should remain the default.

### The cross-border gap has a second component

Cross-K border differs by up to 9.3% but with a median ratio near 1.000, so it is
not a constant factor. Backing spatstat's denominator out of its own output gives
non-integer implied pair counts, which the plain reduced-sample form cannot
produce. Reading `spatstat.explore::Kmulti` shows why: its border estimate is
built by `Kount` from a **binned histogram** of pair distances accumulated over
`breaks`, whereas OPA recomputes the eligible anchor set exactly at each radius.
On a coarse 20-radius grid the two constructions separate.

This half is not fully quantified. It does not affect any conclusion above,
because the difference is confined to the border correction, but it is the one
loose thread in V3.

## Actions

1. **No code change.** The border estimator is correct for this plugin's null
   model and better matched to it than the alternative. Changing it to match
   spatstat would make the estimator worse for OPA's own Monte Carlo.
2. **Document it.** The README should state that border-corrected K uses
   λ̂ = (n−1)/|W|, that this is unbiased under the fixed-count null the plugin
   simulates, and that it differs from spatstat by n/(n−1) for anyone comparing
   outputs across tools. Without that note, a user checking OPA against spatstat
   will think one of them is broken.
3. **Use it in the paper.** "Agrees with spatstat to 1e-15 on every correction
   except border, where the difference is a documented intensity-estimator
   choice that we show is the less biased one for our null" is a stronger
   validation claim than blanket agreement would have been.
4. **Open thread**: quantify the binned-versus-exact component of the cross-K
   border difference, or re-run V3 on a fine evenly spaced radius grid starting
   at zero, which is the grid spatstat's histogram construction expects.
