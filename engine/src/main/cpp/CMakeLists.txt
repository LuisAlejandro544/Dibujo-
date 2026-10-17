cmake_minimum_required(VERSION 3.16)
project(dibujo_engine VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_POSITION_INDEPENDENT_CODE ON)

if(NOT CMAKE_LIBRARY_OUTPUT_DIRECTORY)
    set(CMAKE_LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/lib")
endif()

# Cabeceras JNI (jni.h) del JDK.
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

add_library(dibujo_engine SHARED
    DibujoEngine.cpp
    dibujo_engine.cpp
)

target_include_directories(dibujo_engine PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${JNI_INCLUDE_DIRS}
)

if(UNIX AND NOT APPLE)
    set_target_properties(dibujo_engine PROPERTIES LINK_FLAGS "-Wl,-z,defs")
endif()
