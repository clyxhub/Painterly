# AGENTS.md

## What this is
**Painterly** — an offline-first Android app that deconstructs a reference image into 7 progressive painting stages using deterministic, local computer vision / image processing. It is a *painting roadmap generator*, not a photo-to-painting generator.

## Repo status
Greenfield. The repository is currently empty — no Gradle files, source, or CI. The first session must scaffold the Android project before feature work. Do not assume any existing architecture.

## Absolute constraints (never violate)
- No generative AI, cloud AI, AI APIs, or remote/network image processing. No image uploads. Reference images stay on-device; the app must work fully offline after install.
- No fake implementations: no TODOs, stubs, placeholder screens, dead buttons, or mock processing shown as real. Every visible feature must genuinely work.
- Do not freeze the UI; run all expensive processing off the main thread. Support safe cancellation and progress reporting.
- Never hold multiple full-resolution bitmaps. Decode sampled, honor EXIF orientation, release unused memory, cache results, and only render full-resolution when exporting.
- Never stretch or unintentionally crop the reference or exports; always preserve aspect ratio.

## Stack (greenfield default — keep consistent across sessions)
- Kotlin + Jetpack Compose (Material 3), single-activity, coroutines/Flow.
- ViewModel + repository separation; processing in a background dispatcher / WorkManager only if truly needed.
- Prefer stable, actively maintained libraries. Do not add dependencies casually.

## Build & CI (user does NOT build locally)
- All APK compilation and build validation must run in **GitHub Actions** and publish a **debug APK artifact**. The user does not run Gradle locally.
- Workflow must: trigger on push/PR, pin Java + Android SDK, use Gradle caching, run the build, and surface failures clearly in logs.
- Inspect any existing workflow before editing; preserve working behavior and only improve where necessary.

## Architecture boundaries (keep these separate)
1. Image import  2. Normalization  3. Downscaled preview  4. Analysis pipeline
5. Stage generation  6. Layer generation  7. Full-res export  8. Caching  9. Memory management

- Recompute only the stages affected by a parameter change; cache the rest.
- Processing previews at an optimized size; generate full-resolution output only for export.

## The 7 stages
1. **Drawing / major shapes** — silhouette + major contours, not noisy edge detection.
2. **Squint-readable block-in** — large paintable value/color masses (3 or 4 values).
3. **Shadow mass** — major dark/connected regions using relative luminance + context, not one global threshold.
4. **Light mass** — coherent illuminated planes, not every bright pixel.
5. **Core shadows / form modelling** — tonal transitions describing turning form; presented as a *suggested* analysis layer, never claimed as true 3D understanding.
6. **Accents & highlights** — selective high-value accents, not every white pixel.
7. **Final refinement map** — optional overlays: edge control (hard/soft/lost), detail priority, texture, colour variation.

## Cumulative layer semantics (critical)
Stages are progressive, not independent images. Stack (top→bottom): Refinement → Accents → Core Shadows → Light Mass → Shadow Mass → Block-In → Drawing → Base Canvas.
Support: single stage, cumulative view, per-layer toggle, opacity, "new info only" vs "full composition after this stage". The UI must make that distinction obvious.

## UI/UX rules
- Artistic language only in the UI. Never expose internals like Gaussian sigma, kernel radius, or gradient thresholds.
- Responsive across small/large phones, foldables, portrait/landscape tablets. Use window-size breakpoints, not device models.
- Phones: large canvas + accessible (bottom/collapsible) controls. Tablets: multi-pane (nav | canvas | adjustments) collapsing to single column when narrow.
- Handle system insets, cutouts, nav/gesture bars, and rotation correctly — nothing off-screen, cropped, overlapped, or requiring horizontal scroll.
- Viewer: pinch-zoom, pan, double-tap zoom, fit-to-screen, aspect preserved. Provide reference-vs-stage comparison (side-by-side / toggle / opacity / swipe).
- Feel like a premium creative tool, not a settings app or filter demo. The artwork is always the visual priority.

## Error handling
Gracefully handle unsupported/corrupt/extremely large images, low memory, cancellation, export failures, and permissions. User-facing messages only — never show stack traces.

## Workflow (strict)
1. **Inspect** repo (structure, build config, CI, deps, UI, any image code) — do not blindly overwrite.
2. **Plan** architecture, pipeline, data flow, memory/caching, algorithms, export, testing, CI impact — before coding.
3. **Implement in increments**, verifying each: import → normalization/memory-safe bitmaps → viewer → Stages 1–7 → cumulative composition → adjustments → caching → export → responsive layouts → review → push for CI.
4. Re-inspect modified files; confirm every feature is real, imports resolve, no unused deps, no broken navigation.

## Verification before declaring done
- Functional: import, orientation, all 7 stage generations, layer toggle/opacity, cumulative render, stage nav, parameter changes, cache reuse, export, cancellation.
- Layout: small/large phone portrait+landscape, small/large tablet portrait+landscape — check cropping, overflow, overlaps, unreachable controls, insets, distortion.
- Confirm offline operation and that no AI/cloud service is contacted.
- Confirm GitHub Actions produces the debug APK.
