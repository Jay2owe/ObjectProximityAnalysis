# V4 findings — distance modes against DiAna

Run: 2026-09-29. Object Proximity Analysis 0.3.0 against DiAna 1.54
(mcib3d-core 4.1.7b), both in the same disposable Fiji, on identical 3D label
images with anisotropic voxels of 0.2 × 0.2 × 0.5 µm (voxel diagonal
0.574 µm). How it was produced, tool versions and the exact DiAna calls:
[`validation/v4-diana/README.md`](../../validation/v4-diana/README.md). Result tables:
[`validation/v4-diana/results/`](../../validation/v4-diana/results/).

**Outcome: pass.** Centre-centre agrees with DiAna to floating-point noise and
centre-edge to within half a voxel diagonal, on both scenes; both tools recover
the analytic ball geometry within one voxel diagonal. Edge-edge and surface
contact differ by definition, and the difference is traced below.

## Scenes

- **Ball pairs** (analytic truth): six pairs with radii 1 to 2.5 µm and centre
  separations 2.5 to 7 µm along x, y, a z-tilted axis and the body diagonal;
  one pair exactly touching (d = r_A + r_B), one overlapping.
- **Irregular blobs**: twelve pairs, each object the union of four random
  balls, nearly touching (OPA edge-edge 0.4 to 1.3 µm). No analytic truth;
  tool against tool.

The plan also asked for one real dataset. None was available to this
release's build environment, so that comparison is deferred to the
methods paper (CHANGELOG, *Validation deferred*).

## Results

Nearest partner in B for each object in A, k = 1. Differences in µm.

| Scene | Mode | max \|OPA − DiAna\| | mean (DiAna − OPA) | max \|OPA − truth\| | max \|DiAna − truth\| | Criterion | Verdict |
|---|---|---|---|---|---|---|---|
| balls | centre-centre | 0.000 | 0.000 | 0.001 | 0.001 | ≤ 0.574 | pass |
| balls | centre-edge | 0.251 | +0.160 | 0.151 | 0.230 | ≤ 0.574 | pass |
| balls | edge-centre | 0.253 | +0.162 | 0.202 | 0.250 | explained | explained |
| balls | edge-edge | 0.500 | +0.264 | 0.317 | 0.400 | explained | explained |
| blobs | centre-centre | 0.000 | 0.000 | – | – | ≤ 0.574 | pass |
| blobs | centre-edge | 0.153 | +0.118 | – | – | ≤ 0.574 | pass |
| blobs | edge-centre | 0.172 | +0.127 | – | – | explained | explained |
| blobs | edge-edge | 0.342 | +0.240 | – | – | explained | explained |

Both tools chose the same partner for every object, including blobs A4, A8
and A12, whose nearest B object is the one of the neighbouring pair.
Edge-centre and edge-edge also lie within one voxel diagonal of DiAna (0.253
and 0.500 µm at most); they are marked "explained" because the plan requires
their offset to be traced, not merely bounded.

Centre-centre agrees to about 1e-14 µm: both use the calibrated centroid of
the labelled voxels.

## The differences, traced

**Where the surface is.** OPA places an object's surface on the calibrated
faces of its boundary voxels; DiAna (mcib3d `distCenterBorderUnit`,
`distBorderUnit`) places it at the centres of its boundary voxels. A voxel
centre sits half a voxel inside the face, so:

- centre-edge and edge-centre from DiAna are larger than OPA's by up to half a
  voxel along the approach direction (measured +0.12 to +0.16 µm mean,
  0.25 µm at most, against half-voxels of 0.1 µm in x/y and 0.25 µm in z);
- edge-edge carries that offset from both surfaces, one full voxel along the
  approach direction: +0.2 µm for pairs separated in x or y and +0.5 µm for the
  z-tilted pair (OPA 0.781, DiAna 1.281), exactly the voxel size on that axis.

For exactly touching balls this is the clearest case: OPA reports edge-edge 0
(the objects share voxel faces), DiAna 0.2 µm (the nearest boundary voxel
centres are one voxel apart). Against the analytic ball surface both are
within one voxel diagonal: OPA within 0.32 µm and DiAna within 0.40 µm on
edge-edge.

**Surface contact.** OPA reports exact contact as the calibrated area of A's
boundary faces that directly touch a B voxel (µm² in 3D), and separately an
apposed-surface measure within a chosen distance. DiAna reports
`surfaceContact(B, 0)`, an integer count of boundary elements rather than an
area, and at distance 0 it registers only where the objects overlap:

| Pair | Geometry | OPA exact contact | DiAna surface contact |
|---|---|---|---|
| 4 | touching, no overlap | 1.20 µm² | 0 |
| 5 | overlapping | 5.84 µm² | 88 |
| others | separated | 0 | 0 |

They measure different things in different units; neither is converted into
the other. OPA's is the one that reports face-to-face apposition of two
objects that touch without overlapping, which is the usual case for separately
segmented channels.

## Verdict

V4 is complete for synthetic data: the pass criteria for centre-centre and
centre-edge are met with margin, and every edge-edge and contact difference is
a written definitional difference (surface at voxel faces versus voxel
centres; contact area versus overlap voxel count), not an averaged-away
disagreement. The real-dataset comparison is deferred.
