# Rendering regression foundation

MorphParityHarness compares two real TextMorph composables on one manually advanced frame clock.
The default and explicitly uncached subjects currently use the same eager renderer; equality
checks cache/renderer regressions, not independent typography correctness. MorphSubject supports
alternative implementations. Seeded input sequences include their replay history in failures.

The foundation covers Unicode across three segmentation modes, clipping/rolling/fading, software
and hardware pixels, interrupted channels, ids, and callback order. Cache pressure, asynchronous
fonts and disposal contracts are added with the corresponding implementation changes.

TypographyReferenceHarness supplies a separate BasicText oracle. It requires exact paragraph
metadata and software/hardware pixels for Exact cases; Characterize cases record named differences
without claiming equality. A deliberate two-pixel displacement must be detected in both captures.
