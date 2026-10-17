cmake_minimum_required(VERSION 3.16)
project(dibujo_engine VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_POSITION_INDEPENDENT_CODE ON)

if(NOT CMAKE_LIBRARY_OUTPUT_DIRECTORY)
    set(CMAKE_LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/lib")
endif()

# Cabeceras JNI (jni.h). En Android (NDK) vienen del sysroot; en el resto de
# las plataformas se usan las del JDK vía JDK_HOME/JAVA_HOME.
if(ANDROID)
    message(STATUS "Compilando motor nativo para Android ${ANDROID_ABI}: jni.h del NDK.")
else()
    if(NOT JDK_HOME)
        set(JDK_HOME "$ENV{JAVA_HOME}")
    endif()
    if(NOT JDK_HOME)
        message(FATAL_ERROR "Define JDK_HOME (o JAVA_HOME) para compilar el puente JNI.")
    endif()

    set(JNI_INCLUDE_DIRS "${JDK_HOME}/include")
    if(WIN32)
        list(APPEND JNI_INCLUDE_DIRS "${JDK_HOME}/include/win32")
    elseif(APPLE)
        list(APPEND JNI_INCLUDE_DIRS "${JDK_HOME}/include/darwin")
    else()
        list(APPEND JNI_INCLUDE_DIRS "${JDK_HOME}/include/linux")
    endif()
endif()

add_library(dibujo_engine SHARED
    DibujoEngine.cpp
    dibujo_engine.cpp
)

if(ANDROID)
    target_include_directories(dibujo_engine PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
    )
else()
    target_include_directories(dibujo_engine PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${JNI_INCLUDE_DIRS}
    )
endif()

if(UNIX AND NOT APPLE)
    set_target_properties(dibujo_engine PROPERTIES LINK_FLAGS "-Wl,-z,defs")
endif()

# --- Render GPU (solo Android) -------------------------------------------
# La presentación en GPU (OpenGL ES por EGL y Vulkan) necesita cabeceras y
# librerías del NDK, por eso no se compila en el motor de escritorio.
if(ANDROID)
    set(DIBUJO_GPU_DIR "${CMAKE_CURRENT_SOURCE_DIR}/gpu")

    target_sources(dibujo_engine PRIVATE
        "${DIBUJO_GPU_DIR}/RenderizadorGpu.cpp"
        "${DIBUJO_GPU_DIR}/RenderizadorGL.cpp"
        "${DIBUJO_GPU_DIR}/RenderizadorVulkan.cpp"
        "${DIBUJO_GPU_DIR}/GpuJni.cpp"
    )
    # `VK_USE_PLATFORM_ANDROID_KHR` habilita vkCreateAndroidSurfaceKHR en
    # vulkan.h. La cabecera generada (SPIR-V) vive en el directorio de build.
    target_compile_definitions(dibujo_engine PRIVATE VK_USE_PLATFORM_ANDROID_KHR)
    target_include_directories(dibujo_engine PRIVATE "${CMAKE_CURRENT_BINARY_DIR}")

    # Compila los shaders Vulkan a SPIR-V con el `glslc` del NDK y los deja en
    # una cabecera con los arrays de bytes. Así el Vulkan no depende de archivos
    # en tiempo de ejecución y el build falla temprano si un shader es inválido.
    if(CMAKE_HOST_WIN32)
        set(DIBUJO_NDK_HOST "windows-x86_64")
    elseif(CMAKE_HOST_APPLE)
        set(DIBUJO_NDK_HOST "darwin-x86_64")
    else()
        set(DIBUJO_NDK_HOST "linux-x86_64")
    endif()
    set(DIBUJO_GLSLC "${ANDROID_NDK}/shader-tools/${DIBUJO_NDK_HOST}/glslc")
    if(NOT EXISTS "${DIBUJO_GLSLC}")
        message(FATAL_ERROR "No se encontró glslc en '${DIBUJO_GLSLC}' (se necesita para los shaders Vulkan).")
    endif()

    set(DIBUJO_SPV_VERT "${CMAKE_CURRENT_BINARY_DIR}/presentacion_vk.vert.spv")
    set(DIBUJO_SPV_FRAG "${CMAKE_CURRENT_BINARY_DIR}/presentacion_vk.frag.spv")
    execute_process(
        COMMAND "${DIBUJO_GLSLC}" -fshader-stage=vertex
                "${DIBUJO_GPU_DIR}/shaders/presentacion_vk.vert" -o "${DIBUJO_SPV_VERT}"
        RESULT_VARIABLE DIBUJO_RV_VERT)
    execute_process(
        COMMAND "${DIBUJO_GLSLC}" -fshader-stage=fragment
                "${DIBUJO_GPU_DIR}/shaders/presentacion_vk.frag" -o "${DIBUJO_SPV_FRAG}"
        RESULT_VARIABLE DIBUJO_RV_FRAG)
    if(NOT DIBUJO_RV_VERT EQUAL 0 OR NOT DIBUJO_RV_FRAG EQUAL 0)
        message(FATAL_ERROR "Falló glslc (vert=${DIBUJO_RV_VERT}, frag=${DIBUJO_RV_FRAG}).")
    endif()

    file(READ "${DIBUJO_SPV_VERT}" DIBUJO_HEX_VERT HEX)
    string(REGEX REPLACE "([0-9a-f][0-9a-f])" "0x\\1," DIBUJO_BYTES_VERT "${DIBUJO_HEX_VERT}")
    file(READ "${DIBUJO_SPV_FRAG}" DIBUJO_HEX_FRAG HEX)
    string(REGEX REPLACE "([0-9a-f][0-9a-f])" "0x\\1," DIBUJO_BYTES_FRAG "${DIBUJO_HEX_FRAG}")
    file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/shaders_vk_generado.h"
"// Generado por CMake a partir de gpu/shaders/presentacion_vk.{vert,frag}: no editar.
#pragma once
#include <cstddef>
alignas(4) static const unsigned char kSpvVert[] = { ${DIBUJO_BYTES_VERT} };
static const std::size_t kSpvVertTam = sizeof(kSpvVert);
alignas(4) static const unsigned char kSpvFrag[] = { ${DIBUJO_BYTES_FRAG} };
static const std::size_t kSpvFragTam = sizeof(kSpvFrag);
")

    find_library(DIBUJO_EGL EGL REQUIRED)
    # GLESv3 (API 21+) exporta los símbolos ES3 "core" (p. ej. glBindVertexArray);
    # GLESv2 sigue aportando los comunes y el fallback del sistema.
    find_library(DIBUJO_GLESv3 GLESv3 REQUIRED)
    find_library(DIBUJO_GLESv2 GLESv2 REQUIRED)
    find_library(DIBUJO_VULKAN vulkan REQUIRED)
    find_library(DIBUJO_ANDROID android REQUIRED)
    find_library(DIBUJO_LOG log REQUIRED)
    target_link_libraries(dibujo_engine
        ${DIBUJO_EGL} ${DIBUJO_GLESv3} ${DIBUJO_GLESv2}
        ${DIBUJO_VULKAN} ${DIBUJO_ANDROID} ${DIBUJO_LOG})
endif()
