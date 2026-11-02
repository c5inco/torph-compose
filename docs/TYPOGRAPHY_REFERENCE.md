# Independent typography reference

TypographyReferenceTest uses an independent BasicText composable, not morph segments or geometry,
with matching style, constraints, density, direction and resolver. It checks occupied dimensions,
line metadata, baselines, software pixels and actual hardware captures, without fuzzy thresholds.
The two-pixel negative control must fail equality. Captures and metadata are emitted per case.

The corrected corpus contains 51 cases: 43 exact contracts and 8 named shaping/wrapping findings.
The six original bidi-placement findings now require exact pixels. Characterization is not a passing typography equality assertion.
See TYPOGRAPHY_REFERENCE_BASELINE_RESULTS.json for the historical baseline evidence. BidiGeometryTest adds 30 native selection geometry cases and six animated endpoint cases.

First-layout benchmarks use fresh Activities/states in a warm process, workload-specific readiness,
one outer marker per measurement, and separate initial/update phases. Release traces compare
plain Text with AUTO and GRAPHEME. The first/snap branch now logs timings before returning.
The benchmark fixture does not change production rendering behavior.
