#include "RenderizadorGpu.h"

#include "RenderizadorGL.h"
#include "RenderizadorVulkan.h"

namespace dibujo {
namespace gpu {

RenderizadorGpu* RenderizadorGpu::crear(Backend backend, ANativeWindow* ventana,
                                        DibujoEngine* motor) {
    if (ventana == nullptr || motor == nullptr) return nullptr;
    switch (backend) {
        case Backend::VULKAN:
            return RenderizadorVulkan::crear(ventana, motor);
        case Backend::OPENGL:
        case Backend::AUTOMATICO:
        default:
            return RenderizadorGL::crear(ventana, motor);
    }
}

} // namespace gpu
} // namespace dibujo
