#include <jni.h>

#include "DibujoEngine.h"

// Puente JNI: conecta el objeto Kotlin `com.dibujo.engine.NativeEngine`
// con el núcleo C++ (`dibujo::DibujoEngine`).
//
// Convención: el peer es un puntero crudo (jlong) a la instancia C++.
// Toda la memoria pertenece al motor: crear/destruir son explícitos.

namespace {

using dibujo::DibujoEngine;

inline DibujoEngine* peer(jlong handle) {
    return reinterpret_cast<DibujoEngine*>(static_cast<intptr_t>(handle));
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_dibujo_engine_NativeEngine_nativoCrear(JNIEnv*, jobject, jint ancho, jint alto) {
    if (ancho <= 0 || alto <= 0) return 0;
    const auto* engine = new DibujoEngine(ancho, alto);
    return static_cast<jlong>(reinterpret_cast<intptr_t>(engine));
}

JNIEXPORT void JNICALL
Java_com_dibujo_engine_NativeEngine_nativoDestruir(JNIEnv*, jobject, jlong handle) {
    delete peer(handle);
}

JNIEXPORT void JNICALL
Java_com_dibujo_engine_NativeEngine_nativoConfigurarLapiz(
        JNIEnv*, jobject, jlong handle,
        jint color, jint grosor, jboolean puntaRedonda, jint herramienta) {
    auto* e = peer(handle);
    if (e == nullptr) return;
    e->fijarColor(static_cast<uint32_t>(color));
    e->fijarGrosor(grosor);
    e->fijarPuntaRedonda(puntaRedonda == JNI_TRUE);
    e->fijarHerramienta(static_cast<dibujo::Tool>(herramienta));
}

JNIEXPORT void JNICALL
Java_com_dibujo_engine_NativeEngine_nativoIniciarTrazo(
        JNIEnv*, jobject, jlong handle, jfloat x, jfloat y) {
    peer(handle)->iniciarTrazo(x, y);
}

JNIEXPORT void JNICALL
Java_com_dibujo_engine_NativeEngine_nativoContinuarTrazo(
        JNIEnv*, jobject, jlong handle, jfloat x, jfloat y) {
    peer(handle)->continuarTrazo(x, y);
}

JNIEXPORT jboolean JNICALL
Java_com_dibujo_engine_NativeEngine_nativoTerminarTrazo(JNIEnv*, jobject, jlong handle) {
    return peer(handle)->terminarTrazo() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_dibujo_engine_NativeEngine_nativoCancelarTrazo(JNIEnv*, jobject, jlong handle) {
    peer(handle)->cancelarTrazo();
}

JNIEXPORT jboolean JNICALL
Java_com_dibujo_engine_NativeEngine_nativoDeshacer(JNIEnv*, jobject, jlong handle) {
    return peer(handle)->deshacer() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dibujo_engine_NativeEngine_nativoRehacer(JNIEnv*, jobject, jlong handle) {
    return peer(handle)->rehacer() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dibujo_engine_NativeEngine_nativoPuedeDeshacer(JNIEnv*, jobject, jlong handle) {
    return peer(handle)->puedeDeshacer() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dibujo_engine_NativeEngine_nativoPuedeRehacer(JNIEnv*, jobject, jlong handle) {
    return peer(handle)->puedeRehacer() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_dibujo_engine_NativeEngine_nativoLimpiarHoja(JNIEnv*, jobject, jlong handle) {
    peer(handle)->limpiarHoja();
}

JNIEXPORT void JNICALL
Java_com_dibujo_engine_NativeEngine_nativoExpandir(
        JNIEnv*, jobject, jlong handle,
        jint izquierda, jint arriba, jint derecha, jint abajo) {
    peer(handle)->expandir(izquierda, arriba, derecha, abajo);
}

JNIEXPORT jint JNICALL
Java_com_dibujo_engine_NativeEngine_nativoAncho(JNIEnv*, jobject, jlong handle) {
    return peer(handle)->ancho();
}

JNIEXPORT jint JNICALL
Java_com_dibujo_engine_NativeEngine_nativoAlto(JNIEnv*, jobject, jlong handle) {
    return peer(handle)->alto();
}

JNIEXPORT void JNICALL
Java_com_dibujo_engine_NativeEngine_nativoPan(
        JNIEnv*, jobject, jlong handle, jfloat dx, jfloat dy) {
    peer(handle)->pan(dx, dy);
}

JNIEXPORT void JNICALL
Java_com_dibujo_engine_NativeEngine_nativoZoom(
        JNIEnv*, jobject, jlong handle, jfloat factor) {
    peer(handle)->zoom(factor);
}

JNIEXPORT void JNICALL
Java_com_dibujo_engine_NativeEngine_nativoCentrarVista(JNIEnv*, jobject, jlong handle) {
    peer(handle)->centrarVista();
}

JNIEXPORT void JNICALL
Java_com_dibujo_engine_NativeEngine_nativoRenderizar(
        JNIEnv* env, jobject, jlong handle, jintArray destino, jint vw, jint vh) {
    auto* e = peer(handle);
    if (e == nullptr) return;
    jsize longitud = env->GetArrayLength(destino);
    if (longitud < static_cast<jsize>(vw) * static_cast<jsize>(vh)) return;
    jint* px = env->GetIntArrayElements(destino, nullptr);
    if (px == nullptr) return;
    e->renderizar(reinterpret_cast<uint32_t*>(px), vw, vh);
    env->ReleaseIntArrayElements(destino, px, 0);
}

JNIEXPORT jstring JNICALL
Java_com_dibujo_engine_NativeEngine_nativoVersion(JNIEnv* env, jobject) {
    return env->NewStringUTF("dibujo-engine-c++/1.0");
}

} // extern "C"
