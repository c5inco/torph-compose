# Bidi placement correction

The six original mixed-direction typography cases now require exact `BasicText` parity rather
than being diagnostic exceptions. The fix uses the full paragraph's selection geometry and
resolved run directions when preparing bidi segments. ASCII retains its existing geometry path.

## Cause and correction

Character boxes at a bidi boundary can select the wrong caret edge. In the recorded Pixel 7
24 sp case `שלום 123 (abc)`, the first digit's character box ran from x=39 to x=13, while its
selection range was x=0 to x=13. The last digit's box extended from x=26 to x=98.76172, while its
selection was x=26 to x=39. A positive-width check would miss that second error; swapping reversed
edges would also fail to recover the first digit's actual position.

For bidi content, placement now uses `getPathForRange(segment.index, segment.end).getBounds()`
instead of the ambiguous character-box union. It aligns the segment's actual line origin to
that range, subtracting the standalone layout's `getLineLeft(0)` rather than centering its rounded
integer width. The baseline continues to come from the full paragraph.

A second defect appeared in the expanded bracket tests: the full paragraph can resolve a
parenthesis as RTL even inside an LTR container. Measuring every standalone segment with the
container's direction draws the wrong mirrored glyph. Bidi segment layouts now use their resolved
run direction explicitly. Lazy per-state LTR/RTL caches separate layouts of identical text in
opposite runs; the shared TextMeasurer LRU also keys these direction/style inputs. They share the
existing combined 1024-entry reset policy with the ordinary per-state cache, rather than receiving
independent budgets. The shared 256-layout LRU and 16-environment pool remain unchanged.

Persisting segments refresh their layout when the resolved direction changes. Origin compensation
preserves their current `x + lineLeft` and `y + firstBaseline`, carrying channel velocity into the
new targets. Rolling strips use LTR digit layouts inside the surrounding bidi paragraph. Selection
paths and layout preparation run on layout changes, not in the per-frame draw loop.

## Regression contracts

- Original corpus: the two bidi strings in all three segmentation modes are now hard software
  and hardware pixel assertions. The 51-case corpus has 43 exact cases and eight named remaining
  shaping/wrapping differences.
- Expanded bidi geometry: five strings × LTR/RTL containers × AUTO/WORD/GRAPHEME = 30 exact
  reference cases. These include isolates, brackets around Hebrew, brackets around numbers, and
  the same bracket text resolving in both directions within one paragraph. Cells must have
  nonnegative width and match independent selection geometry.
- Animation: digit rolling, interruption, and a bracket direction-context change in each mode.
  Persistent visual origins/baselines and alpha/scale remain continuous at retarget; digit strips
  stay in range; final frames must equal BasicText exactly. Software/hardware filmstrips and channel
  snapshots are saved under `typography-reference/bidi-animation/`.
- Existing bounds tests retain the character-union oracle for ASCII and mirrored transforms.
  Bidi bounds are checked against the independent paragraph selection geometry.

These tests prove the selected inputs, not every Unicode/font/embedding combination. They do not
assert that every intermediate animation frame should equal static BasicText. A context change can
change the mirrored glyph itself; this work preserves its origin/baseline and does not add a
cross-fade between alternate glyph shapes. Negative/zero horizontal scale retains the legacy
signed-box path pending independent mirrored-transform rendering coverage. WORD wrapping and the
remaining kerning/ligature/Arabic shaping differences are separate follow-ups.

## Performance protocol

The benchmark adds `BidiPlacementWorkload`: eight distinct ASCII labels, eight bidi labels, and
plain Text controls for each. Each case uses five fresh-state first layouts in a warm process,
with the existing full compilation and device-state snapshot/restore protocol. Before/after builds
use the same benchmark and demo source; only `TextMorphState.kt` changes. The pre-fix source already
contains the shared-layout cache and lifecycle/font corrections, so this comparison isolates the
bidi follow-up rather than repeating the earlier cache-versus-no-cache comparison.

Saved source hashes (SHA-256):

- Before: `a22382ea302158a3e2d0dd3a734679d66023cfaad9be0ac00be06552e1cda72b`.
- Fixed: `a60fe28735f6d112bc03ae091bf12141e656a377af5b3f87daa591a3b1ab48e4`.

Raw data and both source versions live under `/tmp/torph-bidi-20261031/`. Missing trace markers
invalidate an iteration for this measurement; rerun the entire affected case rather than drop
an inconvenient sample. Wall-time samples, platform layout counts, marker validation and a scoped
CPU-slice analysis are retained. This is not an FPS or warm text-change throughput benchmark.

## Final results: October 31, 2026

Both phones pass the final **43-test instrumentation suite**, with zero failures or skips:
Pixel 7 (`2C131FDH200FE7`, API 37) and Pixel 4 XL (`98111FFBA000JM`, API 33). Each also has
43/51 exact reference corpus matches, 30/30 expanded bidi matches, and 6/6 animation endpoint
matches. The other eight corpus cases remain documented wrapping/kerning/shaping differences.

The performance result is a cost, not a speedup. Pixel 7 first-measure medians/ranges in ms:

| Eight-label workload | Before median (min–max) | Fixed median (min–max) |
| --- | --- | --- |
| Bidi morph | 5.388 (4.845–5.967) | 8.086 (6.756–8.655) |
| ASCII morph | 2.740 (2.625–2.945) | 2.911 (2.464–2.927) |
| Bidi plain Text | 1.752 (1.666–2.131) | 1.661 (1.567–1.906) |
| ASCII plain Text | 0.617 (0.553–1.300) | 0.625 (0.528–1.114) |

The bidi median increases **2.698 ms (50.1%)** for these eight distinct labels. ASCII morph
ranges overlap; five samples do not establish a reliable ASCII change. This performance
comparison was run only on Pixel 7. Pixel 4 XL was checked for correctness, not matched timing.
No claim about FPS, cold startup, or warm text-change throughput follows from these samples.

For the trace iterations whose wall times equal the bidi workload medians, scoped CPU rises
5.314 → 7.546 ms. Snap preparation CPU rises 3.161 → 5.264 ms, while nested segment-measure
CPU rises 2.108 → 2.416 ms. Platform layouts rise 50 → 53; per-state layout requests rise
113 → 122, including correctly separated neutral/directional layouts. Most added CPU is in
preparation beyond segment measurement. Nested times are not additive. These CPU values are
from the specified median-wall iterations, not independent CPU medians.

Two original plain-Text cases had one missing outer trace marker each. Entire five-iteration
cases were rerun: before/plain-bidi and fixed/plain-ASCII. Original data is retained but those
entire cases are superseded in the summary; no speed outlier was filtered. All 40 included
iterations have exactly one outer marker and the expected eight morph applies or zero plain
morph applies. [BIDI_PLACEMENT_PERFORMANCE.json](BIDI_PLACEMENT_PERFORMANCE.json) records all
samples, platform counts, source paths, recheck reasons and CPU results.

The fix is validated for correctness, with a measured first-layout cost for bidi content.
Reducing that preparation cost is separate work before claiming this improves bidi performance.
The remaining wrapping/shaping exceptions also remain separate follow-ups. No commit/push or
issue closure was performed.

### Evidence and operations

- Final test XML/logcat and all captures: `/tmp/torph-bidi-20261031/pixel7-tests/` and `pixel4xl-tests/`.
- Matched performance: `pixel7-before/`, `pixel7-after/`, `pixel7-before-plain-bidi-recheck/`,
  `pixel7-after-plain-ascii-recheck/` under the same raw directory.
- Median-iteration CPU CSV: `analysis/before-median-cpu.csv`, `analysis/after-median-cpu.csv`.
- Full source before/after: `before-source/TextMorphState.kt`, `after-source/TextMorphState.kt`.
- Current structured reference results: [TYPOGRAPHY_REFERENCE_RESULTS.json](TYPOGRAPHY_REFERENCE_RESULTS.json).

Unmodified Pixel 4 XL hardware evidence for the original bidi case:
[before](media/typography-reference/pixel4xl-bidi-morph.png),
[fixed](media/typography-reference/pixel4xl-bidi-morph-fixed.png),
[BasicText](media/typography-reference/pixel4xl-bidi-reference-fixed.png).

Final raw ATC logs under `/Users/christop/Library/Application Support/atc/logs/`:

- Pixel 7 tests: `gradle.tasks-2026-11-01T02-43-18-615Z-14729.log`.
- Pixel 4 XL tests: `gradle.tasks-2026-11-01T03-01-22-422Z-20819.log`.
- Before benchmarks: `gradle.tasks-2026-11-01T02-47-55-424Z-17772.log`.
- Fixed benchmarks: `gradle.tasks-2026-11-01T02-51-11-511Z-18598.log`.
- Before plain-bidi recheck: `gradle.tasks-2026-11-01T02-54-40-400Z-19407.log`.
- Fixed plain-ASCII recheck: `gradle.tasks-2026-11-01T02-59-35-328Z-20297.log`.

The fixed source was restored after baseline builds and verified against the saved SHA-256.
BenchmarkDeviceState's setting-restoration checks passed for each successful class. Both leases
were released; ATC reports no active leases/builds/queue tickets. The two Perfetto sessions were
stopped. Device-panel discovery failed after its suggested refresh; all device work used the
project's instrumentation runners. The profiler analysis used the skill-pinned Perfetto v57.2.

## Performance follow-up

The [selection-geometry optimization](BIDI_PLACEMENT_OPTIMIZATION.md) measures a follow-up
against this correct renderer. The timings above remain the historical correctness-fix cost,
not the current optimization comparison.
