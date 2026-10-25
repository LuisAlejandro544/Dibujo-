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
        jint color, jint grosor, jboolean puntaRedonda, jint herramienta,
        jint textura, jint herramientaPrincipal, jint herramientaDoble,
        jint ladoDoble, jint colorDoble) {
    auto* e = peer(handle);
    if (e == nullptr) return;
    e->fijarColor(static_cast<uint32_t>(color));
    e->fijarGrosor(grosor);
    e->fijarPuntaRedonda(puntaRedonda == JNI_TRUE);
    e->fijarHerramienta(static_cast<dibujo::Tool>(herramienta));
    e->fijarTextura(static_cast<dibujo::Textura>(textura));
    // Configuración del trazo doble (herramienta DOBLE).
    e->fijarHerramientaPrincipal(static_cast<dibujo::Tool>(herramientaPrincipal));
    e->fijarHerramientaDoble(static_cast<dibujo::ToolDoble>(herramientaDoble));
    e->fijarLadoDoble(static_cast<dibujo::LadoDoble>(ladoDoble));
    e->fijarColorDoble(static_cast<uint32_t>(colorDoble));
}

JNIEXPORT void JNICALL
Java_com_dibujo_engine_NativeEngine_nativoAsistenciaRecta(
        JNIEnv*, jobject, jlong handle, jboolean activa) {
    auto* e = peer(handle);
    if (e == nullptr) return;
    e->fijarAsistenciaRecta(activa == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_dibujo_engine_NativeEngine_nativoSimetria(
        JNIEnv*, jobject, jlong handle, jint modo) {
    auto* e = peer(handle);
    if (e == nullptr) return;
    e->fijarSimetria(static_cast<dibujo::Simetria>(modo));
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
Java_com_dibujo_engine_NativeEngine_nativoLimiteHistorial(
        JNIEnv*, jobject, jlong handle, jint pasos) {
    auto* e = peer(handle);
    if (e == nullptr) return;
    e->fijarLimiteHistorial(pasos < 1 ? 1 : static_cast<std::size_t>(pasos));
}

JNIEXPORT jboolean JNICALL
Java_com_dibujo_engine_NativeEngine_nativoLimpiarTrazos(JNIEnv*, jobject, jlong handle) {
    auto* e = peer(handle);
    if (e == nullptr) return JNI_FALSE;
    // Radio uniforme de redibujo: coincide con `Lienzo.RADIO_LIMPIEZA`.
    return e->limpiarTrazos(1.0f) ? JNI_TRUE : JNI_FALSE;
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
Java_com_dibujo_engine_NativeEngine_nativoRotar(
        JNIEnv*, jobject, jlong handle, jfloat delta) {
    peer(handle)->rotar(delta);
}

JNIEXPORT void JNICALL
Java_com_dibujo_engine_NativeEngine_nativoLeerCamara(
        JNIEnv* env, jobject, jlong handle, jfloatArray destino) {
    auto* e = peer(handle);
    if (e == nullptr || destino == nullptr) return;
    if (env->GetArrayLength(destino) < 4) return;
    float panX = 0.0f;
    float panY = 0.0f;
    float zoom = 1.0f;
    float rotacion = 0.0f;
    e->leerCamara(panX, panY, zoom, rotacion);
    const jfloat datos[4] = {panX, panY, zoom, rotacion};
    env->SetFloatArrayRegion(destino, 0, 4, datos);
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

JNIEXPORT jboolean JNICALL
Java_com_dibujo_engine_NativeEngine_nativoCopiarHoja(
        JNIEnv* env, jobject, jlong handle, jintArray destino) {
    auto* e = peer(handle);
    if (e == nullptr) return JNI_FALSE;
    const jsize capacidad = env->GetArrayLength(destino);
    const auto necesarios = static_cast<jsize>(e->ancho()) * static_cast<jsize>(e->alto());
    if (capacidad < necesarios) return JNI_FALSE;
    jint* px = env->GetIntArrayElements(destino, nullptr);
    if (px == nullptr) return JNI_FALSE;
    // `copiarHoja` devuelve 0 también en una hoja recién creada (revisión 0),
    // así que la copia se considera válida por la capacidad, no por el valor.
    e->copiarHoja(reinterpret_cast<uint32_t*>(px), static_cast<std::size_t>(capacidad));
    env->ReleaseIntArrayElements(destino, px, 0);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_dibujo_engine_NativeEngine_nativoReemplazarHoja(
        JNIEnv* env, jobject, jlong handle, jintArray pixeles) {
    auto* e = peer(handle);
    if (e == nullptr || pixeles == nullptr) return JNI_FALSE;
    const jsize n = env->GetArrayLength(pixeles);
    if (n != e->ancho() * e->alto()) return JNI_FALSE;
    jint* px = env->GetIntArrayElements(pixeles, nullptr);
    if (px == nullptr) return JNI_FALSE;
    const bool ok = e->reemplazarHoja(
        reinterpret_cast<uint32_t*>(px), static_cast<std::size_t>(n));
    // Sólo leímos el buffer; no hace falta copiarlo de vuelta al array de Java.
    env->ReleaseIntArrayElements(pixeles, px, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dibujo_engine_NativeEngine_nativoTransformar(
        JNIEnv*, jobject, jlong handle,
        jboolean voltearHorizontal, jboolean voltearVertical,
        jfloat desplazamientoX, jfloat desplazamientoY, jfloat escala) {
    auto* e = peer(handle);
    if (e == nullptr) return JNI_FALSE;
    const bool ok = e->transformar(
        voltearHorizontal == JNI_TRUE, voltearVertical == JNI_TRUE,
        desplazamientoX, desplazamientoY, escala);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_dibujo_engine_NativeEngine_nativoPixelHoja(
        JNIEnv*, jobject, jlong handle, jint x, jint y) {
    auto* e = peer(handle);
    if (e == nullptr) return 0;
    return static_cast<jint>(e->pixelHoja(x, y));
}

JNIEXPORT jint JNICALL
Java_com_dibujo_engine_NativeEngine_nativoNumeroCapas(JNIEnv*, jobject, jlong handle) {
    auto* e = peer(handle);
    if (e == nullptr) return 1;
    return static_cast<jint>(e->numeroCapas());
}

JNIEXPORT jint JNICALL
Java_com_dibujo_engine_NativeEngine_nativoCapaActiva(JNIEnv*, jobject, jlong handle) {
    auto* e = peer(handle);
    if (e == nullptr) return 0;
    return static_cast<jint>(e->capaActiva());
}

JNIEXPORT jboolean JNICALL
Java_com_dibujo_engine_NativeEngine_nativoAgregarCapa(JNIEnv*, jobject, jlong handle) {
    auto* e = peer(handle);
    if (e == nullptr) return JNI_FALSE;
    return e->agregarCapa() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dibujo_engine_NativeEngine_nativoEliminarCapa(JNIEnv*, jobject, jlong handle, jint indice) {
    auto* e = peer(handle);
    if (e == nullptr) return JNI_FALSE;
    return e->eliminarCapa(indice) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dibujo_engine_NativeEngine_nativoSeleccionarCapa(JNIEnv*, jobject, jlong handle, jint indice) {
    auto* e = peer(handle);
    if (e == nullptr) return JNI_FALSE;
    return e->seleccionarCapa(indice) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_dibujo_engine_NativeEngine_nativoVisibilidadCapa(JNIEnv*, jobject, jlong handle, jint indice, jboolean visible) {
    auto* e = peer(handle);
    if (e == nullptr) return;
    e->visibilidadCapa(indice, visible == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_dibujo_engine_NativeEngine_nativoOpacidadCapa(JNIEnv*, jobject, jlong handle, jint indice, jfloat alfa) {
    auto* e = peer(handle);
    if (e == nullptr) return;
    e->opacidadCapa(indice, alfa);
}

JNIEXPORT jboolean JNICALL
Java_com_dibujo_engine_NativeEngine_nativoEsCapaVisible(JNIEnv*, jobject, jlong handle, jint indice) {
    auto* e = peer(handle);
    if (e == nullptr) return JNI_TRUE;
    return e->esCapaVisible(indice) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jfloat JNICALL
Java_com_dibujo_engine_NativeEngine_nativoOpacidadDeCapa(JNIEnv*, jobject, jlong handle, jint indice) {
    auto* e = peer(handle);
    if (e == nullptr) return 1.0f;
    return e->opacidadDeCapa(indice);
}

JNIEXPORT jstring JNICALL
Java_com_dibujo_engine_NativeEngine_nativoVersion(JNIEnv* env, jobject) {
    return env->NewStringUTF("dibujo-engine-c++/1.0");
}

} // extern "C"
