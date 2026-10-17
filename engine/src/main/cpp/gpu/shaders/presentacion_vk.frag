#version 450

layout(push_constant) uniform Push {
    vec4 uFuera;
    vec2 uPantalla;
    vec2 uHojaTam;
    vec2 uPan;
    float uZoom;
    float uFlipY;
} pc;

layout(binding = 0) uniform sampler2D uHoja;

layout(location = 0) in vec2 vPantalla;
layout(location = 0) out vec4 salida;

void main() {
    vec2 hoja = (vPantalla + pc.uPan) / pc.uZoom;
    ivec2 p = ivec2(floor(hoja));
    if (p.x < 0 || p.y < 0 || p.x >= int(pc.uHojaTam.x) || p.y >= int(pc.uHojaTam.y)) {
        salida = pc.uFuera;
        return;
    }
    // La hoja llega en ARGB (0xAARRGGBB): en memoria B,G,R,A; al leerla como
    // RGBA hay que intercambiar R y B para igualar al render por CPU.
    vec4 c = texelFetch(uHoja, p, 0);
    salida = vec4(c.b, c.g, c.r, c.a);
}
