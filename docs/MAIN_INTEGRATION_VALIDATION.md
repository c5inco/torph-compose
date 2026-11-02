# Main integration audit

Audit entry point: remote main `2cd4a7e3bfc669041e922fbcaae6341490dcb5e7`.
All four PRs were marked merged, but only PR #4 targeted main. PR #5 merged into the
foundation branch, #6 into the cache branch, and #7 into the bidi branch. Main therefore
contained the foundation and still used the uncached eager first-layout renderer.

| PR | Merge target | Merge commit | In main at audit |
| --- | --- | --- | --- |
| #4 | main | 2cd4a7e | yes |
| #5 | issue3/01-test-foundation | 36db2f5 | no |
| #6 | issue3/02-shared-layout-cache | 72afcb4 | no |
| #7 | issue3/03-bidi-geometry | d7db47b | no |

## Integration candidate

Branch `integrate/issue3-stack-into-main` starts at the audited main and merges the three
remaining existing merge commits in order. All four original merge commits are ancestors.
The merge required no conflict resolutions and no new runtime/test code. Before adding this
audit documentation, candidate `b5ac21e752e7481e1491bfb9611bd580f902901c` has exactly the same
entire tree as reviewed head `c5668e6`: `cd1daaf57883a40e1afecff20ab6166a43183b90`.
All 69 source/resource files agree across the candidate build workspaces and the reviewed tree.

A source review rechecked shared-cache environment keys, weak ownership/thread confinement,
stale-font/lifecycle handling, initial deferral, eager fallbacks, paragraph-region drawing,
handoff and the mixed-script bidi correction. No further integration defect was found.
The existing b7965c3 boundary optimization and BenchmarkDeviceState controls remain intact.

## Current validation

- Actual main: build/unit/publishing checks and its full **29-test** instrumentation suite
  passed on Pixel 7/API 37. Its native corpus retains 37 exact matches / 14 named findings.
- Integration candidate: full **56-test** instrumentation suite passed on both Pixel 7/API 37
  and Pixel 4 XL/API 33, zero failures/errors/skips. The fresh initial corpus remains 48 exact
  / 3 named wrapping findings. The 19 mixed-word comparisons and prepared-region, handoff,
  cache/font/lifecycle, geometry, callback and allocation regressions pass.
- Core unit task passes with **18** passing results; release library, debug demo, benchmark
  and instrumentation APK assembly and `publishLocal` pass on the candidate.
- All build/device work used ATC and the build-brief wrapper. The second-device build uses the
  existing source-identical `/tmp/torph-lazy-pixel4` mirror. Both leases released.

Passing main's existing suite does not establish that the missing implementation landed.
The integration PR must target main; merging another stacked base does not complete this step.
Main was not pushed directly and issue #3 was not closed during this audit.

## Fresh first-layout measurement on the candidate

This is a new, post-bidi-review measurement on Pixel 4 XL/API 33, separate from historical
matrices. Five fresh-state samples per case in a warm process, release/R8/full-AOT, eight
distinct labels. Matched eager/deferred controls use the same APK and both include shared cache
and bidi corrections. Plain Text is the control. All 25 samples have exactly one outer initial
marker and the expected 0/8 first-apply count. No individual samples were removed. Device
animation/refresh settings are snapshotted and their restoration verified by BenchmarkDeviceState.

| Mode | Initial measure median, ms | Native layouts in each of five samples |
| --- | ---: | --- |
| Plain Text | 0.667292 | 8 |
| Eager AUTO | 2.948386 | 32 |
| Deferred AUTO | 1.345885 | 8 |
| Eager GRAPHEME | 4.177865 | 45 |
| Deferred GRAPHEME | 1.340417 | 8 |

Initial measurement improves approximately 54% for AUTO and 68% for GRAPHEME in this focused
workload. It still costs more than plain Text. These are measurement wall-time slices, not
whole-frame, cold-process startup or FPS claims. First-update, sixteen-label and current Pixel 7
timing matrices were not rerun here; their older results remain explicitly historical. The
bounded fallback conditions and three wrapping findings are unchanged.

## Evidence and reproduction

[Structured audit results](MAIN_INTEGRATION_RESULTS.json) include all metrics, suite counts,
source hashes and merge ancestry. Raw evidence: `/tmp/torph-main-audit-20261102/`.

Logs:

- Actual main builds/publishing: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T04-13-31-454Z-80077.log`
- Actual main tests: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T04-15-02-616Z-80593.log`
- Candidate builds/publishing: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T04-19-05-630Z-82832.log`
- Candidate Pixel 7 tests: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T04-19-34-719Z-83124.log`
- Candidate Pixel 4 XL tests: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T04-15-01-877Z-80580.log`
- Candidate focused benchmark: `/Users/christop/Library/Application Support/atc/logs/gradle.tasks-2026-11-02T04-22-47-587Z-87978.log`

The temporary `/tmp/torph-issue3-gradle` executable contains `exec build-brief ./gradlew "$@"`.
With an ATC physical-device lease and that wrapper, reproduce the full suite with
`:torph-compose:connectedDebugAndroidTest` and the focused benchmark with
`:benchmark:connectedBenchmarkAndroidTest` plus
`--property android.testInstrumentationRunnerArguments.annotation=des.c5inco.torph.benchmark.DeferredFirstLayoutWorkload`.
