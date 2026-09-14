# Painterly

An offline-first Android app that deconstructs a reference image into **seven progressive
painting stages** using deterministic, on-device computer vision. It is a *painting roadmap
generator*, not a photo-to-painting filter.

## What it does

1. **Drawing** — silhouette and major shapes
2. **Block-In** — large, squint-readable value masses
3. **Shadow Mass** — the major connected darks
4. **Light Mass** — the illuminated planes
5. **Core Shadows** — suggested turning-form transitions
6. **Accents** — selective highlights
7. **Refinement** — edge control, detail priority, texture, colour shift overlays

Stages are cumulative: you can view a single stage's *new* information or the *full
composition* after that stage, toggle individual layers, and adjust layer opacity.

## Privacy / offline

- No generative AI, no cloud AI, no AI APIs, no image uploads.
- No `INTERNET` permission is declared.
- Reference images never leave the device; the app works fully offline after install.

## Build

The user does not build locally. APK compilation and validation run in **GitHub Actions**
(`.github/workflows/android.yml`), which produces a debug APK artifact on every push/PR.

## Stack

Kotlin · Jetpack Compose (Material 3) · coroutines/Flow · single activity · pure-Kotlin
image-processing pipeline.
