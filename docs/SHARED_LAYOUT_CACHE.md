# Shared layout cache implementation and measurements

Historical cache measurements are retained below. Current extracted-revision validation is in
[STACK_VALIDATION.md](STACK_VALIDATION.md); the first-layout renderer is still eager in this PR.

This follows [the issue #3 investigation](FIRST_LAYOUT_INVESTIGATION.md). It implements layout
sharing only. Segmenters, segment selection, independent segment shaping, placement, drawing,
channels and callbacks remain separate per state.

## Implementation

`rememberTextMorphState()` now obtains a bounded `TextMeasurer` shared by matching states within
the same Compose view. Compose's own 256-entry LRU keys text/style, constraints, wrapping,
max lines, overflow, density, layout direction and resolver, and checks stale resolved fonts.
The registry separates density/font scale, resolver identity, direction and platform default
locales. Reading configuration causes default locales to be reconsidered on configuration changes.
The project resolves Compose UI 1.12.1; its constructor does not take a default-locale argument,
so locale isolation belongs to the environment registry. No Compose version was changed.

A 16-entry environment LRU holds weak owner, density, resolver and measurer references.
Existing states retain their own measurer when an environment entry is evicted. Disposed states
and views are not kept alive by the registry. Main-thread composition can share; background
composition gets an isolated bounded measurer and cannot borrow the UI thread's mutable cache.
No public configuration or provider is required.

The existing per-state segment map remains to preserve cheap warm changes. Its hits now reject
stale resolved fonts, including rolling-digit strips. Full layouts are refreshed on font changes
even if the text/layout key is unchanged. Direction changes invalidate segment-map entries and
are passed explicitly to measurement. The previous boundary-position optimization is intact.

The cache is bounded in entry count, not bytes. Large paragraphs can retain more memory than
short labels; per-state/live layout references also remain until their states disappear. A full
cache or environment eviction reduces hit rate; correctness does not depend on a hit.

## Validation

New tests cover default-instance sharing, independent live segments, density/font-scale,
direction and resolver separation, owner/default-locale separation, environment and layout LRU
eviction, async font completion through a real AndroidFont loader, and exact pixel equivalence
against isolated measurers across AUTO/GRAPHEME, kerning pairs, ligatures, scripts, emoji,
wrapping and draw-only style changes. Existing regression assertions and allocation limits are preserved; the allocation test adds layout synchronization and a pre-sampling animation assertion.

## Measurement protocol

The same `FirstLayoutBenchmark` workload and fixed refresh/animation controls are used. Each
warm-process iteration creates fresh states in a new activity after warming the normal demo.
The new activity's view scope starts empty, so hits measure sharing among its labels rather
than reuse from the warm-up screen. Five iterations per workload, full compilation, physical
phones. Measurements exclude composition/drawing and are not total-frame or presentation claims.
Two repeated-label workloads (eight copies of `12,345`) were added and measured on Pixel 4 XL with a fresh baseline.
On Android 13 the harness explicitly runs `cmd package compile -f -m speed` before each method
and uses `CompilationMode.Ignore()` to avoid Macrobenchmark's profile-reset reinstall (which
stalled during the first attempt). Both matched runs use this same full-AOT protocol. Android 17
continues using `CompilationMode.Full()`. Labels are measured only after a stable awake/home setup.

Raw data and logs: `/tmp/torph-shared-cache-20261031/`.

### Font-refresh behavior

An unchanged-text font/EmojiCompat refresh waits until an active morph settles, then invalidates
layout automatically. This preserves the in-flight generation and its complete/cancel callback
contract. New text still measures with the loaded font, including persisting segments and digit
strips. A UI regression test completes a real asynchronous AndroidFont during an animation and
checks that the generation completes exactly once, is not canceled, and refreshes without a
manual measure call. Stale-font checks also disable geometry-prefix reuse.

The existing allocation guard now waits for Android layout around warm-up and asserts that the
morph has started before sampling. Its allocation ceiling and final still-animating assertion are
unchanged. The initial failure exposed an actual refresh-cancellation regression, which was fixed;
it was not resolved by weakening the test.

## Matched first-layout results

Median milliseconds; five iterations each. Before/after use the same phone, strings, ordering,
refresh controls and AOT compilation. Percentages describe these runs, not confidence intervals.

| device / workload | before | shared cache | lower time | layouts before → after |
| --- | ---: | ---: | ---: | ---: |
| pixel4xl / warmMorph8Auto | 3.68 | 2.84 | 22.9% | 42 → 32 |
| pixel4xl / warmMorph8 | 5.49 | 3.94 | 28.3% | 87 → 45 |
| pixel4xl / warmMorph16Auto | 6.09 | 3.77 | 38.1% | 84 → 32 |
| pixel4xl / warmMorph16 | 10.56 | 5.88 | 44.3% | 174 → 45 |
| pixel4xl / warmMorph8Repeated | 3.69 | 1.50 | 59.3% | 56 → 7 |
| pixel4xl / warmPlain8 | 0.63 | 0.66 | -4.6% | 8 → 8 |
| pixel4xl / warmPlain16 | 1.08 | 1.03 | 4.5% | 16 → 16 |
| pixel4xl / warmPlain8Repeated | 0.64 | 0.65 | -0.5% | 8 → 8 |
| pixel7 / warmMorph8Auto | 2.44 | 2.34 | 4.0% | 42 → 32 |
| pixel7 / warmMorph8 | 3.53 | 2.76 | 21.8% | 87 → 45 |
| pixel7 / warmPlain8 | 0.64 | 0.66 | -3.3% | 8 → 8 |

Pixel 4 XL's plain Text medians stay within about 5% between runs. The largest gain is for
repeated labels; sixteen-label layouts also benefit more than eight because the full strings
repeat. The cache still segments and places each instance; it does not eliminate first-layout
work or make a morph as cheap as Text. Unique strings/styles and cache saturation reduce hits.

Pixel 7 GRAPHEME falls from 3.53 to 2.76 ms (21.8%). AUTO falls from 2.44 to 2.34 ms (4.0%),
with overlapping ranges and run variation of similar magnitude; this does not establish a
reliable AUTO benefit on that phone. One original AUTO trace omitted the outer measurement
markers and was replaced by a complete five-iteration recheck. No timing outliers were removed.
All included captures have one outer measurement and the expected number of fresh state applies.
Raw original/recheck data is retained in the results JSON and raw directory.

Exact runs/min/max and trace references: [SHARED_LAYOUT_CACHE_RESULTS.json](SHARED_LAYOUT_CACHE_RESULTS.json).
No cold-process startup, total-frame/FPS, heap-size or matched warm-change throughput gain is
claimed. The existing steady-state allocation guard passes at 11,741 bytes/frame on Pixel 4 XL
against its unchanged 16 KB ceiling; this includes Compose 1.12's own drawing allocations.

## CPU attribution

The profiler skill's pinned Perfetto v57.2 wrapper analyzed iteration 0 of the fresh sixteen-label
Pixel 4 XL GRAPHEME pair. The before trace is
`FirstLayoutBenchmark_warmMorph16_iter000_2026-10-31-22-44-41.perfetto-trace`;
the after trace is `FirstLayoutBenchmark_warmMorph16_iter000_2026-10-31-22-48-16.perfetto-trace`.
Both are under their corresponding `pixel4xl-before/` and `pixel4xl-after/` raw directories.
Schemas were checked using `LIMIT 0` before the query below.

```sql
INCLUDE PERFETTO MODULE slices.with_context;
INCLUDE PERFETTO MODULE slices.cpu_time;
SELECT s.name, COUNT(*) AS n, SUM(s.dur)/1e6 AS wall_ms,
       SUM(c.cpu_time)/1e6 AS cpu_ms
FROM thread_slice s JOIN thread_slice_cpu_time c USING(id)
WHERE s.process_name='des.c5inco.torph.demo.benchmark' AND s.dur>0
  AND (s.name GLOB 'Morph:*' OR s.name='Labels:firstMeasure'
       OR s.name='TextLayout:initLayout')
GROUP BY s.name;
```

The label measure falls from 11.42 ms wall / 11.23 ms CPU to 5.74 / 5.54 ms in this pair.
Snap preparation falls from 8.58 / 8.58 ms to 3.85 / 3.71 ms, including segment-measure requests
from 6.74 / 6.74 ms to 2.33 / 2.33 ms. The number of per-state requests remains 158; physical
platform layouts fall from 174 to 45. This directly supports reuse as the cause of cheaper
measurement. These are representative iterations, not the workload medians. Nested durations
are not additive. The known empty power-rail packet is present; no power analysis uses it.
Diagnostics now label segment-map misses as *local* misses, since they can hit the shared LRU.

## Tests and operational notes

- Pixel 4 XL: full 29-test suite passes; the strengthened UI font test was also rerun with all seven cache tests passing.
- Pixel 7: full 29-test suite passes with the final UI font test.
- Cache vs isolated-renderer pixel equality covers both AUTO and GRAPHEME, ligatures on/off,
  kerning, Arabic, Devanagari, Thai, emoji, wrapping and cached draw-color changes.
- Bounds for both LRUs, environment separation, and async-font invalidation are tested.
- The initial default-font/EmojiCompat cancellation regression was fixed before completion;
  failed runs are retained for audit and are not the final test status.
- An interrupted Android 13 profile-reset attempt could not execute normal teardown. The
  phone was explicitly returned to the last verified idle snapshot (animation scale 1,
  min/peak refresh absent), with read-back verification. The temporary recovery test is not
  included in the final source. All subsequent successful benchmark classes verified their own
  snapshot restoration. Both leases were released.
- No issue closure, commit/push to main, segmenter sharing, or renderer switch was performed.

See [REGRESSION_TESTING.md](REGRESSION_TESTING.md) for the subsequent expanded frame-parity,
font/accessibility, environment-pressure and lifecycle suite, including the disposal correction
found by that coverage. Those functional tests add no new performance measurements.

## Reproduction and logs

Use the ATC/build-brief wrapper documented in [the investigation](FIRST_LAYOUT_INVESTIGATION.md).
Run one device-test task per invocation. Claim a phone after checking availability, then select
`FirstAppearanceWarm` for the full matrix or `FirstAppearanceEight` for the eight-label subset.
`FirstAppearanceAutoEight` selects the AUTO recheck. The exact pre-cache source is retained under
`/tmp/torph-shared-cache-20261031/before-source/`; final cache source is in this working tree.
Do not replace the benchmark harness when rebuilding the before version.

ATC full logs under `/Users/christop/Library/Application Support/atc/logs/`:

- Pixel 4 XL before: `gradle.tasks-2026-10-31T22-44-19-560Z-26591.log`
- Pixel 4 XL after: `gradle.tasks-2026-10-31T22-48-04-624Z-36360.log`
- Pixel 4 XL full tests: `gradle.tasks-2026-10-31T23-02-29-761Z-56946.log`
- Pixel 4 XL strengthened cache tests: `gradle.tasks-2026-10-31T23-04-35-360Z-58248.log`
- Pixel 7 before: `gradle.tasks-2026-10-31T23-07-18-643Z-59177.log`
- Pixel 7 after: `gradle.tasks-2026-10-31T23-10-48-362Z-59925.log`
- Pixel 7 AUTO recheck: `gradle.tasks-2026-10-31T23-16-55-636Z-61223.log`
- Pixel 7 full tests: `gradle.tasks-2026-10-31T23-14-32-338Z-60723.log`

The initial canceled/profile-reset and diagnostic test logs are retained alongside the raw data.
