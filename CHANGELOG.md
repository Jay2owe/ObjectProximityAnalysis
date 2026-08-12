# Changelog

## [Unreleased]

### Added

- **Cross pair correlation g12(r)** — the A-to-B form of pair correlation,
  completing the cross family alongside cross-K, cross-L and cross-G. Available
  as the `Function_CROSS_PAIR_CORRELATION` checkbox in both dialogs and as
  `function_cross_pair_correlation` in macros, with the same Monte Carlo
  envelope, global p-value and status as the other cross functions.

  It carries the same restriction as the univariate form and refuses
  `BORDER` edge correction, with the same message.

  **Not selected by default.** Every pattern function is another full pass per
  Monte Carlo simulation, so switching it on for everyone would make existing
  runs slower and wider without being asked. The dialog offers it unticked;
  `OPAParameters` requests the same eight functions it always has unless a
  caller says otherwise. `OPAParameters.defaultPatternFunctions()` and
  `isDefaultPatternFunction(...)` expose that set.

### Changed

- The analysis engine was extracted into the embeddable `opa-core` module, so
  other plugins can compile in OPA's distance and point-pattern measurements
  without a user installing OPA. The engine is shaded into this plugin's JAR and
  privately relocated to `opa.internal.engine`, alongside `oc3d-core` at
  `opa.internal.core`. Users still install one JAR and install no core.

- **Java API, source-incompatible for callers that named an engine type.** The
  engine's classes moved package:

  | Was | Now |
  |---|---|
  | `opa.spatial.*` | `sc.fiji.opa.core.spatial.*` |
  | `opa.geometry.*` | `sc.fiji.opa.core.geometry.*` |
  | `opa.CalibrationInfo` | `sc.fiji.opa.core.CalibrationInfo` |
  | `opa.DistanceMode` | `sc.fiji.opa.core.DistanceMode` |
  | `opa.AnalysisCancelledException` | `sc.fiji.opa.core.AnalysisCancelledException` |

  Every class named in this README's Java API section — `OPA`, `OPAParameters`,
  `OPAResult`, `OPALabelImages`, `OPAOutput`, `OPAProgressListener`,
  `PatternResult`, `OPABatchParameters`, `OPABatchRunner`, `OPABatchResult` —
  keeps its package and is never relocated. `OPAProgressListener` keeps its
  single `onProgress(double, String)` method and now extends the engine's
  listener, so existing implementations and lambdas are unaffected.

  Macro use is entirely unaffected: option names, enum constant names and every
  table column keep their exact text.

### Unchanged, and gated

- **No output moved.** 546 golden dumps were captured from the pre-extraction
  build — 32 corpus cases x 17 configurations covering every documented input
  mode, output option, degenerate case and bit depth in 2D and 3D, plus the
  engine surface called directly and all 87 documented rejection messages by
  exact text. Every value is compared as its raw IEEE-754 bit pattern. All 546
  are unchanged after extraction, bit for bit. The goldens are immutable and
  gate every later change.

## [0.2.0] - 2026-08-07

- Initial ImageJ/Fiji plugin scaffold.
- Replaced the private recursive regex folder walker with `oc3d-core`'s shared
  discovery, including cycle protection and exclusion of OPA's own output tree;
  the reachable core is shaded and privately relocated into the plugin JAR.
- Calibrated object geometry and inter-object distance engine.
- Univariate and bivariate point-pattern statistics.
- Exact exchangeable Monte Carlo global ranks and explicit undefined-pattern
  status.
- Unit-safe batch curve/ECDF aggregation and summaries for every requested
  neighbour rank.
- Strict validation of voxel calibration and label-image values.
- Collision-safe result-table names and explicit incomplete Monte Carlo ranks.
- Observation-window validation for G functions, disambiguated ImageJ window
  choices, effective-window edge flags, and overwrite-safe batch group names.
- Unique output identities for duplicate channel titles, explicit rejection of
  channel/time hyperstacks, and validation against empty distance requests.
- Traceable batch group identities, common-grid curve interpolation, and
  hash-protected aggregate filenames.
- Raw analysis identities for collision-proof aggregation, template-aware
  batch preview, transactional group aggregation, and honest group error
  counts.
- Rejection of single-channel cross-only pattern requests.
- Canonical batch channel captures, bounded collision-safe filenames, reusable
  option validation, and template-aware preview for all static settings.
- Escape-key cancellation through Monte Carlo loops with explicit cancelled
  batch state, group manifest outcomes, and marked partial output.
- Filtered centroid-input and full provenance tables, plus interactive
  histogram and ECDF display.
- Collision-safe raw save prefixes, explicit empty-distance summary statuses,
  effective-radius provenance, always-visible provenance, and per-group batch
  calibration and analysis warnings.
- Full-precision CSV and batch scalars, overlap-safe ROI conversion, case-safe
  prefixes, explicit one-channel cross-function statuses, optional-capture
  validation, complete batch analysis controls, and undefined singleton sample
  standard deviations.
- Complete-only pointwise Monte Carlo envelopes with per-radius counts,
  area/Z/bounds validation for object ROIs, spatial-origin compatibility and
  provenance, output-shape identities, and isolated batch-run folders.
- Non-negative pair-correlation safeguards, executable Unix Maven wrapper,
  fully origin-aware coordinates and windows, end-to-end Escape cancellation,
  and area-only observation-window ROIs.
- Bounded exact-integer controls, staged atomic output replacement,
  within-analysis progress callbacks, final-stage cancellation checks,
  guarded dialog calibration errors, and strict pair-correlation inputs.
- Terminal-callback cancellation, composed headless batch progress, and
  always-refreshed JAR build provenance.
- Complete progress for skipped/all-invalid batches, development-safe citation
  metadata, and the BSD licence packaged inside the plugin JAR.
- Cancellation-safe skipped/terminal batch progress and continuous-integration
  checks of the actual JAR licence and source commit.
- Correct cancellation outcomes for every unvisited batch group, including
  groups already known to have invalid inputs.
- Exact voxel-face contact at non-dyadic pixel sizes, comparison-free radii
  excluded from the global Monte Carlo statistic, interpolation endpoints
  pinned to every contributing batch curve, and honest aggregate group counts.
- Zero-spread radii excluded from the global Monte Carlo statistic, so a
  saturated G curve can no longer force every p-value to 1, and rejection of
  duplicate ROI sets across channels.

[0.2.0]: https://github.com/Jay2owe/ObjectProximityAnalysis/releases/tag/v0.2.0
