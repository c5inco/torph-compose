# Independent typography reference

Current implementation and validation: [bounded deferred preparation for issue #3](DEFERRED_PREPARATION.md).
The results below describe the earlier investigation/renderer; the linked report distinguishes
the final optimized path, its fallback limits and the latest device measurements.

`TypographyReferenceTest` compares settled `TextMorph` output with a real `BasicText` composable.
The reference does not consume morph segments, character unions, offsets, or the shared measurer.
It uses Compose's whole-paragraph text renderer. This adds an independent rendering contract to
[the cache differential harness](REGRESSION_TESTING.md); the two harnesses answer different questions.

## Contracts and artifacts

Both subjects receive the same text, explicit style/color, font resolver, density/font scale,
layout direction and wrapping constraints. Tests assert the effective layout-input values match,
including wrapping, line limit and overflow. Density implementations differ between the UI node
and `TextMeasurer`, so the test compares their numeric density/font-scale values, not object equality.
The two paragraph objects must be distinct. `BasicText.onTextLayout` supplies all reference metadata.

Every corpus case asserts identical occupied size, paragraph size, line starts/ends, line edges,
line top/bottom, per-line baselines and first/last baseline. Software captures compare a draw of
the independent BasicText paragraph with the segment draw. Hardware captures compare the two real
UI nodes. Each comparison uses exact pixel equality without masks, thresholds or fuzzy matching.

The 1024 × 640 capture panes place both subjects at the absolute physical origin (256, 256),
including in RTL. Generous margins retain displaced or overhanging segments; draw extents and
image edges are checked so cropping cannot conceal a mismatch. A negative control deliberately
moves one morph glyph two pixels and requires both pixel comparisons to detect the displacement.

The corpus covers AUTO, WORD and GRAPHEME; digits, single glyphs, empty text and spaces; kerning,
ligatures, Arabic, Indic, Thai, combining marks, emoji, bidi, isolates, explicit newlines, wrapped
lines and long words. Additional cases exercise RTL with density/font-scale changes, disabled
font features, and a serif font with an explicit Arabic locale.

EmojiCompat matching is disabled through `PlatformTextStyle(emojiSupportMatch = None)` on both
subjects, keeping this reference about deterministic platform shaping. Native emoji fallback
still renders. Actual asynchronous font completion and EmojiCompat timing are separate concerns
covered by the existing font tests; this corpus does not claim their typography parity.

Every case saves these artifacts, even when it is a known difference:

- `software-reference.png`, `software-morph.png`, `software-diff.png`.
- `hardware-reference.png`, `hardware-morph.png`, `hardware-diff.png`.
- `metadata.json`: exact changed-pixel counts, text/style/environment, line metrics, character
  boxes and bidi run directions, plus morph segment positions and cells.

Diffs mark unequal pixels in magenta. Per-device `results.json` collects the cases. Artifacts are
under Gradle's `connected_android_test_additional_output/.../typography-reference/corpus/`.
The deliberate-displacement test writes to the separate `oracle-self-check/` directory.

## Exact requirements versus known differences

`Exact` cases fail for any different software or hardware pixel. `Characterize` cases are explicit
known differences with a reason; their layout-metadata assertions remain strict, but their pixels
are diagnostic findings, not passing equality checks. They do not require a defect to persist:
an improvement can become an exact case after review. Characterization provides no pixel regression
budget for that case, so changed artifacts must be reviewed when the renderer changes.

After the [bidi correction](BIDI_PLACEMENT_FIX.md), the current 51-case corpus has 43 exact
pixel contracts and eight explicitly characterized differences. In addition, all 51 cases require matching layout metadata. These counts exclude
the deliberate-displacement self-check.

New cases require exact parity by default unless they deliberately exercise a named existing defect.
Do not resolve a new failure by adding a blanket tolerance or replacing the independent reference
with a screenshot of the current morph. The existing shared-versus-isolated harness still checks
that optimizations preserve behavior, including on these currently incorrect inputs.

## Findings and follow-up

The initial reference exposed these visible differences, not only unusual box coordinates.
The bidi placement cases have since been [fixed](BIDI_PLACEMENT_FIX.md); wrapping and shaping
cases remain as explicit findings:

- For `שלום 123 (abc)`, the morph has overlapping/misplaced glyphs while BasicText draws the full
  paragraph coherently. Both use matching line metrics. Character metadata retains the original
  bounds, including reversed horizontal edges where returned by Compose. This establishes a visual
  placement problem in the morph; it does not establish which Compose/Android API or edge-selection
  rule is responsible. The follow-up uses selection geometry and resolved run directions instead
  of those ambiguous character edges; the six original bidi cases now require exact pixels.
- In WORD mode, `Supercalifragilisticexpialidocious` at a 100 px width is drawn as one unwrapped
  segment even though the full paragraph and its occupied height wrap. The reference images make
  that disagreement visible. Segment shaping and full-paragraph measurement can agree on overall
  layout metrics while the actual segment ink is wrong.
- Ordinary wrapped lines also differ in WORD/AUTO without splitting a word across lines.
  In the recorded `wrapping` segment, centering a 97 px segment layout in a 96.53125 px paragraph
  cell produces x = −0.234375 px, whereas the full paragraph starts that line at x = 0. This
  identifies a concrete placement/rounding difference to investigate separately from word splitting.
- Kerning pairs and grapheme-split ligatures/Arabic have differences between separately shaped
  segments and the whole-paragraph reference. These are specific text/font/mode observations,
  not universal claims about an entire script.

Preserved Pixel 4 XL hardware examples from before the bidi fix (unmodified captures):

- Mixed bidi: [BasicText](media/typography-reference/pixel4xl-bidi-basictext.png) and [morph](media/typography-reference/pixel4xl-bidi-morph.png).
- Long word in WORD mode: [BasicText](media/typography-reference/pixel4xl-wrapped-word-basictext.png) and [morph](media/typography-reference/pixel4xl-wrapped-word-morph.png).

The next rendering work should address the cost of bidi preparation and WORD segments crossing
wrapped lines, then remove the remaining explicit exceptions. Bidi placement is now corrected for
the tested inputs. A broader shaping-preserving renderer needs its own design
and intermediate-frame continuity checks. This work does not switch rendering at rest, alter
production geometry, or measure new performance benefits.

This is independent of the morph renderer, not independent of Compose/Android's typography engine.
If the platform itself renders an input incorrectly, BasicText is not an oracle for that platform
bug. Captures are compared within a device/run; no cross-OS pixel equality is required. This first
iteration covers settled text with a deterministic platform font environment, not animation-boundary
parity, Activity recreation, font downloads, all font families or every Unicode sequence.

## Validation and recorded data

The full suite after the bidi fix passes 43 tests with zero failures/skips on Pixel 7/API 37
(`2C131FDH200FE7`) and Pixel 4 XL/API 33 (`98111FFBA000JM`). On each phone, all 43 exact corpus
cases have zero changed pixels in both software and hardware. The other eight cases have recorded
differences and explicit reasons; they are not passing typography-equality assertions. The 30
expanded bidi cases and six animation endpoint comparisons also match exactly on both phones.
The negative control detects the deliberate displacement on both rendering paths.

[TYPOGRAPHY_REFERENCE_RESULTS.json](TYPOGRAPHY_REFERENCE_RESULTS.json) contains the current per-case
counts and input/environment identifiers. The [initial reference baseline](TYPOGRAPHY_REFERENCE_BASELINE_RESULTS.json)
preserves the earlier 37-match/14-difference result before correction. See [the bidi report](BIDI_PLACEMENT_FIX.md)
for final validation artifacts, source hashes and performance results. The raw paths and logs below
are the earlier reference-only runs. Full metadata, PNGs, JUnit XML and logcat are retained
under `/tmp/torph-typography-20261031/pixel7-final/` and `pixel4xl-final/`. Initial harness runs
are retained separately: the density-implementation equality check was corrected to compare
numeric values, capture margins were enlarged after a crop check failed, and the stopped
Activity run motivated explicit test-runner wake/keyguard setup. They are not final results.

Final raw ATC Gradle logs:

- Pixel 7: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-01T01-53-50-889Z-1126.log`
- Pixel 4 XL: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-01T01-50-27-246Z-99102.log`

`git diff --check` passes. Both ATC leases were released. Device-panel discovery still failed
after its suggested refresh; validation and captures used the project's instrumentation runner.
The original reference-only change did not alter production rendering. The subsequent bidi
correction is documented separately; the prior ASCII optimization remains in place.

## Running

Use ATC and the build-brief wrapper described in [REGRESSION_TESTING.md](REGRESSION_TESTING.md).
Select class `des.c5inco.torph.compose.TypographyReferenceTest`. Claim one phone and run one connected
task per invocation. The test runner wakes the screen and dismisses a nonsecure keyguard before
hardware capture; it cannot bypass a secure lock. `keepScreenOn` is restored on disposal, and no
persistent animation or refresh-rate settings are changed.

## Bidi preparation performance follow-up

The [geometry optimization](BIDI_PLACEMENT_OPTIMIZATION.md) adds exhaustive native range
comparisons alongside these independent pixel contracts. The 43 exact corpus cases and eight
explicit differences remain separate from optimized-versus-correct-renderer capture parity.
