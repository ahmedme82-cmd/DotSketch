# Contributing

1. Fork and branch from `main`.
2. Run `./gradlew testDebugUnitTest` before opening a PR.
3. Keep image processing and layout code free of Android imports so it stays unit-testable (`core/ContourTracer`, `DotSampler`, `ContourFilter`, `PageLayout`).
