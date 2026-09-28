# V5 findings — behaviour on known non-random patterns

Harness: `src/test/java/opa/validation/NonRandomPatternValidationTest.java`
(generators in `PointProcesses.java`).
Run: 2026-09-29, full mode, `-Dopa.validation=true`.

```text
sh ./mvnw -B -q test -Dtest='*ValidationTest' -Dopa.validation=true
```

Raw report: `target/validation/v5-report.txt` (generated, not tracked).

**Outcome: pass.** K recovers the clustering and inhibition signatures at the
right spatial scale, a closed-form cluster process is recovered to within 2%
of its parameters, and the global test's power rises monotonically with effect
size in both directions while holding its nominal size under CSR.

Window 1000 × 1000 throughout; seed 20260928. Cluster parents are simulated on
a window enlarged by 4σ (Thomas) or R (Matérn), and hard-core competitors on a
window enlarged by h, so the pattern inside the window is a sample of the
stationary process rather than one thinned at its own edge.

## Clustered: Thomas process (quantitative)

The validation plan names a Matérn cluster process. The Thomas process is
substituted for the quantitative check because its K is closed-form:
K(r) = πr² + (1 − exp(−r²/4σ²)) / κ.

κ = 50 parents per window, μ = 8 children per parent, σ = 20; 1,000
realisations; translation-corrected K at 20 radii, 5 to 100.

| r | theory K | mean K̂ | ratio |
|---|---|---|---|
| 5 | 388.6 | 394.7 | 1.016 |
| 20 | 5,680.6 | 5,772.5 | 1.016 |
| 40 | 17,669.0 | 17,901.9 | 1.013 |
| 60 | 29,201.8 | 29,428.4 | 1.008 |
| 80 | 39,739.9 | 39,825.3 | 1.002 |
| 100 | 51,377.3 | 51,232.1 | 0.997 |

Every radius is within 1.8% of the closed form (criterion: 5%). Minimum-contrast
fit of (σ, κ) to the mean curve: **σ̂ = 19.65 (true 20, −1.7%), κ̂ = 50.01 per
window (true 50, +0.0%)** (criteria: 10% and 15%).

At 1,000 realisations the standard error is small enough (0.5%) to resolve the
+1.6% at small radii, so those radii sit 2 to 3.7 SE from the closed form. This
is the second-order bias of a ratio estimator: K̂ divides by n(n−1), and under
a clustered process the point count is overdispersed, so E[1/n(n−1)] exceeds
1/E[n(n−1)]. It shrinks with the number of parents and is irrelevant to the
signature; it is reported rather than hidden.

## Clustered: Matérn cluster process (qualitative)

κ = 50 parents per window, μ = 8, cluster radius R = 25; 1,000 realisations.

- Excess K̂ − πr² is significant at every radius up to the cluster diameter
  2R = 50 (z from 144 to 184).
- It stops growing at 2R: from 50 to 100 the excess moves by at most 2.3%
  (19,656.8 to 20,112.4 against a plateau of 1/κ = 20,000).
- Against the closed-form excess (the disc line-picking distance distribution
  divided by κ) the mean agrees to within 1.8% at every radius.

The clustering signature appears, and appears at the right spatial scale.

## Inhibited: Matérn type II hard-core process

Proposal intensity 400 per window, hard-core distance h = 20; 1,000
realisations.

| r | πr² | mean K̂ | largest K̂ in any realisation |
|---|---|---|---|
| 5, 10, 15, 19, 20 | 78.5 to 1,256.6 | 0 | 0 |
| 25 | 1,963.5 | 741.2 | 1,107.7 |
| 30 | 2,827.4 | 1,631.4 | 2,192.6 |
| 40 | 5,026.6 | 3,853.0 | 4,606.6 |
| 60 | 11,309.7 | 10,128.0 | 11,475.7 |

K̂ is exactly zero below h in every one of the 1,000 realisations, and sits
far below πr² just above h (z = −331 at r = 25).

## Power of the global maximum-deviation test

119 simulations, reject at p ≤ 0.05, 200 realisations per level. About 100
points per pattern in every row. Clustered family tested with K (translation,
radii 10 to 100); inhibited family with nearest-neighbour G (radii 2 to 40),
which is the function a hard core acts on directly.

| Family | Level | Rejection rate | Wilson 95% |
|---|---|---|---|
| Thomas (κ = 100, μ = 1) | CSR | 0.060 | 0.035 – 0.102 |
| | σ = 90 | 0.160 | 0.116 – 0.217 |
| | σ = 60 | 0.305 | 0.245 – 0.372 |
| | σ = 30 | 0.905 | 0.856 – 0.938 |
| Matérn II (130 proposals) | CSR | 0.050 | 0.027 – 0.090 |
| | h = 20 | 0.515 | 0.446 – 0.583 |
| | h = 30 | 0.925 | 0.880 – 0.954 |
| | h = 40 | 0.990 | 0.964 – 0.997 |

Power rises monotonically with effect size in both families, and the CSR rows
sit on the nominal 5% (both Wilson intervals cover 0.05), consistent with the
Type I error measured in V2.

One practical observation: with about 100 points, a hard core of h = 10
(a tenth of the mean nearest-neighbour distance) is not reliably detected by
the global test; the fast-mode sweep in development saw almost no rejections
at h = 5 or 10. Weak inhibition needs more points, not more simulations.

## Fast mode

Every `mvn verify` runs the same tests in fast mode: 50 realisations for the
curve checks, and 16 realisations per level at 39 simulations for power (39 is
the smallest count at which the rank envelope expresses exactly 5%). The two
validation classes add about 45 s to a build.

## Verdict

V5 is complete: the estimators recover the cluster and inhibition signatures
at the correct spatial scale, and the global test rejects CSR at a rate that
rises with effect size.
