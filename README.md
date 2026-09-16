# Torph for Jetpack Compose

An unofficial port of [torph](https://github.com/lochie/torph) to Jetpack Compose: text that
morphs between values. Characters shared between the old and new string glide to their new
position, new ones fade in, removed ones fade out, and digits roll vertically by place value.

> **This is a personal project.** It is not a Google product, and it is not affiliated with,
> endorsed by, or supported by Google or the Jetpack Compose team. It is also not affiliated with
> or endorsed by the [torph](https://github.com/lochie/torph) project. See
> [License and attribution](#license-and-attribution).

```kotlin
TextMorph(text = "$1,234.50")          // change the string, get a morph
TextMorph(value = 1234.5, decimals = 2) // or hand it a number
```

## Modules

| Module | What | Depends on |
| --- | --- | --- |
| `:torph-core` | Pure Kotlin/JVM: segmentation, place-value number matching, diff, spring math. Unit-tested. | nothing |
| `:torph-compose` | Android library: `TextMorph`, `rememberTextMorphState`, `Modifier.textMorph`, easing helpers. | core, Compose foundation/animation/ui-text |
| `:demo` | Material 3 app mirroring torph.lochie.me: Playground, Numbers, Typing, Multi-line, Scripts, Interruption, Debug, Perf. | compose, Material 3 |

## API

```kotlin
@Composable
fun TextMorph(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified,
    ease: MorphEase = MorphEase.Curve(MorphEasings.Default),   // cubic-bezier(0.19, 1, 0.22, 1)
    duration: Duration = 400.milliseconds,                     // ignored for MorphEase.Spring
    scale: Boolean = true,            // scale entering/exiting segments 0.5 <-> 1
    numbers: Boolean = true,          // place-value matching + vertical roll
    locale: Locale = Locale.current,  // grapheme/word boundaries and number symbols
    cursorIndex: Int? = null,         // caret matching for editable fields
    segmentation: Segmentation = Segmentation.AUTO,  // GRAPHEME | WORD | AUTO
    sizeMode: MorphSizeMode = MorphSizeMode.Animate, // or Snap for fixed slots
    disabled: Boolean = false,
    respectReducedMotion: Boolean = true,
    debug: Boolean = false,           // draw segment rects, ids, enter/exit colours
    onAnimationStart: (() -> Unit)? = null,
    onAnimationComplete: (() -> Unit)? = null,
    onAnimationCancel: (() -> Unit)? = null,
    state: TextMorphState = rememberTextMorphState(),
)

sealed interface MorphEase {
    data class Curve(val easing: Easing) : MorphEase
    data class Spring(stiffness = 100f, damping = 10f, mass = 1f, precision = 0.001f) : MorphEase
}

cssCubicBezier("cubic-bezier(0.19, 1, 0.22, 1)") // port torph configs verbatim
```

Lower level: `rememberTextMorphState()` + `Modifier.textMorph(state)` for custom containers, and
`segmentText` / `diffSegments` / `findNumericWords` / `settleTime` from `des.c5inco.torph.core`.

## How it works

Per text change (never per frame):

1. The full target string is measured once with `TextMeasurer` under the incoming constraints, so
   line breaks and kerning match a real `Text`.
2. `diffSegments` pairs numeric words by order and matches digits by place value; everything else
   goes through a left-biased LCS on grapheme (or word) text.
3. Each segment gets a target rect from the layout's bounding boxes and a cached single-segment
   `TextLayoutResult`.
4. Animatables are retargeted: persist → `offset.animateTo`, enter → alpha/scale in (a digit that changed at the same place
   becomes an odometer strip from old to new digit), exit → alpha/scale out then removed from the list by the animation coroutine.
5. The container size animates (or snaps) to the new layout size.

Per frame: one `drawText(cachedLayout, topLeft, alpha)` per live segment inside a `Canvas`-style
`drawBehind`. Animatables are read only in the layout and draw phases, never in composition, and
the traces confirm composition does not run per frame. Note that a *layout* pass does run per frame
while the container size animates; use `sizeMode = Snap` to skip it.

Complex scripts: `Segmentation.AUTO` detects Arabic, Hebrew, Indic, Thai, Lao, Khmer, Myanmar,
Tibetan and Mongolian words via `Character.UnicodeScript` and morphs them as whole words so
contextual shaping survives. Above 300 segments any mode falls back to `WORD`.

## Building

```bash
./gradlew :torph-core:test :demo:installDebug
```

Instrumentation tests for the Compose layer: `./gradlew :torph-compose:connectedDebugAndroidTest`
with an emulator running. That suite includes `DrawAllocationTest`, which measures process-wide ART
allocation per animated frame with ~200 live segments and fails above 16 KB/frame (a regression
guard for per-segment lambdas or string building in the draw path; last measured 3.3 KB/frame,
down from 80 KB before per-segment `Animatable`s were replaced by plain float channels).

## Benchmarks

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

### Pixel 10 Pro (Android 17), 3 iterations each

| benchmark | live segments | frame CPU P50 | P90 | P99 | overrun P50 | overrun P99 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| morph50 | ~50 | 4.6 ms | 6.3 ms | 11.8 ms | -10.0 ms | -2.3 ms |
| morph200 | ~200 | 5.6 ms | 7.6 ms | 16.1 ms | -9.3 ms | 1.0 ms |
| morph1000 (auto → word fallback) | ~360 | 6.2 ms | 8.1 ms | 22.8 ms | -7.8 ms | 8.8 ms |
| morph1000Word | ~360 | 6.4 ms | 8.7 ms | 23.1 ms | -7.6 ms | 9.6 ms |

Cold startup time to initial display: 263 ms median.

Overrun is how late a frame finished against its deadline, so negative means it finished early.
Every size holds frame rate at P50 and P90, including 1000 characters. Sustained performance mode
was off for these runs, so expect some thermal variance.

### Where the time actually goes (from the Perfetto traces)

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

### Feature benchmarks

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

### Where a text change spends its time

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

The obvious fix is to stop scanning every character. A segment that does not straddle a line break
only needs its first and last character, which in word mode cuts 1000 calls to about 680. Getting
further means a cheaper primitive than `getBoundingBox`, which allocates a `Rect` per call;
`getHorizontalPosition` returns a float and may be cheaper, but that is untested.

One caveat on these numbers: segment-layout measuring shows 0.00 ms because the demo cycles strings
built from a single word list, so the layout cache always hits. Text with genuinely new words pays
to measure each one the first time it appears.

### Emulator comparison (arm64, API 36)

The same suite on an emulator, kept for reference. It overstates cost badly on long text, so treat
emulator runs as a regression signal rather than a measurement:

| benchmark | frame CPU P50 | P90 | P99 | overrun P50 |
| --- | ---: | ---: | ---: | ---: |
| morph50 | 3.9 ms | 7.9 ms | 21.1 ms | -9.6 ms |
| morph200 | 5.2 ms | 19.2 ms | 45.0 ms | -10.4 ms |
| morph1000 | 18.9 ms | 21.6 ms | 31.5 ms | 2.8 ms |
| morph1000Word | 18.9 ms | 20.7 ms | 24.6 ms | 2.8 ms |

### Running on a physical device

Two things bite on a personal phone:

- **Play Protect blocks the install.** Sideloading the ~46 MB test APK trips
  `INSTALL_FAILED_VERIFICATION_FAILURE` and waits on an on-device prompt. Approve it once, or turn
  off *Verify apps over ADB* in Developer options.
- **Anything else holding UiAutomation makes every test fail** with `UiAutomation not connected`.
  Layout Inspector, a CLI instrumentation server, or an accessibility automation tool will do it.
  Check with `adb shell dumpsys activity | grep -A2 "Active instrumentation"` and stop the owner.

To rerun without reinstalling (useful when installs are slow or gated), drive the instrumentation
directly. `additionalTestOutputDir` must be a directory the app itself can write, so an app-owned
media directory works and `/sdcard/Download` does not:

```bash
adb shell am instrument -w -r \
  -e additionalTestOutputDir /sdcard/Android/media/des.c5inco.torph.benchmark/bench-out \
  -e class des.c5inco.torph.benchmark.PerfScreenBenchmark \
  des.c5inco.torph.benchmark/androidx.test.runner.AndroidJUnitRunner
```

## Status vs. plan

- M1 core, M2 render, M3 parity, M4 scripts/perf: implemented.
- M5: `maven-publish` is configured for both libraries (`publishToMavenLocal`); Maven Central
  signing/credentials and Roborazzi screenshot tests are not set up yet. Macrobenchmark and the
  draw-allocation guard are in place (see Benchmarks).
- Number rolling is an odometer strip: a changed digit scrolls through every intermediate digit
  (wrapping 9 → 0) inside its clipped cell, up when the number grows and down when it shrinks. A
  digit interrupted mid-roll continues from where its strip is, with velocity preserved.
- `AnnotatedString`, selection and glyph-level (`drawGlyphs`) animation are out of scope, as planned.

## License and attribution

Torph for Jetpack Compose is MIT licensed. See [LICENSE](LICENSE).

This is an **independent, unofficial port** of [torph](https://github.com/lochie/torph) by
[Lochie Axon](https://github.com/lochie), used under the MIT License. The Kotlin here was written
from scratch, but the API surface, option names, algorithms, and default values follow torph's so
that configurations carry over directly. Upstream's full copyright notice is in [NOTICE](NOTICE).

If you want the original, go use it: <https://torph.lochie.me>.

### Not affiliated with Google

This is a personal project by [Chris Sinco](https://github.com/c5inco). It is not a Google
product and is not endorsed or supported by Google. "Android" and "Jetpack Compose" are trademarks
of Google LLC, used here only to describe what this library targets.
