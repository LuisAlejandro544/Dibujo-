#include "RenderizadorGL.h"

#include <android/native_window.h>

#include "ParametrosPresentacion.h"
#include "Registro.h"

namespace dibujo {
namespace gpu {

namespace {

// Shader de presentación (OpenGL ES 3.0): triángulo que cubre la pantalla y
// resuelve la cámara por fragmento. `flipY` corrige el origen vertical de
// OpenGL (abajo) frente al de la hoja (arriba).
const char* kVert = R"(#version 300 es
precision highp float;
uniform vec2 uPantalla;
uniform float uFlipY;
out vec2 vPantalla;
void main() {
    vec2 pos = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2)) * 2.0 - 1.0;
    vec2 n = pos * 0.5 + 0.5;
    float y = mix(n.y, 1.0 - n.y, uFlipY);
    vPantalla = vec2(n.x * uPantalla.x, y * uPantalla.y);
    gl_Position = vec4(pos, 0.0, 1.0);
}
)";

const char* kFrag = R"(#version 300 es
precision highp float;
precision highp int;
uniform sampler2D uHoja;
uniform vec2 uHojaTam;
uniform vec2 uPan;
uniform float uZoom;
uniform vec4 uFuera;
in vec2 vPantalla;
out vec4 salida;
void main() {
    vec2 hoja = (vPantalla + uPan) / uZoom;
    ivec2 p = ivec2(floor(hoja));
    if (p.x < 0 || p.y < 0 || p.x >= int(uHojaTam.x) || p.y >= int(uHojaTam.y)) {
        salida = uFuera;
        return;
    }
    // La hoja llega en ARGB (0xAARRGGBB): en memoria los bytes son B,G,R,A, así
    // que al leerla como RGBA hay que intercambiar R y B para que coincida con
    // el render por CPU (paridad píxel a píxel).
    vec4 c = texelFetch(uHoja, p, 0);
    salida = vec4(c.b, c.g, c.r, c.a);
}
)";

bool compilarShader(GLenum tipo, const char* fuente, GLuint& salida) {
    salida = glCreateShader(tipo);
    glShaderSource(salida, 1, &fuente, nullptr);
    glCompileShader(salida);
    GLint ok = GL_FALSE;
    glGetShaderiv(salida, GL_COMPILE_STATUS, &ok);
    if (ok != GL_TRUE) {
        GLint largo = 0;
        glGetShaderiv(salida, GL_INFO_LOG_LENGTH, &largo);
        std::string log(static_cast<std::size_t>(largo > 1 ? largo : 1), '\0');
        glGetShaderInfoLog(salida, largo, nullptr, log.data());
        LOGE("shader no compila (%s): %s",
             tipo == GL_VERTEX_SHADER ? "vertex" : "fragment", log.c_str());
        glDeleteShader(salida);
        salida = 0;
        return false;
    }
    return true;
}

} // namespace

RenderizadorGpu* RenderizadorGL::crear(ANativeWindow* ventana, DibujoEngine* motor) {
    auto* r = new RenderizadorGL();
    if (!r->iniciar(ventana, motor)) {
        delete r;
        return nullptr;
    }
    return r;
}

RenderizadorGL::~RenderizadorGL() { liberar(); }

bool RenderizadorGL::iniciar(ANativeWindow* ventana, DibujoEngine* motor) {
    ventana_ = ventana;
    motor_ = motor;
    anchoHoja_ = motor_->ancho();
    altoHoja_ = motor_->alto();
    buffer_.assign(static_cast<std::size_t>(anchoHoja_) * altoHoja_, 0);

    dpy_ = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (dpy_ == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay falló");
        return false;
    }
    EGLint mayor = 0;
    EGLint menor = 0;
    if (eglInitialize(dpy_, &mayor, &menor) != EGL_TRUE) {
        LOGE("eglInitialize falló (0x%x)", eglGetError());
        return false;
    }

    const EGLint atributos[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 0,
        EGL_STENCIL_SIZE, 0,
        EGL_NONE,
    };
    EGLint n = 0;
    if (eglChooseConfig(dpy_, atributos, &config_, 1, &n) != EGL_TRUE || n < 1) {
        LOGE("eglChooseConfig falló (0x%x)", eglGetError());
        return false;
    }

    const EGLint atributosContexto[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    contexto_ = eglCreateContext(dpy_, config_, EGL_NO_CONTEXT, atributosContexto);
    if (contexto_ == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext falló (0x%x)", eglGetError());
        return false;
    }

    superficie_ = eglCreateWindowSurface(dpy_, config_, ventana_, nullptr);
    if (superficie_ == EGL_NO_SURFACE) {
        LOGE("eglCreateWindowSurface falló (0x%x)", eglGetError());
        return false;
    }
    if (eglMakeCurrent(dpy_, superficie_, superficie_, contexto_) != EGL_TRUE) {
        LOGE("eglMakeCurrent falló (0x%x)", eglGetError());
        return false;
    }

    // Identidad de la GPU (una vez, para diagnóstico).
    const char* version = reinterpret_cast<const char*>(glGetString(GL_VERSION));
    const char* renderer = reinterpret_cast<const char*>(glGetString(GL_RENDERER));
    LOGI("OpenGL: %s | %s | EGL %d.%d",
         version != nullptr ? version : "?", renderer != nullptr ? renderer : "?",
         mayor, menor);

    // 4096 es el mínimo garantizado por ES 3.x solo a partir de ciertos niveles;
    // si la hoja excede el límite del equipo, no hay textura posible -> CPU.
    GLint maxTextura = 0;
    glGetIntegerv(GL_MAX_TEXTURE_SIZE, &maxTextura);
    if (anchoHoja_ > maxTextura || altoHoja_ > maxTextura) {
        LOGE("hoja %dx%d > GL_MAX_TEXTURE_SIZE %d; se usa render por CPU",
             anchoHoja_, altoHoja_, maxTextura);
        return false;
    }

    if (!compilarPrograma()) return false;

    glGenTextures(1, &textura_);
    glBindTexture(GL_TEXTURE_2D, textura_);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, anchoHoja_, altoHoja_, 0, GL_RGBA,
                 GL_UNSIGNED_BYTE, nullptr);

    glGenVertexArrays(1, &vao_);

    glDisable(GL_DEPTH_TEST);
    glDisable(GL_BLEND);
    glDisable(GL_CULL_FACE);

    nombre_ = std::string("OpenGL ES (") + (renderer != nullptr ? renderer : "?") + ")";
    return true;
}

bool RenderizadorGL::compilarPrograma() {
    GLuint vert = 0;
    GLuint frag = 0;
    if (!compilarShader(GL_VERTEX_SHADER, kVert, vert)) return false;
    if (!compilarShader(GL_FRAGMENT_SHADER, kFrag, frag)) {
        glDeleteShader(vert);
        return false;
    }
    programa_ = glCreateProgram();
    glAttachShader(programa_, vert);
    glAttachShader(programa_, frag);
    glLinkProgram(programa_);
    glDeleteShader(vert);
    glDeleteShader(frag);

    GLint ok = GL_FALSE;
    glGetProgramiv(programa_, GL_LINK_STATUS, &ok);
    if (ok != GL_TRUE) {
        GLint largo = 0;
        glGetProgramiv(programa_, GL_INFO_LOG_LENGTH, &largo);
        std::string log(static_cast<std::size_t>(largo > 1 ? largo : 1), '\0');
        glGetProgramInfoLog(programa_, largo, nullptr, log.data());
        LOGE("programa no enlaza: %s", log.c_str());
        glDeleteProgram(programa_);
        programa_ = 0;
        return false;
    }

    uPantalla_ = glGetUniformLocation(programa_, "uPantalla");
    uFlipY_ = glGetUniformLocation(programa_, "uFlipY");
    uHojaTam_ = glGetUniformLocation(programa_, "uHojaTam");
    uPan_ = glGetUniformLocation(programa_, "uPan");
    uZoom_ = glGetUniformLocation(programa_, "uZoom");
    uFuera_ = glGetUniformLocation(programa_, "uFuera");
    uHoja_ = glGetUniformLocation(programa_, "uHoja");
    return true;
}

void RenderizadorGL::subirHoja() {
    const uint64_t rev = motor_->copiarHoja(buffer_.data(), buffer_.size());
    if (rev == 0) return;
    glBindTexture(GL_TEXTURE_2D, textura_);
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, anchoHoja_, altoHoja_, GL_RGBA,
                    GL_UNSIGNED_BYTE, buffer_.data());
    revision_ = rev;
}

bool RenderizadorGL::render() {
    if (superficie_ == EGL_NO_SURFACE || motor_ == nullptr) return false;

    if (vw_ <= 0 || vh_ <= 0) {
        EGLint w = 0;
        EGLint h = 0;
        if (eglQuerySurface(dpy_, superficie_, EGL_WIDTH, &w) == EGL_TRUE &&
            eglQuerySurface(dpy_, superficie_, EGL_HEIGHT, &h) == EGL_TRUE) {
            vw_ = w;
            vh_ = h;
        }
        if (vw_ <= 0 || vh_ <= 0) return true; // aún sin tamaño; reintentar luego
    }

    if (motor_->revision() != revision_) subirHoja();

    float panX = 0.0f;
    float panY = 0.0f;
    float zoom = 1.0f;
    motor_->leerCamara(panX, panY, zoom);

    ParametrosPresentacion p{};
    p.fuera[0] = 236.0f / 255.0f;
    p.fuera[1] = 236.0f / 255.0f;
    p.fuera[2] = 236.0f / 255.0f;
    p.fuera[3] = 1.0f; // 0xFFECECEC
    p.pantalla[0] = static_cast<float>(vw_);
    p.pantalla[1] = static_cast<float>(vh_);
    p.hojaTam[0] = static_cast<float>(anchoHoja_);
    p.hojaTam[1] = static_cast<float>(altoHoja_);
    p.pan[0] = panX;
    p.pan[1] = panY;
    p.zoom = zoom;
    p.flipY = 1.0f; // OpenGL: origen abajo

    glViewport(0, 0, vw_, vh_);
    glClearColor(p.fuera[0], p.fuera[1], p.fuera[2], 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    glUseProgram(programa_);
    if (uPantalla_ >= 0) glUniform2f(uPantalla_, p.pantalla[0], p.pantalla[1]);
    if (uFlipY_ >= 0) glUniform1f(uFlipY_, p.flipY);
    if (uHojaTam_ >= 0) glUniform2f(uHojaTam_, p.hojaTam[0], p.hojaTam[1]);
    if (uPan_ >= 0) glUniform2f(uPan_, p.pan[0], p.pan[1]);
    if (uZoom_ >= 0) glUniform1f(uZoom_, p.zoom);
    if (uFuera_ >= 0) glUniform4f(uFuera_, p.fuera[0], p.fuera[1], p.fuera[2], p.fuera[3]);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, textura_);
    if (uHoja_ >= 0) glUniform1i(uHoja_, 0);

    glBindVertexArray(vao_);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glBindVertexArray(0);

    if (eglSwapBuffers(dpy_, superficie_) != EGL_TRUE) {
        const EGLint error = eglGetError();
        LOGE("eglSwapBuffers falló (0x%x)", error);
        return false;
    }
    return true;
}

void RenderizadorGL::redimensionar(int ancho, int alto) {
    vw_ = ancho;
    vh_ = alto;
}

void RenderizadorGL::liberar() {
    if (dpy_ != EGL_NO_DISPLAY) {
        if (contexto_ != EGL_NO_CONTEXT) {
            eglMakeCurrent(dpy_, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            if (programa_ != 0) glDeleteProgram(programa_);
            if (textura_ != 0) glDeleteTextures(1, &textura_);
            if (vao_ != 0) glDeleteVertexArrays(1, &vao_);
            programa_ = 0;
            textura_ = 0;
            vao_ = 0;
            eglDestroyContext(dpy_, contexto_);
            contexto_ = EGL_NO_CONTEXT;
        }
        if (superficie_ != EGL_NO_SURFACE) {
            eglDestroySurface(dpy_, superficie_);
            superficie_ = EGL_NO_SURFACE;
        }
        eglTerminate(dpy_);
        dpy_ = EGL_NO_DISPLAY;
    }
    ventana_ = nullptr;
    motor_ = nullptr;
}

} // namespace gpu
} // namespace dibujo
