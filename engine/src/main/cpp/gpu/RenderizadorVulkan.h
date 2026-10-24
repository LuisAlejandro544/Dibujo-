#pragma once

#include <vulkan/vulkan.h>

#include <atomic>
#include <cstdint>
#include <string>
#include <vector>

#include "RenderizadorGpu.h"

namespace dibujo {
namespace gpu {

// Renderizador Vulkan 1.0/1.1: cadena de intercambio sobre la ventana nativa,
// un descriptor con la hoja como textura y un pipeline que resuelve la cámara
// en el fragment shader (mismos parámetros que el backend OpenGL).
//
// Robustez ante cambios bruscos de interfaz: cuando la superficie queda
// momentáneamente inválida (tamaño 0×0 durante una reconfiguración, formatos
// no disponibles) el render NO se da por perdido: se devuelve
// `EstadoCuadro::REINTENTAR` y el siguiente cuadro reintenta. Sólo un fallo
// real del dispositivo devuelve `EstadoCuadro::FALLO` (y ahí sí se cae al
// render por CPU). Es la diferencia con OpenGL, que reintenta solo cuando la
// superficie todavía no tiene tamaño.
class RenderizadorVulkan final : public RenderizadorGpu {
public:
    static RenderizadorGpu* crear(ANativeWindow* ventana, DibujoEngine* motor);
    ~RenderizadorVulkan() override;

    EstadoCuadro render() override;
    void redimensionar(int ancho, int alto) override;
    const char* nombre() const override { return nombre_.c_str(); }

private:
    RenderizadorVulkan() = default;

    bool iniciar(ANativeWindow* ventana, DibujoEngine* motor);
    void liberar();

    bool crearInstancia();
    bool crearSuperficie(ANativeWindow* ventana);
    bool elegirFisico();
    bool crearDispositivo();
    bool crearCadena();
    bool crearPasada();
    bool crearTuberia();
    bool crearDescriptores();
    bool crearHoja();
    bool crearStaging();
    bool crearRecursosCuadro();
    bool recrearCadena();
    void limpiarCadena();
    void subirHojaInicial();
    void grabarSubidaHoja(VkCommandBuffer cmd, uint32_t slot);
    void generarMipmaps(VkCommandBuffer cmd);

    uint32_t buscarFamiliaCola(VkPhysicalDevice fisico) const;
    uint32_t buscarTipoMemoria(uint32_t filtro, VkMemoryPropertyFlags props) const;
    bool crearBuffer(VkDeviceSize tamano, VkBufferUsageFlags uso,
                     VkMemoryPropertyFlags props, VkBuffer& buffer,
                     VkDeviceMemory& memoria);
    VkCommandBuffer comenzarComandoUnico();
    void terminarComandoUnico(VkCommandBuffer cmd);

    ANativeWindow* ventana_ = nullptr;
    DibujoEngine* motor_ = nullptr;

    VkInstance instancia_ = VK_NULL_HANDLE;
    VkSurfaceKHR superficie_ = VK_NULL_HANDLE;
    VkPhysicalDevice fisico_ = VK_NULL_HANDLE;
    VkDevice dispositivo_ = VK_NULL_HANDLE;
    uint32_t familiaCola_ = 0;
    VkQueue cola_ = VK_NULL_HANDLE;

    VkSwapchainKHR cadena_ = VK_NULL_HANDLE;
    VkFormat formato_ = VK_FORMAT_UNDEFINED;
    VkExtent2D extension_{0, 0};
    std::vector<VkImage> imagenes_;
    std::vector<VkImageView> vistas_;

    VkRenderPass pasada_ = VK_NULL_HANDLE;
    std::vector<VkFramebuffer> marcos_;
    VkPipelineLayout diseno_ = VK_NULL_HANDLE;
    VkPipeline tuberia_ = VK_NULL_HANDLE;

    VkCommandPool pool_ = VK_NULL_HANDLE;

    // Recursos por cuadro en vuelo: command buffer, semáforos, valla y buffer
    // de subida. Son uno por ranura para que un cuadro en vuelo no pise los
    // recursos del otro (semáforos y staging).
    std::vector<VkCommandBuffer> comandos_;
    std::vector<VkSemaphore> disponibles_;
    std::vector<VkSemaphore> presentado_;
    std::vector<VkFence> enVuelo_;
    std::vector<VkBuffer> staging_;
    std::vector<VkDeviceMemory> memoriaStaging_;
    uint32_t cuadro_ = 0;

    VkSampler muestreo_ = VK_NULL_HANDLE;
    VkDescriptorSetLayout disenoDescriptores_ = VK_NULL_HANDLE;
    VkDescriptorPool poolDescriptores_ = VK_NULL_HANDLE;
    VkDescriptorSet descriptores_ = VK_NULL_HANDLE;

    VkImage hoja_ = VK_NULL_HANDLE;
    VkDeviceMemory memoriaHoja_ = VK_NULL_HANDLE;
    VkImageView vistaHoja_ = VK_NULL_HANDLE;
    VkImageLayout disposicionHoja_ = VK_IMAGE_LAYOUT_UNDEFINED;

    VkDeviceSize tamanoStaging_ = 0;

    int anchoHoja_ = 0;
    int altoHoja_ = 0;
    uint32_t nivelesMipmap_ = 1;
    // El tamaño lo escribe el hilo de la interfaz y lo lee el de render:
    // son atómicos para que no haya carreras.
    std::atomic<int> vw_{0};
    std::atomic<int> vh_{0};
    std::atomic<bool> reiniciarCadena_{false};
    // La superficie está en transición (tamaño 0×0 o sin formatos): hay que
    // reintentar, no perder el render.
    bool superficieInvalida_ = false;
    // El dispositivo Vulkan se perdió: no hay recuperación posible.
    bool dispositivoPerdido_ = false;
    std::vector<uint32_t> buffer_;
    uint64_t revision_ = ~static_cast<uint64_t>(0);
    std::string nombre_;
};

} // namespace gpu
} // namespace dibujo
