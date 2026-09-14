# Painterly

An offline-first Android app that turns a reference photograph into a **seven-stage
progressive painting workflow**. It is a *painting roadmap generator*, not a photo filter and
not a photo editor. At every step it answers one question: **“What do I paint next?”**

## The seven stages

1. **Draw** — a simplified drawing guide: silhouette, large shapes, structural boundaries.
2. **Block-In** — large squint-readable value masses, in **3 or 4 values**.
3. **Shadows** — the major connected dark masses, judged against local context.
4. **Lights** — coherent illuminated planes, not every bright pixel.
5. **Form** — suggested turning-form tonal transitions.
6. **Accents** — selective highlights and small high-contrast detail.
7. **Refine** — optional **Edges** (hard / soft / lost) and **Detail** priority views.

Every stage has two view modes:

- **ADD** — only the new information introduced by the current stage.
- **COMPLETE** — everything cumulatively up to the current stage.

Layers share one transformed coordinate system, so zoom, pan, and the optional drawing grid
stay perfectly aligned with no drift.

## Privacy / offline

- No generative AI, no cloud AI, no AI APIs, no image uploads, no remote processing.
- No `INTERNET` permission is declared.
- Reference images never leave the device; the app works fully offline after install.
- All processing is deterministic on-device image processing.

## Build

The user does not build locally. APK compilation and validation run in **GitHub Actions**
(`.github/workflows/android.yml`), which produces a debug APK artifact on every push/PR.

## Stack

Kotlin · Jetpack Compose (Material 3) · coroutines/Flow · single activity · pure-Kotlin
image-processing pipeline under `com.painterly.app.imaging`.
