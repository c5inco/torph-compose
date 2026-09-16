# Status

Where the implementation stands against its original implementation plan, which organized the work
into milestones M1 through M5.

- M1 core, M2 render, M3 parity, M4 scripts/perf: implemented.
- M5: `maven-publish` is configured for both libraries (`publishLocal` for `~/.m2`,
  `publishLocalRepo` for `build/maven-repo`); Maven Central signing/credentials and Roborazzi screenshot tests are not set up yet. Macrobenchmark and the
  draw-allocation guard are in place (see [BENCHMARKS.md](BENCHMARKS.md)).
- Number rolling is an odometer strip: a changed digit scrolls through every intermediate digit
  (wrapping 9 → 0) inside its clipped cell, up when the number grows and down when it shrinks. A
  digit interrupted mid-roll continues from where its strip is, with velocity preserved.
- `AnnotatedString`, selection and glyph-level (`drawGlyphs`) animation are out of scope, as planned.
