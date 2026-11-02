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
