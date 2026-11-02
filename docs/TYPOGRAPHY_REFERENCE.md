# Independent typography reference

TypographyReferenceTest uses an independent BasicText composable, not morph segments or geometry,
with matching style, constraints, density, direction and resolver. It checks occupied dimensions,
line metadata, baselines, software pixels and actual hardware captures, without fuzzy thresholds.
The two-pixel negative control must fail equality. Captures and metadata are emitted per case.

The baseline corpus contains 51 cases: 37 exact contracts and 14 named findings, including six
bidi-placement differences. Characterization is not a passing typography equality assertion.
See TYPOGRAPHY_REFERENCE_BASELINE_RESULTS.json for the historical baseline evidence. Tests will
promote the bidi findings to exact contracts when the placement correction lands.

First-layout benchmarks use fresh Activities/states in a warm process, workload-specific readiness,
one outer marker per measurement, and separate initial/update phases. Release traces compare
plain Text with AUTO and GRAPHEME. The first/snap branch now logs timings before returning.
The benchmark fixture does not change production rendering behavior.
