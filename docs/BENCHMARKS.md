# Benchmarks

How Torph for Jetpack Compose performs, measured on three phones, and how to reproduce it.

> **These numbers are a snapshot, not a spec.** They were measured in October 2026 with the code
> at that time. Frame times drift by several milliseconds between sessions on the same phone, and
> the Pixel 4a varies most (see [Which CPU core runs the change](#which-cpu-core-runs-the-change)).
> Rerun the suite before relying on a specific figure.

## How these were measured

| phone | Android | display | frame budget |
| --- | --- | --- | ---: |
| Pixel 10 Pro | 17 | 120 Hz | 8.3 ms |
| Pixel 7 | 17 | 90 Hz | 11.1 ms |
| Pixel 4a | 13 | 60 Hz | 16.7 ms |

- Macrobenchmark against the demo app's `benchmark` build (R8-minified, with the library's
  Baseline Profile), `CompilationMode.Partial()`, three warm iterations per benchmark.
- **Frame CPU** is main-thread plus RenderThread time per frame (`frameDurationCpuMs`). **Overrun**
  is how late a frame finished against its deadline (`frameOverrunMs`); negative means early. Read
  overrun to judge smoothness: the budget differs per display.
- **Text change** is the cost of the work a text change triggers (measuring, segmenting, diffing,
  placing and starting the animation), from `TextMorphDiagnostics.logTimings`; median over 23-64
  changes.
- The Perf screen cycles between three different lorem-ipsum strings every 1.5 s, so nearly every
  segment enters or exits on each change. Every benchmark changes text on a timer, which is the
  worst case for CPU core placement (see below); a change made in response to a tap is cheaper.

## At a glance

- **Numbers, rapid interruption and multi-line reflow** stay on time at P99 on all three phones
  (to within 0.2 ms on the Pixel 4a).
- **Text up to 200 characters** stays within about a frame at P99 on the Pixel 10 Pro and Pixel 7.
- **The expensive frame is the text-change frame**, one per change. At 1000 characters it runs
  late on every phone, and on the Pixel 4a long text misses two or three frames per change.
- **Segmentation mode barely changes what a text change costs**, because that cost is mostly per
  character, not per segment. The default (word by word for text with spaces) mainly saves drawing
  work in the frames that follow.

## Text morphing

`AUTO`, the default, morphs text with spaces word by word, as torph does. The worst case is
`Segmentation.GRAPHEME`, one segment per character. Above 300 segments every mode morphs by word,
so the 1000-character rows are both default and worst case.

Cost of one text change (median):

| benchmark | segments | Pixel 10 Pro | Pixel 7 | Pixel 4a |
| --- | ---: | ---: | ---: | ---: |
| `morph50`: 50 chars, default | 19 | 2.0 ms | 2.9 ms | 3.4 ms |
| `morph50Grapheme`: 50 chars, worst case | 50 | 2.4 ms | 2.4 ms | 3.3 ms |
| `morph200`: 200 chars, default | 67 | 3.5 ms | 5.0 ms | 12.3 ms |
| `morph200Grapheme`: 200 chars, worst case | 200 | 3.6 ms | 5.2 ms | 15.5 ms |
| `morph1000`: 1000 chars | 340 | 8.9 ms | 12.3 ms | 14.3 ms |
| `morph1000Edit`: 1000 chars, one word edited | 332 | 6.8 ms | 8.7 ms | 12.6 ms |

Frame CPU P50 / P99, and overrun at P99:

| benchmark | Pixel 10 Pro | Pixel 7 | Pixel 4a |
| --- | ---: | ---: | ---: |
| `morph50` | 4.3 / 13.1 ms, +0.0 | 4.9 / 15.1 ms, +3.6 | 14.9 / 32.2 ms, +15.4 |
| `morph50Grapheme` | 4.7 / 12.5 ms, -0.9 | 5.2 / 15.1 ms, +5.5 | 15.1 / 33.4 ms, +17.8 |
| `morph200` | 5.5 / 12.9 ms, -1.2 | 6.2 / 17.1 ms, +6.6 | 28.1 / 34.4 ms, +23.9 |
| `morph200Grapheme` | 6.5 / 14.7 ms, +1.5 | 6.7 / 21.3 ms, +8.7 | 16.2 / 42.0 ms, +33.1 |
| `morph1000` | 8.7 / 23.4 ms, +12.6 | 10.2 / 28.7 ms, +18.8 | 22.1 / 77.3 ms, +68.4 |
| `morph1000Edit` | 6.0 / 16.0 ms, +3.4 | 7.3 / 25.4 ms, +14.7 | 14.7 / 56.1 ms, +46.3 |

A run has about three text changes in roughly 300 frames, so P99 is essentially the text-change
frame. The Pixel 4a's P50 sits above its 16.7 ms budget for long text but that does not mean
30 fps: its main thread and RenderThread work in parallel, so frames still come out at close to the
display rate, each presented a vsync late. Android's frame timeline in the traces classes almost
all of them as *Buffer Stuffing*. Only 4-8 frames per 4.8 s run miss their deadline outright,
mostly text-change frames. The Pixel 4a's `morph200` and `morph200Grapheme` P50s are a reversed
pair (28.1 against 16.2 ms) that comes from core placement in single runs, not from segmentation.

### Where a text change spends its time

Medians per phase (columns are independent medians, so they do not sum to the total):

| | total | measure text | segment | diff | place | of which boxes | animation setup |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Pixel 10 Pro, 200 chars | 3.5 ms | 0.9 ms | 0.2 ms | 0.3 ms | 1.3 ms | 1.2 ms | 0.3 ms |
| Pixel 10 Pro, 1000 chars | 8.9 ms | 1.9 ms | 0.4 ms | 0.6 ms | 5.2 ms | 4.9 ms | 0.6 ms |
| Pixel 7, 200 chars | 5.0 ms | 1.6 ms | 0.2 ms | 0.2 ms | 1.6 ms | 1.5 ms | 0.3 ms |
| Pixel 7, 1000 chars | 12.3 ms | 2.0 ms | 0.4 ms | 0.5 ms | 7.5 ms | 7.1 ms | 0.5 ms |
| Pixel 4a, 200 chars | 12.3 ms | 1.4 ms | 0.8 ms | 0.8 ms | 6.8 ms | 6.4 ms | 1.4 ms |
| Pixel 4a, 1000 chars | 14.3 ms | 2.0 ms | 1.0 ms | 0.9 ms | 7.5 ms | 7.0 ms | 1.4 ms |

Placement dominates, and inside it the per-character box fill: every character's bounding box is
read from the layout to find each segment's edges. That is why segmenting by word saves little on
the change itself. After an edit, boxes are kept for the leading lines that are provably unchanged
(same start, end, extent, top, bottom and paragraph direction as before the first changed
character), which is the gap between `morph1000` and `morph1000Edit`: the edit benchmark changes
one word 40-80% of the way through, so about a third of the characters are reused. Text that only
grows at the end, like a streaming reply, reuses all but the last line; single-line text reuses
nothing, but its box fill is already well under a millisecond.

Segment layouts are cached by text, so the demo's repeated word list never measures a segment twice.
Text that keeps introducing new words pays to measure each one the first time.

## Numbers, interruption and reflow

Frame CPU P50 / P99, and overrun at P99:

| benchmark | what it drives | Pixel 10 Pro | Pixel 7 | Pixel 4a |
| --- | --- | ---: | ---: | ---: |
| `numberRolling` | random jumps, most digits roll | 4.3 / 10.8 ms, -1.6 | 4.4 / 10.6 ms, -0.2 | 6.2 / 13.4 ms, -0.2 |
| `numberIncrement` | +1 taps, one or two digits roll | 3.8 / 6.9 ms, -3.2 | 3.8 / 10.1 ms, -0.1 | 4.8 / 11.9 ms, -1.9 |
| `interruption` | 80 ms timer plus live typing | 4.8 / 9.5 ms, -3.0 | 4.8 / 12.9 ms, -3.1 | 9.0 / 15.3 ms, +0.2 |
| `multiLine` | paragraph swap, lines reflow | 4.0 / 9.8 ms, -4.7 | 5.0 / 11.4 ms, -1.2 | 5.1 / 9.9 ms, -4.6 |

A number change costs about 0.7-1.6 ms. Every one of these holds its deadline at P99, to within
0.2 ms on the Pixel 4a.

## First changes after a cold start

`ColdChangeBenchmark` kills the app before each of 10 launches, opens the Perf screen and records
its first text changes: two at 200 characters (timer-driven), the switch to 1000 characters (a tap),
then two more at 1000 (timer-driven). Medians:

| change after launch | Pixel 10 Pro | Pixel 7 | Pixel 4a |
| --- | ---: | ---: | ---: |
| 1st, 200 chars | 7.8 ms | 5.8 ms | 25.7 ms |
| 2nd, 200 chars | 4.2 ms | 5.8 ms | 17.3 ms |
| switch to 1000 chars (tap) | 7.0 ms | 10.3 ms | 10.7 ms |
| 1st, 1000 chars | 6.9 ms | 11.7 ms | 46.7 ms |
| 2nd, 1000 chars | 7.5 ms | 8.5 ms | 18.5 ms |

`:torph-compose` ships a Baseline Profile (`src/main/generated/baselineProfiles/baseline-prof.txt`,
in the AAR as `baseline-prof.txt`) covering `des.c5inco.torph.**`, so apps compile the text-change
path at install rather than interpreting it until the JIT catches up. It matters for these first
changes; once the JIT has compiled the hot code, as in the warm benchmarks above, it makes no
difference. On the Pixel 4a, compare the tap-triggered switch (10.7 ms) with the timer-driven
change right after it (46.7 ms): that gap is core placement, described next.

## Which CPU core runs the change

On phones with more than one kind of CPU core, the biggest single factor in what a text change
costs is not this library's code but which core the scheduler runs the main thread on. From the
scheduling data in the `morph1000` traces (where each text-change layout pass ran):

| phone | cores | where the text changes ran | cost by core |
| --- | --- | --- | --- |
| Pixel 4a | 2 fast, 6 slow | 7 of 12 on slow cores | about 47 ms slow, 9-18 ms fast |
| Pixel 7 | 2 fastest, 2 middle, 4 slow | most on the middle cores, a third on the fastest | about 13 ms middle, 7.6 ms fastest |
| Pixel 10 Pro | four groups by top frequency | 24 of 30 on its fastest group, the rest one step down | 8-10 ms either way |

Almost every pass runs entirely on one kind of core, and the main thread spends about a millisecond
or less waiting for one, so this is placement, not contention. It is why the Pixel 4a's numbers swing between
runs.

**What triggers the change decides the core.** Android boosts the CPU briefly on touch input, so a
change made in response to a tap usually runs on a fast core. A change driven by a timer or
incoming data arrives after the app has been idle, starts on a slow core, and is not moved within
the frame. Counters, stopwatches, live values and streaming text are all timer- or data-driven, so
the benchmarks here measure their case. Moving the work to a background thread would not change
this, since background threads are placed the same way. Android's Performance Hint API (ADPF) lets
an app tell the system a CPU burst is coming; the Pixel 7 and Pixel 10 Pro support it, but the
Pixel 4a does not (it refuses to create a hint session), so it cannot help the phone that needs it
most. Doing less work helps on every core, so it remains the main lever.

## Drawing

Composition does not run per frame: animation state lives in plain float channels advanced by one
frame loop and read only in layout and draw. In a Pixel 7 `morph200` trace, 13 of 118 rendered
frames recomposed (the text changes and the demo's frame-time readout); the rest only ran Compose's
check for pending work, about 0.25 ms. Each frame draws one cached single-segment layout per
live segment. The drawing has its own graphics layer, so a sibling redrawing (a clock, a progress
bar, a ripple) does not re-record the text; `RedrawIsolationTest` checks this. A layout pass does
run every frame while the container size animates; `sizeMode = Snap` skips it for fixed-size slots.

## Allocation guard

`DrawAllocationTest` in `:torph-compose` measures process-wide ART allocation per animated frame
with about 200 live segments and fails above 12 KB per frame. It guards against per-segment lambdas,
string building or other allocation creeping into the draw path. Current figures, three runs each:

| Pixel 10 Pro | Pixel 7 | Pixel 4a |
| ---: | ---: | ---: |
| 4.4-4.9 KB | 3.3-3.8 KB | 3.0-3.3 KB (has reached 9.3 KB) |

Most of that is Compose's and the test clock's own per-frame work; the draw path itself allocates
next to nothing.

## Running the suite

`:benchmark` is a Macrobenchmark module targeting the demo app:

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest
```

| class | benchmarks |
| --- | --- |
| `PerfScreenBenchmark` | `morph50`, `morph200`, `morph1000`, `morph1000Edit` (default segmentation), `morph50Grapheme`, `morph200Grapheme` (worst case), `startup` |
| `FeatureBenchmark` | `numberRolling`, `numberIncrement`, `interruption`, `multiLine` |
| `ColdChangeBenchmark` | `firstChangesAfterColdStart` |

The benchmark variant installs as `des.c5inco.torph.demo.benchmark`, so it coexists with the debug
demo. (This needs androidx.benchmark 1.4.1+: on API 36, 1.3.x reads the 15-character kernel process
name from `pgrep -l` and cannot match a package id longer than that.) Results (JSON and Perfetto
traces) land in `benchmark/build/outputs/connected_android_test_additional_output/`; frame timing is
under `sampledMetrics`, not `metrics`. The traces open in [ui.perfetto.dev](https://ui.perfetto.dev)
or Android Studio.

For the per-phase breakdown, the demo's Perf and Numbers screens turn on
`TextMorphDiagnostics.logTimings`, which logs one line per text change:

```bash
adb logcat -s TextMorphPerf
```

To regenerate the Baseline Profile after significant changes to the hot path, with one phone
attached (one without a work profile, see below):

```bash
./gradlew :torph-compose:generateBaselineProfile
```

A stale profile is harmless; it just stops covering the code that changed.

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

The screen must stay on and unlocked for the whole run; a phone that locks mid-run fails the
remaining benchmarks.

To rerun without reinstalling (useful when installs are slow or gated), drive the instrumentation
directly. `additionalTestOutputDir` must be a directory the app itself can write, so an app-owned
media directory works and `/sdcard/Download` does not:

```bash
adb shell am instrument -w -r \
  -e additionalTestOutputDir /sdcard/Android/media/des.c5inco.torph.benchmark/bench-out \
  -e class des.c5inco.torph.benchmark.PerfScreenBenchmark \
  des.c5inco.torph.benchmark/androidx.test.runner.AndroidJUnitRunner
```
