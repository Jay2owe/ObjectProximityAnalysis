# V4 — distance modes against DiAna

How `V4_FINDINGS.md` was produced. Only the generator, runner and the small
result tables are tracked; the label images are regenerated on demand
(about 2.6 MB each).

## Tools

| Tool | Version | Source |
|---|---|---|
| Fiji | latest, bundled JDK 21 (2026-09-28 download) | disposable copy, not a user installation |
| Object Proximity Analysis | 0.3.0 (commit 5bb226a) | this repository, `target/Object_Proximity_Analysis-0.3.0.jar` |
| DiAna | 1.54 (`DiAna_1.54.jar`) | update site `DistanceAnalysis`, https://sites.imagej.net/DistanceAnalysis/ |
| 3D ImageJ Suite | mcib3d-core 4.1.7b | update site `3D ImageJ Suite`, https://sites.imagej.net/Tboudier/ |

DiAna's menu commands were read from its jar's `plugins.config`
(`DiAna_Segment` -> `DiAna.Di_Ana`, `DiAna_Analyse` -> `DiAna.DiAna_Ana`) and
its macro keys (`img1 img2 lab1 lab2 mask coloc distc adja kclosest dista
measure shuffle`) and preference keys (`Diana_proxyCC1.boolean` ...) from the
class constants, not guessed.

## Steps

```text
fiji --update add-update-site DistanceAnalysis https://sites.imagej.net/DistanceAnalysis/
fiji --update add-update-site "3D ImageJ Suite" https://sites.imagej.net/Tboudier/
fiji --update update
copy Object_Proximity_Analysis-0.3.0.jar into Fiji's plugins/

fiji --headless --console -macro make_scenes.ijm "<scene dir>/"
fiji --headless --run run_comparison.groovy "dir='<scene dir>/'"
python compare.py "<scene dir>/" results/summary.md
```

`make_scenes.ijm` is deterministic (fixed seed 20260928 for the blobs).

## Why a Groovy runner rather than DiAna's macro command

`run("DiAna_Analyse", "img1=... lab1=... adja kclosest=1 dista=50 measure")`
returned without creating its `AdjacencyResults` table both with `--headless`
and with `-batch` (probe macro, 2026-09-29), so its numbers could not be read
programmatically. DiAna's distance work is done by `DiAna.Measures.
ComputeAdjacency`, which the GUI and macro paths both call. The runner calls it
directly and, because that method shows its table rather than returning it,
also makes the identical per-pair calls that its bytecode shows
(`javap -c DiAna/Measures.class`):

| DiAna column | mcib3d call, A = object in image A, B = closest object in B by centre |
|---|---|
| Dist CenterA-CenterB | `A.distCenterUnit(B)` |
| Dist min CenterA-EdgeB | `A.distCenterBorderUnit(B)` |
| Dist min EdgeA-CenterB | `B.distCenterBorderUnit(A)` |
| Dist min EdgeA-EdgeB | `A.distBorderUnit(B)` |
| Surface contact | `A.surfaceContact(B, 0)[0] + [1]` |

The `source` column of `results/comparison.csv` records which route produced
each DiAna row. In the recorded run every row came from the per-pair calls.

## Files

- `make_scenes.ijm` — six ball pairs with analytic truth, twelve irregular blob pairs.
- `run_comparison.groovy` — runs OPA (public Java API, all five modes, k = 1,
  contact distance 0) and DiAna on the same images, writes `comparison.csv`.
- `compare.py` — per-mode maxima and the per-object table.
- `results/` — `comparison.csv`, `summary.md`, `spheres_truth.csv` from the recorded run.
