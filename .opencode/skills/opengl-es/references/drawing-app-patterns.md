# Drawing-app patterns: the stroke engine

Reference for implementing and reviewing the Dibujo- stroke renderer.
Frame budget target: 8 ms per frame on a mid-range phone (Adreno 6xx-class)
to leave headroom for system compositing.

## 1. Stroke rendering core

### Geometry model

- A stroke = polyline of control points (x, y, pressure, t) plus metadata
  (tool id, premultiplied RGBA color, blend mode, brush params).
- Convert to triangle-strip quad geometry at init/batch time; per-vertex
  width gives the variable-width (pressure) brush.
- Use half-float (`GL_HALF_FLOAT`) for geometry attributes: screens and
  hand-drawn ranges fit half precision, and it halves vertex bandwidth.
- Cache computed quad geometry per completed stroke; keep working geometry in
  a persistent growable buffer so a long swipe never reallocates mid-frame.

### Batching rules

- Batch all strokes of the same tool + blend into ONE draw call, using
  vertex ranges inside a shared VBO/IBO pair. Per-stroke variance (color,
  alpha) goes into vertex attributes, not into per-stroke uniforms that force
  separate draws.
- Render the active stroke to a "current stroke" scratch texture on a tile
  grid; only tiles that the stroke grew into are re-rendered (the tile-dirty
  model). Completed strokes commit to persistent tile layers (see §3 Undo).

### Frame pipeline (fixed order)

1. Drain the input queue (all pending touch events) into work, once.
2. Clear/reuse only dirty tiles of the "current stroke" scratch texture.
3. Draw committed content (tile-grid draw or single composited texture).
4. Draw the active stroke on its dirty tiles (small, cheap).
5. Compose layers + apply zoom/pan to the final canvas quad.
6. `eglSwapBuffers` (blocks on vsync; never artificial fps limiting).

### Render-on-demand

- A stroke-based app must render exactly one frame per drained input batch
  (plus one per animation tick that actually changes pixels); never
  `RENDERMODE_CONTINUOUSLY` for the canvas.
- With a plain `SurfaceView` + EGL: schedule the next frame with
  `Choreographer`-aligned timing on the render thread; one frame per
  input batch maximum. Never render the same input twice.

## 2. Anti-aliasing

- Preferred: analytic AA in the fragment shader (signed distance from the
  quad edge, feather width proportional to stroke width). Quality-controlled,
  no extra memory, works with variable widths.
- If MSAA is needed for a specific pass (e.g., geometric preview shapes),
  use a 4x multisample renderbuffer with `glBlitFramebuffer` resolve. MSAA is
  NOT suitable as the main stroke AA: blending and MSAA samples don't
  accumulate alpha the way strokes require.
- Analytic AA requires the premultiplied alpha rule
  (`glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)`), documented once and used
  across the whole GL + Compose pipeline ("one alpha rule").

## 3. Undo/redo and layers

- Model: committed content = stack of tile-grid layers, one per committed
  stroke or small stroke group. Undo = pop the newest tile diff and
  re-render the touched tiles only.
- Alternatively snapshot the stroke list every N strokes and re-composite
  from scratch — simpler code, higher peak cost; document the choice.
- GPU memory ledger: hold only the committed composite + tiles near undo
  points. When the budget (tracked via `debugging-profiling.md` §6) is
  exceeded, regenerate tiles from the stroke list; never let tile caches
  grow unbounded.
- Never use `glReadPixels`-style CPU copies as the undo mechanism (synchronous
  slow path); FBO texture layers are the cache; CPU copies are only for export.

## 4. Brush caching

- Stamp-based brushes (pencil texture, airbrush): bake a stamp texture atlas
  once at init; at stroke time stamp along the polyline with the tool's
  spacing rule. Per-stroke state is only the distance accumulator.
- Geometry-based brushes (pen strokes): generate AA quads directly, no atlas.
- Both must respect the zero-allocations-per-frame rule: preallocate working
  buffers for the documented worst case per frame, then grow persistently if
  a single stroke exceeds them.

## 5. Touch-to-pixel latency

Track the full path or it drifts: touch event → Kotlin (JNI) → render thread
queue → GL frame → `eglSwapBuffers` → scan-out.
- Queue depth per layer ≤ 1 input batch; if backing up, drop the oldest batch
  and resample along the segment (stroke continuity > every raw point).
- Measure with an in-app overlay (Kotlin records event time, C++ renders the
  latency indicator on the frame) — debug builds only.
- Optional: extrapolative prediction from the last two points, rendered as a
  provisional segment and corrected by the next event batch.

## 6. Common Dibujo- review findings

- Strokes darker near intersections or seams → the "one alpha rule" is wrong
  somewhere (unpremultiplied texture with `glBlendFunc(GL_SRC_ALPHA, ...)`).
- Stroke disappears past ~N points → static vertex array rebuilt per frame;
  switch to a persistent growable batch buffer.
- Jank during fast pan → non-dirty tiles being re-rendered, or a texture
  upload per frame; verify the tile-dirty model.
- Ghost of the first stroke after restart → GL-side cache survived context
  loss; enforce the full-recreate path (`android-egl-and-window.md` §4).
- Slow undo → missing tile-diff layer model (undo doing a full redraw).
- `glFinish`/`glGetError` allow-list (see also `debugging-profiling.md` §5):
  (a) setup, (b) foreground/background transitions, (c) user-triggered
  export/share, (d) debug overlays. Nowhere per-frame else.
