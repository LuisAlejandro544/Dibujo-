#pragma once

#include <EGL/egl.h>
#include <GLES3/gl3.h>

#include <cstdint>
#include <string>
#include <vector>

#include "RenderizadorGpu.h"

namespace dibujo {
namespace gpu {

// Renderizador OpenGL ES 3.x sobre EGL: sube la hoja a una textura y dibuja un
// triángulo que cubre la pantalla resolviendo la cámara en el fragment shader.
// Es el backend universal (OpenGL ES 3.0 está en todos los Android soportados).
class RenderizadorGL final : public RenderizadorGpu {
public:
    static RenderizadorGpu* crear(ANativeWindow* ventana, DibujoEngine* motor);
    ~RenderizadorGL() override;

    bool render() override;
    void redimensionar(int ancho, int alto) override;
    const char* nombre() const override { return nombre_.c_str(); }

private:
    RenderizadorGL() = default;

    bool iniciar(ANativeWindow* ventana, DibujoEngine* motor);
    bool compilarPrograma();
    void subirHoja();
    void liberar();

    ANativeWindow* ventana_ = nullptr;
    DibujoEngine* motor_ = nullptr;

    EGLDisplay dpy_ = EGL_NO_DISPLAY;
    EGLSurface superficie_ = EGL_NO_SURFACE;
    EGLContext contexto_ = EGL_NO_CONTEXT;
    EGLConfig config_ = nullptr;

    GLuint programa_ = 0;
    GLuint vao_ = 0;
    GLuint textura_ = 0;
    GLint uPantalla_ = -1;
    GLint uFlipY_ = -1;
    GLint uHojaTam_ = -1;
    GLint uPan_ = -1;
    GLint uZoom_ = -1;
    GLint uRot_ = -1;
    GLint uFuera_ = -1;
    GLint uHoja_ = -1;

    int anchoHoja_ = 0;
    int altoHoja_ = 0;
    int vw_ = 0;
    int vh_ = 0;
    std::vector<uint32_t> buffer_;
    uint64_t revision_ = ~static_cast<uint64_t>(0);
    std::string nombre_;
};

} // namespace gpu
} // namespace dibujo
