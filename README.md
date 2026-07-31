# Object Proximity Analysis

Object Proximity Analysis is an ImageJ/Fiji plugin for calibrated inter-object
distances and 2D spatial point-pattern statistics. It accepts label images or
ImageJ ROI sets from any segmentation workflow. Segmentation is deliberately
separate from analysis.

Version `0.1.0-SNAPSHOT` is under development and is not yet a validated
scientific release.

## Current scope

- 1-5 channels, every directed channel pair, and optional self-channel analysis.
- Centre-centre, centre-edge, edge-centre, edge-edge, and surface-contact modes.
- First through k-th neighbours, partner labels, contact-threshold flags, and
  edge-object flags.
- Exact shared-face contact and thresholded surface apposition are separate
  outputs. In 2D these are lengths; in 3D they are areas.
- Ripley K, L, L(r)-r, nearest-neighbour G, pair correlation g(r), cross-K,
  cross-L, and cross-G.
- Translation or border edge correction for K and its derived curves.
- Reproducible complete-spatial-randomness Monte Carlo envelopes with recorded
  seed, pointwise 95% bounds, and an exchangeable global maximum-deviation
  Monte Carlo p-value.
- Per-object, summary, histogram, empirical cumulative distribution (ECDF), and
  curve tables.
- Folder batch grouping by regular expression, with preview, recursive scanning,
  mean curves, and mean ECDFs with between-group spread.
- Dialog-free Java API and ImageJ macro operation.

The first point-pattern implementation is 2D. A 3D label stack is rejected for
pattern analysis unless the caller explicitly requests XY centroid projection.
Distance measurements remain fully 3D.

## Measurement definitions

- Centre-edge is zero when the source centre lies inside the target object.
- Edge-centre is the directed reverse of centre-edge.
- Edge-edge is the exact minimum distance between calibrated voxel faces and is
  zero for touching or overlapping objects.
- Exact contact counts source boundary faces directly adjacent to the selected
  target label.
- Apposed surface counts source boundary faces within the chosen contact
  distance of an oppositely facing target surface.
- Surface-contact partners are ranked by apposed surface measure, largest first.
  Distance modes are ranked smallest first.

Label images must contain positive integer object labels on a zero background.
Each input must contain one ImageJ channel and one time frame; split
multichannel or time-series data into separate label images first. All inputs
must have identical dimensions and voxel calibration. Duplicate image or API
channel names receive distinct effective names in every output. Multi-channel
analyses also require identical ImageJ X/Y/Z spatial origins; differing origins
are rejected rather than treating physically shifted channels as registered.
Centroids, inverse pixel lookups, default windows, and ROI-derived windows all
use `(pixel coordinate - spatial origin) × voxel size`.

## Calibration and observation window

The plugin displays detected voxel size in the dialog. An uncalibrated image is
reported in `pixel`, never silently in micrometres. The Java API can require
physical calibration with `requirePhysicalCalibration(true)`. Batch runs also
log a warning for every uncalibrated group and record it in the group manifest.

Point-pattern statistics require an observation window. The default is the full
XY image rectangle. An optional region ROI set supplies a calibrated rectangular
union bounding box for v0.1.0; every region ROI must be an area selection.
Objects are included when their centroid lies
inside that window; objects crossing either the acquisition boundary or the
effective observation-window boundary are marked as edge objects.

## Fiji use

Build the JAR, copy it into Fiji's `plugins/` folder, restart Fiji, then use:

```text
Plugins > Object Proximity Analysis
Plugins > Object Proximity Analysis Batch...
```

The main dialog supports either open label images or `.zip`/`.roi` sets. ROI
input needs an open reference image for dimensions and calibration. Object ROIs
must not overlap; conversion reports both ROI labels and the first conflicting
pixel instead of overwriting an object. Each object ROI must be an area
selection, use an in-range Z position (or Z=0 for an unpositioned ROI), and
cover at least one in-bounds pixel.

Auto-save writes:

```text
Object Proximity Analysis/
  Objects/
  Distributions/
  Curves/
  Folder/
```

Every subfolder contains a `README.txt`.
Batch aggregate runs are kept in identity-named subfolders below `Folder/`, so
rerunning a different input root or analysis shape cannot mix old and new
aggregate files.
Interactive runs display histogram and ECDF tables as well as per-object,
summary, centroid, pattern-curve, and provenance tables.

Pattern outputs carry separate radius and curve-value units: K is area, L is
length, and G/pair-correlation are dimensionless. Requests with too few points
are marked `INSUFFICIENT_POINTS` and return undefined values rather than
apparently valid zero curves. Batch aggregates keep incompatible units in
separate tables. A global rank is marked `INCOMPLETE_MONTE_CARLO` and its
p-value is undefined unless every requested simulation contributes a valid
curve.

Distance summaries always contain every requested mode and neighbour rank.
Empty or undersized inputs retain object counts and are marked
`NO_SOURCE_OBJECTS`, `NO_TARGET_OBJECTS`, `INSUFFICIENT_NEIGHBOURS`, or
`UNDEFINED_MEASUREMENTS` rather than disappearing from the output.
With one channel, requested cross-pattern functions receive explicit
`NOT_APPLICABLE_REQUIRES_TWO_CHANNELS` summary rows. Sample standard deviation
is undefined (`NaN`) when fewer than two measurements or groups contribute.

A one-channel run cannot request distances with self-distances disabled or
request only cross-pattern functions; the API rejects both configurations
instead of returning empty output.
Distance settings are validated only when distance analysis runs, and pattern
settings only when pattern analysis runs, so either half remains independently
usable.

## Java API

The core call opens no dialogs, shows no windows, and writes no files:

```java
OPAParameters parameters = OPAParameters.builder(labelA, labelB)
        .neighborCount(3)
        .contactDistance(2.0)
        .simulations(999)
        .seed(12345L)
        .progressListener((fraction, message) -> {
            // Update a calling application's status display if desired.
        })
        .build();

OPAResult result = OPA.run(parameters);
```

`OPAResult.getCentroidTables()` returns the filtered centroid lists actually
used by pattern analysis. `getProvenanceTable()` records calibration, effective
observation-window bounds, edge correction, projection, seed, contact
threshold, requested analysis settings, and the radii actually used after
automatic or maximum-radius resolution. The provenance table is shown for both
distance and pattern runs.

Whole-number dialog fields reject fractional and out-of-range values instead of
truncating them. Resource limits are 1,000 neighbours, 10,000 histogram bins,
10,000 radius bins, and 10,000 simulations, with at most 10,000,000 stored
simulation-radius values in one Monte Carlo analysis.

ROI conversion is also dialog-free:

```java
ImagePlus labels = OPALabelImages.fromRoiSet(reference, "cells.zip");
```

Saving is explicit:

```java
OPAOutput.save(result, outputFolder, "Sample_01");
```

Folder batch:

```java
OPAParameters options = OPAParameters.builder()
        .simulations(999)
        .seed(12345L)
        .build();

OPABatchParameters batch = OPABatchParameters.builder(
                inputFolder, "(sample\\d+)_([^_]+)\\.tif", 2)
        .analysisTemplate(options)
        .recursive(true)
        .outputDirectory(outputFolder)
        .build();

String preview = OPABatchRunner.preview(batch);
OPABatchResult result = OPABatchRunner.run(batch);
```

The batch dialog and recorded macro expose the same distance modes, pattern
functions, and histogram-bin setting as the main analysis dialog.
The selected capture group is the channel name. Replacing that group with `*`
forms the sample group, so `sample1_A.tif` and `sample1_B.tif` run together.
If an optional capture group is unmatched for a filename, that file is retained
as an explicitly invalid group in the preview and manifest.
Saved batch prefixes include a lossless group-identity token, preventing groups
whose human-readable names are similar from overwriting one another. Aggregate
summary rows include the raw `Group_Identity`. Mean curves are linearly
interpolated onto a common grid over the radius range shared by every group;
`Group_N` therefore represents the number of contributing curves at every
reported radius. Aggregate filenames include a SHA-256 identity digest so
different raw units or names cannot collide after filename sanitisation.
Curve and distribution tables also carry raw source, target, function, mode,
and rank fields. Batch aggregation uses those raw fields rather than sanitised
display names. The preview marks groups that cannot run under the selected
analysis options. A group is added to summaries and aggregates only after its
required per-group save succeeds; batch-level aggregate-save errors are
reported separately and do not inflate the failed-group count.
Captured channel names are trimmed once before grouping, while their original
text and input filenames remain in the saved group manifest. Long output names
use bounded readable stems plus SHA-256 identity digests. Pressing Escape stops
geometry extraction, distance and surface calculations, or Monte Carlo work,
sets `OPABatchResult.isCancelled()`, marks unprocessed groups
as `CANCELLED`, and writes `Status: CANCELLED` into partial batch output.
Fiji progress updates continue within each Monte Carlo analysis, including
single-group batches.
Every user-supplied save prefix gains a SHA-256 identity digest, so names that
differ only by punctuation or case cannot overwrite each other on Windows.
The digest also includes the exact output shape; reusing a prefix for a run
with different requested tables creates a distinct run identity instead of
leaving obsolete files under the current identity.
CSV output writes the full stored double value rather than ImageJ's rounded
display form. The group manifest records non-OK distance or pattern statuses
in `Analysis_Warnings`. A complete table set is staged before publication, and
each final CSV or README replaces its predecessor with a same-directory atomic
move, so a failed write does not truncate the prior valid file.

## Macro use

ImageJ's `GenericDialog` makes the command recordable:

```text
run("Object Proximity Analysis",
    "input_mode=[Open label images] channel_count=2 "
  + "label_image_1=A label_image_2=B "
  + "run_distances include_self_distances k_nearest_neighbours=1 "
  + "contact_distance=2 run_pattern_analysis "
  + "monte_carlo_simulations=99 random_seed=777 "
  + "edge_correction=TRANSLATION hide_display");
```

Important option names are:

| Group | Options |
|---|---|
| Input | `input_mode`, `channel_count`, `roi_reference_image`, `label_image_1`...`label_image_5`, `roi_set_1`...`roi_set_5`, `observation_region_roi` |
| Distances | `run_distances`, `include_self_distances`, `k_nearest_neighbours`, `contact_distance`, `centre_centre`, `centre_edge`, `edge_centre`, `edge_edge`, `surface_contact` |
| Pattern | `run_pattern_analysis`, `function_k`, `function_l`, `function_l_minus_r`, `function_g`, `function_pair_correlation`, `function_cross_k`, `function_cross_l`, `function_cross_g`, `maximum_radius_0_is_auto`, `radius_bins`, `monte_carlo_simulations`, `random_seed`, `edge_correction`, `project_3d_centroids_to_xy` |
| Output | `histogram_bins`, `auto_save`, `output_directory`, `output_prefix`, `hide_display` |

The smallest attainable Monte Carlo p-value is `1/(simulations+1)`. For example,
99 simulations cannot report a p-value below 0.01.
Pointwise envelopes are emitted only when all requested simulations contribute
at that radius. Curve tables record `Envelope_N` and `Envelope_Status` for each
radius; incomplete bounds are `NaN` without invalidating an otherwise complete
global rank test.

Pair correlation supports translation edge correction (or no edge correction).
Border correction is rejected because its risk-set weighting is not valid for
this pair-correlation estimator and can otherwise produce negative estimates.
Radii must be strictly increasing. The public K-to-pair-correlation helper also
rejects negative, infinite, or materially decreasing K values.

## Build

macOS/Linux:

```text
./mvnw clean verify
```

Windows:

```text
mvnw.cmd clean verify
```

The packaged JAR is written to `target/Object_Proximity_Analysis-<version>.jar`.
The JAR is forced to rebuild during `package`/`verify` so its manifest cannot
retain stale source-control commit metadata after a new commit. It includes the
BSD 3-Clause licence at `META-INF/LICENSE`. The post-package integration test
byte-compares that entry with `LICENSE` and checks the manifest commit against
Git HEAD; continuous integration runs the full `clean verify` lifecycle.

## Licence

BSD 3-Clause. See `LICENSE`.
