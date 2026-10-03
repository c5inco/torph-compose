# Benchmarks

Performance measurements for Torph for Jetpack Compose, and how to reproduce them.

> **These numbers are a snapshot, not a spec.** They were measured in September 2026 on a Pixel 10
> Pro running Android 17, against the code at that time. Any change to the diff, placement, or draw
> path can move them. Rerun the suite before relying on a specific figure.

## Allocation guard

`DrawAllocationTest` in `:torph-compose` measures process-wide ART allocation per animated frame with
about 200 live segments, and fails above 16 KB per frame. It is a regression guard against
per-segment lambdas or string building creeping into the draw path. When last measured it allocated
3.3 KB per frame, down from 80 KB before per-segment `Animatable`s were replaced by plain float
channels.

Since then, rolling digits stopped rebuilding their fade gradient every frame (about 0.9 KB per
rolling digit per frame). On the Pixel, measured in one session with three runs per build, that
took the test from 7.1-7.6 KB to 4.4-4.9 KB per frame (its 60-frame window only resolves steps of
about 0.5 KB). On an emulator, over a 600-frame window, the same scene went from 5.1 KB to 2.3 KB
per frame, and one rolling digit on its own now allocates the same 2.3 KB as the 200-segment scene:
what remains is Compose's and the test clock's fixed per-frame cost, not per-segment work.

## Running the suite

`:benchmark` is a Macrobenchmark module targeting the demo's Perf screen:

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest
```

The benchmark variant installs as `des.c5inco.torph.demo.benchmark`, so it coexists with the debug demo.
(This needs androidx.benchmark 1.4.1+: on API 36, 1.3.x reads the 15-character kernel process name
from `pgrep -l` and cannot match a package id longer than that.)
Results (JSON + Perfetto traces) land in
`benchmark/build/outputs/connected_android_test_additional_output/`. Frame timing is under
`sampledMetrics`, not `metrics`.

`ColdChangeBenchmark` is separate from the frame-timing suites: it kills the app before every
iteration to measure the first text changes after launch (see [Baseline Profile](#baseline-profile)).

## Pixel 10 Pro (Android 17), 3 iterations each

| benchmark | live segments | frame CPU P50 | P90 | P99 | overrun P50 | overrun P99 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| morph50 | ~50 | 4.6 ms | 6.3 ms | 11.8 ms | -10.0 ms | -2.3 ms |
| morph200 | ~200 | 5.6 ms | 7.6 ms | 16.1 ms | -9.3 ms | 1.0 ms |
| morph1000 (auto → word fallback) | ~360 | 6.2 ms | 8.1 ms | 22.8 ms | -7.8 ms | 8.8 ms |

Cold startup time to initial display: 263 ms median.

Overrun is how late a frame finished against its deadline, so negative means it finished early.
Every size holds frame rate at P50 and P90, including 1000 characters. Sustained performance mode
was off for these runs, so expect some thermal variance.

There used to be a separate `morph1000Word` run forcing `Segmentation.WORD`. It measured within
noise of `morph1000` (P50 6.4 ms, P99 23.1 ms) because `AUTO` already falls back to word
segmentation above 300 segments, so it was dropped.

### Before and after the placement and diff work

A same-session comparison on the same Pixel: the library before (`c09fd17`) and after (`5fe78ee`)
the batched box fill, the unboxed LCS, the reused fade gradient and the early segmentation cutoff.
Builds alternated before, after, after, before, two runs of three iterations each; values are means
of the per-run percentiles. Frame times drift between sessions (this session's baseline `morph1000`
P99 was about 28 ms, against 22.8 ms in the table above), so compare within this table only:

| benchmark | frame CPU P50 | P90 | P99 |
| --- | ---: | ---: | ---: |
| morph50 | 4.7 → 4.7 ms | 7.2 → 7.2 ms | 16.5 → 14.9 ms |
| morph200 | 6.4 → 6.5 ms | 8.2 → 8.7 ms | 16.3 → 14.4 ms |
| morph1000 | 10.2 → 8.9 ms | 25.2 → 16.5 ms | 28.5 → 25.0 ms |
| numberRolling | 4.5 → 4.4 ms | 5.5 → 5.4 ms | 10.3 → 9.7 ms |

Typical frames do not move, because the draw path was already cheap. The gain is in the tail, which
is the text-change frames. The P99 improvement held in every run (`morph1000` 28.2 and 28.8 ms
before, 26.3 and 23.7 ms after; `morph200` 16.2 and 16.3 ms before, 14.6 and 14.2 ms after). The
`morph1000` P90 varied too much between the two runs after (21.6 and 11.4 ms) to quote as one
number. 1000 characters still misses frames on a change, just less often and by less.

#### Pixel 7 (Android 16)

Same method and library code as above.

| benchmark | frame CPU P50 | P90 | P99 |
| --- | ---: | ---: | ---: |
| morph50 | 4.6 → 4.7 ms | 6.1 → 6.3 ms | 13.7 → 13.8 ms |
| morph200 | 6.8 → 7.0 ms | 14.9 → 11.6 ms | 27.1 → 28.5 ms |
| morph1000 | 12.6 → 9.2 ms | 17.4 → 16.7 ms | 37.4 → 28.5 ms |
| numberRolling | 4.7 → 4.4 ms | 7.0 → 6.4 ms | 12.7 → 12.3 ms |

`morph1000` P99 improved in every run (38.9 and 35.8 ms before, 29.0 and 28.0 ms after). `morph200`
P99 did not move (28.2 and 26.0 ms before, 29.0 and 28.1 ms after), even though its text change got
4.6 ms cheaper, so its worst frames are dominated by something other than the change itself. The
`morph1000` P50 and `morph200` P90 differences are within run-to-run noise (`morph1000` P50 was 10.6
and 14.5 ms in the two runs before).

The cost of one text change (medians, about 45 changes per size per build):

| chars | total | position | of which box | measure text | tokenize | diff | anim setup |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10 | 1.64 → 1.62 ms | 0.30 → 0.25 ms | 0.19 → 0.15 ms | 0.49 → 0.61 ms | 0.16 → 0.17 ms | 0.27 → 0.23 ms | 0.29 → 0.25 ms |
| 50 | 4.77 → 3.59 ms | 1.04 → 0.56 ms | 0.88 → 0.44 ms | 1.21 → 1.33 ms | 0.34 → 0.45 ms | 0.95 → 0.22 ms | 0.34 → 0.31 ms |
| 200 | 10.72 → 6.09 ms | 3.18 → 1.74 ms | 2.86 → 1.40 ms | 1.72 → 1.58 ms | 0.99 → 0.84 ms | 2.93 → 0.63 ms | 0.74 → 0.57 ms |
| 1000 | 21.80 → 13.10 ms | 13.95 → 7.17 ms | 13.23 → 6.69 ms | 2.13 → 1.89 ms | 2.29 → 1.25 ms | 1.04 → 0.70 ms | 1.17 → 0.80 ms |

`DrawAllocationTest` went from 6.6-7.1 KB to 3.3-4.4 KB per frame, consistent across all six runs
of each build.

#### Pixel 4a (Android 13)

The same comparison on a mid-range phone from 2020, same method (before, after, after, before; two
runs of three iterations each). The library code is the same as above.

| benchmark | frame CPU P50 | P90 | P99 |
| --- | ---: | ---: | ---: |
| morph50 | 23.2 → 15.4 ms | 32.2 → 24.0 ms | 40.3 → 27.9 ms |
| morph200 | 16.8 → 16.3 ms | 32.9 → 32.4 ms | 58.2 → 43.3 ms |
| morph1000 | 31.4 → 30.5 ms | 35.0 → 34.5 ms | 96.7 → 80.2 ms |
| numberRolling | 6.2 → 6.2 ms | 12.6 → 12.6 ms | 13.9 → 13.7 ms |

The P99 improvement held in every run (`morph1000` 95.6 and 97.7 ms before, 80.3 and 80.1 ms after;
`morph200` 56.8 and 59.7 ms before, 42.5 and 44.0 ms after). The `morph50` row is noisy: one run
before had double the P50 of the other three (30.7 ms against about 15.5 ms), so its typical frames
did not really change.

The cost of one text change, from `TextMorphDiagnostics` (medians, about 45 changes per size per
build):

| chars | total | position | of which box | measure text | tokenize | diff | anim setup |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10 | 2.21 → 1.73 ms | 0.80 → 0.45 ms | 0.68 → 0.37 ms | 0.65 → 0.63 ms | 0.16 → 0.16 ms | 0.19 → 0.16 ms | 0.35 → 0.30 ms |
| 50 | 12.58 → 8.07 ms | 3.87 → 2.39 ms | 3.15 → 1.80 ms | 1.67 → 1.67 ms | 0.93 → 1.05 ms | 3.33 → 0.58 ms | 1.27 → 1.25 ms |
| 200 | 24.59 → 16.08 ms | 8.88 → 5.26 ms | 7.76 → 4.56 ms | 1.54 → 1.55 ms | 1.92 → 1.75 ms | 8.15 → 1.51 ms | 2.28 → 1.99 ms |
| 1000 | 52.90 → 22.26 ms | 34.80 → 14.22 ms | 33.23 → 13.02 ms | 3.21 → 1.94 ms | 6.83 → 2.94 ms | 5.09 → 1.48 ms | 3.34 → 1.70 ms |

The slower the phone, the larger the gain: 58% off a 1000-character change here, against 40% on
the Pixel 7 and 31% on the Pixel 10 Pro. `DrawAllocationTest` was noisier here (3.0-9.0 KB per frame after, 5.7-9.9 KB before; medians
about 3.3 and 5.9 KB).

The high P50 frame times here do not mean the animation drops to 30 fps. Frames are only rendered
while a morph animates (about 27 frames per 400 ms morph), and the main thread and RenderThread work
in parallel, so frames still come out at close to 60 fps. Android's frame timeline in the traces
classes almost all of them as *Buffer Stuffing*: the app is queuing frames ahead, so each one is
presented a vsync later than scheduled, but at a steady rate. That is why overrun is positive at P50.

Real missed deadlines (*App Deadline Missed*) are 4-7 frames per 4.8 s iteration, which covers about
three text changes, and nearly all of them fall at the start of a morph: the text-change frame (22-55 ms at 200
characters, 22-92 ms at 1000). In `morph1000` a few more land about 80 ms into the morph, which is
not yet explained. So on mid-range hardware too, the lever is the text-change frame (placement, or
moving segmentation and the diff off the main thread), not the per-frame draw.

## Which CPU core runs the change

On phones with more than one kind of CPU core, the biggest single factor in what a text change
costs is not this library's code but which core the scheduler runs the main thread on. Measured
from the scheduling data in the Perfetto traces of the runs above (time the main thread spent
running on each core during each text-change layout pass):

| phone | cores | where 1000-character changes ran | cost by core |
| --- | --- | --- | --- |
| Pixel 4a | 2 fast (A76), 6 slow (A55) | 22 of 36 on slow cores | 18-23 ms fast, 40-52 ms slow |
| Pixel 7 | 2 fastest, 2 middle, 4 slow | 33 of 36 on middle cores | 13.8 ms middle (median), 7.7 ms fastest |
| Pixel 10 Pro | | 45 of 48 on the same group of cores | steady |

Each pass ran entirely on one kind of core, and the main thread spent under 1 ms waiting for a
core, so it is placement, not contention. This is why the Pixel 4a's 1000-character changes land at
either about 20 ms or about 50 ms.

**What triggers the change decides the core.** In the Pixel 4a's `ColdChangeBenchmark` traces, all
39 text changes triggered by a tap ran on the fast cores (13.5-19 ms), but only 47 of 156 changes
triggered by the demo's timer did. Android boosts the CPU briefly on touch input; a change driven by
a timer or incoming data arrives after the app has been idle, starts on a slow core, and is not
moved within the frame. So:

- Every benchmark here changes text on a timer, so they measure the worst case. A change made in
  response to a tap is cheaper.
- That worst case is the common one for counters, stopwatches, live values and streaming text.
- Moving the work to a background thread would not change this: background threads are placed the
  same way.
- Android's Performance Hint API (ADPF) lets an app tell the system a CPU burst is coming. It is
  supported on the Pixel 7 and Pixel 10 Pro but not on the Pixel 4a (Android 13 reports no support
  and refuses to create a hint session), so it cannot help the phone that needs it most.

Reducing the work helps on every core, so it remains the main lever.

## Where the time actually goes (from the Perfetto traces)

Measured with Perfetto's `trace_processor` over the captured traces, not inferred:

**The slow frames are the text-change frames.** In `morph1000` the four worst frames (22.9-24.4 ms)
are spaced exactly 1.5 s apart, matching the Perf screen's cycle period. Every other frame is well
under budget.

**Almost none of that frame is text measurement.** On the worst frame, 16.1 ms of the 24.4 ms sits
in Compose's `AndroidOwner:measureAndLayout`, and inside it:

| part of the text-change frame | time |
| --- | ---: |
| `AndroidOwner:measureAndLayout` | 16.1 ms |
| of which `Constructing StaticLayout` (text layout) | 0.46 ms |
| remainder: segment + diff + `place()` + animation setup | ~15.6 ms |

Only **one** `StaticLayout` is built on that frame, the full target string. The per-segment layouts
hit the `layoutCache`, because the demo cycles strings drawn from one word list. So text measurement
is about 3% of the cost, and the remaining ~15.6 ms is this library's own layout work. That part is
not separately traced, so it cannot be split further without adding trace sections to the code.

Worst measure/layout pass by size, showing the cost is sub-linear in characters (20x the text for
3.7x the cost):

| | 50 chars | 200 chars | 1000 chars |
| --- | ---: | ---: | ---: |
| worst measure/layout | 4.4 ms | 8.0 ms | 16.1 ms |

**Composition never runs per frame**, which was the design goal:

| run | frames | layout passes | recompositions | avg draw |
| --- | ---: | ---: | ---: | ---: |
| morph50 | 145 | 145 | 15 | 1.23 ms |
| morph200 | 136 | 112 | 12 | 2.06 ms |
| morph1000 | 108 | 14 | 14 | 3.09 ms |

Recomposition happens per text change, not per frame. Draw works out to roughly **6 µs per segment**
plus ~0.9 ms of fixed cost for the rest of the demo screen.

**Layout, however, does run per frame while the container size animates.** `morph50` takes a layout
pass on all 145 frames; `morph1000` takes only 14, because wrapped text of a fixed character count
keeps the same height and the size animation has nothing to do. That pass does not re-measure text
(the layout key is unchanged), but it is still ~0.3 ms of tree layout per frame. `sizeMode = Snap`
avoids it entirely, which is the cheap win for fixed-width slots like counters.

So the honest summary: drawing scales fine, composition is absent from the hot path, and the
remaining cost is concentrated in one frame per text change, inside this library's own diff and
placement work rather than in text measurement. Caching settled segments in a `GraphicsLayer` would
not touch that frame, so it is the wrong lever; reducing per-segment work in `place()` is the right
one. Neither is needed at current frame times.

## Feature benchmarks

`FeatureBenchmark` covers what `PerfScreenBenchmark` misses: digit rolling, retargeting under rapid
updates, and multi-line reflow. Same device, 3 iterations each.

| benchmark | what it drives | frames | P50 | P90 | P99 | overrun P99 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| numberIncrement | +1 taps, one or two digits roll | 684 | 3.5 ms | 4.4 ms | 9.3 ms | -3.3 ms |
| numberRolling | random jumps, most digits roll | 411 | 3.6 ms | 4.6 ms | 8.1 ms | -3.8 ms |
| interruption | 80 ms timer + live typing | 338 | 5.0 ms | 8.6 ms | 10.9 ms | -3.3 ms |
| multiLine | paragraph swap, lines re-flow | 455 | 5.0 ms | 6.1 ms | 12.2 ms | -2.3 ms |

Overrun is negative at every percentile including P99, so **not one frame missed its deadline** in
any of these. The features most likely to be expensive turn out to be the cheapest: digit rolling
is the fastest thing here, and interrupting a morph every 80 ms costs nothing extra. Long text
remains the only case that drops frames.

## Where a text change spends its time

Turn on `TextMorphDiagnostics.logTimings` and every change logs a phase breakdown
(`adb logcat -s TextMorphPerf`). Medians on a Pixel 10 Pro:

| chars | segments | total | position | of which per-char box | measure text | tokenize | diff | anim setup |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 9 | 9 | 1.64 ms | 0.18 ms | 0.11 ms | 0.75 ms | 0.19 ms | 0.24 ms | 0.14 ms |
| 50 | 50 | 3.93 ms | 0.80 ms | 0.65 ms | 1.04 ms | 0.42 ms | 1.03 ms | 0.43 ms |
| 200 | 200 | 7.32 ms | 2.39 ms | 2.08 ms | 1.44 ms | 0.77 ms | 1.83 ms | 0.76 ms |
| 1000 | 340 | 14.43 ms | 8.45 ms | 7.94 ms | 1.79 ms | 1.71 ms | 0.97 ms | 1.07 ms |

(Column medians are taken independently, so they do not sum exactly to the total.)

**Positioning dominates, and inside it the per-character bounding-box scan is the whole story.** At
1000 characters it is 55% of the change. `place()` calls `TextLayoutResult.getBoundingBox` once per
character to find each segment's left and right edge, at roughly 8 µs per call, so the cost is
linear in characters no matter how few segments there are. Neither of the earlier guesses was right:
text measurement is 12% and the diff is 7%.

**Update:** placement now fills every character's box in one `MultiParagraph.fillBoundingBoxes`
call per layout instead of calling `getBoundingBox` per character (same boxes, no per-call `Rect`s,
and neighbouring characters share an edge lookup), and the diff's LCS no longer boxes its indices.
The same breakdown on the same Pixel, before and after, from the comparison runs above (55-65
changes per size per build):

| chars | total | position | of which box | measure text | tokenize | diff | anim setup |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 50 | 3.46 → 2.43 ms | 0.81 → 0.48 ms | 0.60 → 0.34 ms | 0.90 → 0.82 ms | 0.35 → 0.41 ms | 0.69 → 0.19 ms | 0.32 → 0.30 ms |
| 200 | 6.77 → 4.11 ms | 2.33 → 1.38 ms | 1.97 → 1.08 ms | 0.55 → 0.67 ms | 0.56 → 0.64 ms | 1.89 → 0.51 ms | 0.61 → 0.54 ms |
| 1000 | 14.34 → 9.93 ms | 8.23 → 5.26 ms | 7.80 → 4.94 ms | 1.74 → 1.76 ms | 1.98 → 1.18 ms | 0.91 → 0.67 ms | 0.84 → 0.60 ms |

That is 30-39% off a change at every size. The before column reproduces the table above (14.34
against 14.43 ms at 1000 characters), so the two are comparable. Positioning is still about half
of a long change.

The box fill is still linear in characters, since each lookup walks its line. Scanning only each
segment's first and last character would cut it further in word mode, but is not equivalent for
bidirectional text.

One caveat on these numbers: segment-layout measuring shows 0.00 ms because the demo cycles strings
built from a single word list, so the layout cache always hits. Text with genuinely new words pays
to measure each one the first time it appears.

## Baseline Profile

`:torph-compose` ships a Baseline Profile (`src/main/generated/baselineProfiles/baseline-prof.txt`,
in the AAR as `baseline-prof.txt`) covering `des.c5inco.torph.**`, so an app compiles the
text-change path ahead of time at install instead of interpreting it until the JIT catches up.
Compose's own libraries already ship profiles for their code; this one covers ours. It is
generated by `BaselineProfileGenerator` in `:benchmark`, which drives every demo screen against
the non-minified release build. Regenerate it after significant changes to the hot path, with one
device attached:

```bash
./gradlew :torph-compose:generateBaselineProfile
```

(On a phone with a work profile, the Gradle install fails as described under
[Running on a physical device](#running-on-a-physical-device); use a phone without one.) A stale
profile is harmless, it just stops covering the code that changed.

**It only matters cold.** With the profile against without it, both under `CompilationMode.Partial()`,
the warm `PerfScreenBenchmark` and `FeatureBenchmark` runs showed no difference beyond noise: they
restart the activity, not the process, so by the measured frames the JIT has compiled the hot code
either way. `ColdChangeBenchmark` kills the process every iteration and records the first changes
after launch: two at 200 characters, the switch to 1000, then two at 1000. Medians over 20 cold
launches per build per phone, from `TextMorphDiagnostics`, without the profile → with it:

| change after a cold start | Pixel 10 Pro | Pixel 7 | Pixel 4a |
| --- | ---: | ---: | ---: |
| 1st, 200 chars | 7.96 → 7.21 ms | 8.22 → 6.88 ms | 29.4 → 19.3 ms |
| 2nd, 200 chars | 5.32 → 4.79 ms | 5.33 → 5.29 ms | 22.1 → 18.5 ms |
| switch to 1000 chars | 11.5 → 10.7 ms | 14.9 → 13.0 ms | 16.3 → 14.5 ms |
| 1st, 1000 chars | 8.64 → 7.12 ms | 15.7 → 13.1 ms | noisy, see below |
| 2nd, 1000 chars | 9.61 → 8.78 ms | 9.79 → 9.29 ms | noisy, see below |

On the Pixel 10 Pro and Pixel 7, both runs with the profile beat both runs without it for the
first change, the switch and the first 1000-character change. On the Pixel 4a the first change
improved in every run, but its 1000-character changes land at either about 20 ms or about 50 ms
with or without the profile, so their medians flip between runs. That split is which CPU core the
change ran on, not uncompiled code (see [Which CPU core runs the change](#which-cpu-core-runs-the-change)). Frame-time percentiles did not change, since this run is dominated by
startup and navigation frames. The profile costs nothing at runtime.

## Emulator comparison (arm64, API 36)

The same suite on an emulator, kept for reference. It overstates cost badly on long text, so treat
emulator runs as a regression signal rather than a measurement:

| benchmark | frame CPU P50 | P90 | P99 | overrun P50 |
| --- | ---: | ---: | ---: | ---: |
| morph50 | 3.9 ms | 7.9 ms | 21.1 ms | -9.6 ms |
| morph200 | 5.2 ms | 19.2 ms | 45.0 ms | -10.4 ms |
| morph1000 | 18.9 ms | 21.6 ms | 31.5 ms | 2.8 ms |

## Running on a physical device

Three things bite on a personal phone:

- **Play Protect blocks the install.** Sideloading the ~46 MB test APK trips
  `INSTALL_FAILED_VERIFICATION_FAILURE` and waits on an on-device prompt. Approve it once, or turn
  off *Verify apps over ADB* in Developer options.
- **Anything else holding UiAutomation makes every test fail** with `UiAutomation not connected`.
  Layout Inspector, a CLI instrumentation server, or an accessibility automation tool will do it.
  Check with `adb shell dumpsys activity | grep -A2 "Active instrumentation"` and stop the owner.
- **A copy in a work profile blocks the install.** Gradle installs for every user, so a benchmark
  build left in another profile, signed with a different key, fails with
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, and `adb uninstall` cannot reach that profile. Build with
  `./gradlew :demo:assembleBenchmark :benchmark:assembleBenchmark`, install both APKs with
  `adb install --user 0 -r -t`, and run with `am instrument --user 0` as below.

To rerun without reinstalling (useful when installs are slow or gated), drive the instrumentation
directly. `additionalTestOutputDir` must be a directory the app itself can write, so an app-owned
media directory works and `/sdcard/Download` does not:

```bash
adb shell am instrument -w -r \
  -e additionalTestOutputDir /sdcard/Android/media/des.c5inco.torph.benchmark/bench-out \
  -e class des.c5inco.torph.benchmark.PerfScreenBenchmark \
  des.c5inco.torph.benchmark/androidx.test.runner.AndroidJUnitRunner
```
