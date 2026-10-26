# DotSketch

Android app that turns a photo or drawing into a dotted outline you can print and trace with a pen
(handwriting practice, drawing practice, kids' activities).

- Pick an image or take a photo
- Adjust threshold, speck filter, dot spacing and dot size (all in **millimetres**)
- Choose A4 / A3 / US Letter, portrait or landscape, margins, scale, centering
- Export a true-scale **PDF** or a **PNG** and share it (print from the share sheet)

## How it works

```
Bitmap -> grayscale -> optional 3x3 blur -> threshold -> marching-squares contours
       -> drop tiny loops -> equal-arc-length dots -> mm layout on page -> PDF / PNG
```

No OpenCV: contour tracing is plain Kotlin (`core/ContourTracer.kt`), so the build has no native dependencies.
Everything under `core/` except `BitmapLoader`, `ImagePipeline`, `PdfExporter`, `PngExporter` and
`PreferencesRepository` is Android-free and covered by JVM unit tests.

## Build

Requires JDK 17 and the Android SDK (platform 34).

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions builds the debug APK on every push (Actions tab -> latest run -> Artifacts).
Pushing a tag like `v1.0.0` publishes a release APK.

## Release signing (optional)

Add these repository secrets to sign release builds: `ANDROID_KEYSTORE_BASE64`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.
Without them the release APK is debug-signed (fine for sideloading, not for the Play Store).

## Status

Source and unit tests written and the core algorithms sanity-checked, but this project had not been
compiled on a device or in CI at the time of writing. First CI run is the real test.

## License

MIT
