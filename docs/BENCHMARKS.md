# Benchmarks

How Torph for Jetpack Compose performs, measured on three phones, and how to reproduce it.

> **These numbers are a snapshot, not a spec.** They were measured in October 2026. The Pixel 4 XL
> figures are 0.2.0 (AGP 9.4, Kotlin 2.4, Compose 1.12); the Pixel 10 Pro and Pixel 7 figures are
> 0.1.0 (AGP 8.13, Kotlin 2.2, Compose 1.10) and have not been rerun. On the Pixel 4 XL, text
> changes cost the same or less with 0.2.0 than with 0.1.0 in the same session, and frame times
> were within run-to-run drift (see [0.2.0 against 0.1.0](#020-against-010)). Frame times drift
> by several milliseconds between sessions on the same phone, mostly with the CPU core each change
> lands on (see [Which CPU core runs the change](#which-cpu-core-runs-the-change)). Rerun the suite
> before relying on a specific figure.

## How these were measured

| phone | Android | display | frame budget |
| --- | --- | --- | ---: |
| Pixel 10 Pro | 17 | 120 Hz | 8.3 ms |
| Pixel 7 | 17 | 90 Hz | 11.1 ms |
| Pixel 4 XL | 13 | 90 Hz | 11.1 ms |

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

- **Numbers, rapid interruption and multi-line reflow** stay on time at P99 on all three phones.
- **Text up to 200 characters** stays within about a frame at P99 on the Pixel 10 Pro and Pixel 7.
- **The expensive frame is the text-change frame**, one per change. At 1000 characters it runs
  late on every phone, and on the Pixel 4 XL, whose timer-driven changes run on its slow cores,
  long text misses two or three frames per change.
- **Segmentation mode barely changes what a text change costs**, because that cost is mostly per
  character, not per segment. The default (word by word for text with spaces) mainly saves drawing
  work in the frames that follow.

## 0.2.0 against 0.1.0

0.2.0 moved to AGP 9.4, Kotlin 2.4 and Compose 1.12, added `MorphClip` and regenerated the Baseline
Profile. On the Pixel 4 XL, 0.2.0, then 0.1.0 (5cafb8a), then 0.2.0 again ran back to back; the
0.2.0 figures here are from the second run, and the first agreed with them to within about a
millisecond except where noted. Median cost of one text change, and frame CPU at P99:

| benchmark | text change, 0.1.0 | text change, 0.2.0 | frame P99, 0.1.0 | frame P99, 0.2.0 |
| --- | ---: | ---: | ---: | ---: |
| `morph50` | 4.0 ms | 3.3 ms | 17.3 ms | 19.6 ms |
| `morph200` | 9.1 ms | 7.5 ms | 26.7 ms | 25.6 ms |
| `morph200Grapheme` | 10.6 ms | 9.1 ms | 30.1 ms | 29.0 ms |
| `morph1000` | 29.9 ms | 25.8 ms | 57.2 ms | 57.2 ms |
| `morph1000Edit` | 18.5 ms | 12.5 ms | 42.6 ms | 41.3 ms |
| `numberRolling` | 1.1 ms | 1.0 ms | 7.7 ms | 7.4 ms |
| 1st 1000-char change after a cold start | 32.9 ms | 31.0 ms | | |

Text changes cost the same or less with 0.2.0; frame P99 moves by about a millisecond either way,
within the drift between runs (the first 0.2.0 run had `morph50` at 16.8 ms). The one regression is
allocation in the draw path from Compose 1.12 (see [Allocation guard](#allocation-guard)).

## Text morphing

`AUTO`, the default, morphs text with spaces word by word, as torph does. The worst case is
`Segmentation.GRAPHEME`, one segment per character. Above 300 segments every mode morphs by word,
so the 1000-character rows are both default and worst case.

Cost of one text change (median):

| benchmark | segments | Pixel 10 Pro | Pixel 7 | Pixel 4 XL |
| --- | ---: | ---: | ---: | ---: |
| `morph50`: 50 chars, default | 19 | 2.0 ms | 2.9 ms | 3.3 ms |
| `morph50Grapheme`: 50 chars, worst case | 50 | 2.4 ms | 2.4 ms | 3.4 ms |
| `morph200`: 200 chars, default | 67 | 3.5 ms | 5.0 ms | 7.5 ms |
| `morph200Grapheme`: 200 chars, worst case | 200 | 3.6 ms | 5.2 ms | 9.1 ms |
| `morph1000`: 1000 chars | 340 | 8.9 ms | 12.3 ms | 25.8 ms |
| `morph1000Edit`: 1000 chars, one word edited | 332 | 6.8 ms | 8.7 ms | 12.5 ms |

Frame CPU P50 / P99, and overrun at P99:

| benchmark | Pixel 10 Pro | Pixel 7 | Pixel 4 XL |
| --- | ---: | ---: | ---: |
| `morph50` | 4.3 / 13.1 ms, +0.0 | 4.9 / 15.1 ms, +3.6 | 7.5 / 19.6 ms, +3.6 |
| `morph50Grapheme` | 4.7 / 12.5 ms, -0.9 | 5.2 / 15.1 ms, +5.5 | 8.2 / 19.7 ms, +4.4 |
| `morph200` | 5.5 / 12.9 ms, -1.2 | 6.2 / 17.1 ms, +6.6 | 16.5 / 25.6 ms, +12.9 |
| `morph200Grapheme` | 6.5 / 14.7 ms, +1.5 | 6.7 / 21.3 ms, +8.7 | 12.3 / 29.0 ms, +16.7 |
| `morph1000` | 8.7 / 23.4 ms, +12.6 | 10.2 / 28.7 ms, +18.8 | 15.1 / 57.2 ms, +44.8 |
| `morph1000Edit` | 6.0 / 16.0 ms, +3.4 | 7.3 / 25.4 ms, +14.7 | 15.9 / 41.3 ms, +27.1 |

A run has about three text changes in a few hundred frames, so P99 is essentially the
text-change frame. The Pixel 4 XL's P50 sits above its 11.1 ms budget from 200 characters up, but
that does not mean a lower frame rate: its main thread and RenderThread work in parallel, so while
the text animates frames still come out one vsync apart (143 of 147 frame intervals in a `morph200`
run, 121 of 129 in `morph1000`), each presented a vsync late. Android's frame timeline in the
traces classes almost all of them as *Buffer Stuffing*. Only 3-6 frames per 4.8 s run miss their
deadline outright, mostly text-change frames. On the Pixel 4 XL the default's `morph200` P50 is
higher than the worst case's (16.5 against 12.3 ms) in every run, with 0.1.0 as well; the
per-frame main-thread and RenderThread time varies from one text cycle to the next, and these runs
do not explain the order. The change cost and P99 order as expected.

### Where a text change spends its time

Medians per phase (columns are independent medians, so they do not sum to the total):

| | total | measure text | segment | diff | place | of which boxes | animation setup |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Pixel 10 Pro, 200 chars | 3.5 ms | 0.9 ms | 0.2 ms | 0.3 ms | 1.3 ms | 1.2 ms | 0.3 ms |
| Pixel 10 Pro, 1000 chars | 8.9 ms | 1.9 ms | 0.4 ms | 0.6 ms | 5.2 ms | 4.9 ms | 0.6 ms |
| Pixel 7, 200 chars | 5.0 ms | 1.6 ms | 0.2 ms | 0.2 ms | 1.6 ms | 1.5 ms | 0.3 ms |
| Pixel 7, 1000 chars | 12.3 ms | 2.0 ms | 0.4 ms | 0.5 ms | 7.5 ms | 7.1 ms | 0.5 ms |
| Pixel 4 XL, 200 chars | 7.5 ms | 1.2 ms | 0.3 ms | 0.4 ms | 4.6 ms | 4.3 ms | 0.6 ms |
| Pixel 4 XL, 1000 chars | 25.8 ms | 2.8 ms | 1.1 ms | 1.1 ms | 18.3 ms | 17.2 ms | 1.9 ms |

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

| benchmark | what it drives | Pixel 10 Pro | Pixel 7 | Pixel 4 XL |
| --- | --- | ---: | ---: | ---: |
| `numberRolling` | random jumps, most digits roll | 4.3 / 10.8 ms, -1.6 | 4.4 / 10.6 ms, -0.2 | 5.3 / 7.4 ms, -9.1 |
| `numberIncrement` | +1 taps, one or two digits roll | 3.8 / 6.9 ms, -3.2 | 3.8 / 10.1 ms, -0.1 | 4.2 / 7.2 ms, -11.4 |
| `interruption` | 80 ms timer plus live typing | 4.8 / 9.5 ms, -3.0 | 4.8 / 12.9 ms, -3.1 | 6.9 / 11.3 ms, -5.8 |
| `multiLine` | paragraph swap, lines reflow | 4.0 / 9.8 ms, -4.7 | 5.0 / 11.4 ms, -1.2 | 4.3 / 6.1 ms, -11.5 |

A number change costs about 0.7-1.6 ms. Every one of these holds its deadline at P99.

## First changes after a cold start

`ColdChangeBenchmark` kills the app before each of 10 launches, opens the Perf screen and records
its first text changes: two at 200 characters (timer-driven), the switch to 1000 characters (a tap),
then two more at 1000 (timer-driven). Medians:

| change after launch | Pixel 10 Pro | Pixel 7 | Pixel 4 XL |
| --- | ---: | ---: | ---: |
| 1st, 200 chars | 7.8 ms | 5.8 ms | 15.2 ms |
| 2nd, 200 chars | 4.2 ms | 5.8 ms | 10.2 ms |
| switch to 1000 chars (tap) | 7.0 ms | 10.3 ms | 8.8 ms |
| 1st, 1000 chars | 6.9 ms | 11.7 ms | 31.0 ms |
| 2nd, 1000 chars | 7.5 ms | 8.5 ms | 30.0 ms |

`:torph-compose` ships a Baseline Profile (`src/main/generated/baselineProfiles/baseline-prof.txt`,
in the AAR as `baseline-prof.txt`) covering `des.c5inco.torph.**`, so apps compile the text-change
path at install rather than interpreting it until the JIT catches up. It matters for these first
changes; once the JIT has compiled the hot code, as in the warm benchmarks above, it makes no
difference. On the Pixel 4 XL, compare the tap-triggered switch (8.8 ms) with the timer-driven
change right after it (31.0 ms): that gap is core placement, described next.

## Which CPU core runs the change

On phones with more than one kind of CPU core, the biggest single factor in what a text change
costs is not this library's code but which core the scheduler runs the main thread on. From the
scheduling data in the `morph1000` traces (where each text-change layout pass ran):

| phone | cores | where the text changes ran | cost by core |
| --- | --- | --- | --- |
| Pixel 7 | 2 fastest, 2 middle, 4 slow | most on the middle cores, a third on the fastest | about 13 ms middle, 7.6 ms fastest |
| Pixel 10 Pro | four groups by top frequency | 24 of 30 on its fastest group, the rest one step down | 8-10 ms either way |
| Pixel 4 XL | 1 fastest, 3 middle, 4 slow | all 18 on the slow cores; the tapped switch in `ColdChangeBenchmark` on a middle core, 10 of 10 | about 30 ms slow, 9.5-12 ms middle |

Almost every pass runs entirely on one kind of core, and the main thread spends about a millisecond
or less waiting for one, so this is placement, not contention. On the Pixel 4 XL every timer-driven
change ran on the slow cores, so its numbers are slow but steady between runs; on phones where
placement is mixed, it is what makes them swing.

**What triggers the change decides the core.** Android boosts the CPU briefly on touch input, so a
change made in response to a tap usually runs on a fast core. A change driven by a timer or
incoming data arrives after the app has been idle, starts on a slow core, and is not moved within
the frame. Counters, stopwatches, live values and streaming text are all timer- or data-driven, so
the benchmarks here measure their case. Moving the work to a background thread would not change
this, since background threads are placed the same way. Android's Performance Hint API (ADPF) lets
an app tell the system a CPU burst is coming; the Pixel 7 and Pixel 10 Pro support it, but the
Pixel 4 XL does not (its power HAL reports no hint support), so it cannot help the phone that needs
it most. Doing less work helps on every core, so it remains the main lever.

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
with about 200 live segments and fails above 16 KB per frame. It guards against per-segment lambdas,
string building or other allocation creeping into the draw path. Current figures (three runs each,
two for the Pixel 4 XL on 0.1.0):

| Pixel 10 Pro (0.1.0) | Pixel 7 (0.1.0) | Pixel 4 XL (0.1.0) | Pixel 4 XL (0.2.0) |
| ---: | ---: | ---: | ---: |
| 4.4-4.9 KB | 3.3-3.8 KB | 3.0 KB | 11.2 KB |

With Compose 1.10, as in 0.1.0, most of that is Compose's and the test clock's own per-frame work,
and the draw path itself allocates next to nothing. Compose 1.12, which 0.2.0 builds against,
allocates a small lambda inside every `drawText` of a `TextLayoutResult` (`AndroidParagraph.paint`),
about 40 bytes, so with one call per live segment the test's 205 segments add about 8 KB per frame.
The draw code is unchanged: the same build allocates 3.0 KB per frame against Compose 1.10, and so
does Compose 1.13.0-alpha03, where the lambda is gone. An app on the Compose 1.13 alphas does not
pay it.

The ceiling was 12 KB and is 16 KB while torph builds against Compose 1.12, so that this does not
fail the guard on phones whose baseline is higher. **To do:** lower it back to 12 KB once torph
builds against Compose 1.13 or later (the `TODO` in `DrawAllocationTest`).

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

A stale profile is harmless; it just stops covering the code that changed. A toolchain upgrade can
make it stale without any code changing: AGP 9 names `internal` members after the module
(`getLive$torph_compose`) instead of the variant (`getLive$torph_compose_release`), so regenerate
after upgrading AGP. Generate with animations on (see below), or the profile records snaps instead
of the animation path.

## Running on a physical device

A few things bite on a personal phone:

- **Animations must be on.** With the animator duration scale at 0, `TextMorph` respects reduced
  motion and snaps every change, so the benchmarks and the Baseline Profile measure snapping. A
  phone can be in that state with `animator_duration_scale` unset (a Pixel 4 XL on Android 13 was);
  run `adb shell settings put global animator_duration_scale 1` first, and
  `adb shell settings delete global animator_duration_scale` afterwards to put it back.
- **Play Protect blocks the install.** Sideloading the ~46 MB test APK trips
  `INSTALL_FAILED_VERIFICATION_FAILURE` and waits on an on-device prompt. Approve it once, or turn
  off *Verify apps over ADB* in Developer options. On Android 13 and earlier Macrobenchmark also
  reinstalls the demo before every benchmark to reset its compilation, and a few seconds later Play
  Protect may ask to *Send app for a security check?*. Until someone answers, the next iteration
  cannot start; answer *Don't send*.
- **A leftover trace processor stalls the run.** Macrobenchmark analyses each trace with
  `/data/local/tmp/trace_processor_shell` serving on port 9001. One left running by an interrupted
  run keeps the port, and later runs stall for minutes at a time between iterations. Check with
  `adb shell ps -A | grep trace_processor` before starting and stop any that predate the run.
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
