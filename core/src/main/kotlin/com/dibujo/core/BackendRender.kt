package com.dibujo.core

/**
 * Backend gráfico elegible por el usuario para presentar la hoja.
 *
 * - [OPENGL]: OpenGL ES. Disponible en todos los Android soportados (≥ 2.0),
 *   por eso es el respaldo universal.
 * - [VULKAN]: Vulkan. Sólo se puede elegir si el equipo declara soporte para
 *   Vulkan 1.1 (ver `CapacidadesGraficas` en `:app`).
 *
 * La selección es una preferencia: [AUTOMATICO] resuelve el mejor backend
 * disponible y una elección explícita de [VULKAN] cae a [OPENGL] si el equipo
 * no lo soporta (defensa; la UI ya bloquea esa opción).
 */
enum class BackendRender {
    AUTOMATICO,
    OPENGL,
    VULKAN;

    /** Backend real que correrá en el equipo dado su soporte de Vulkan. */
    fun resolver(vulkanSoportado: Boolean): BackendRender = when (this) {
        AUTOMATICO, VULKAN -> if (vulkanSoportado) VULKAN else OPENGL
        OPENGL -> OPENGL
    }

    /** ¿El usuario puede elegir esta opción en un equipo con este soporte? */
    fun disponible(vulkanSoportado: Boolean): Boolean =
        this != VULKAN || vulkanSoportado
}
