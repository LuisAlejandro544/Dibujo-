#version 450

// Presentación de la hoja en Vulkan: un triángulo que cubre la pantalla.
// `uFlipY` queda en 0 aquí porque Vulkan tiene el origen arriba.
layout(push_constant) uniform Push {
    vec4 uFuera;
    vec2 uPantalla;
    vec2 uHojaTam;
    vec2 uPan;
    float uZoom;
    float uFlipY;
    float uRot;
    float uPad0;
    float uPad1;
    float uPad2;
} pc;

layout(location = 0) out vec2 vPantalla;

void main() {
    vec2 pos = vec2(float((gl_VertexIndex << 1) & 2), float(gl_VertexIndex & 2)) * 2.0 - 1.0;
    vec2 n = pos * 0.5 + 0.5;
    float y = mix(n.y, 1.0 - n.y, pc.uFlipY);
    vPantalla = vec2(n.x * pc.uPantalla.x, y * pc.uPantalla.y);
    gl_Position = vec4(pos, 0.0, 1.0);
}
