# Torph for Jetpack Compose

A port of [lochie/torph](https://github.com/lochie/torph) to Jetpack Compose: text that morphs
between values. Characters shared between the old and new string glide to their new position,
new ones fade in, removed ones fade out, and digits roll vertically by place value.

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
`segmentText` / `diffSegments` / `findNumericWords` / `settleTime` from `io.torph.core`.

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
`drawBehind`. Animatables are read only in the layout and draw phases, never in composition.

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

The benchmark variant installs as `io.torph.demo.benchmark`, so it coexists with the debug demo.
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

What the numbers say: per-frame draw cost is close to flat from 50 to 1000 segments (4.6 → 6.2 ms
P50), so drawing is no longer what dominates. The P99 spikes are the single frame per text change
that measures the new string and runs the diff; the JVM timing test puts segment+diff of 1000 chars
at ~0.3 ms in word mode and ~4 ms forced to graphemes, so most of that frame is text measurement,
not diffing.

If the P99 on long text ever needs to come down, the lever is caching settled segments in a
`GraphicsLayer` so per-frame work scales with *animating* segments only. On this hardware it is not
worth doing yet.

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
  -e additionalTestOutputDir /sdcard/Android/media/io.torph.benchmark/bench-out \
  -e class io.torph.benchmark.PerfScreenBenchmark \
  io.torph.benchmark/androidx.test.runner.AndroidJUnitRunner
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
