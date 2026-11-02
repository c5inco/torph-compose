# Bidi first-layout preparation optimization

Current extracted-stage checks are in [STACK_VALIDATION.md](STACK_VALIDATION.md).
The historical performance data below predates the final first-layout renderer.

This follow-up targets the first-layout cost of the [correct bidi renderer](BIDI_PLACEMENT_FIX.md).
The comparison baseline already includes its placement, mirroring and retarget-continuity fixes,
as well as the shared-layout cache. It does not compare with the earlier incorrect renderer.

## Attribution

A diagnostic-only Pixel 7 GRAPHEME eight-label trace added separate preparation slices to the
correct renderer. In its median-wall iteration, native selection-path construction consumed
**2.361 ms CPU across 126 calls**. Standalone segment line-origin queries consumed 0.362 ms;
bidi detection consumed 0.037 ms. Segment measurement consumed 2.358 ms, while the enclosing
snap preparation consumed 6.291 ms. Nested slice times are not additive. Extra trace slices
perturb timing; this diagnostic build is excluded from the matched latency comparison.

This directly supports optimizing selection geometry. Shared origin caching and caret-edge
experiments were explored but did not establish a useful reliable improvement. Their raw data
is retained separately, not counted in the final benchmark.

## Implementation and limits

`BidiRangeBounds` derives single-line range bounds from platform-resolved embedding levels and
Compose's native primary/secondary caret positions. Android assigns the primary caret to the
lower embedding level at a split caret. The helper chooses the logical upstream/downstream edge
and includes both edges at interior run boundaries. Queried coordinates are cached per layout;
no glyph widths are estimated and no font shaping is replaced.

Native selection paths remain the fallback for wrapped paragraphs, negative letter spacing,
empty ranges, surrogate/ZWJ/variation-selector/keycap candidates, and any disagreement between
platform levels and Compose's resolved directions. State-local data resets on layout identity.
The existing segment-origin alignment, directional layout caches, font handling and animation
retargeting are retained. ASCII does not enter this helper. Mirrored transforms retain the
previous signed-box path. Shared cache capacities and environment keys are unchanged.

The implementation follows Android's
[primary/secondary caret selection](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/text/Layout.java)
and uses the public
[TextLayoutResult geometry APIs](https://developer.android.com/reference/kotlin/androidx/compose/ui/text/TextLayoutResult).
It does not call hidden platform APIs or inspect Compose internals by reflection.

## Geometry validation

`BidiRangeBoundsTest` compares every nonempty UTF-16 range, in shuffled query order, with native
`getPathForRange().getBounds()`. Coordinate tolerance is zero. Fixtures include nested embeddings,
overrides and isolates, Hebrew/Arabic/numbers/brackets, combining marks, emoji, empty controls,
newlines, long words and 16 seeded token combinations. Each runs in LTR/RTL, three widths and
four typography settings: ordinary spacing, negative spacing, positive spacing with justification,
and centered serif text with explicit line height and font padding.

On Pixel 7 the expanded test checks **93,912 ranges**: 16,554 use the shortcut and 77,358 exercise
native fallback. The shortcut is explicitly required for the primary bidi fixture; a vacuous
all-fallback result cannot pass. Native equality checks geometry, while the independent BasicText
suite separately checks rendered ink and animation endpoints.

## Reproduction and evidence

Raw artifacts, diagnostic/prototype source, final source snapshots and analysis are retained under
`/tmp/torph-bidi-perf-20261031/`. Builds and test runners use ATC with a wrapper invoking
`build-brief ./gradlew`. BenchmarkDeviceState snapshots, fixes and verifies restoration of animator
scale and refresh settings. No phone is described as supporting 120 Hz.

The matched benchmark selects `BidiPlacementWorkload` in `FirstLayoutBenchmark`: eight distinct
labels, fresh morph states in an already warm process, five samples per case, full compilation.
It includes bidi GRAPHEME and AUTO, ASCII GRAPHEME, and plain Text controls for both label sets.
Missing outer/first-apply markers invalidate the entire affected five-sample case; no individual
latency sample is filtered. This is not an FPS, cold-startup or warm text-change benchmark.

Validated scoped CPU query (run through the skill-pinned Perfetto v57.2 named sessions):

```sql
INCLUDE PERFETTO MODULE slices.cpu_time;
SELECT c.name, count(*) AS calls, sum(c.cpu_time) / 1e6 AS cpu_ms
FROM thread_slice_cpu_time c
JOIN descendant_slice((SELECT id FROM slice
  WHERE name = 'Labels:firstMeasure' LIMIT 1)) s ON s.id = c.id
WHERE c.name GLOB 'Morph:*'
GROUP BY c.name ORDER BY c.name;
```

The diagnostic CSV is `analysis/profile-phases.csv`; its trace is the `warmBidi8_iter001`
trace under `profile-probe/`. Source hashes and matched measurements are recorded with the
final results. No commit, push to main or issue closure is part of this investigation.

## Matched performance results

Pixel 7/API 37 first-measure wall times, milliseconds; five valid fresh-state samples per cell:

| Eight-label workload | Correct baseline median (min–max) | Optimized median (min–max) |
| --- | --- | --- |
| Bidi GRAPHEME | 7.682 (6.591–8.583) | 6.611 (6.203–7.358) |
| Bidi AUTO | 5.788 (5.034–6.630) | 5.802 (4.898–6.018) |
| ASCII GRAPHEME | 2.860 (2.391–3.006) | 2.673 (2.257–3.596) |
| Bidi plain Text | 1.662 (1.521–1.833) | 1.447 (1.381–2.166) |
| ASCII plain Text | 0.600 (0.485–0.749) | 0.752 (0.584–0.808) |

GRAPHEME's observed median decreases **1.071 ms (13.9%)**. AUTO's median changes +0.014 ms
(+0.2%): **no demonstrated AUTO wall-time benefit**. The ranges overlap, and five samples do
not establish a universal latency improvement. ASCII/control changes are treated as run variation;
ASCII does not execute this helper. The plain Text gap remains substantial.

CPU was queried for all five valid iterations in each bidi mode, rather than only one representative
trace. Medians of scoped CPU sums in ms:

| Bidi mode | First-apply CPU, baseline → optimized | Snap-preparation CPU | Nested segment-measure CPU |
| --- | --- | --- | --- |
| GRAPHEME | 6.897 → 6.082 | 4.861 → 4.115 | 2.287 → 2.273 |
| AUTO | 5.425 → 4.915 | 3.496 → 3.003 | 1.714 → 1.687 |

Preparation CPU decreases with nearly unchanged segment-measure CPU, consistent with the intended
geometry saving. The CPU reduction does not establish a wall-time benefit for AUTO. Each column
has its own five-sample median; nested columns must not be added. All valid iterations retain
53 platform layout initializations in GRAPHEME and 38 in AUTO, and respectively 122/74 per-state
segment-layout requests. This optimization reduces geometry work, not layout-count duplication.

The initial candidate GRAPHEME case lacked both markers in iteration 3; ASCII GRAPHEME lacked them
in iteration 0. Both complete five-sample cases were rerun and replaced, including all their
original valid-looking samples. The other three cases retain their original complete valid runs.
All **50 included iterations** have one outer marker and exactly eight morph applies or zero plain
morph applies. No speed outlier was filtered. Raw originals and replacement cases are preserved.

[Structured results](BIDI_PLACEMENT_OPTIMIZATION_RESULTS.json) retain every wall sample, CPU sample,
layout count, trace path, source hash and replacement reason. CPU measurements are Pixel 7 only;
Pixel 4 XL checks correctness, not matched timing.

Correct-baseline `TextMorphState.kt` SHA-256:
`a60fe28735f6d112bc03ae091bf12141e656a377af5b3f87daa591a3b1ab48e4`.
Optimized state SHA-256:
`d9d2bcc61ea455b8d632546d038f542bd30ab1c29bee1e8825c5a164dc19ef82`.
Helper SHA-256:
`01172a0ab8e2cfee6bc491adf3332f0a4a70dd6f0e7c6e34338dbca3df564c94`.

## Recommendation

Retain this state-local, single-layout geometry shortcut for the measured preparation-CPU saving and
GRAPHEME first-layout reduction, with native fallback and exact reference tests as requirements.
Do not advertise an AUTO latency speedup or consider issue #3 fully resolved. Initial layouts
still eagerly prepare segment layouts; the shared cache addresses reuse but does not remove that
work. The remaining Text gap requires a separate design/prototype.

A full-string-at-rest/lazy-segment strategy could avoid more initial work, but it would change ink
for the eight existing segmentation/shaping/wrapping exceptions. Switching back to the current
segment renderer at animation start can then introduce a visible jump. Do not enable that strategy
as an unconditional fast path. First repair WORD wrapping and establish shaping-preserving drawing
and transition contracts against BasicText. Any later lazy prototype must test first motion,
retargeting, mirrored glyph changes, async fonts and intermediate-frame continuity, not only settled
screenshots. Additional shared segmenter work should follow separate attribution. Bidi detection took only
0.037 ms in the diagnostic trace; segmenter construction occurs outside the first-apply slice
and was not isolated by this follow-up.

## Final correctness and operations

The complete **44-test instrumentation suite passes with zero failures or skips on both phones**:
Pixel 7 (`2C131FDH200FE7`, API 37) and Pixel 4 XL (`98111FFBA000JM`, API 33).
Each device checks 93,912 native ranges at zero coordinate tolerance. Pixel 4 XL exercises
15,672 shortcut ranges and 78,240 native fallback ranges; device density changes which fixtures
wrap and therefore which branch executes.

The independent corpus remains **43/51 exact BasicText matches**, with the same eight named
wrapping/kerning/ligature/Arabic differences. All 30 expanded bidi cases and six animation endpoint
comparisons remain exact. In addition, **204 software/hardware morph captures per phone are
pixel-identical to the correct pre-optimization renderer**. This includes the characterized
corpus cases, expanded bidi cases, and resting/retarget/intermediate/settled animation captures.
This preserves observed output without treating the eight old defects as typography correctness.
[Current reference results](TYPOGRAPHY_REFERENCE_RESULTS.json) record per-device geometry counts,
reference results, suite metadata and capture-comparison totals.

Raw artifacts are under `/tmp/torph-bidi-perf-20261031/`:

- `pixel7-tests/`, `pixel4xl-tests/`: final test XML, logcat and captures.
- `pixel7-before/`, `pixel7-after/`, `pixel7-after-recheck-grapheme/`: matched benchmark artifacts.
- `analysis/*-capture-parity.json`: all 204 capture comparisons per phone.
- `analysis/{before,after}-all-cpu.json`: every included bidi CPU sample and trace path.
- `analysis/{before,after}-*-median-cpu.csv`: median-wall representative CPU queries.
- `before-source/`, `after-source/`: correct baseline and optimized production snapshots.
- `native-bounds-pixel7/`: the expanded standalone native range test.
- `profile-probe/`, `prototype-*/`: diagnostic and exploratory runs, excluded from final timing.

Raw ATC logs under `/Users/christop/Library/Application Support/atc/logs/`:

- Correct baseline benchmark: `gradle.tasks-2026-11-01T04-18-05-793Z-41291.log`.
- Candidate benchmark: `gradle.tasks-2026-11-01T04-21-28-190Z-42301.log`.
- Complete-case recheck: `gradle.tasks-2026-11-01T04-26-43-985Z-54205.log`.
- Final Pixel 7 suite: `gradle.tasks-2026-11-01T04-23-14-006Z-42688.log`.
- Final Pixel 4 XL suite: `gradle.tasks-2026-11-01T04-29-58-612Z-70576.log`.
- Standalone expanded geometry: `gradle.tasks-2026-11-01T04-17-26-615Z-40956.log`.

ATC serializes queued operations in this workspace; log timestamps include time waiting for that
lock. A method-name filter containing `#` was rejected by ATC before execution; the recheck used
a supported annotation filter instead. Benchmark setting-restoration assertions passed. Final
optimized source hashes match the saved snapshots, and `git diff --check` passes. Both physical
leases were released and the investigation's Perfetto sessions stopped. Device-panel discovery
remained unavailable after its suggested retry; all device execution used project instrumentation
runners. Existing prior performance changes remain intact. No commit, push or issue closure occurred.
