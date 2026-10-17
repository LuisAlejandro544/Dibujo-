#pragma once

#include <android/log.h>

// Registro mínimo para el subsistema GPU. Se usa únicamente en la creación y
// el diagnóstico (nunca dentro del bucle por cuadro).
#define LOG_TAG_DIBUJO_GPU "dibujo_gpu"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG_DIBUJO_GPU, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG_DIBUJO_GPU, __VA_ARGS__)
