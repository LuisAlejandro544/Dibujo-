# Debugging and profiling OpenGL ES on Android

Recipes for finding and fixing native-GL issues in Dibujo-. GPU identity
strings (SKILL.md operating rules) are always the first thing to ask for/provide.

## 1. `GL_KHR_debug` (debug builds only)

- Enable once at init, on the render thread:
  ```c
  PFNGLDEBUGMESSAGECALLBACKKHRPROC glDebugMessageCallback =
      (PFNGLDEBUGMESSAGECALLBACKKHRPROC)eglGetProcAddress("glDebugMessageCallback");
  glEnable(GL_DEBUG_OUTPUT);
  glEnable(GL_DEBUG_OUTPUT_SYNCHRONOUS); // debug builds only — allows break-on-error
  glDebugMessageCallback(khrdebugCb, nullptr);
  glDebugMessageControl(GL_DONT_CARE, GL_DONT_CARE, GL_DONT_CARE, 0, NULL, GL_TRUE);
  ```
- Severity policy: `GL_DEBUG_SEVERITY_HIGH` → abort/dump-renderer-log
  (fail-fast), `GL_DEBUG_SEVERITY_MEDIUM` → warning in the in-app dev
  overlay, `LOW`/`NOTIFICATION` → visible in debug only, off in release.
- Synchronous mode stalls on every violation; keep it in debug builds only,
  never ship it.
- Prefix every message with the GPU identity (from setup logs) when filing
  issues — driver-specific behavior is the #1 source of device-only bugs.

## 2. RenderDoc (frame capture)

- Use for: geometry-level debugging (why a stroke quad has wrong UVs →
  texture sampling; wrong blend test; bad AA feather), shader inspection,
  draw-call ordering, per-draw timing on the capture frame.
- Caveats: attach to a debug build with non-DRM-protected surfaces (the
  drawing canvas qualifies); captured frames freeze the app while inspected.
- Reproduction recipe: (1) `RenderDoc` hooks the app on launch, (2) draw the
  suspected stroke with the in-app "capture next frame" marker overlay on,
  (3) capture, (4) compare pipeline-state at the suspect draw vs the
  documented expected pipeline in `drawing-app-patterns.md` §1
  (Frame pipeline), (5) bake the fix's delta into the SKILL/test checklist.

## 3. Android GPU Inspector (AGI)

- Use for: long-running jank (every-frame regressions), GPU vs CPU
  attribution, driver-level render-pass timing, surface/composite cost at the
  system compositor level.
- Reproduction recipe: (1) AGI+systrace-style capture during a scripted
  drawing session (gesture recorder, ~20 s), (2) in the trace, open the
  drawing session timeline, (3) classify the jank: GPU-bound (fragment
  stalls, render pass duration > 8 ms), CPU-bound (`glBufferData`/upload
  spikes), or scheduling-bound (render thread starved; look for the render
  thread's CPU slice end + eglSwapBuffers gap).
- AGI captures include the same GL calls; cross-check with RenderDoc frames
  when the cause is per-draw rather than global.

## 4. Standing symptom table (Dibujo-)

| Symptom | First checks | Likely cause |
|---|---|---|
| Stroke darker at intersections | alpha rule across GL+Compose; `glBlendFunc` documented in `drawing-app-patterns.md` §2 | unpremultiplied texture in a premultiplied pipeline |
| Jank during fast swipes | tile-dirty model; per-frame allocations | stale all-tiles re-render, or per-frame upload |
| First frame after resume wrong | full GL-recreate path (`android-egl-and-window.md` §4) | stale GL objects reused after context loss |
| Strokes vanish past N points | growable ring buffer capacity | static vertex array never rebuilt |
| Crash when backgrounding during stroke | teardown acknowledgment (`android-egl-and-window.md` §3) | Surface released with GL state still current |
| Recording / screen-share shows nothing | `EGL_RECORDABLE_ANDROID` flag (`android-egl-and-window.md` §2) | config missed the recordable attribute |
| Emulator works, device janks | GPU identity in logs; AGI capture | device-specific driver path; check blend/fence usage |

## 5. glGetError budget policy

- Allow-list (and nowhere else): init, foreground/background transitions,
  surfaceChanged rebuilds, export/share paths (user-triggered), debug
  overlays.
- During the release branch of these paths, treat `GL_NO_ERROR` as the only
  pass state; emit one log line with GPU identity when failing.
- Never gate release behavior on `glGetError` — it's a diagnostic, not a
  flow-control mechanism; keep business logic off the error state.

## 6. Memory (GPU side)

- Track a conservative GPU-memory ledger in the dev overlay (sum of texture
  allocations + buffers, with a documented per-item formula).
- When the ledger exceeds the documented budget (per
  `drawing-app-patterns.md` §3), free back in priority order: oldest undo
  tile snapshots → brush atlases not in use → cached preview composites.
- OOM-style GPU failures surface as `EGL_BAD_ALLOC`/`GL_OUT_OF_MEMORY`;
  handle as "renderer must shed cache" using the same priority order, not as
  a crash.
