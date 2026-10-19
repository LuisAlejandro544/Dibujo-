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
    // vPantalla llega en el centro del píxel (+0.5); se descuenta para que la
    // coordenada de hoja coincida con la del render por CPU.
    vec2 e = (vPantalla - vec2(0.5)) + pc.uPan;
    // R(−rot) aplicado a e y des-escalado por el zoom.
    vec2 hoja = vec2(c * e.x + s * e.y, -s * e.x + c * e.y) / pc.uZoom;
    if (hoja.x < 0.0 || hoja.y < 0.0 ||
        hoja.x >= pc.uHojaTam.x || hoja.y >= pc.uHojaTam.y) {
        salida = pc.uFuera;
        return;
    }
    // La hoja llega en ARGB (0xAARRGGBB): en memoria B,G,R,A; al leerla como
    // RGBA hay que intercambiar R y B para igualar al render por CPU.
    // `texture` con filtro LINEAR interpola los 4 texeles vecinos.
    vec2 uv = (hoja + vec2(0.5)) / pc.uHojaTam;
    vec4 c2 = texture(uHoja, uv);
    salida = vec4(c2.b, c2.g, c2.r, c2.a);
}
