#include <jni.h>

#include <android/native_window.h>
#include <android/native_window_jni.h>

#include "DibujoEngine.h"
#include "RenderizadorGpu.h"

// Puente JNI del render GPU. Envuelve el renderizador nativo y la referencia
// al `ANativeWindow` (adquirida con `ANativeWindow_fromSurface`) en un peer
// opaco. El hilo de render de Kotlin es el dueño: crea, dibuja y destruye.

namespace {

using dibujo::DibujoEngine;
using dibujo::gpu::Backend;
using dibujo::gpu::EstadoCuadro;
using dibujo::gpu::RenderizadorGpu;

struct PeerGpu {
    RenderizadorGpu* renderizador = nullptr;
    ANativeWindow* ventana = nullptr;
};

inline PeerGpu* peer(jlong handle) {
    return reinterpret_cast<PeerGpu*>(static_cast<intptr_t>(handle));
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_dibujo_engine_NativeEngine_nativoGpuCrear(
        JNIEnv* env, jobject, jlong motorHandle, jobject surface, jint backend) {
    auto* motor = reinterpret_cast<DibujoEngine*>(static_cast<intptr_t>(motorHandle));
    if (motor == nullptr || surface == nullptr) return 0;

    ANativeWindow* ventana = ANativeWindow_fromSurface(env, surface);
    if (ventana == nullptr) return 0;

    RenderizadorGpu* r = RenderizadorGpu::crear(static_cast<Backend>(backend), ventana, motor);
    if (r == nullptr) {
        ANativeWindow_release(ventana);
        return 0;
    }
    return static_cast<jlong>(reinterpret_cast<intptr_t>(new PeerGpu{r, ventana}));
}

    JNIEXPORT jint JNICALL
    Java_com_dibujo_engine_NativeEngine_nativoGpuRender(JNIEnv*, jobject, jlong handle) {
        PeerGpu* p = peer(handle);
        if (p == nullptr || p->renderizador == nullptr) {
            return static_cast<jint>(dibujo::gpu::EstadoCuadro::FALLO);
        }
        return static_cast<jint>(p->renderizador->render());
    }

JNIEXPORT void JNICALL
Java_com_dibujo_engine_NativeEngine_nativoGpuRedimensionar(
        JNIEnv*, jobject, jlong handle, jint ancho, jint alto) {
    PeerGpu* p = peer(handle);
    if (p == nullptr || p->renderizador == nullptr) return;
    p->renderizador->redimensionar(ancho, alto);
}

JNIEXPORT jstring JNICALL
Java_com_dibujo_engine_NativeEngine_nativoGpuNombre(JNIEnv* env, jobject, jlong handle) {
    PeerGpu* p = peer(handle);
    if (p == nullptr || p->renderizador == nullptr) return env->NewStringUTF("");
    return env->NewStringUTF(p->renderizador->nombre());
}

JNIEXPORT void JNICALL
Java_com_dibujo_engine_NativeEngine_nativoGpuDestruir(JNIEnv*, jobject, jlong handle) {
    PeerGpu* p = peer(handle);
    if (p == nullptr) return;
    delete p->renderizador;
    if (p->ventana != nullptr) ANativeWindow_release(p->ventana);
    delete p;
}

} // extern "C"
