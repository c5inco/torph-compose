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

| Numbers roll by place value | Shared letters glide into place |
| :---: | :---: |
| <img src="docs/media/numbers.gif" alt="A dollar amount changing, with each digit rolling vertically to its new value" width="360"> | <img src="docs/media/words.gif" alt="Hello world morphing into Hello there, Goodbye world and Hello, world 42" width="360"> |
| **Updates faster than the roll keep rolling** | **Rapid updates never snap** |
| <img src="docs/media/timer.gif" alt="A stopwatch ticking every 80 ms, its last digits rolling continuously like an odometer" width="360"> | <img src="docs/media/typing.gif" alt="A sentence being typed one character at a time, wrapping across lines" width="360"> |

Recorded from the `:demo` app on a Pixel 10 Pro.

## Use it in your project

There is no Maven Central release yet, so you build the library from this repo and resolve it from
your local Maven repository:

```bash
git clone https://github.com/c5inco/torph-compose.git
cd torph-compose
./gradlew publishLocal    # installs both libraries into ~/.m2/repository
```

That needs a JDK and an Android SDK with platform 37.1; see
[Building from source](#building-from-source). Then, in the project that wants to use it:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}
```

```kotlin
// app/build.gradle.kts
implementation("des.c5inco.torph:torph-compose:0.2.0")  // Compose UI, brings :torph-core with it
```

`./gradlew torphCoordinates` prints that dependency line for the version you have checked out, and
`publishLocal` takes `-PVERSION_NAME=0.3.0-SNAPSHOT` if you want to stamp a different one.

### Or build it inside your own build

A checkout (a clone, a submodule or a subtree) can also be a Gradle
[composite build](https://docs.gradle.org/current/userguide/composite_builds.html) of your project,
so the library compiles from source with your app and edits to it show up without publishing:

```kotlin
// settings.gradle.kts
includeBuild("libs/torph-compose")  // path to the checkout
```

```kotlin
// app/build.gradle.kts: no version, the included build supplies it
implementation("des.c5inco.torph:torph-compose")
```

Gradle substitutes `des.c5inco.torph:torph-compose` and `des.c5inco.torph:torph-core` with the
projects in the checkout. Two things to know:

- Your build must use the same Android Gradle Plugin version as this one (see
  `gradle/libs.versions.toml`); AGP refuses to mix versions in one build.
- The included build looks for the Android SDK itself: set `ANDROID_HOME`, or give the checkout
  its own `local.properties` with `sdk.dir`.

`:demo` and `:benchmark` are configured along with the libraries but never built unless you run
their tasks.

### What your project needs

- `minSdk` 24 or higher, `compileSdk` 37 or higher, and Android Gradle Plugin 9.1 or newer: the
  Compose release it builds against requires both.
- Java 17 bytecode (`compileOptions` / `jvmToolchain(17)`).
- Compose: the library is built against Compose BOM 2026.09.00 (Compose 1.12) and only depends on
  foundation/animation/ui/ui-text, not Material. A newer Compose in your app, alphas included,
  takes precedence.

Then:

```kotlin
import des.c5inco.torph.compose.TextMorph

TextMorph(text = "$1,234.50")
```

`:torph-core` is plain Kotlin/JVM (`des.c5inco.torph:torph-core`) if you only want the segmentation and
diff algorithms without Compose.

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
    clip: MorphClip = MorphClip.Default,              // how far past the line box digits roll
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
    data class Spring(stiffness = 100f, damping = 10f, mass = 1f) : MorphEase
}

cssCubicBezier("cubic-bezier(0.19, 1, 0.22, 1)") // any CSS cubic-bezier or keyword easing, as written
```

Lower level: `rememberTextMorphState()` + `Modifier.textMorph(state)` for custom containers, and
`segmentText` / `diffSegments` / `findNumericWords` from `des.c5inco.torph.core`.

### Clipping rolling digits

A rolling digit is drawn through a soft clip: a band around its line box, faded at the top and
bottom, so the digits before and after it in the roll slide in and out of view. `clip` sets that
band, in fractions of the line height:

```kotlin
TextMorph(value = count, clip = MorphClip.Default)    // a quarter line past the line box
TextMorph(value = count, clip = MorphClip.lineBox())  // nothing past the line box
TextMorph(value = count, clip = MorphClip.window())   // 6% inside it, like a counter's window
TextMorph(value = count, clip = MorphClip(overflow = -0.1f, inset = 0.2f))
```

`overflow` is how far past the line box a digit may draw (negative clips inside it), and `inset` is
how far inside the line box the fade completes. `Default` (0.25, 0.1) is how this library has always
drawn rolling digits, and looks best with room around the figure. When a figure sits tight against a
label above or below it, the neighbouring digits paint over that label mid-roll, and with some fonts
their edges stay faintly visible after the roll settles; `lineBox()` or `window()` keep the roll
inside the figure's own line. Keep `inset` larger than `-overflow` and inside the gap between the
line box and the digits' ink, or settled digits are faded at their edges. The Numbers screen of
`:demo` switches between the presets.

### Coming from torph

This library follows torph's behavior, option names and defaults where they make sense on Compose,
so it should feel familiar. It is not a drop-in equivalent, though: there is no way to load a torph
setup, so you translate it to Kotlin yourself, and some options differ because the platforms do.

Same name, meaning and default as torph: `scale`, `numbers`, `decimals` (unset formats up to 3
fraction digits, rounded like `Intl.NumberFormat`), `debug`, `disabled`, `respectReducedMotion`,
`cursorIndex`, `onAnimationStart`, `onAnimationComplete` and `onAnimationCancel` (exactly one of
complete or cancel per morph).

Where it differs, and why:

| torph | here | why |
| --- | --- | --- |
| A component per framework, plus `MorphController` with `update()` | The `TextMorph` composable, or `rememberTextMorphState()` with `Modifier.textMorph(state)` for custom containers | Compose has composables and modifiers instead of components and DOM controllers. |
| `text` / `children`: a string or a number | Two overloads: `text: String` and `value: Number` | Kotlin overloads instead of a union type. |
| `duration`: milliseconds as a number | `duration: Duration`, default `400.milliseconds` | Kotlin's typed duration. |
| `ease`: a CSS easing string or spring params | `ease: MorphEase`, either `Curve(Easing)` or `Spring(stiffness, damping, mass)`; `cssCubicBezier("…")` turns a CSS cubic-bezier or keyword easing into an `Easing` | Compose animates with typed `Easing`s, not CSS strings. |
| Spring `precision` | No equivalent | torph bakes a spring into a fixed-length CSS `linear()` curve and uses `precision` to decide where to cut it off. Here the spring runs live, keeps its velocity when a new value interrupts it, and stops once movement is under half a pixel. The same spring can therefore finish at a slightly different time than in torph. |
| `locale`: `Intl.LocalesArgument`, default `"en"` | `locale: Locale` (Compose), default `Locale.current` | Compose's locale type; following the device locale is the Android convention. |
| Chooses segmentation itself: by word when the text has a space or line break, by character for a single word | `segmentation`: `AUTO` (default) applies torph's rule, `GRAPHEME` and `WORD` force one | `AUTO` differs in one case: a single word in a script that needs contextual shaping (Arabic, Indic, Thai and others) stays whole, because splitting it into characters breaks its shaping. Any mode switches to words above 300 segments to keep the diff fast. |
| `className`, `style`, `as` | `modifier`, `style: TextStyle`, `color` | Compose styling. |
| Container size always animates | `sizeMode`: `Animate` (default) or `Snap` | `Snap` skips the per-frame layout pass while the size animates, for fixed-size slots like counters. |
| Rolling digits clip at the line box, fading over `0.15em` inside it | `clip: MorphClip`, by default a quarter line past the line box, fading until a tenth of a line inside it; `MorphClip.lineBox()` comes close to torph | The default is this port's original look, kept so that existing layouts do not change. See [Clipping rolling digits](#clipping-rolling-digits). |
| `segmentText`, `diffSegments`, number helpers | `segmentText`, `diffSegments`, `findNumericWords` in `torph-core` | Same names and purpose, but Kotlin signatures: segmenter, options and id arguments differ. |

## How it works

Per text change (never per frame):

1. The full target string is measured once with `TextMeasurer` under the incoming constraints, so
   line breaks and kerning match a real `Text`.
2. `diffSegments` pairs numeric words by order and matches digits by place value; everything else
   goes through a left-biased LCS on grapheme (or word) text.
3. Each segment gets a target rect from the layout's bounding boxes and a cached single-segment
   `TextLayoutResult`. Boxes for leading lines an edit leaves unchanged are kept from the previous
   layout.
4. Each segment's position, alpha and scale are plain float channels, retargeted from their current
   value and velocity: persist → move to the new position, enter → alpha/scale in (a digit that
   changed at the same place becomes an odometer strip from old to new digit), exit → alpha/scale
   out, then dropped by the frame loop once settled.
5. The container size animates (or snaps) to the new layout size.

Per frame: one frame loop advances every channel, and one `drawText(cachedLayout, topLeft, alpha)`
per live segment runs inside a `drawBehind` in its own graphics layer, so siblings redrawing do not
re-record the text. Channels are read only in the layout and draw phases, never in composition, and
the traces confirm composition does not run per frame. Note that a *layout* pass does run per frame
while the container size animates; use `sizeMode = Snap` to skip it.

Segmentation: `Segmentation.AUTO` morphs text with a space or line break word by word and a single
word per character, as torph does. A single word in a script that needs contextual shaping (Arabic,
Hebrew, Indic, Thai, Lao, Khmer, Myanmar, Tibetan, Mongolian, detected via `Character.UnicodeScript`)
stays whole so its shaping survives. `GRAPHEME` morphs every character, even across words. Above 300
segments any mode falls back to `WORD`.

## Building from source

You need a JDK to run Gradle (any recent one; the build uses a Java 17 toolchain and downloads it
if your machine does not have one) and, for everything except `:torph-core`, an Android SDK with
platform 37.1 installed. Point at it with `ANDROID_HOME` or a `local.properties` containing
`sdk.dir=/path/to/Android/sdk`.

| Command | What it does |
| --- | --- |
| `./gradlew torphCoordinates` | Print the coordinates this checkout publishes. |
| `./gradlew publishLocal` | Build both libraries into `~/.m2/repository`. |
| `./gradlew :torph-core:test` | Core unit tests. Pure JVM, no device or SDK. |
| `./gradlew :torph-compose:assembleRelease` | Build the Android library AAR only. |
| `./gradlew :demo:installDebug` | Install the demo app on a connected device or emulator. |
| `./gradlew :torph-compose:connectedDebugAndroidTest` | Compose instrumentation tests (device needed). |
| `./gradlew :benchmark:connectedBenchmarkAndroidTest` | Macrobenchmarks (device needed). |

The instrumentation suite includes `DrawAllocationTest`, a regression guard that fails if the draw
path starts allocating per frame. See [docs/BENCHMARKS.md](docs/BENCHMARKS.md).

## Benchmarks

`:benchmark` is a Macrobenchmark module covering long text, number rolling, rapid interruption, and
multi-line reflow (`./gradlew :benchmark:connectedBenchmarkAndroidTest`). Results, the
phase-by-phase breakdown of where a text change spends its time, and notes on running against a
physical device are in [docs/BENCHMARKS.md](docs/BENCHMARKS.md).

Implementation status and known gaps are tracked in [docs/STATUS.md](docs/STATUS.md).

## License and attribution

Torph for Jetpack Compose is MIT licensed. See [LICENSE](LICENSE).

This is an **independent, unofficial port** of [torph](https://github.com/lochie/torph) by
[Lochie Axon](https://github.com/lochie), used under the MIT License. The Kotlin here was written
from scratch. Its API, option names, algorithms and defaults follow torph's where that makes sense
on Compose, so it will feel familiar, but it is not a drop-in equivalent: you translate a torph setup
yourself, and the two differ where the platforms do. Upstream's full copyright notice is in [NOTICE](NOTICE).

If you want the original, go use it: <https://torph.lochie.me>.

### Not affiliated with Google

This is a personal project by [Chris Sinco](https://github.com/c5inco). It is not a Google
product and is not endorsed or supported by Google. "Android" and "Jetpack Compose" are trademarks
of Google LLC, used here only to describe what this library targets.
