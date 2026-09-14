# AGENTS.md

## What this is
**Painterly** — an offline-first Android app for painters. It takes a reference photograph and
deconstructs it into a **seven-stage progressive painting workflow**. The app is a *painting
roadmap generator*, not a photo-to-painting filter and not a photo editor.

The app answers one question at every step: **"What do I paint next?"**

## Repo status
The Gradle scaffold, Android manifest, resources and GitHub Actions workflow exist. All Kotlin
source must be written under `app/src/main/java/com/painterly/app/`. Do not assume source exists.

## Absolute constraints (never violate)
- **No AI of any kind.** No generative AI, AI image generation, cloud AI, AI APIs, ChatGPT APIs,
  remote image processing, upload servers, or mandatory internet. The app works fully offline.
  There is deliberately **no `INTERNET` permission**. Reference images never leave the device.
- **All processing is local and deterministic** — conventional image processing / computer vision.
- **No fake implementations.** No TODOs-as-features, stubs, placeholder screens, dead buttons, or
  mock processing presented as real. Every visible control must genuinely change real output.
- **Never freeze the UI.** All heavy processing runs off the main thread with coroutines, reports
  progress, and supports cancellation.
- **Memory-safe bitmaps.** Decode sampled, honor EXIF orientation, recycle aggressively, cache
  results, never hold multiple full-resolution bitmaps. Work at a bounded preview resolution and
  only render the working resolution for export.
- **Never stretch or unintentionally crop** the reference or exports. Aspect ratio is sacred.
- **Artistic language only in the UI.** Never expose Gaussian sigma, kernel radius, thresholds,
  Laplacian, "posterize", or any computer-vision/developer term. Users are painters.

## Stack
- Kotlin + Jetpack Compose (Material 3), single activity, coroutines/Flow.
- ViewModel + repository separation. Pure-Kotlin/Android-graphics image pipeline.
- Stable, actively maintained libraries only. Do not add dependencies casually.
- No navigation-compose: navigation is a tiny sealed-route state machine in the activity.

## Build & CI (the user does NOT build locally)
- All compilation and validation run in **GitHub Actions** (`.github/workflows/android.yml`) and
  publish a **debug APK artifact**.
- Workflow triggers on push/PR/workflow_dispatch, pins JDK 17 + Android SDK 34, caches Gradle,
  runs unit tests, assembles the debug APK, and surfaces failures in logs.
- Inspect the workflow before editing; preserve working behavior and only improve it.

## Architecture boundaries (keep these separate)
1. Import (`ProjectRepository.createProject`) 2. Normalization/EXIF (`BitmapLoader`)
3. Bounded preview 4. Analysis pipeline (`StageGenerator` shared intermediates)
5. Stage generation 6. Layer outputs 7. Export (`Exporter`) 8. Caching (`ProjectRepository` cache)
9. Memory management.

- Recompute only the stages affected by a parameter change; reuse the rest.
- One normalized coordinate system for every layer: layers are separate transparent bitmaps that
  are drawn into the **same transformed container**, so they zoom/pan in perfect lockstep with
  zero drift. Never bake independent transforms per layer.

## The 7 stages
1. **DRAW** — simplified drawing guide: major silhouette, large shapes, structural boundaries and
   overlaps. NOT noisy edge detection. One control: Simpler ↔ More detailed.
2. **BLOCK-IN** — large squint-readable value/colour masses; choose **3 VALUES** or **4 VALUES**.
   Must not be a cheap posterization: smooth edge-preserving first, cluster colour, then clean
   the regions with a majority filter.
3. **SHADOWS** — major connected dark masses using relative luminance **and context** (local
   comparison), not one global threshold. Control: Less shadow ↔ More shadow.
4. **LIGHTS** — coherent illuminated planes, not every bright pixel. Control: Simpler ↔ More complete.
5. **FORM** — suggested turning-form tonal transitions (a suggestion, never claimed as true 3D
   understanding). Control: Less form ↔ More form.
6. **ACCENTS** — selective highlights, sharp accents and small high-contrast detail: less
   information than earlier stages. Control: Fewer accents ↔ More accents.
7. **REFINE** — optional refinement views, never a fake finished painting:
   **EDGES** (hard / soft / lost emphasis) and **DETAIL** (higher / medium / lower refinement
   priority).

## ADD vs COMPLETE (core interaction — never hide it in a menu)
Every stage has two modes:
- **ADD** — show only the new information introduced by the current stage ("what do I paint now?").
- **COMPLETE** — show everything cumulatively up to the current stage ("what should it look like now?").

Layer stack (bottom → top): Base Canvas → DRAW → BLOCK-IN → SHADOWS → LIGHTS → FORM → ACCENTS → REFINE.

## Viewer & workspace rules
- The artwork dominates the screen (~70–80% of attention). Controls must not bury it.
- Pinch-zoom, pan, double-tap zoom, fit/reset. Correct for portrait/landscape/square/very tall/
  very wide images. No accidental crop, no stretch, always returnable to Fit.
- Reference view: **Reference / Guide / Compare**, with a simple opacity slider in Compare.
- Optional grid: on/off, rows, columns, opacity, labels. It scales and pans with the image and
  never distorts it.
- Responsive: use width breakpoints (phones = canvas + collapsible bottom controls; tablets =
  nav | canvas | adjustments). Handle system insets/cutouts, rotation, and never let anything go
  off-screen, overlap, or require horizontal scrolling.
- Feel like a premium creative tool, not a settings app or filter demo.

## Persistence
- Projects save locally with no account/login. Reference + thumbnail + stage cache live in the
  app's internal storage under `files/projects/<id>/`. Metadata is JSON.
- Stage bitmaps are cached to PNG keyed by a settings signature; unchanged stages are reused.
  Recompute only what changed.

## Error handling
Gracefully handle unsupported/corrupt/extremely large images, low memory, cancellation, export
failures, and storage permissions. User-facing messages only — never show stack traces.

## Workflow (strict)
1. **Inspect** the repo (structure, build config, CI, deps, UI, image code) — never blindly overwrite.
2. **Plan** architecture, pipeline, data flow, memory/caching, export before coding.
3. **Implement in increments**, verifying each: import → normalization → viewer → stages 1–7 →
   ADD/COMPLETE → compare/grid → responsive layouts → export → review.
4. Re-inspect modified files: confirm every feature is real, imports resolve, no unused deps, no
   broken navigation.

## Verification before declaring done
- Functional: import + orientation, all 7 stage generations, ADD/COMPLETE, stage navigation,
  parameter changes, reference/compare, grid, cache reuse, export, cancellation.
- Layout: small/large phone portrait+landscape, tablet portrait+landscape — check cropping,
  overflow, overlap, unreachable controls, insets, distortion.
- Confirm fully offline operation (no `INTERNET` permission) and no AI/cloud contact.
- Confirm GitHub Actions produces the debug APK.
