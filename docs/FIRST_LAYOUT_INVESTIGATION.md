# Issue #3: first appearance of multiple morphing labels

Historical investigation before PR extraction. Its timings and test counts describe that run;
current extracted-stage validation is recorded in [STACK_VALIDATION.md](STACK_VALIDATION.md).

Investigation baseline: `b7965c3efbf13a4324c6c0e5377d3d0ac75fefb8`.
Issue: https://github.com/c5inco/torph-compose/issues/3 (left open).
The boundary-position optimization and device-setting controls remain intact.

## Primary results and recommendation

Final validation: all 22 instrumentation tests pass on both Pixel 7 and Pixel 4 XL.
The existing geometry optimization and tests are unchanged.

The final matched warm-process runs use the same finalized MAIN/LAUNCHER intent, wake/keyguard
setup, full compilation, fresh states and controlled refresh settings on both phones. Five
iterations per workload. Median measurement wall times (milliseconds), excluding composition,
draw and the rest of the frame:

| device / labels | plain Text | Morph AUTO | Morph GRAPHEME | AUTO extra | GRAPHEME extra |
| --- | ---: | ---: | ---: | ---: | ---: |
| Pixel 7 / 8 | 0.64 | 2.52 | 3.41 | 1.87 | 2.76 |
| Pixel 7 / 16 | 0.90 | 4.23 | 6.83 | 3.33 | 5.93 |
| Pixel 4 XL / 8 | 0.70 | 3.34 | 5.40 | 2.64 | 4.71 |

Eager segment preparation is confirmed, but the issue's eight-label 5–10 ms estimate and
62/16 layout count are workload-specific. In the matched eight-label corpus, platform layout
counts are 8 for Text, 42 for AUTO and 87 for GRAPHEME on both phones. The final Pixel 7
GRAPHEME eight-label median spends 2.45 ms preparing segments, including 1.91 ms measuring
segments, versus 0.37 ms full measurement and 0.18 ms segmentation. Pixel 4 XL spends
4.15 ms preparing segments, including 3.25 ms segment measurement, versus 0.45 ms full
measurement and 0.33 ms segmentation. Nested medians are not additive or paired deltas.

**Recommendation:** prototype a composition/environment-scoped bounded shared layout service
first, preserving the current renderer and delegating cache input/stale-font handling to Compose.
A shared-measurer probe proves paragraph reuse with identical positions; no production cache
speedup is claimed. Segmenter sharing is a smaller separate opportunity in these warm workloads.
Lazy full-string drawing remains a second design experiment because the pixel probe finds
kerning, ligature, Arabic and wrapping differences at the renderer switch. It also shifts work to
the first morph and needs explicit transition/interruption tests.

Exact runs and min/max values are retained in [FIRST_LAYOUT_RESULTS.json](FIRST_LAYOUT_RESULTS.json).
The preliminary Pixel 7 tables below document the investigation and are superseded by this
matched final comparison. Cold-process initialization is reported separately.

## Attribution and experiment

`apply(prev == null)` measures the full string, segments it, diffs against an empty list,
and calls `snapToLayout`. That prepares placement and independently shaped layouts before the
first draw. The per-state map already deduplicates identical segment text within a label:
layout count is one full layout plus distinct segment strings, not necessarily one per character.
`place` itself requests a segment layout, then `snapToLayout` requests the same cached layout.
This is two lookups, not two measurements. A new `IcuSegmenter` allocates an empty map; its
iterator pair is initialized lazily at the first nonempty ICU request, inside segmentation.
Numeric-only labels can avoid ICU entirely. No layouts are shared between states.

The added `FirstLayoutBenchmark` creates fresh states in cold processes, with full AOT compilation,
10 iterations per workload, release/R8 target, fixed highest supported refresh and animator scale 1.
Eight short stats-like labels are compared with the same plain Material `Text` strings/style;
16 repeats that set twice. A repeated `12,345` case tests identical text across eight instances.
GRAPHEME explicitly exercises the reported per-character case; AUTO covers default word selection.
A second group warms the normal demo in setup, then creates fresh labels in a new activity
in the same process (five iterations). This isolates screen-first-appearance from process-first
class/font/ICU initialization; no label value ever changes. This is separate from `ColdChangeBenchmark` and warm `PerfScreenBenchmark`.
The wrapper trace encloses child measurement; phase sums include all labels, while the platform
`TextLayout:initLayout` metric covers the activity and can include work outside the label wrapper.
Cold process is not a cold OS font cache. Full AOT intentionally isolates layout algorithm cost,
and is not representative of every installation's compilation state.

Diagnostics now log initial/snap branches and can emit trace sections independently of logging.
Measured benchmark runs keep logging off to avoid string formatting/logcat overhead. Initial phase
logging uses the same exclusive placement/segment-measure accounting as changes; `box` remains a
subset of placement. Trace `prepareSnap` includes placement and segment measurement and must not
be added to its nested `segmentMeasure` time.

## Candidate assessment

1. **Lazy preparation / full-string drawing:** removes first segment measuring and placement;
   segmentation/diff could also be postponed, but `segments` and `liveCount` are public observations
   whose semantics need a deliberate decision. Keep the old full layout and segment metadata until
   a change, prepare the old side using the OLD style/layout/environment before applying the new
   side, and preserve exiting content, interruptions, snaps, debug overlays and callback ordering.
   This shifts work onto the first morph; it does not eliminate it. Measure that first morph too.
   Drawing full paragraphs preserves shaping at rest, but switching to independently shaped pieces
   can change kerning, ligatures, connected scripts, bidi placement and wrapped-word behavior.
   The feasibility test renders both paths into equal software bitmaps; differences identify a
   continuity risk, not proof of visible severity or a hardware-rendering golden.
2. **Shared segmenters:** sharing the existing object as a global is unsafe: both its map and
   BreakIterators are mutable. A bounded locale LRU with synchronization around lookup AND the
   entire setText/first/next traversal is a simple correct design. Return materialized ranges while
   holding the lock, never iterators. Thread-local bounded pools avoid lock contention but retain
   one pool per thread and need a lifecycle policy; cloning cached iterator prototypes is another
   option that still allocates. Main-thread-only sharing requires enforcement, since public
   `IcuSegmenter` also serves callers outside Compose. Cap locales and clear iterator text after
   traversal so a shared iterator does not retain the last large label. Iterator sharing reduces
   initialization only; it does not reuse the segmentation or segment layouts themselves.
3. **Shared layouts:** a bounded composition-scoped service is preferable to an unbounded process
   singleton. A key must include annotated text/spans, resolved style (or a proven measurement-only
   style plus draw overrides), density AND font scale, layout direction, font resolver identity,
   constraints, softWrap, maxLines, overflow and placeholders. These are different for full strings
   and single-line segments. Font features/variation settings belong in resolved style; include the default locale list
   when the measurement API uses it and style does not override it.
   Keying only text/style is insufficient. Cache hits must reject stale async font results even
   when resolver identity is unchanged; Compose's TextMeasurer already handles this internally.
   Reusing a bounded TextMeasurer per stable environment, using explicit measure inputs, is safer
   than reimplementing its stale-font checks. Use it on one owning thread or serialize access;
   do not assume its cache or TextLayoutResult paragraph internals are thread safe. Bound entries
   and preferably retained text/size as long strings and layouts vary greatly in memory cost.
   Existing per-state maps would still duplicate cache references and mask global eviction
   AND bypass stale-font checks on local hits; remove those hits or validate them on every
   apply before consulting the shared measurer, rather than merely swapping the measurer;
   account for live references and test disposal/environment changes. Do not share IDs, channels,
   LiveSegments or mutable geometry buffers.

## Recommendation and acceptance criteria

Prefer a bounded environment-scoped shared measurer/segment cache as the first production
experiment: it preserves the existing renderer and animation continuity, especially for digits
and repeated labels. Keep the per-state fallback, verify async font invalidation and density,
font-scale, direction and resolver changes, and benchmark mixed styles and low-hit-rate text.
A synchronized bounded segmenter pool is a separate smaller optimization whose value must follow
segmentation timings, not be assumed from object count.

Treat lazy full-string drawing as a second design experiment. Require pixel/filmstrip checks at
rest → first animation frame → completion (also interruption), both AUTO and GRAPHEME, ligature
features on/off, kerning pairs, complex scripts, emoji, wraps, and loaded/custom/async fonts. A
safe implementation may need shaped-run/cluster drawing from the original full paragraph rather
than independently remeasuring graphemes. A crossfade can hide discontinuity but changes behavior
and drawing cost and must itself be measured. Do not silently ship a renderer switch as a cache fix.

No production optimization is claimed by this investigation. No issue closure or push to main.

TextMeasurer reference: [AndroidX source](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-text/src/commonMain/kotlin/androidx/compose/ui/text/TextMeasurer.kt).
Its built-in LRU keys layout inputs and rejects stale resolved fonts. This is a design reference
from androidx-main, not a version-pinned guarantee for every Compose release.

## Perfetto query and a representative cold trace

The profiler skill's pinned v57.2 wrapper loaded
`FirstLayoutBenchmark_morph8Repeated_iter005_2026-10-31-02-06-48.perfetto-trace`.
The `thread_slice` and `thread_slice_cpu_time` schemas were checked with `LIMIT 0`.

```sql
INCLUDE PERFETTO MODULE slices.with_context;
INCLUDE PERFETTO MODULE slices.cpu_time;
SELECT s.name, COUNT(*) AS n, SUM(s.dur)/1e6 AS wall_ms,
       SUM(c.cpu_time)/1e6 AS cpu_ms
FROM thread_slice s JOIN thread_slice_cpu_time c USING(id)
WHERE s.process_name='des.c5inco.torph.demo.benchmark' AND s.dur>0
  AND (s.name GLOB 'Morph:*' OR s.name='TextLayout:initLayout')
GROUP BY s.name;
```

This iteration has 8 first applies (27.17 ms wall / 23.01 ms CPU), 8 full measures
(9.68 / 9.53), 8 segmentation phases (5.98 / 4.33), and 8 snap preparations
(10.59 / 8.23), including 48 segment measures (7.55 / 5.90).
There are 56 platform initLayout slices (2.20 / 1.13). Narrow platform slice durations
therefore undercount total segment measurement work. Nested times are not additive.
The FIRST label's full measure is 8.80 ms and segmentation is 5.72 ms; subsequent
segmentation phases are 0.024–0.080 ms. This is evidence for substantial one-time
process initialization, not evidence that constructing eight iterator pairs takes 5.72 ms.
CPU-time attribution is scheduling-based; no sampled stack attribution is claimed.

## Rendering and sharing feasibility probes

Pixel 7 software bitmap comparisons at 24 sp, a 300 px wrapping constraint, transparent background,
and the same density/style/layout size found these exact changed-pixel counts (no tolerance):

| text | AUTO | GRAPHEME |
| --- | ---: | ---: |
| `12,345` | 0 | 0 |
| `AVATAR To Wa` | 724 | 2333 |
| `office affinity ffi` | 0 | 2319 |
| Arabic `مرحبا بالعالم` | 0 | 4797 |
| Devanagari `नमस्ते दुनिया` | 0 | 0 |
| Thai `ภาษาไทย` | 0 | 0 |
| combining acute + family emoji | 0 | 0 |
| wrapping words + explicit newline | 791 | 340 |

This establishes that the renderers are not interchangeable. Zero differences in one string/font
are not a universal guarantee for that script. This probe does not yet test animation-boundary
filmstrips or quantify perceived severity. Pixel counts use the entire padded bitmap denominator,
not only ink pixels, so counts are reported directly rather than misleading percentage scores.

A bounded shared `TextMeasurer(cacheSize = 128)` feasibility test gives independent states the
same paragraph objects for equal segment inputs and identical x/y placements. A font-size change
gets different paragraphs, and a narrower full-layout constraint wraps. This validates a reuse
mechanism; it is not a production cache implementation or a measured cache speedup.

## Pixel 7 cold-process results

Median elapsed times in milliseconds; 10 iterations each. These include process-first
initialization and must not be presented as the warmed screen-appearance estimate.

| workload | label measure | full measure | segmentation | snap preparation | segment measure | platform layouts |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| morph8Repeated | 32.1 | 11.7 | 5.7 | 10.1 | 7.4 | 56.0 |
| morph16 | 58.7 | 14.3 | 10.7 | 30.2 | 23.4 | 174.0 |
| plain8Repeated | 13.0 | 0.0 | 0.0 | 0.0 | 0.0 | 8.0 |
| plain16 | 21.8 | 0.0 | 0.0 | 0.0 | 0.0 | 16.0 |
| plain8 | 18.6 | 0.0 | 0.0 | 0.0 | 0.0 | 8.0 |
| morph8 | 53.3 | 16.0 | 13.1 | 17.6 | 13.3 | 87.0 |

## Validation caveat

An initial combined library-test/macrobenchmark invocation overlapped device tasks and was stopped.
Its warm results are discarded. Subsequent benchmark and test invocations run one device-test task
at a time. The isolated current-source Pixel 7 library run has 22 tests, 15 failures, including
missing Compose hierarchies and unobserved recomposition/state changes in existing tests. Both new
feasibility probes pass. Do not infer a clean regression run from the probe results. Re-running with the exact two baseline production files from b7965c3 (keeping the same test
suite/dependencies/device) reproduces the identical set of 15 failures; the two new probes pass
there too. The investigation files were restored afterward. This does not identify the underlying
test-environment problem, but does not implicate the diagnostic wrappers as its cause. The prior
20-test clean run is historical evidence, not a clean result reproduced today.

## Pixel 7 preliminary warmed-process first appearance

Five fresh-label iterations per workload. Times are measurement wall time, excluding composition,
drawing and the rest of the frame. Min–max is descriptive, not a confidence interval.

| labels / renderer | median measure (min–max) ms | segment preparation ms | segment measures ms | segmentation ms | platform layouts |
| --- | ---: | ---: | ---: | ---: | ---: |
| warmPlain8 | 0.57 (0.55–0.72) | 0.00 | 0.00 | 0.00 | 8 |
| warmMorph8Auto | 2.39 (2.28–4.03) | 1.40 | 0.94 | 0.19 | 42 |
| warmMorph8 | 3.72 (3.62–4.40) | 2.77 | 2.14 | 0.19 | 87 |
| warmPlain16 | 0.86 (0.67–0.92) | 0.00 | 0.00 | 0.00 | 16 |
| warmMorph16Auto | 4.23 (3.64–4.81) | 2.70 | 1.87 | 0.32 | 84 |
| warmMorph16 | 6.99 (6.96–7.58) | 5.38 | 4.18 | 0.35 | 174 |

The eight-label median differences from plain Text are about 1.8 ms (AUTO) and 3.1 ms
(GRAPHEME); sixteen-label differences are 3.3 and 6.1 ms. These are differences of workload
medians, not paired per-frame deltas. Counts are 42 vs 8 (AUTO eight), 87 vs 8 (GRAPHEME eight),
84 vs 16 (AUTO sixteen), and 174 vs 16 (GRAPHEME sixteen), stable across all five iterations.
The original app's 62/16 count is not reproduced by these different strings/modes. The mechanism
is confirmed, but the original count and eight-label 5–10 ms estimate are not universal numbers.
Segmentation totals of about 0.2–0.3 ms bound the likely benefit of segmenter sharing on this
warm corpus; iterator sharing cannot remove the larger segment-layout/placement cost.

## Reproduction and artifacts

Use ATC to check availability and claim a physical phone explicitly. Run device-test tasks in
SEPARATE invocations; Gradle may schedule two instrumentation tasks concurrently within one build.
For `build-brief` inside ATC's build slot, the local wrapper used here is:

```sh
#!/bin/sh
exec build-brief ./gradlew "$@"
```

Save it as an executable `/tmp/torph-issue3-gradle`, then run from the repository root:

```sh
atc status
atc claim --serial 2C131FDH200FE7 --session torph-issue3
atc exec gradle tasks :benchmark:connectedBenchmarkAndroidTest \
  --property android.testInstrumentationRunnerArguments.annotation=des.c5inco.torph.benchmark.FirstAppearanceWarm \
  --executable /tmp/torph-issue3-gradle --brief --session torph-issue3
# After the benchmark completes:
atc exec gradle tasks :torph-compose:connectedDebugAndroidTest \
  --executable /tmp/torph-issue3-gradle --brief --session torph-issue3
atc free --session torph-issue3
```

For cold-process workloads, select individual non-`warm` methods with the runner's `class`
argument instead. The initial cold capture predates the AUTO methods: only `plain8`, `morph8`,
`plain16`, `morph16`, `plain8Repeated`, and `morph8Repeated` were captured cold.

All new raw data is under `/tmp/torph-issue3-20261030/`, including cold/warm Pixel 7 traces,
macrobenchmark JSON and test logs, current/baseline regression XML, and source backups used for
the baseline check. Prior `/tmp/torph-perf-20261029/` data is still present and was not modified.

ATC logs under `/Users/christop/Library/Application Support/atc/logs/`:

- Pixel 7 cold: `gradle.tasks-2026-10-31T02-03-56-254Z-31691.log`
- Pixel 7 clean warm: `gradle.tasks-2026-10-31T02-18-53-127Z-90014.log`
- Current-source isolated Pixel 7 regression: `gradle.tasks-2026-10-31T02-13-36-936Z-74857.log`
- Baseline-source Pixel 7 regression: `gradle.tasks-2026-10-31T02-22-40-079Z-91090.log`

`build-brief` raw regression logs:

- `/var/folders/fv/zzkljc6n60q_f6fdn47ldp280000gn/T/build-brief/build-brief-3528ecfc-2122844089.log`
- `/var/folders/fv/zzkljc6n60q_f6fdn47ldp280000gn/T/build-brief/build-brief-3528ecfc-1121696866.log`

The Device panel failed twice with “Could not communicate with device support. Try refreshing
devices.” (also reporting missing SDK command-line tools). No interactive device driving was
attempted through a fallback. ATC-coordinated project instrumentation supplied the measurements.

The warm Pixel 7 `warmMorph8` iteration 0 trace
(`FirstLayoutBenchmark_warmMorph8_iter000_2026-10-31-02-20-26.perfetto-trace`) gives
3.48 ms first apply, 0.37 ms full measurement, 0.19 ms segmentation, 2.78 ms snap
preparation, and 2.14 ms segment measurement. The scheduling-derived CPU time equals
wall time for these slices: this iteration's cost is CPU work, not a buffer wait or refresh switch.
The same validated SQL above produces these results. No 120 Hz presentation claim is made.

## Pixel 4 XL warmed-process first appearance

Five iterations, fixed 90 Hz, full compilation, Android 13. The finalized harness wakes the
phone/dismisses a non-secure keyguard and uses an explicit MAIN/LAUNCHER intent. Initial
attempts omitted the intent action (activity-not-found), and then did not obtain a rendered
frame (`lastFrameNs=null`). Those failed runs have no usable timing data. A speculative
no-reinstall compilation experiment did not fix launch and was removed; the successful run
uses the same standard `CompilationMode.Full()` as Pixel 7.

| eight labels | median measure (min–max) ms | segment preparation ms | segment measures ms | segmentation ms | layouts |
| --- | ---: | ---: | ---: | ---: | ---: |
| warmPlain8 | 0.70 (0.64–0.93) | 0.00 | 0.00 | 0.00 | 8 |
| warmMorph8Auto | 3.34 (3.02–4.36) | 2.03 | 1.46 | 0.30 | 42 |
| warmMorph8 | 5.40 (5.21–5.61) | 4.15 | 3.25 | 0.33 | 87 |

Eight-label differences from plain Text are 2.64 ms (AUTO) and 4.71 ms (GRAPHEME).
Preparation dominates again. This supports the issue's mechanism on both phones but does not
reproduce 5–10 ms for every eight-label workload. The original app's strings/styles may differ.
ATC log: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-10-31T02-35-23-919Z-94429.log`.


## Final harness and validation

The final Pixel 7 warm run also uses the wake/MAIN/LAUNCHER setup. Its results are close to the
preliminary run; this is the primary matched comparison above. The earlier Pixel 7 baseline/current
failures were recorded before that wake setup; the final Pixel 7 run also passes all 22 tests after explicitly waking the phone. No existing
regression tests or production renderer behavior were changed to obtain this result. Wake state
is supported as the operational cause by this successful rerun; the earlier identical baseline
and current-source failures are retained for audit.
Pixel 4 XL: all 22 tests pass, including the original 20 and both new probes.

Final benchmark/test ATC logs:

- Pixel 7 final warm: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-10-31T02-37-56-067Z-95111.log`
- Pixel 4 XL tests: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-10-31T02-36-43-444Z-94829.log`

Both successful macrobenchmark classes completed their device-setting restoration checks.

Pixel 7 final tests: all 22 pass.
ATC log: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-10-31T02-41-29-338Z-96226.log`.
The earlier failed Pixel 7 test reports are historical investigation artifacts, not the final
regression status. Final XML is under `pixel7-tests-final/` and `pixel4xl-tests/` in the raw directory.

No commits were pushed, main remains untouched, and issue #3 remains open. Both physical-device
leases were released. Profiler sessions were stopped or had already expired.
