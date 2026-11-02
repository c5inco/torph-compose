# Issue #3: bounded deferred preparation

Performance tables and byte-identity claims below describe initial extraction head `026e714`, before the [mixed-script bidi review correction](BIDI_REVIEW_FOLLOWUP.md). Current source includes that correction; its device validation is recorded in the follow-up. The full timing matrices have not been rerun on the revised source.

This follow-up implements the first-layout change requested in issue #3. The earlier shared cache
and bidi geometry work remain intact. The eager baseline in this comparison already contains
those optimizations; it is not the original uncached renderer.

## Change

Eligible first layouts measure the complete paragraph, resolve the public segment list/ids, and
draw that paragraph once. They do not create live animation channels, standalone segment layouts,
or per-segment placement data before the first draw. `liveCount` retains its logical segment count.
The existing bounded shared TextMeasurer can also reuse identical full paragraphs between states.

When text first changes, the old side reuses regions of its already measured paragraph. The new
side also uses shaped paragraph regions. Neither side reshapes each character/word separately.
Only odometer intermediates still need standalone digit layouts; the final strip entry draws the
native target region to avoid a glyph/alignment change when the strip finishes. Source baselines,
resolved bidi geometry, scale/alpha channels, interruption and callback ordering remain explicit.

ASCII geometry uses batched native boxes. Other scripts use native caret/selection geometry;
disjoint bidi selections are unions of nonoverlapping horizontal intervals, not bounding rectangles.
The clips are hard rectangular clips. An experimental complex-path clip produced 2–3 antialiasing
seam pixels at fractional boundaries, even though the full source paragraph exactly matched the
reference. The hard interval implementation removes those differences without pixel tolerances.
Visual edge regions own outside overhang; vertical clipping does not cut the line's glyph ink.

A handoff of at most 64 ms blends the initial paragraph into moving regions, using isolated
premultiplied image interpolation rather than dimming shared ink with two source-over fades.
Its duration never exceeds a curve's requested duration. Handoff state is ready before start
callbacks run, survives interruption, and its layer bounds are cached outside drawing. At rest,
the optimized renderer draws the full paragraph once. The normal draw path never measures text.

## Boundaries and fallback

The automatic path is limited to one line, at most 64 UTF-16 units and a bounded fragment workload,
nonnegative spacing, positive horizontal scale, and styles without shadow/baseline-shift overrides.
Debug first layouts and ineligible inputs use the existing eager renderer. This protects long-text,
wrapping and mirrored-transform behavior/performance while keeping the short-label optimization
bounded. Public segment ids/list contents remain available immediately. Internal geometry/debug
inspection can explicitly materialize live regions; normal drawing does not request that inspection.

`TextMorphDiagnostics.forceEagerPreparation` selects the old strategy for controlled comparisons
and is captured when a state is created. The production default enables the eligible path. No new
shared mutable segmenter or global geometry cache was introduced. Existing cache capacities,
environment keys, stale-font handling, refresh/animation setting controls, and the prior boundary
optimization remain intact.

## Why simple deferral was rejected

The first prototype only postponed the old standalone segment measurements. On Pixel 7 its
GRAPHEME first-update median rose 1.84 → 5.19 ms, with samples near 10 ms. That merely moved work
and was not enabled as the production solution. Reusing old paragraph regions reduced that cost,
but cold new-side segment layouts still cost too much. Reusing shaped regions on both sides and
batching ASCII geometry removes that duplication. Exploratory data stays separate from final runs.

## Validation contracts

The harness now has separate contracts rather than treating old-renderer parity as typography
correctness:

- Cached/isolated rendering, fonts, lifecycle, callback and animation regressions remain covered.
- Real BasicText supplies independent software and hardware layout/ink references.
- Fresh optimized cases must have no prepared live segments before drawing; wrapped cases must
  take the eager fallback. Initial exact cases include kerning, ligatures and connected scripts.
- Prepared regions are rendered before animation completion, with native target positions, so a
  resting full-paragraph shortcut cannot make the fragment comparison pass vacuously.
- Hardware cases include kerning, ligatures, Arabic, bidi/isolation, combining sequences, emoji,
  serif italic overhang, density/font-scale and RTL. Custom valid range providers force two
  disjoint bidi selections and an interior mixed-direction italic selection.
- Two-pixel displacement controls must fail the software and hardware pixel comparisons.
- Actual first-motion images must equal the pre-change images at time zero. Callback-visible ink,
  retarget continuity, strip endpoints and bounded eager fallbacks have separate assertions.
- Native range geometry remains checked at zero coordinate tolerance by BidiRangeBoundsTest.

Intermediate animation frames are not required to equal static BasicText. Ligatures/connected
shapes can be divided into moving selection regions by the requested segmentation. Reference
fixtures disable EmojiCompat matching for deterministic platform shaping; timing of EmojiCompat
initialization and every custom-font/Unicode combination are not exhaustively proven.

## Measurement protocol

One release/R8 APK contains eager and optimized choices. Each macrobenchmark uses five fresh-state
samples in a warm process with full compilation (explicit speed compilation on API 33). Warmup
and target launches use `NEW_TASK | CLEAR_TASK`, and the readiness description includes the exact
mode and label count. This forces a fresh Activity without killing the warm process. This is
separate from warm text-change loops. Initial-only cases compare plain Text, eager AUTO/GRAPHEME,
and optimized AUTO/GRAPHEME for eight labels and sixteen distinct labels. The old repeated-eight
16-label workload remains available separately.

First-update cases change all eight values after two frame-clock callbacks, retain exactly one
initial and one first-update marker, and wait through the animation. They explicitly measure the
cold first update, including old-region preparation. Bidi AUTO has its own matched pair. Whole-
window FrameTimingMetric samples are retained but must not be described as first-update-only
frame costs. Scoped trace CPU and frame analysis distinguish the measured phases.

A valid case has five outer initial markers, expected first-apply counts (8/16 for morphs, zero
for plain Text), and, for update cases, five update markers and eight applies per iteration.
Missing markers invalidate the entire five-sample case. Entire cases are rerun; individual speed
outliers are not filtered. Device animation scale and min/peak refresh settings are snapshotted,
set and verified on restore through BenchmarkDeviceState. Neither phone is described as 120 Hz.

## Results and recommendation

The eager-per-segment first-layout behavior is fixed for the bounded short-label path, enabled by
default. Recommend keeping that path enabled. Larger/multiline inputs retain the old renderer;
this is not a claim that every possible input now has plain Text's cost or typography.

Final validated-launch matrices contain 16 cases on Pixel 7 and 9 on Pixel 4 XL, five samples each.
All cases pass the marker/count contracts. Medians of the outer initial measurement slice, in ms:

| Device / labels | Plain Text | Eager AUTO | Optimized AUTO | Eager GRAPHEME | Optimized GRAPHEME |
| --- | ---: | ---: | ---: | ---: | ---: |
| Pixel 7 / 8 | 0.686 | 2.087 | 1.071 | 2.948 | 1.165 |
| Pixel 7 / 16 distinct | 0.909 | 3.747 | 1.679 | 4.311 | 1.885 |
| Pixel 4 XL / 8 | 0.675 | 2.793 | 1.254 | 3.773 | 1.298 |

Eight-label first measurement improves 49–66%, saving 1.02–2.48 ms. Sixteen distinct labels on
Pixel 7 improve 55–56%, saving 2.07–2.43 ms. The optimized path still has segmentation, diff and
state overhead above plain Text. These are measurement slices, not whole-frame times or app startup.

Native `TextLayout:initLayout` counts provide direct attribution. For eight labels, eager AUTO
makes 32 calls and GRAPHEME 45; optimized modes make exactly 8, the same as plain Text. For sixteen
distinct labels the counts are 52 / 59 → 16. These counts are identical in all five iterations.
This confirms that duplicated segment layout work is removed from first appearance. It does not
reproduce the issue author's exact 62-call workload or justify attributing their entire 5–10 ms
estimate to this code on every device.

The immediate first update has a real tradeoff. Its outer measure/apply slice includes deferred
old-region preparation and all eight new values, while RenderThread work is measured separately:

| Device / mode | Update body ms eager → optimized | UI-frame CPU ms eager → optimized | RenderThread CPU ms eager → optimized | Actual frame duration ms eager → optimized |
| --- | ---: | ---: | ---: | ---: |
| Pixel 7 / AUTO | 1.634 → 3.479 | 3.262 → 5.215 | 3.418 → 1.134 | 8.684 → 8.105 |
| Pixel 7 / GRAPHEME | 2.972 → 1.969 | 5.829 → 3.213 | 3.460 → 1.058 | 9.908 → 5.236 |
| Pixel 7 / bidi AUTO | 3.431 → 4.917 | 5.494 → 6.455 | 4.594 → 1.506 | 12.045 → 8.383 |
| Pixel 4 XL / AUTO | 1.904 → 2.185 | 3.488 → 3.269 | 3.075 → 1.378 | 8.163 → 5.002 |
| Pixel 4 XL / GRAPHEME | 1.983 → 2.596 | 3.799 → 3.767 | 3.293 → 1.352 | 8.744 → 5.643 |

These are medians of five individually matched trace phases/frames; medians of components do not
sum. No first-update frame in these pairs has a FrameTimeline jank classification. Expected app
frame budgets in these traces are 16.6 ms (Pixel 7) and 16.5 ms (Pixel 4 XL), independent of physical
panel capability. Across initial + immediate update, native layouts fall 46 → 26 (AUTO), 57 → 26
(GRAPHEME), and 49 → 33 (bidi AUTO). Thus the implementation removes duplicated layout work rather
than merely postponing all of it, but some geometry/channel preparation does move to first update.
The AUTO/bidi CPU increases are explicit costs of that choice.

The full 500 ms animation windows do **not** establish a general jank improvement. Pixel 7 has
substantial Buffer Stuffing in both strategies and unchanged app-deadline-miss counts per matched
pair (AUTO 5/5, GRAPHEME 4/4, bidi 5/5 across five runs). Pixel 4 XL has no app deadline misses in
these windows, but optimized AUTO has 33 frames with a Buffer Stuffing component versus zero in
the eager control. Its other classifications include compositor/display events. These observations
remain in the results; they are not filtered or reclassified as an app-layout regression/fix.
The optimized path produces one extra completion redraw per iteration in these windows. Further
frame-pacing work is separate from the demonstrated first-layout saving.

Earlier `pixel7-final`, `pixel7-recheck`, and `pixel4xl-final` matrices predate the workload-specific
readiness/fresh-Activity correction. Some captured the wrong launch workload. They remain raw
exploratory evidence and are excluded from every final table above. No individual samples were
removed from the final matrices.

## Final test status

Both physical phones passed the full **54-test instrumentation suite**, with zero failures,
errors or skips. An additional tight-line-height handoff test was then added; the complete focused
8-method deferred-preparation class passed on both phones, bringing distinct executed methods to
55 per device. Production files were unchanged between these validations and the final benchmarks.

On each phone:

- Fresh optimized initial corpus: **48 exact matches / 51 cases**, with 3 named existing wrapping
  differences on the eager fallback. The explicit legacy corpus remains 43 exact / 8 known.
- **135 eligible paragraph-region reconstructions** match native software pixels exactly across
  AUTO, WORD and GRAPHEME; no seam tolerance.
- **15 prepared-region hardware/software fixtures** match BasicText, including two custom bidi
  selection cases. Negative displacement controls fail as required. Actual first-motion time-zero
  ink, interruption, callback-visible ink, strict odometer endpoint, and fallbacks pass.
- Native selection geometry, cache environment/eviction, async fonts, lifecycle/callback balance,
  accessibility snaps, draw allocation and prior boundary-position regression tests pass.

Exact first/last images and region reconstruction establish the tested continuity contracts.
Intermediate region animation is intentionally different from standalone segment rendering;
unchanged old-renderer pixels are not the typography oracle for this new path.

The report's [structured results](DEFERRED_PREPARATION_RESULTS.json) retain all final benchmark
metrics, scoped CPU medians, frame classifications, test counts and source hashes. Older typography
result JSON files remain historical snapshots of the explicitly eager renderer.

## Evidence locations

Raw data, source hashes/snapshots, rejected prototypes and analyses live under
`/tmp/torph-lazy-20261101/`. The Pixel 4 XL build uses `/tmp/torph-lazy-pixel4/`, a verified
source-identical build mirror, so ATC can coordinate both devices without sharing build outputs.
All builds/test runners use ATC and `build-brief ./gradlew`. No commit, push, or issue closure is
part of this work.


Final raw directories: `pixel7-launch-validated/`, `pixel4xl-launch-validated/`, `pixel7-tests/`,
`pixel4xl-tests/`, `pixel7-handoff-extra/`, and `pixel4xl-handoff-extra/` under that root.
`analysis/*launch-validated-{cpu,frames,animation-frames}.json` contains per-iteration Perfetto
queries. Analysis used the Android-profiler skill's pinned trace_processor v57.2, named local
sessions, module/schema checks, `thread_slice_cpu_time` descendants of the phase markers, and
`android_frames`/FrameTimeline joined through the update marker's ancestor `doFrame`. Sessions
were stopped after each trace. All device leases were released; ATC reports no leases/builds queued.

Reproduce through ATC with a physical-device lease and the build-brief wrapper:

```sh
atc exec gradle tasks :torph-compose:connectedDebugAndroidTest --executable /tmp/torph-issue3-gradle --brief --session <leased-session>
atc exec gradle tasks :benchmark:connectedBenchmarkAndroidTest --property android.testInstrumentationRunnerArguments.annotation=des.c5inco.torph.benchmark.DeferredValidationWorkload --executable /tmp/torph-issue3-gradle --brief --session <leased-session>
```

For the shorter second-device matrix use `DeferredEightLabelWorkload`. The temporary executable
contains `exec build-brief ./gradlew "$@"`. Raw Gradle logs, retained for deeper diagnostics:

- Pixel 7 full suite: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T00-18-50-536Z-32613.log`
- Pixel 4 XL full suite: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T00-10-23-087Z-26269.log`
- Pixel 7 final matrix: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T00-37-45-371Z-66276.log`
- Pixel 4 XL final matrix: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T00-37-35-603Z-66738.log`
- Pixel 7 additional focused validation: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T00-43-00-566Z-71376.log`
- Pixel 4 XL additional focused validation: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T00-43-05-407Z-71761.log`

## PR extraction validation

See [STACK_VALIDATION.md](STACK_VALIDATION.md) for checks on each extracted revision.
At initial extraction head `026e714`, the complete production, test and benchmark source/resources
were byte-identical to the preserved combined implementation measured above. The later review
correction and its new regression test are recorded in the linked follow-up.

The initial extracted tree (`026e714`) passed the full **55-test suite on both physical phones**,
zero failures/errors/skips. At that point all 69 source/resource files matched the measured implementation.
See the linked stack validation for the new per-device logs and structured source hashes.
