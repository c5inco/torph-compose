# Contributing

Thanks for taking a look. This is a small personal project, so please open an
issue before starting anything large.

## Building

```bash
./gradlew :torph-core:test          # pure-Kotlin tests, no device or Android SDK needed
./gradlew :demo:installDebug        # the demo app
./gradlew publishLocal              # both libraries into ~/.m2, to try them in another project
```

The full task list, the prerequisites, and the ways to consume a source build are in the README
([Building from source](README.md#building-from-source) and
[Use it in your project](README.md#use-it-in-your-project)).

`:torph-compose` has instrumentation tests that need a running emulator or a
connected device:

```bash
./gradlew :torph-compose:connectedDebugAndroidTest
```

Benchmarks live in `:benchmark`. See [docs/BENCHMARKS.md](docs/BENCHMARKS.md),
including the notes on running against a physical device.

## Code style

- `kotlin.code.style=official`.
- The library modules use Kotlin's `explicitApi()` mode, so every public
  declaration needs an explicit visibility modifier and return type, plus KDoc
  explaining what it is for.
- Keep animation state reads out of composition. They belong in the layout or
  draw phase. `DrawAllocationTest` will fail if the draw path starts
  allocating per frame.

## Behavior changes

This library is a port, and its value is that torph configurations carry over
directly. If a change alters observable behavior, say in the pull request how
it lines up with upstream torph. Deliberate divergence is fine, but it should
be a decision rather than a side effect.

## Tests

- Logic changes in `:torph-core` need unit tests. The diff has property-style
  tests over random strings; extend them rather than adding one-off cases where
  that fits.
- Rendering changes should come with a note on what you verified on a device,
  since not everything is covered by automated tests.
