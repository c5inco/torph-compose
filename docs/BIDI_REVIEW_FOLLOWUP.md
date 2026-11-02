# Bidi review follow-up: mixed-script words

Codex review of PR #6 found that one ICU WORD segment can contain opposite-direction runs.
For `abcאב` with explicit `TextDirection.Rtl`, forcing its first run's LTR direction on the
whole standalone word reorders its internal runs. Positioning its selection bounds cannot fix
the order. [Review finding](https://github.com/c5inco/torph-compose/pull/6#discussion_r4438624109).

## Reproduction and fix

Before changing production code, the exact BasicText reference test failed on Pixel 7: one
segment containing the complete word, with 755 differing software pixels and 755 differing
hardware pixels. The other two existing bidi methods passed. Raw reproduction:
`/tmp/torph-review-bidi-20261102/before/`.

Segment layout direction now keeps the resolved paragraph base when a segment spans opposite-
direction runs. Single-run segments retain their resolved run direction, preserving the bracket
mirroring correction. The existing directional layout caches already separate the selected base;
no cache capacity, shared ownership or geometry algorithm changes.

The new test covers both Latin/Hebrew script orders, explicit LTR/RTL paragraph styles, both
container directions and WORD/AUTO. AUTO's single-token cases are graphemes; a separate spaced
AUTO fixture keeps the mixed word intact, changes its paragraph base with a new leading word,
checks retained origin/baseline, settles to exact native pixels, then returns to the original
context to exercise cache reuse. All 19 software/hardware reference comparisons match exactly.
This proves these fixtures, not every possible embedding/control/font combination.

## Validation of the corrected bidi PR

- Pixel 7/API 37: full **45-test suite** passed, zero failures/errors/skips. Existing exact
  typography, selection geometry, font/cache/lifecycle, animation and allocation checks remain.
- Pixel 4 XL/API 33: the full **3-method BidiGeometryTest class** passed. Its 19 mixed-word,
  30 geometry and six animation reference captures match exactly. This was a focused run, not
  a new full-suite run on that phone.

[Structured results and source hashes](BIDI_REVIEW_FOLLOWUP_RESULTS.json). Raw evidence under
`/tmp/torph-review-bidi-20261102/{pr6-pixel7,pr6-pixel4xl}/`.

Logs:

- Before failure: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T03-25-05-083Z-51365.log`
- Corrected Pixel 7 full suite: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T03-33-31-710Z-56186.log`
- Corrected Pixel 4 XL focused suite: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T03-34-05-674Z-56415.log`

## Performance evidence provenance

Previous performance tables and byte-identity statements describe the source measured before
this review correction. This change adds direction checks for bidi segment layouts; the complete
performance matrices have not been rerun on it. Retain their original hashes and timings as
historical evidence rather than claiming they measure the revised source. Geometry helpers and
the ASCII eligibility paths remain unchanged.

## Combined first-layout PR after rebasing

The combined tree, including the corrected eager fallback and bounded paragraph-region renderer,
passed the full **56-test instrumentation suite on both Pixel 7/API 37 and Pixel 4 XL/API 33**,
zero failures/errors/skips. The 69 source/resource hashes agree between build workspaces. The
fresh initial corpus remains 48 exact / 3 named wrapping findings; the mixed-word regression
adds 19 exact comparisons per phone. Prepared regions, handoff, callbacks, fonts, cache/lifecycle,
geometry and allocation checks remain intact. Core tests, release library, debug demo and
benchmark assembly checks pass. Both physical leases released.

Logs:

- Pixel 7 full suite: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T03-43-47-230Z-62411.log`
- Pixel 4 XL full suite: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T03-45-14-278Z-63344.log`
- Build/core checks: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T03-51-12-353Z-72573.log`

Source changes from initial extraction are limited to `segmentLayoutDirection()` and its new
regression method; the rebase retains deferred preparation's original-environment handling.
The refreshed Codex review of corrected PR #6 commit `fe87fa4` completed without findings, and
its CI passed. The initial review of PR #7 commit `026e714` had no findings; that review does not
count as review of the rebased head. A fresh review is requested after publishing the update.
