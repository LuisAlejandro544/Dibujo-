#version 450

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

layout(binding = 0) uniform sampler2D uHoja;

layout(location = 0) in vec2 vPantalla;
layout(location = 0) out vec4 salida;

void main() {
    float c = cos(pc.uRot);
    float s = sin(pc.uRot);
    vec2 e = vPantalla + pc.uPan;
    // R(−rot) aplicado a e y des-escalado por el zoom.
    vec2 hoja = vec2(c * e.x + s * e.y, -s * e.x + c * e.y) / pc.uZoom;
    ivec2 p = ivec2(floor(hoja));
    if (p.x < 0 || p.y < 0 || p.x >= int(pc.uHojaTam.x) || p.y >= int(pc.uHojaTam.y)) {
        salida = pc.uFuera;
        return;
    }
    // La hoja llega en ARGB (0xAARRGGBB): en memoria B,G,R,A; al leerla como
    // RGBA hay que intercambiar R y B para igualar al render por CPU.
    vec4 c2 = texelFetch(uHoja, p, 0);
    salida = vec4(c2.b, c2.g, c2.r, c2.a);
}
