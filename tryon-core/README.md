# tryon-core

Reusable Android library for live virtual try-on of GLB accessories (Cap / Glasses / Earrings / Locket)
using MediaPipe Face Landmarker + Filament + CameraX.

## Modules

- `domain/` — pure Kotlin (no Android/Filament imports): accessory types, `FaceFrame`, placement strategies.
- `data/` — OkHttp GLB download + validation + DataStore calibration persistence.
- `tracking/` — `FaceTracker` interface + `MediaPipeFaceTracker`.
- `render/` — Filament GLB renderer + `ModelBoundsNormalizer`.
- `ui/` — Compose form + live try-on screens.

## Swapping FaceTracker (e.g. ARCore Augmented Faces later)

`FaceTracker` is an interface:

```kotlin
interface FaceTracker {
    fun start(lifecycleOwner: LifecycleOwner, previewView: PreviewView, onFrame: (FaceFrame?) -> Unit)
    fun stop()
}
```

To use ARCore Augmented Faces instead of MediaPipe:

1. Implement `FaceTracker` (e.g. `ArCoreFaceTracker`) that maps ARCore face mesh / pose into the same
   `FaceFrame` (landmarks list + 4x4 transform + `faceDetected`).
2. Construct that implementation in `TryOnViewModel.createTracker()` (or inject via a factory).
3. Placement strategies and Filament rendering stay unchanged — they only consume `FaceFrame` / `Placement`.

Keep `domain/` free of ARCore / MediaPipe imports so unit tests remain JVM-only.

## Flutter PlatformView plugin (planned wrap)

Expose these platform-channel methods when wrapping this module:

| Method | Direction | Payload | Notes |
|--------|-----------|---------|-------|
| `submitTryOn` | Flutter → Native | `{ url: String, type: String }` | Triggers download + opens try-on session |
| `onModelLoaded` | Native → Flutter | `{ path: String }` | Fired after GLB validated & cached |
| `onError` | Native → Flutter | `{ message: String }` | Download / camera / render failures |
| `disposeTryOn` | Flutter → Native | `{}` | Releases camera + Filament engine |

Embed `TryOnScreen` (or a non-Compose SurfaceView host) inside a Flutter `PlatformView` /
`AndroidView`. Prefer calling into `tryon-core` APIs rather than the demo `:app` module.

## Known limitations

- **Earrings / Locket placement is a procedural approximation.** MediaPipe’s frontal face mesh has no
  true ear or neck landmarks. Earrings are offset from jaw corners (234 / 454); lockets are offset
  below the chin (152). Calibration sliders exist to tune these per device / model.
- Arbitrary community/AI GLB files are re-centered and unit-normalized via `ModelBoundsNormalizer`
  on every load — do not skip this.
- Requires a front-facing camera (`android.hardware.camera.any`); devices without one show an
  unsupported-device message.

## Known pitfalls (glasses / Filament)

- **Analysis landmarks must be preview-upright.** Rotating only via MediaPipe
  `ImageProcessingOptions` (without rotating the CameraX bitmap) left landmarks in sideways
  buffer space: eye corners shared X and spanned Y → `rollDeg≈90°` and glasses drawn vertical
  on a frontal face. Rotate the analysis bitmap to upright before `detectAsync` (official sample).
  Also apply [LandmarkOrientation.ensureEyeLineHorizontal] as a safety net, and
  [LandmarkOrientation.effectiveBitmapRotationDegrees] to avoid missed/double buffer rotates.
- **Never `setTransform` on a glTF node for Khronos Sunglasses.** Those roots bake ≈RX(90°) in
  their local matrices. Overwriting them → vertical temples, overscale, frames near the chin.
  Always invent an empty identity pivot in `bindUnifiedRoot` and apply placement only there.
- **Multi-root GLB:** Prefer parenting the whole asset hierarchy under that empty pivot. Do not
  pick `instance.root` / a temple mesh as the transform target.
- **Parent cycles** in `bindUnifiedRoot` → Filament SIGSEGV / stack overflow on Submit.
- **No RX(−90°) when AABB is already deep (sizeZ > sizeY).** That tipped correct −Z temples
  into the face plane / above the head. Only bake RX(−90°) when `sizeY > sizeZ` (local +Y
  temples / hanging arms on the selfie).
- **Glasses orientation:** full eye-line **roll** + **2D nose-tip yaw**; pitch default off
  (Z-pitch swung temples into the face plane).
- **No FILL_CENTER analysis→view crop** with mismatched buffers in `FaceCoordMapper` (corner drift).
- **No double EMA** (landmark + placement) for glasses — keep passthrough while face is detected.
- `mesh=478/478` is the full iris mesh, not a partial-tracking bug.

## Assets

Place MediaPipe’s Face Landmarker model at:

```
tryon-core/src/main/assets/face_landmarker.task
```

Download:

```
https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task
```
