# Android + NDK: EGL and window management

Concrete guidance for the Dibujo- native renderer.
Target: OpenGL ES 3.2 on Android (NDK `libEGL` / `libGLESv3`).

## 1. Startup order (do exactly this order)

1. Obtain an `ANativeWindow` from the Kotlin `Surface` via
   `ANativeWindow_fromSurface()` on the render thread.
2. `eglGetDisplay(EGL_DEFAULT_DISPLAY)` → `eglInitialize`.
3. `eglChooseConfig` with an explicit `EGLint[]` attribute list, logged in
   debug builds.
4. `eglCreateContext`, sharing with the app's share-group context only if
   multiple contexts are truly needed (otherwise exactly one context, one
   thread).
5. `eglCreateWindowSurface(display, config, window, NULL)` — or no surface at
   all when the renderer only targets FBOs (surfaceless context).
6. `eglMakeCurrent` on the render thread, then log the GPU identity strings
   (see SKILL.md operating rules), then create resources.
7. Signal back to Kotlin that drawing may start only after a successful setup.

Never interleave steps or call `eglMakeCurrent` concurrently from two
threads; serialize setup/teardown through the render thread queue.

## 2. EGL config selection

- Color buffer RGBA8888; depth 0 or 24 (pick explicitly and log it); stencil 0.
- Attach `EGL_RECORDABLE_ANDROID` (with `EGL_NATIVE_RENDERABLE false`) —
  required for MediaProjection-based screen recording/capture.
- Match the window format to the config: compare with
  `ANativeWindow_getFormat(window)`; on a mismatch some drivers fail
  `eglCreateWindowSurface` with `EGL_BAD_MATCH`. Fix by adjusting the window
  format or re-picking the config; never ignore it.
- For background/export rendering that does not need a window, create a
  surfaceless context (`eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE,
  ctx)`). If `EGL_KHR_surfaceless_context` is not supported, fall back to a
  1×1 pbuffer and log the fallback.

## 3. SurfaceView lifecycle rules

- `surfaceCreated` → it posts "window ready" to the render thread; never
  create EGL state on the UI thread.
- `surfaceChanged` → new width/height in pixels; the render thread rebuilds
  render targets (FBOs, scratch textures, tile cache) with the context
  intact. This is the one place allowed to reallocate per-frame buffers.
- `surfaceDestroyed` → stop rendering and wait for render-thread
  acknowledgement before Kotlin releases the Surface. If the Surface is
  destroyed first, `eglSwapBuffers` fails with `EGL_BAD_NATIVE_WINDOW`;
  treat it as teardown, not as a retryable error.
- On Activity pause: hold the frame, then tear down (or fully recreate on
  resume) according to the device policy decision — never keep rendering.

## 4. GL_CONTEXT_LOST and background/foreground

- If `eglMakeCurrent` returns `EGL_FALSE` with `eglGetError()` ==
  `EGL_CONTEXT_LOST` (or the context is otherwise lost), ALL GL objects are
  invalid. Rebuild everything: programs, textures, buffers, FBOs.
- On resume, re-run the whole "Startup order" section. Canvas content must be
  recoverable from the document model (stroke list) or CPU-side caches, not
  from GPU-side caches, which may vanish with the context.

## 5. JNI bridge rules (Kotlin ↔ C++)

- Stroke properties cross as flat data: `nativeSetBrush(float r, float g,
  float b, float a, float widthPx, float diameterPx, int toolId)`-style calls
  or one packed struct; keep no retained Java objects on the native side.
- Input: forward from Kotlin with `MotionEvent.getEventTime()` (never
  `System.currentTimeMillis()`), pointer ids and pressure. Never drop the
  timestamp — it feeds latency measurement and motion prediction.
- The C++ side must not call back into Kotlin mid-frame; responses/requests
  are queued and delivered between frames.

## 6. Coordinate conventions (px vs dp)

- Store stroke width, brush radius and offsets in **pixels of the canvas
  bitmap space**, so zoom/pan keeps a stable coordinate space.
- Resolve dp→px in Kotlin once per configuration change and pass floats
  across JNI; avoid `TypedValue.applyDimension` inside the render thread.

## 7. Anti-patterns (seen in real crashes)

- `eglMakeCurrent` ping-ponged between the GL thread and UI thread → random
  `EGL_BAD_ALLOC` and corrupted state.
- Keeping the EGL surface alive after `surfaceDestroyed` while still calling
  `eglSwapBuffers` (survives a few frames, then hard crash).
- On emulator config failure (`EGL_BAD_CONFIG` for RGBA16F), silently falling
  back to RGBA8 without logging it — every fallback must appear in the
  setup log so bug reports include the real render path.
- Using `GLSurfaceView` continuous render mode for a stroke-based app; the
  canvas should render on demand (see `drawing-app-patterns.md`).
