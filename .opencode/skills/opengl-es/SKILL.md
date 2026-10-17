---
name: opengl-es
description: >
  Guides Android NDK development with OpenGL ES 3.x (and desktop GL 4.5) for
  the Dibujo- drawing app: EGL context/window setup, render thread lifecycle,
  stroke rendering (VBOs, batching, anti-aliasing, undo layers), error
  checking, GL_KHR_debug, and profiling with Android GPU Inspector / RenderDoc.
  Use when writing, reviewing, or debugging native C++/GL code (gl*, egl*,
  GLSL ES shaders), integrating the native renderer with the Kotlin UI layer,
  or optimizing touch-to-pixel latency. For Vulkan C++ code use the `vulkan`
  skill instead; for the Kotlin/Compose layer use the Compose skills.
license: MIT
metadata:
  author: LuisAlejandro544 (OpenCode)
  version: "1.0.0"
  origin: Original skill written for Dibujo- (no polished dedicated upstream skill exists)
---

# OpenGL ES Skill (Dibujo-)

Dibujo- is an Android drawing app: the UI layer is Kotlin/Compose, the drawing
engine is C++ (NDK) with OpenGL ES. This skill covers the full path
EGL → context → renderer → strokes. Cite spec rules and extension names before
asserting behavior; never invent constants or valid-usage rules. If a source is
needed, verify against the Khronos GLES/GLSL/EGL specs online.

Primary target: **OpenGL ES 3.2** on Android (NDK headers `<GLES3/gl3.h>` +
`<EGL/egl.h>`). Desktop GL 4.5+ only if a desktop target is added; mark all
divergences with a `// DESKTOP-GL:` comment at the divergence site.

## Operating rules

- **Log GPU identity at setup**: when the EGL context is created, log
  `eglQueryString(dpy, EGL_VERSION)`, plus `glGetString(GL_RENDERER)`,
  `glGetString(GL_VERSION)`, and `glGetString(GL_SHADING_LANGUAGE_VERSION)`
  once. Every bug report or diagnosis must include these values.
- **One thread owns GL**: an `EGLContext` can be current on only one thread at
  a time. The render thread (or `GLSurfaceView`'s queue) owns it; the Kotlin UI
  thread must never call `gl*`. Cross-thread work goes through the render
  thread's message queue, never through shared GL state.
- **Explicit error policy**: check `glGetError()` after each batch of setup
  work (context creation, shader compile, resource creation) and escalate
  non-zero results; never inside the per-frame stroke path. In debug builds,
  enable `GL_KHR_debug` via `glDebugMessageCallback` with high-severity
  messages set to break/stall the frame (fail-fast). Treat `GL_CONTEXT_LOST`
  as a full reset: recreate every GL resource; do not reuse stale handles.
- **GL resources follow RAII in C++**: every buffer/texture/FBO/program lives
  in a wrapper type with a destructor that deletes it, created and destroyed on
  the render thread, with one clear owner (`std::unique_ptr<StrokeBatch>`, etc.).
  Delete GL objects before destroying the context they belong to.
- **Hot loop hygiene**: zero allocations and zero synchronizing calls per frame
  in the stroke path. Banned there: `glGetError`, `glFinish`, `glReadPixels`,
  `glMapBufferRange` without `GL_MAP_UNSYNCHRONIZED_BIT`, shader recompiles.
  See `references/drawing-app-patterns.md`.
- **Shaders**: one shading language version for the whole project
  (`#version 320 es` if ES 3.2 is guaranteed; otherwise `#version 300 es`).
  Compile and link at init time with mandatory error logging
  (`GL_COMPILE_STATUS` / `GL_LINK_STATUS` + `glGetShaderInfoLog`). At runtime,
  a program failing to validate is a launch failure, not a per-frame retry.
- **Kotlin↔C++ interface**: pass stroke properties (color, width, tool, blend)
  across JNI as plain structs/scalars, never as Java objects retained on the
  native side. Touch input must stay timestamped (`MotionEvent.getEventTime`)
  end-to-end so pressure/prediction logic can reason about latency. See
  `references/android-egl-and-window.md`.

## Task workflow

### Setting up a new native render surface
1. Choose the surface host: plain `SurfaceView` + manual EGL (full control of
   the render loop — the default for Dibujo- because pointer/stroke rendering
   needs a controlled frame schedule) or `GLSurfaceView` (simpler, less control
   of the GL thread loop).
2. EGL config: request RGBA 8888, stencil 0, `EGL_RECORDABLE_ANDROID` (needed
   for screen capture / MediaProjection and system text pre-rendering), and a
   surfaceless context when rendering only to FBOs.
3. Full step-by-step code and parameters:
   `references/android-egl-and-window.md`.

### Writing or reviewing stroke code
1. Route drawing-engine topics through
   `references/drawing-app-patterns.md`: vertex batching, static vs dynamic
   VBOs, half-float attributes, anti-aliasing strategy (multisample
   renderbuffer vs analytic AA in the fragment shader), premultiplied alpha,
   undo/redo as cached FBO layers, brush stamp caching, touch-to-pixel latency.
2. Enforce the correctness checklist below; in review, flag anything that
   synchronizes or allocates inside the per-frame path.

### Debugging and profiling
1. Debug builds: `GL_KHR_debug` active; correlate message IDs with the inline
   checklists in `references/debugging-profiling.md`.
2. RenderDoc for frame captures on devices/emulators without DRM surfaces;
   Android GPU Inspector (AGI) for system traces and per-frame analysis.
   Concrete reproduction and triage pipelines:
   `references/debugging-profiling.md`.

### Integrating with the Kotlin/Compose layer
- Compose and the GL canvas are sibling layers. Compose must not render over
  the `SurfaceView` except for floating controls drawn edge-to-edge on top.
- Ownership split: Kotlin owns input, lifecycle and user settings; C++ owns
  render state, GL objects and frame scheduling.

## Correctness checklist (hard rules)

- [ ] Exactly one thread calls `gl*`; the EGL context is current there only
- [ ] Every GL object is created and deleted on the same thread, deleted before
      its context is destroyed, with exactly one owner
- [ ] `glGetError()` checked after setup/shader compiles; never in the frame loop
- [ ] Every shader compile/link checks status and logs errors before use
- [ ] Zero allocations and zero synchronizing calls per frame in the stroke path
- [ ] Undo/export never calls `glReadPixels`/`glFinish` synchronously in a
      frame; it uses cached FBO layers or async PBO readback off the frame path
- [ ] One alpha rule everywhere: premultiplied alpha agreed across GL and
      Compose, or strokes composite incorrectly (dark/ghosting) against the UI
- [ ] Surface size changes rebuild the render target with the context intact;
      sizes come from the real window metrics, converting px↔dp consistently
- [ ] `GL_CONTEXT_LOST` and app-background/foreground transitions trigger a
      full recreate of GL resources (a missing path here is a hard crash after
      a GPU reset)
- [ ] Release builds ship no `GL_KHR_debug` hooks and no per-frame logging

## References

- `references/android-egl-and-window.md` — Android + NDK specifics: EGL init
  order, config selection, SurfaceView lifecycle hooks, surfaceless contexts,
  GL_CONTEXT_LOST handling, the JNI bridge, dp/px conventions.
- `references/drawing-app-patterns.md` — The stroke engine: batching, dynamic
  VBO ring buffers, half-float, MSAA vs analytic AA, undo layers, brush
  caching, touch-to-pixel latency, frame budget.
- `references/debugging-profiling.md` — `GL_KHR_debug`, RenderDoc, Android GPU
  Inspector, capture recipes for common Dibujo- symptoms (jank during stroke,
  wrong compositing, crash on background/foreground).
