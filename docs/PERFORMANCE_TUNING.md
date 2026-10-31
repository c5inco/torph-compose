# Boundary-position tuning, 2026-10-29

This session targets an 8.33 ms workload budget for 120 fps. Measurements use the physical Pixel 7
(Android 17, build CP3A.261005.002.A1), followed by the Pixel 4 XL (Android 13). Both displays support
90 Hz: approximately 11.11 ms between vsyncs while animating. Android's pipelined frame-completion
deadline is longer than one display interval; frame CPU duration and frame overrun are reported
separately. Neither requested phone can display 120 fps, so these measurements cannot establish
120 Hz presentation.

The Pixel 4 XL was connected for a follow-up pass. Its initial adaptive-refresh comparison exposed
large hardware-composer and buffer-queue stalls around refresh switches. A separate controlled
90 Hz A/B comparison confirmed cheaper geometry while isolating those stalls; both sets of raw
results are preserved below.

## Results: Pixel 7

Median text-change cost and placement cost, milliseconds (before → after):

| workload | text change | placement |
| --- | ---: | ---: |
| `morph50` | 2.36 → 2.00 | 0.50 → 0.35 |
| `morph200` | 4.67 → 3.35 | 1.63 → 0.92 |
| `morph1000` | 11.68 → 8.82 | 7.70 → 3.96 |
| `morph1000Edit` | 8.84 → 7.25 | 5.08 → 2.64 |
| `morph50Grapheme` | 2.65 → 2.68 | 0.55 → 0.54 |
| `morph200Grapheme` | 5.35 → 4.86 | 1.73 → 1.69 |

Placement fell 44% for 200-character replacements and about 48% for both 1000-character
workloads. Total text-change cost fell 28%, 24%, and 18%, respectively. The full 1000-character
replacement median is still 8.82 ms, above the 8.33 ms target before accounting for the rest of
the frame. Grapheme placement stayed essentially unchanged, as intended.

Frame CPU P50/P99 and P99 overrun, milliseconds (before → after):

| workload | CPU P50 | CPU P99 | overrun P99 |
| --- | ---: | ---: | ---: |
| `morph50` | 4.43 → 4.73 | 14.96 → 14.82 | 5.49 → 5.40 |
| `morph200` | 6.44 → 6.36 | 14.63 → 15.71 | 4.25 → 5.84 |
| `morph1000` | 10.10 → 9.67 | 27.34 → 20.95 | 16.61 → 11.75 |
| `morph1000Edit` | 6.63 → 6.97 | 22.75 → 19.90 | 13.11 → 6.89 |
| `morph50Grapheme` | 5.17 → 5.65 | 14.46 → 15.20 | 4.70 → 5.39 |
| `morph200Grapheme` | 7.17 → 7.20 | 16.97 → 18.37 | 7.71 → 7.60 |

The 1000-character replacement P99 frame CPU duration improved by 23%, and its P99 overrun
improved by 4.87 ms. The edit case also improved at P99. Smaller workloads did not show consistent
frame-percentile gains: `morph200` P99 CPU duration increased 1.08 ms despite a cheaper text update,
and grapheme P99 varied by 0.75–1.39 ms. Another matched run is needed to distinguish scheduling
variation from a small-workload regression; this session does not establish a smoothness gain
for those cases.

**Outcome:** retain the measured geometry optimization, but do not claim sustained 120 fps or
universally jank-free 90 Hz. Per-frame drawing remains significant for long text. In the first
1000-character iteration, mean `AndroidOwner:draw` was 5.52 ms before and 5.24 ms after, while
maximum measure/layout fell from 12.52 ms to 11.53 ms. The optimized trace still has late frames
(four `Non Animating`, two SurfaceFlinger scheduling/jitter), versus ten late frames in that
baseline iteration. Different frame counts (144 before, 150 after) make these counts illustrative,
not a controlled jank-rate estimate.

## Artifacts and remaining validation

Raw results, per-test logs, and all traces are preserved locally under:

- `/tmp/torph-perf-20261029/before/`
- `/tmp/torph-perf-20261029/after/`
- `/tmp/torph-perf-20261029/before-summary.json`
- `/tmp/torph-perf-20261029/after-summary.json`

The current Gradle output contains the optimized run. ATC full logs:

- Baseline: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-10-29T08-27-24-669Z-69845.log`
- Initial complete test suite: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-10-29T08-31-54-791Z-71319.log`
- Final geometry tests: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-10-29T08-44-19-033Z-83930.log`
- Optimized benchmarks: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-10-29T08-35-56-986Z-79998.log`

The long-text trace comparison used:

- `PerfScreenBenchmark_morph1000_iter000_2026-10-29-08-32-54.perfetto-trace` (before)
- `PerfScreenBenchmark_morph1000_iter000_2026-10-29-08-41-43.perfetto-trace` (after)

The 200-character comparison used `PerfScreenBenchmark_morph200_iter000_2026-10-29-08-30-15.perfetto-trace` (before) and
`PerfScreenBenchmark_morph200_iter000_2026-10-29-08-39-04.perfetto-trace` (after).

A 120 Hz device is still required to validate presentation at the requested frame rate.
Cold-start performance and regenerating the baseline profile were not measured in this warm-frame
tuning pass. Pixel 4 XL follow-up measurements are described separately from the Pixel 7 run.

## Pixel 4 XL follow-up: display-mode stalls

The initial full suite uses the phone's normal adaptive refresh policy. In the first baseline
`morph1000` trace (`PerfScreenBenchmark_morph1000_iter000_2026-10-30-03-42-39.perfetto-trace`),
the app's RenderThread `dequeueBuffer` took 82.41 ms at timestamp `194239287416962 ns`.
The main thread spent 86.51 ms in `postAndWait`, beginning at `194239297091755 ns`.

Just before this, SurfaceFlinger spent 62.40 ms in a binder transaction beginning at
`194239273132742 ns`, with the display HAL spending 59.95 ms in
`DisplayBuiltIn::Commit::RegisterVsync` at `194239273314200 ns`. The trace records six refresh
switches during the short capture. The optimized adaptive 200-character trace also records four
refresh switches, a 50.23 ms maximum in that HAL operation, and a 90.94 ms maximum app
`dequeueBuffer`. These waits are separate from the library's character-box work and affect both
implementations. Refresh-mode switching is a supported suspect for these outliers; a fixed-rate
comparison tests that hypothesis.

In matching baseline 200-character first iterations, adaptive refresh recorded four switches,
a 61.69 ms maximum `RegisterVsync`, and an 86.40 ms maximum app `dequeueBuffer`. Fixed 90 Hz recorded
no refresh-switch events, a 0.01 ms maximum in `RegisterVsync`, and a 7.38 ms maximum app buffer
wait. This controlled result supports refresh switching as the source of the large HAL/buffer
outliers on this phone; the library itself does not change display policy.

The benchmark harness now enables animator duration scale 1 and sets minimum/peak refresh rates to
the highest supported rate at the current display resolution for the class. It snapshots the
original three settings, validates the writes, and restores and verifies them in class teardown.
These settings belong only to the benchmark harness; the library does not change display policy
or the user's animation settings. `GeometryWorkload` selects the 200-character replacement,
1000-character replacement, and 1000-character edit cases for the controlled comparison.

### Adaptive-refresh results

The initial full-suite comparison, milliseconds (before → after):

| workload | text-change median | placement median | frame CPU P99 |
| --- | ---: | ---: | ---: |
| `morph50` | 3.71 → 3.13 | 1.25 → 0.72 | 103.88 → 92.29 |
| `morph200` | 7.86 → 5.98 | 4.77 → 2.05 | 110.75 → 101.99 |
| `morph1000` | 28.23 → 16.57 | 20.33 → 9.31 | 108.19 → 106.44 |
| `morph1000Edit` | 16.48 → 14.91 | 10.36 → 7.10 | 103.31 → 108.43 |
| `morph50Grapheme` | 4.20 → 4.09 | 1.38 → 1.27 | 100.19 → 66.29 |
| `morph200Grapheme` | 9.63 → 9.57 | 5.43 → 5.02 | 100.34 → 71.95 |

The full 1000-character replacement's placement median fell 54% and total change cost fell 41%.
However, P99 frame duration in both builds is dominated by roughly 100 ms outliers. Grapheme code
is unchanged yet its P99 changes greatly between runs, another reason not to attribute these
frame-percentile differences to the library optimization.

Raw adaptive results are under `/tmp/torph-perf-20261029/pixel4xl/before/` and `after/`, with
`before-summary.json` and `after-summary.json` beside those directories. The baseline ATC log is
`/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-10-30T03-36-30-557Z-77201.log`;
the optimized log is `gradle.tasks-2026-10-30T03-43-41-912Z-79642.log` in the same directory. The
optimized run paused on a Play Protect prompt between workloads; the prompt was dismissed before
the remaining measurements resumed. The controlled comparison below is the primary frame-timing
comparison for isolating geometry cost.

### Fixed-90-Hz results

The primary controlled comparison uses the same three warm iterations per workload, on the same
Pixel 4 XL with animations enabled and minimum/peak refresh fixed at 90 Hz for both builds.
Milliseconds (before → after):

| workload | text-change median | placement median | frame CPU P50 | frame CPU P99 | overrun P99 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `morph200` | 7.69 → 5.63 | 4.32 → 2.36 | 18.09 → 8.82 | 25.31 → 22.94 | 12.77 → 9.94 |
| `morph1000` | 26.46 → 16.75 | 19.12 → 9.54 | 19.09 → 15.28 | 54.35 → 44.91 | 44.43 → 33.88 |
| `morph1000Edit` | 12.20 → 12.57 | 6.97 → 6.25 | 11.59 → 14.79 | 42.56 → 34.65 | 29.18 → 21.55 |

For full 1000-character replacements, placement cost fell 50%, total change cost fell 37%, and
P99 frame CPU duration fell 17%. The 200-character replacement also improved in this comparison.
For edits, placement was only 10% cheaper, total change cost was slightly worse (12.20 → 12.57 ms),
and frame CPU P50 increased, while P99 improved. Full-text measurement, segmentation, diff, and
animation setup medians were higher in the optimized edit run. Those phases were not changed by
the geometry patch, so scheduling/frequency variation is plausible, but another matched run would
be needed to resolve it. The edit case does not establish a median-performance improvement on
this phone.

No refresh switches were recorded in the first fixed-rate `morph1000` iterations. Maximum
`RegisterVsync` duration was 0.02 ms before and 0.06 ms after; maximum app `dequeueBuffer` duration
was 5.38 ms before and 5.89 ms after. The large adaptive-mode stalls therefore remained absent in
both implementations. Slow text-change frames remain, so the geometry gain does not establish
jank-free 90 Hz, much less 120 fps.

All 20 library instrumentation tests passed on Pixel 4 XL, including boundary equivalence,
wrapping/script changes, clipping, redraw isolation, interruption, callbacks, and allocation.
The benchmark classes completed successfully, including their setting-restoration checks.
The production library was unchanged during this follow-up; the extra changes are benchmark
controls and workload selection.

Controlled artifacts:

- `/tmp/torph-perf-20261029/pixel4xl/fixed-before/`
- `/tmp/torph-perf-20261029/pixel4xl/fixed-after/`
- `/tmp/torph-perf-20261029/pixel4xl/fixed-before-summary.json`
- `/tmp/torph-perf-20261029/pixel4xl/fixed-after-summary.json`

ATC full logs under `/Users/christop/Library/Application Support/atc/logs/`:

- Controlled baseline: `gradle.tasks-2026-10-30T03-53-20-741Z-82756.log`
- Controlled optimized: `gradle.tasks-2026-10-30T03-56-06-099Z-83547.log`
- Pixel 4 XL instrumentation tests: `gradle.tasks-2026-10-30T03-56-08-674Z-83610.log`

The long-text first-iteration comparison used:

- `PerfScreenBenchmark_morph1000_iter000_2026-10-30-03-55-19.perfetto-trace` (before)
- `PerfScreenBenchmark_morph1000_iter000_2026-10-30-03-58-02.perfetto-trace` (after)

For the controlled subset, add this argument to the reproduction command:

```sh
--property android.testInstrumentationRunnerArguments.annotation=des.c5inco.torph.benchmark.GeometryWorkload
```

## Method

- Library baseline commit `63f31dfedf8cd8c078d0dd0d6186eb920d43fafe`, followed by the optimized
  working tree on the same phone. For Pixel 4 XL, an isolated source copy restores the baseline
  `TextMorphState`; each before/after pair uses the same benchmark harness.
- Pixel 7 was measured before adding the fixed-rate benchmark rule. Its traces show 90 Hz cadence
  while animating; the follow-up Pixel 4 XL controlled results use the explicit 90 Hz rule. Compare
  each phone's own before/after pair rather than treating the sessions as identical environments.
- Existing `PerfScreenBenchmark`: three warm iterations per text workload, R8-minified benchmark
  app, `CompilationMode.Partial()`, existing library baseline profile, 4.8 seconds per iteration.
- Per-change phase medians come from `TextMorphPerf` log lines, including setup/warmup changes.
  Frame percentiles come from Macrobenchmark's measured intervals, not those log lines.
- Default `AUTO` uses word segments for text with spaces; grapheme cases exercise the unchanged
  batch-box path. No animation duration, segmentation default, segment limit, or visual effect was
  reduced to obtain the result.
- Sequential A/B measurements remain sensitive to scheduling, CPU frequency, temperature, and
  unrelated phone activity. P99 reflects relatively few text changes; do not treat one session as
  a device-independent performance guarantee.

## Change

`TextMorphState` avoids filling every character box for eligible ASCII word layouts. Interior
left-to-right segments use their two insertion positions; adjacent segments share a cached edge.
The cache retains positions only on leading lines verified unchanged by `reusableBoxPrefix`.
Wrapped ranges and line ends retain upstream-aware character boxes, filled only for that range.

Non-ASCII text, negative letter spacing, nonpositive horizontal text scale, and layouts with too
many segment boundaries use the existing batch path. The boundary path also rejects RTL paragraphs. This keeps grapheme-heavy
layouts from doing more position queries than the batch implementation. Cached segment layouts,
kerning-based centering, line metrics, rolling-digit cells, and animation channels are preserved.

The new `SegmentBoundsTest` compares interior ranges to fresh character-box unions across wrapping,
kerning samples, positive letter spacing, justification, and RTL paragraph settings. Integration
coverage checks placement through edits, appends, deletions, Unicode/script changes, negative
spacing/scale, and cache reuse.
All 20 instrumentation tests (including the new geometry coverage) passed on Pixel 7, as did core and
Compose JVM tests. The final cached-boundary geometry tests also passed in a separate run.

## Reproduction

With the requested device exclusively leased through ATC:

```sh
ATC_SESSION_ID=torph-perf-8cda atc exec gradle tasks \
  :benchmark:connectedBenchmarkAndroidTest \
  --property android.testInstrumentationRunnerArguments.class=des.c5inco.torph.benchmark.PerfScreenBenchmark \
  --brief
```

Preserve both `benchmark/build/outputs/connected_android_test_additional_output/` and
`benchmark/build/outputs/androidTest-results/` before the next run overwrites them.

The Android CLI was used for SDK and official-documentation discovery. Macrobenchmark recorded
Perfetto traces under ATC's build/device coordination. PerfettoSQL analysis used the Android
profiler skill's pinned trace processor (v57.2).

API reference: [MultiParagraph](https://developer.android.com/reference/kotlin/androidx/compose/ui/text/MultiParagraph).

## Pixel 7 baseline trace evidence

In the first baseline `morph1000` iteration, `AndroidOwner:measureAndLayout` ran for 12.52 ms
at trace timestamp `824833013932043 ns`, on main thread `.demo.benchmark` (`utid=3`, `upid=3`).
Two other layout slices took 12.01 ms and 11.38 ms. The per-change diagnostics put the placement
median at 7.70 ms, of which character-box fill accounted for 7.29 ms. This identifies text-change
geometry as a measured optimization target; it does not attribute all frame time to geometry.

The same trace includes 132 on-time frames classified as `Buffer Stuffing`, seven late frames
classified as `Non Animating`, two on-time `None` frames, and three late
`SurfaceFlinger Scheduling, App Resynced Jitter` frames. These are Android's recorded labels;
`Non Animating` is not evidence that the library's animation was disabled.

The default 200-character first iteration's maximum measure/layout slice fell from 7.55 ms to
6.38 ms, but its late-frame count did not improve (4 to 5). One of the optimized late frames was
classified as SurfaceFlinger scheduling jitter. This illustrates why a faster text update alone
is insufficient to claim every frame meets a refresh-rate target.

## Validated PerfettoSQL

After loading each trace with the profiler skill's pinned wrapper:

```sh
python3 /Users/christop/.agents/skills/android-profiler/references/perfetto/bin/trace_processor \
  server unix --name torph-comparison --daemonize /path/to/trace.perfetto-trace
```

Run the following queries with `query --remote torph-comparison`. Schemas were checked with
`LIMIT 0` before using these columns.

```sql
INCLUDE PERFETTO MODULE slices.with_context;
SELECT s.name, COUNT(*) AS n, ROUND(AVG(s.dur)/1e6,2) AS avg_ms,
       ROUND(MAX(s.dur)/1e6,2) AS max_ms
FROM thread_slice s
WHERE s.process_name='des.c5inco.torph.demo.benchmark' AND s.is_main_thread=1
  AND s.dur>0
  AND s.name IN ('AndroidOwner:measureAndLayout','AndroidOwner:draw',
                 'Compose:recompose','Record View#draw()')
GROUP BY s.name;
```

```sql
SELECT a.jank_type, a.on_time_finish, COUNT(*) AS frames,
       ROUND(AVG(a.dur)/1e6,2) AS avg_ms
FROM actual_frame_timeline_slice a JOIN process p USING(upid)
WHERE p.name='des.c5inco.torph.demo.benchmark' AND a.dur>0
GROUP BY a.jank_type,a.on_time_finish;
```

Display cadence, including gaps while no app frames were produced:

```sql
WITH f AS (
  SELECT e.ts-LAG(e.ts) OVER(ORDER BY e.ts) AS dt
  FROM expected_frame_timeline_slice e JOIN process p USING(upid)
  WHERE p.name='des.c5inco.torph.demo.benchmark'
)
SELECT ROUND(f.dt/1e6,2) AS vsync_ms, COUNT(*) AS n
FROM f GROUP BY vsync_ms ORDER BY n DESC LIMIT 8;
```

End the analysis session with `server kill torph-comparison`.

Display-switch/buffer-wait comparison, using `slices.with_context`:

```sql
SELECT s.process_name, s.thread_name, s.name, COUNT(*) AS n,
       ROUND(MAX(s.dur)/1e6,2) AS max_ms
FROM thread_slice s
WHERE s.name IN ('dequeueBuffer','DisplayBuiltIn::Commit::RegisterVsync',
                 'incrementRefreshRateSwitches')
GROUP BY s.process_name,s.thread_name,s.name;
```

The Pixel 4 XL traces report an empty power-rail packet during import; the analysis uses frame,
thread, and scheduling data and makes no power measurements.
