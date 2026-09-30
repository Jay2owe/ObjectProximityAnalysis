# V1 findings — convergence to analytic Poisson expectations

Harness: [`PoissonConvergenceValidationTest.java`](../../src/test/java/opa/validation/PoissonConvergenceValidationTest.java)
(generators in `PointProcesses.java`, shared statistics in `ValidationSupport.java`).
Run: 2026-09-29, full mode, `-Dopa.validation=true`.

```text
sh ./mvnw -B -q test -Dtest='*ValidationTest' -Dopa.validation=true
```

Raw report: `target/validation/v1-report.txt` (generated, not tracked).

**Outcome: pass.** Every estimator converges to its expectation. The one
systematic departure — univariate border-corrected K running low by about 1/n
at large radii — is a known property of the reduced-sample estimator, is
measured below, and is why translation correction is the default.

## Design

| Setting | Value |
|---|---|
| Realisations | 1,000 per configuration (fast mode in every build: 50) |
| Seed | 20260928; realisation *i* of configuration *c* uses `seed + 1000003 c + 7919 i` |
| Null | binomial process: exactly n uniform points, the null the plugin's own Monte Carlo simulates |
| Windows | 1:1 (1000 × 1000) and 4:1 (2000 × 500), equal area 10⁶ |
| Point counts | n = 50, 150, 400 (λ = 5×10⁻⁵, 1.5×10⁻⁴, 4×10⁻⁴) |
| K radii | 10 radii, 0.02 to 0.20 × the shorter side |
| G radii | 10 radii, 0.01 to 0.10 × the shorter side |
| Cross functions | an independent second binomial pattern of the same n |

Checked: K and cross-K (translation and border), L (translation and border),
pair correlation (translation), G and cross-G (uncorrected, as the engine
defines them). L(r)−r is L shifted by a constant, so its z-scores are L's.
514 radius checks in all.

### Expectations used

- **K, cross-K, pair correlation (translation); cross-K (border):** πr² and 1.
  These are exactly unbiased under a binomial process.
- **L:** r minus the second-order Jensen term Var(K̂)/(8π²r³). L is the square
  root of an unbiased estimator, so its mean sits below r by that much; a test
  of the raw mean against r would fail at 1,000 realisations for a reason that
  is arithmetic, not a defect. Assessed where the relative spread of K̂ is at
  most 0.3, where the expansion holds (18 radii, all at the smallest radius and
  lowest density, are reported but not assessed).
- **G and cross-G:** the exact expectation of the *uncorrected* estimator on
  that window, `1 − (1/|W|) ∫_W (1 − |b(x,r) ∩ W| / |W|)^m dx` with m = n−1
  (G) or n (cross-G), by quadrature. Radii where that expectation exceeds 0.99
  are saturated (every realisation reads 1) and are not assessed — the same
  0.99 threshold the engine uses for its saturation warning. 8 radii, all at
  n = 400 on the square window.

### Pass line and multiplicity

The plan's per-radius criterion is |mean − expected| ≤ 3 SE. With 514 checks,
1.4 are expected beyond 3 SE by chance alone even if everything is exact, and
radii along one curve are strongly correlated, so exceedances cluster. The
test therefore asserts the Bonferroni bound for a 1% family-wise error
(|z| ≤ 4.27 at 514 checks) and lists every 3 SE exceedance in the report.

## Results

| Curve | Correction | Largest \|z\| across all 6 configurations | Verdict |
|---|---|---|---|
| K | translation | 3.11 (4:1, n=50, r=80) | pass |
| cross-K | translation | 2.94 | pass |
| pair correlation | translation | 2.74 | pass |
| L | translation | 3.14 (assessed radii) | pass |
| cross-K | border | 2.28 | pass |
| G | uncorrected, exact edge expectation | 2.51 | pass |
| cross-G | uncorrected, exact edge expectation | 2.88 | pass |
| K | border | 4.41 (1:1, n=50, r=200) | known ratio bias, below |
| L | border | 4.43 (1:1, n=50, r=200) | follows K border |

Summary line from the report: 514 checks, 53 beyond 2 SE, 14 beyond 3 SE.
Twelve of the fourteen are univariate border K or L at n = 50 (explained
below); the other two are K and L translation at one radius of the 4:1 window,
n = 50 (z = −3.11 and −3.14, the same curve read twice), which is within what
514 correlated checks produce by chance.

### Univariate border-corrected K runs low by about 1/n

| n | 1:1 window, relative bias at r = 0.2 × side | 4:1 window, largest relative bias |
|---|---|---|
| 50 | −2.4% (z = −4.4) | −3.1% (z = −4.0) |
| 150 | −0.7% (z = −2.7) | −0.6% (z = −2.4) |
| 400 | −0.2% (z = −1.1) | −0.2% (z = −1.5) |

n × bias stays near 1, so the bias is of order 1/n and grows with radius.
Cause: the reduced-sample estimator divides the pairs found around eligible
points (those at least r from the boundary) by the number of eligible points.
A point near an eligible point is itself more likely to be eligible, so the
numerator and denominator covary and the ratio runs low. Cross-K border shows
no such bias (largest |z| 2.28) because the target pattern does not decide
eligibility — which confirms the mechanism.

This is the "residual bias that grows with radius and is inherent to the
reduced-sample method" recorded in V3_FINDINGS.md (1.06% mean absolute bias at
n = 50 there), now measured directly. It is not a code defect, and no change
is made. The test allows 2/n (relative) for this curve and says why. Users
should keep translation correction, the default, which is exactly unbiased.

### Nearest-neighbour G: residual edge bias, as a number

Against the textbook `1 − exp(−λπr²)`, the uncorrected estimators run low
because points near the edge have part of their neighbourhood outside the
window. Largest measured bias per configuration (absolute, in G units):

| Window | n | G | cross-G |
|---|---|---|---|
| 1:1 | 50 | −0.039 (r = 100) | −0.030 (r = 100) |
| 1:1 | 150 | −0.023 (r = 60) | −0.018 (r = 60) |
| 1:1 | 400 | −0.013 (r = 40) | −0.013 (r = 40) |
| 4:1 | 50 | −0.023 (r = 50) | −0.012 (r = 50) |
| 4:1 | 150 | −0.025 (r = 50) | −0.024 (r = 50) |
| 4:1 | 400 | −0.018 (r = 35) | −0.015 (r = 35) |

The same estimators agree with their exact edge-inclusive expectation to
within 2.9 SE everywhere, so the bias is fully explained by the window edge.
This is why the README tells users to read G against the simulated envelope,
which carries the same edge effect, rather than against the `CSR_Expectation`
column.

## Verdict

V1 is complete. Translation-corrected K, L and pair correlation, cross-K under
both corrections, and G and cross-G against their exact expectation all
converge. Border-corrected univariate K carries a measured bias of order 1/n,
documented here and in the test.
