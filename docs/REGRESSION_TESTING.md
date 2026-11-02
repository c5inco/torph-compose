# Rendering and lifecycle regression coverage

Current implementation and validation: [bounded deferred preparation for issue #3](DEFERRED_PREPARATION.md).
The results below describe the earlier investigation/renderer; the linked report distinguishes
the final optimized path, its fallback limits and the latest device measurements.

The instrumentation suite includes a reusable differential harness for changes to caching,
segmentation, geometry, drawing and animation. `MorphParityHarness` mounts two real `TextMorph`
composables on the same manually advanced Compose frame clock. Its default subjects use the
shared measurer and an isolated `TextMeasurer(cacheSize = 0)`, respectively. Both use the same
state/renderer algorithm; substitute a `MorphSubject` factory to test another state setup.
`MorphScene` and `MorphEnvironment` describe the inputs without depending on a particular cache.

## Coverage

| Area | Scenarios and assertions |
| --- | --- |
| Animation | Applied, intermediate, interrupted and settled frames; digit rolling, entering/exiting text, scale on/off, and all three clip presets. Exact software pixels and selected hardware node captures. |
| Unicode/shaping | 22 cases under AUTO, WORD and GRAPHEME: kerning pairs, ligatures on/off, composed/decomposed combining marks, Arabic, Hebrew, bidi isolates, Devanagari conjuncts, Thai, CJK/Hangul, skin tones, ZWJ families, flags, keycaps, localized digits, whitespace, tabs, newlines and long wrapped words. |
| Reproducibility | Seeds 73013 and 99173, each with 18 text/style/width/option changes, mixing interrupted and completed generations. Every check includes seed, step and complete input history. |
| Environment | A live composition changes density, font scale, layout direction and resolver, then returns to its original environment. Text changes also animate within each environment; a moving state is cancelled and replaced during a density change. Style locale changes are covered in the corpus; default-locale pool separation is covered by `SharedTextMeasurerTest`. |
| Cache pressure | More than 256 distinct layout inputs; existing states keep drawing correctly after eviction, and remounting produces a fresh paragraph. More than 16 live measurers saturate the actual environment registry, holding strong references so weak-entry cleanup cannot substitute for eviction. Separate pool tests cover owner/locale isolation. |
| Font loading | Real deferred `AndroidFont` resolution at rest, with concurrent text changes, and during entry, exit and rolling. Loading must not cancel an active generation. Retargeting after loading refreshes persistent segments; settled layouts must not retain stale fonts. |
| Accessibility | Disabled updates and the actual system animator-scale-zero setting both snap, with balanced callbacks. The system-setting test snapshots, restores and reads back its previous value in `finally`. |
| Lifecycle | Removing a moving remembered state cancels exactly once, stops its frame loop, and clears `isAnimating`. Remounting creates an independent state and generation. |
| Independent contracts | Target segments reconstruct the input, IDs are unique, channels/bounds are finite, digit strips remain in range, settled exits disappear, callback counts balance, persistent IDs survive, and retargeting preserves current persistent channel values before another frame advances. Nonblank settled/software and selected hardware captures must contain ink. |

The existing geometry, clipping, interruption, redraw/allocation and callback tests remain in
place. `DrawAllocationTest` keeps its existing allocation threshold. These functional tests do
not establish performance improvements; use the separate first-appearance benchmark and warm
text-change benchmarks for that purpose.

## Extending a scenario

Use the same clock and paired input mutation, and check at least an intermediate and settled
frame. For example, inside an instrumentation test with a Compose rule:

```kotlin
val h = MorphParityHarness(rule)
h.mount(MorphScene(text = "Hello world"))
h.update(h.scene.copy(text = "Hello there"))
h.advance(96)
h.check("new-feature-mid", hardware = true)
h.advance(1000)
h.check("new-feature-settled", settled = true, hardware = true)
```

Add a small independent assertion for the intended behavior: pixel equality alone cannot
prove that the animation actually started or that two implementations did not make the same
mistake. The harness asserts its subjects, measurers and initial paragraphs are distinct.
Hardware panes have equal dimensions and integer pixel coordinates; captures use Compose's
node capture API and require an accelerated view. `keepScreenOn` is restored on disposal.

`check` samples the frame after two application frames, rather than claiming to capture an
exact animation percentage. `retargetPreservesPersistingChannelValuesBeforeNextFrame` separately
checks immediate channel continuity without advancing time. The seeded sequences use the
Compose test clock rather than wall-clock sleeps.

On a pixel mismatch, the harness writes `expected.png`, `actual.png`, `diff.png` and `replay.txt`
into the instrumentation `additionalTestOutputDir`, which Gradle pulls under
`torph-compose/build/outputs/connected_android_test_additional_output/`. Without that argument,
it uses the target application's external files directory. The assertion includes the path.
The diff marks every unequal pixel in magenta; no tolerance masks or cross-device goldens are
used. Replay history is also included in structural assertion failures.

For independent settled typography comparisons against `BasicText`, see
[TYPOGRAPHY_REFERENCE.md](TYPOGRAPHY_REFERENCE.md). That suite adds a separate correctness
contract; the differential harness described here continues to isolate optimization regressions.

## Limits and findings

These are same-device, same-frame differential tests, not an independent typography oracle or
cross-platform screenshot baseline. They preserve the current segmented renderer, including
its existing differences from full-string shaping. Passing does not prove every grapheme/word
split renders like plain `Text`, nor that all possible animation times are continuous.

The expanded bidi cases exposed reversed horizontal extents in some character boxes on Pixel
7/API 37 (for example the first digit in `שלום 123 (abc)`). A fresh call to Compose's direct
`TextLayoutResult.getBoundingBox()` returned the same reversed edges. The harness checks finite
coordinates and nonnegative vertical extent; it does not assume horizontal extents are always
positive or normalize them in production. The independent typography reference subsequently
confirmed visible errors, and the [bidi correction](BIDI_PLACEMENT_FIX.md) now uses selection
geometry and resolved run layouts for bidi content. Raw platform character boxes can still have
reversed edges; corrected bidi cells are independently tested as nonnegative.

The new disposal test did expose a library defect: forgetting a remembered moving state
cancelled its coroutine scope but left `isAnimating` true and never delivered cancellation.
The remembered-state factory now disposes its state explicitly, cancels the loop, and finishes
its generation once. The disposal callback runs synchronously because the remembered scope may
already be cancelled. The uncached test subject uses the same lifecycle helper.

Default platform locale switching, actual Activity recreation, background-thread composition,
heap retention/GC behavior and a wider device/font matrix are not exhaustively exercised.
The environment tests change CompositionLocals; they do not claim to simulate an OS locale
change or Activity restart. Cache capacity checks bound entry counts, not retained bytes.

## Validation: October 31, 2026

The final full instrumentation suite passes **39 tests, zero failures or skips** on both
physical phones: Pixel 7 (`2C131FDH200FE7`, Android 17/API 37) and Pixel 4 XL
(`98111FFBA000JM`, Android 13/API 33). This includes all ten new scenario tests, their exact
software/hardware comparisons, and all 29 prior tests. `git diff --check` also passes.
The animator-scale restoration assertions passed, and both ATC leases were released.

Final JUnit XML and per-case logcat are retained under
`/tmp/torph-test-coverage-20261031/pixel7-final/` and
`/tmp/torph-test-coverage-20261031/pixel4xl-final/`. Initial failing lifecycle/bounds and
unretained-cache-pressure test runs are retained alongside them for diagnosis, not counted
as final successful runs. The pressure test now retains all environment measurers strongly
until its eviction assertion, so collection of weak entries cannot make the test vacuous.

Raw ATC Gradle logs (UTC filenames fall on November 1):

- Pixel 7: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-01T00-23-14-846Z-78710.log`
- Pixel 4 XL: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-01T00-25-21-430Z-79296.log`

The Device panel discovery failed even after its suggested refresh; these checks used the
project's instrumentation runner through ATC. Hardware comparisons are test node captures
from accelerated views, not manual Device-panel screenshots. No refresh-rate settings were
changed for this functional suite.

## Running through ATC and build-brief

Check availability with `atc status`, then claim one physical phone explicitly. Use a wrapper
so ATC coordinates the build and `build-brief` reduces Gradle output:

```sh
cat > /tmp/torph-test-gradle <<'SH'
#!/bin/sh
exec build-brief ./gradlew "$@"
SH
chmod +x /tmp/torph-test-gradle
atc exec gradle tasks :torph-compose:assembleDebugAndroidTest --executable /tmp/torph-test-gradle --brief --session torph-tests
atc claim --serial DEVICE_SERIAL --session torph-tests
atc exec gradle tasks :torph-compose:connectedDebugAndroidTest --executable /tmp/torph-test-gradle --brief --session torph-tests
atc free --session torph-tests
```

Run only one connected test task per invocation. Select the new scene suite with
`--property android.testInstrumentationRunnerArguments.class=des.c5inco.torph.compose.MorphRegressionTest`
or the font/accessibility suite with class `des.c5inco.torph.compose.MorphFontAndMotionTest`.
Use full-suite runs before handoff because lifecycle/font changes can affect other tests.

## Bidi optimization validation

The later [bidi preparation follow-up](BIDI_PLACEMENT_OPTIMIZATION.md) passes 44 tests on both
phones, including exhaustive native range geometry and unchanged captures versus the correct
renderer. Its [current reference results](TYPOGRAPHY_REFERENCE_RESULTS.json) supersede the older
suite counts above. The eight independent typography exceptions remain explicit.
