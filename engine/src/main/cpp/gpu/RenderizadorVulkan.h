#pragma once

#include <vulkan/vulkan.h>

#include <cstdint>
#include <string>
#include <vector>

#include "RenderizadorGpu.h"

namespace dibujo {
namespace gpu {

// Renderizador Vulkan 1.0/1.1: cadena de intercambio sobre la ventana nativa,
// un descriptor con la hoja como textura y un pipeline que resuelve la cámara
// en el fragment shader (mismos parámetros que elbackend OpenGL). Si algo falla
// en la creación, `crear` devuelve nullptr y la app cae al render por CPU.
class RenderizadorVulkan final : public RenderizadorGpu {
public:
    static RenderizadorGpu* crear(ANativeWindow* ventana, DibujoEngine* motor);
    ~RenderizadorVulkan() override;

    bool render() override;
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
    bool crearDestinos();
    bool crearPasada();
    bool crearDescriptores();
    bool crearTuberia();
    bool crearSincronizacion();
    bool crearHoja();
    bool crearRecursosCadena();
    bool recrearCadena();
    void limpiarCadena();
    void subirHoja(VkCommandBuffer cmd);

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
    VkCommandBuffer comando_ = VK_NULL_HANDLE;

    VkSemaphore disponible_ = VK_NULL_HANDLE;
    std::vector<VkSemaphore> presentado_;
    std::vector<VkFence> enVuelo_;
    uint32_t cuadro_ = 0;
    bool reiniciarCadena_ = false;

    VkSampler muestreo_ = VK_NULL_HANDLE;
    VkDescriptorSetLayout disenoDescriptores_ = VK_NULL_HANDLE;
    VkDescriptorPool poolDescriptores_ = VK_NULL_HANDLE;
    VkDescriptorSet descriptores_ = VK_NULL_HANDLE;

    VkImage hoja_ = VK_NULL_HANDLE;
    VkDeviceMemory memoriaHoja_ = VK_NULL_HANDLE;
    VkImageView vistaHoja_ = VK_NULL_HANDLE;
    VkImageLayout disposicionHoja_ = VK_IMAGE_LAYOUT_UNDEFINED;

    VkBuffer staging_ = VK_NULL_HANDLE;
    VkDeviceMemory memoriaStaging_ = VK_NULL_HANDLE;
    VkDeviceSize tamanoStaging_ = 0;

    int anchoHoja_ = 0;
    int altoHoja_ = 0;
    int vw_ = 0;
    int vh_ = 0;
    std::vector<uint32_t> buffer_;
    uint64_t revision_ = ~static_cast<uint64_t>(0);
    std::string nombre_;
};

} // namespace gpu
} // namespace dibujo
