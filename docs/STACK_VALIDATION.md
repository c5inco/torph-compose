# Extracted PR validation

The combined investigation is preserved on local branch `backup/issue3-combined-20261102`.
The following checks run against each extracted revision, not only the final combined tree.
No main-branch push, merge or issue closure is part of extraction.

## 1. Test and benchmark foundation

Pixel 7 / API 37: full 29-test instrumentation suite passed, zero failures/errors/skips.
The native reference corpus has 37 exact matches and 14 named baseline findings. The negative
control detects a two-pixel displacement on software and hardware. Production behavior stays
eager; only opt-in tracing and initial/snap timing logs are added.

Build/test log: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T02-32-28-972Z-26191.log`.
Raw extraction evidence: `/tmp/torph-pr-stack-20261102/stage1/`.

Release/AOT plain Text, AUTO and GRAPHEME eight-label benchmark smoke: three cases × five
samples; all 15 samples have one outer first-measure marker and the expected 0/8 first applies.
Benchmark log: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T02-35-52-323Z-27907.log`.

## 2. Shared layout cache and state ownership

Pixel 7/API 37: full 41-test instrumentation suite passed, zero failures/errors/skips,
including cache keys/eviction, async fonts, environment transitions, disposal, accessibility
and the independent baseline typography corpus. This revision still uses eager preparation.
Build/test log: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T02-38-31-677Z-28587.log`.
Raw evidence: `/tmp/torph-pr-stack-20261102/stage2/`.

## 3a. Bidi correctness before geometry acceleration

Pixel 7/API 37: both BidiGeometryTest methods passed. The runner executed only the first
class in the requested multi-class filter; no claim is made that the other classes ran here.
The six original bidi findings are promoted to exact contracts; the complete typography corpus
is run with the following geometry-optimization revision. The expanded 30-case bidi geometry
corpus and six animation reference cases pass, including
mirrored brackets, isolates and retarget continuity.
Log: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T02-42-23-553Z-30386.log`.
Raw evidence: `/tmp/torph-pr-stack-20261102/stage3-correctness/`.

## 3b. Bidi geometry acceleration

Pixel 7/API 37: full 44-test instrumentation suite passed, zero failures/errors/skips.
The independent corpus has 43 exact matches and 8 known shaping/wrapping differences; all
30 expanded bidi geometry and six animation reference cases match. Native range geometry
checks compare 93,912 ranges at zero tolerance, including the native-path fallbacks.
Log: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T02-44-01-697Z-31691.log`.
Raw evidence: `/tmp/torph-pr-stack-20261102/stage3-optimized/`.

## 4. Bounded deferred first layout

The extracted final tree passed the complete **55-test instrumentation suite on both Pixel 7
(API 37) and Pixel 4 XL (API 33)**, with zero failures/errors/skips. This includes the additional
tight-line-height handoff test in the full suite, rather than only a separate focused run.
Each phone reports 48 exact matches / 3 named wrapping findings in the fresh optimized corpus;
the explicit legacy corpus remains 43 exact / 8 known. All 15 prepared-region hardware/software
fixtures and 135 software region reconstructions pass, with negative displacement controls.

At initial extraction head `026e714`, all 69 source/resource files across the four modules were byte-identical to the preserved combined
implementation, including benchmark sources. Thus the previously recorded final validated-launch
performance evidence applies to this extracted tree; the matrices were not rerun or their samples
mixed with earlier exploratory data during packaging. All 22 core/library production files matched at that head.
The existing b7965c3 geometry and benchmark-device controls remain intact.

Core test task passed (18 passing unit-test results). Release library, debug demo, benchmark APK
and instrumentation APK build checks passed. The Pixel 4 XL used the existing source-identical
build mirror `/tmp/torph-lazy-pixel4`, with a separate ATC workspace/session. Both leases released.

Logs:

- Builds/core tests: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T02-49-06-728Z-34632.log`
- Pixel 7 full suite: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T02-49-51-014Z-34860.log`
- Pixel 4 XL full suite: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T02-49-58-060Z-34937.log`

Raw evidence: `/tmp/torph-pr-stack-20261102/stage4-pixel7/` and `stage4-pixel4xl/`.
[Structured extraction results](STACK_VALIDATION_RESULTS.json) include counts and final source hashes.
The original measured performance and tradeoffs are in [DEFERRED_PREPARATION.md](DEFERRED_PREPARATION.md).

## Review order

1. [Foundation, PR #4](https://github.com/c5inco/torph-compose/pull/4), branch `issue3/01-test-foundation`, base `main`.
2. [Shared cache, PR #5](https://github.com/c5inco/torph-compose/pull/5), branch `issue3/02-shared-layout-cache`, base foundation.
3. [Bidi correctness/geometry, PR #6](https://github.com/c5inco/torph-compose/pull/6), branch `issue3/03-bidi-geometry`, base cache. Review correctness commit before optimization commit.
4. Bounded first-layout renderer, branch `issue3/04-deferred-first-layout`, base bidi.

Review and merge bottom-up when approved, retargeting each next PR to main after its predecessor
lands. These drafts are not merged, main is not pushed, and issue #3 remains open. Avoid combining
historical cache/bidi gains with the final eager-versus-deferred comparison: that eager baseline
already includes both improvements. The remaining wrapping coverage and animation frame-pacing
findings are separate follow-ups, not hidden within this stack.

## Subsequent bidi review correction

The source-identity claims and counts above describe the initial extraction heads. The later
[mixed-script word correction](BIDI_REVIEW_FOLLOWUP.md) changes the bidi direction helper and adds
one regression method; its current checks and hashes are recorded separately in that follow-up.
