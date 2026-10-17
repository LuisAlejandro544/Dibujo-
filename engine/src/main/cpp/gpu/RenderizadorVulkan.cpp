#include "RenderizadorVulkan.h"

#include <android/native_window.h>

#include <algorithm>
#include <cstring>

#include "ParametrosPresentacion.h"
#include "Registro.h"
#include "shaders_vk_generado.h"

namespace dibujo {
namespace gpu {

namespace {

constexpr int kMaxCuadrosEnVuelo = 2;
constexpr const char* kExtensionSwapchain = VK_KHR_SWAPCHAIN_EXTENSION_NAME;

bool soportaExtension(VkPhysicalDevice fisico, const char* nombre) {
    uint32_t n = 0;
    vkEnumerateDeviceExtensionProperties(fisico, nullptr, &n, nullptr);
    std::vector<VkExtensionProperties> extensiones(n);
    vkEnumerateDeviceExtensionProperties(fisico, nullptr, &n, extensiones.data());
    for (const auto& e : extensiones) {
        if (std::strcmp(e.extensionName, nombre) == 0) return true;
    }
    return false;
}

} // namespace

RenderizadorGpu* RenderizadorVulkan::crear(ANativeWindow* ventana, DibujoEngine* motor) {
    auto* r = new RenderizadorVulkan();
    if (!r->iniciar(ventana, motor)) {
        delete r;
        return nullptr;
    }
    return r;
}

RenderizadorVulkan::~RenderizadorVulkan() { liberar(); }

bool RenderizadorVulkan::iniciar(ANativeWindow* ventana, DibujoEngine* motor) {
    ventana_ = ventana;
    motor_ = motor;
    anchoHoja_ = motor_->ancho();
    altoHoja_ = motor_->alto();
    buffer_.assign(static_cast<std::size_t>(anchoHoja_) * altoHoja_, 0);
    if (ventana_ != nullptr) {
        vw_ = ANativeWindow_getWidth(ventana_);
        vh_ = ANativeWindow_getHeight(ventana_);
    }

    if (!crearInstancia()) return false;
    if (!crearSuperficie(ventana_)) return false;
    if (!elegirFisico()) return false;
    if (!crearDispositivo()) return false;

    // Grupo de comandos (se usa en la subida inicial de la hoja).
    VkCommandPoolCreateInfo pc{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
    pc.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    pc.queueFamilyIndex = familiaCola_;
    if (vkCreateCommandPool(dispositivo_, &pc, nullptr, &pool_) != VK_SUCCESS) {
        LOGE("Vulkan: vkCreateCommandPool falló");
        return false;
    }

    if (!crearHoja()) return false;
    if (!crearDescriptores()) return false;
    if (!crearCadena()) return false;
    if (!crearPasada()) return false;
    if (!crearTuberia()) return false;
    if (!crearDestinos()) return false;
    if (!crearSincronizacion()) return false;

    VkCommandBufferAllocateInfo cai{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
    cai.commandPool = pool_;
    cai.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    cai.commandBufferCount = 1;
    if (vkAllocateCommandBuffers(dispositivo_, &cai, &comando_) != VK_SUCCESS) {
        LOGE("Vulkan: vkAllocateCommandBuffers falló");
        return false;
    }

    VkPhysicalDeviceProperties props{};
    vkGetPhysicalDeviceProperties(fisico_, &props);
    nombre_ = std::string("Vulkan (") + props.deviceName + ")";
    LOGI("Vulkan: %s | API %u.%u.%u", props.deviceName,
         VK_VERSION_MAJOR(props.apiVersion), VK_VERSION_MINOR(props.apiVersion),
         VK_VERSION_PATCH(props.apiVersion));
    return true;
}

bool RenderizadorVulkan::crearInstancia() {
    // Comprobamos la extensión de superficie antes de pedirla.
    uint32_t n = 0;
    vkEnumerateInstanceExtensionProperties(nullptr, &n, nullptr);
    std::vector<VkExtensionProperties> extensiones(n);
    vkEnumerateInstanceExtensionProperties(nullptr, &n, extensiones.data());
    bool tieneSuperficie = false;
    bool tieneAndroid = false;
    for (const auto& e : extensiones) {
        if (std::strcmp(e.extensionName, VK_KHR_SURFACE_EXTENSION_NAME) == 0) tieneSuperficie = true;
        if (std::strcmp(e.extensionName, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME) == 0) tieneAndroid = true;
    }
    if (!tieneSuperficie || !tieneAndroid) {
        LOGE("Vulkan: faltan extensiones de superficie (surface=%d android=%d)",
             tieneSuperficie, tieneAndroid);
        return false;
    }

    VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
    app.pApplicationName = "Dibujo";
    app.applicationVersion = VK_MAKE_VERSION(0, 3, 0);
    app.pEngineName = "dibujo-engine";
    app.engineVersion = VK_MAKE_VERSION(1, 0, 0);
    app.apiVersion = VK_API_VERSION_1_0;

    const char* nombres[] = {VK_KHR_SURFACE_EXTENSION_NAME,
                             VK_KHR_ANDROID_SURFACE_EXTENSION_NAME};
    VkInstanceCreateInfo ci{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
    ci.pApplicationInfo = &app;
    ci.enabledExtensionCount = 2;
    ci.ppEnabledExtensionNames = nombres;
    if (vkCreateInstance(&ci, nullptr, &instancia_) != VK_SUCCESS) {
        LOGE("Vulkan: vkCreateInstance falló");
        return false;
    }
    return true;
}

bool RenderizadorVulkan::crearSuperficie(ANativeWindow* ventana) {
    VkAndroidSurfaceCreateInfoKHR ci{VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR};
    ci.window = ventana;
    if (vkCreateAndroidSurfaceKHR(instancia_, &ci, nullptr, &superficie_) != VK_SUCCESS) {
        LOGE("Vulkan: vkCreateAndroidSurfaceKHR falló");
        return false;
    }
    return true;
}

uint32_t RenderizadorVulkan::buscarFamiliaCola(VkPhysicalDevice fisico) const {
    uint32_t n = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(fisico, &n, nullptr);
    std::vector<VkQueueFamilyProperties> familias(n);
    vkGetPhysicalDeviceQueueFamilyProperties(fisico, &n, familias.data());
    for (uint32_t i = 0; i < n; ++i) {
        if (familias[i].queueCount == 0) continue;
        if ((familias[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) == 0) continue;
        VkBool32 soporta = VK_FALSE;
        vkGetPhysicalDeviceSurfaceSupportKHR(fisico, i, superficie_, &soporta);
        if (soporta == VK_TRUE) return i;
    }
    return UINT32_MAX;
}

bool RenderizadorVulkan::elegirFisico() {
    uint32_t n = 0;
    if (vkEnumeratePhysicalDevices(instancia_, &n, nullptr) != VK_SUCCESS || n == 0) {
        LOGE("Vulkan: no hay dispositivos físicos");
        return false;
    }
    std::vector<VkPhysicalDevice> dispositivos(n);
    vkEnumeratePhysicalDevices(instancia_, &n, dispositivos.data());
    for (VkPhysicalDevice d : dispositivos) {
        if (!soportaExtension(d, kExtensionSwapchain)) continue;
        const uint32_t familia = buscarFamiliaCola(d);
        if (familia == UINT32_MAX) continue;
        fisico_ = d;
        familiaCola_ = familia;
        return true;
    }
    LOGE("Vulkan: ningún dispositivo con cola gráfica + swapchain");
    return false;
}

bool RenderizadorVulkan::crearDispositivo() {
    const float prioridad = 1.0f;
    VkDeviceQueueCreateInfo qci{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
    qci.queueFamilyIndex = familiaCola_;
    qci.queueCount = 1;
    qci.pQueuePriorities = &prioridad;

    VkDeviceCreateInfo dci{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
    dci.queueCreateInfoCount = 1;
    dci.pQueueCreateInfos = &qci;
    dci.enabledExtensionCount = 1;
    dci.ppEnabledExtensionNames = &kExtensionSwapchain;
    if (vkCreateDevice(fisico_, &dci, nullptr, &dispositivo_) != VK_SUCCESS) {
        LOGE("Vulkan: vkCreateDevice falló");
        return false;
    }
    vkGetDeviceQueue(dispositivo_, familiaCola_, 0, &cola_);
    return true;
}

uint32_t RenderizadorVulkan::buscarTipoMemoria(uint32_t filtro,
                                               VkMemoryPropertyFlags props) const {
    VkPhysicalDeviceMemoryProperties mem{};
    vkGetPhysicalDeviceMemoryProperties(fisico_, &mem);
    for (uint32_t i = 0; i < mem.memoryTypeCount; ++i) {
        if ((filtro & (1u << i)) &&
            (mem.memoryTypes[i].propertyFlags & props) == props) {
            return i;
        }
    }
    return UINT32_MAX;
}

bool RenderizadorVulkan::crearBuffer(VkDeviceSize tamano, VkBufferUsageFlags uso,
                                     VkMemoryPropertyFlags props, VkBuffer& buffer,
                                     VkDeviceMemory& memoria) {
    VkBufferCreateInfo bci{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
    bci.size = tamano;
    bci.usage = uso;
    bci.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    if (vkCreateBuffer(dispositivo_, &bci, nullptr, &buffer) != VK_SUCCESS) return false;

    VkMemoryRequirements req{};
    vkGetBufferMemoryRequirements(dispositivo_, buffer, &req);
    const uint32_t tipo = buscarTipoMemoria(req.memoryTypeBits, props);
    if (tipo == UINT32_MAX) {
        vkDestroyBuffer(dispositivo_, buffer, nullptr);
        buffer = VK_NULL_HANDLE;
        return false;
    }
    VkMemoryAllocateInfo ai{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
    ai.allocationSize = req.size;
    ai.memoryTypeIndex = tipo;
    if (vkAllocateMemory(dispositivo_, &ai, nullptr, &memoria) != VK_SUCCESS) {
        vkDestroyBuffer(dispositivo_, buffer, nullptr);
        buffer = VK_NULL_HANDLE;
        return false;
    }
    vkBindBufferMemory(dispositivo_, buffer, memoria, 0);
    return true;
}

VkCommandBuffer RenderizadorVulkan::comenzarComandoUnico() {
    VkCommandBufferAllocateInfo ai{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
    ai.commandPool = pool_;
    ai.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    ai.commandBufferCount = 1;
    VkCommandBuffer cmd = VK_NULL_HANDLE;
    if (vkAllocateCommandBuffers(dispositivo_, &ai, &cmd) != VK_SUCCESS) return VK_NULL_HANDLE;
    VkCommandBufferBeginInfo bi{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
    bi.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    vkBeginCommandBuffer(cmd, &bi);
    return cmd;
}

void RenderizadorVulkan::terminarComandoUnico(VkCommandBuffer cmd) {
    if (cmd == VK_NULL_HANDLE) return;
    vkEndCommandBuffer(cmd);
    VkSubmitInfo si{VK_STRUCTURE_TYPE_SUBMIT_INFO};
    si.commandBufferCount = 1;
    si.pCommandBuffers = &cmd;
    vkQueueSubmit(cola_, 1, &si, VK_NULL_HANDLE);
    vkQueueWaitIdle(cola_);
    vkFreeCommandBuffers(dispositivo_, pool_, 1, &cmd);
}

bool RenderizadorVulkan::crearHoja() {
    VkImageCreateInfo ici{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
    ici.imageType = VK_IMAGE_TYPE_2D;
    ici.format = VK_FORMAT_R8G8B8A8_UNORM;
    ici.extent = {static_cast<uint32_t>(anchoHoja_), static_cast<uint32_t>(altoHoja_), 1};
    ici.mipLevels = 1;
    ici.arrayLayers = 1;
    ici.samples = VK_SAMPLE_COUNT_1_BIT;
    ici.tiling = VK_IMAGE_TILING_OPTIMAL;
    ici.usage = VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT;
    ici.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    ici.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    if (vkCreateImage(dispositivo_, &ici, nullptr, &hoja_) != VK_SUCCESS) {
        LOGE("Vulkan: vkCreateImage (hoja) falló");
        return false;
    }

    VkMemoryRequirements req{};
    vkGetImageMemoryRequirements(dispositivo_, hoja_, &req);
    uint32_t tipo = buscarTipoMemoria(req.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    if (tipo == UINT32_MAX) tipo = buscarTipoMemoria(req.memoryTypeBits, 0);
    if (tipo == UINT32_MAX) return false;
    VkMemoryAllocateInfo ai{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
    ai.allocationSize = req.size;
    ai.memoryTypeIndex = tipo;
    if (vkAllocateMemory(dispositivo_, &ai, nullptr, &memoriaHoja_) != VK_SUCCESS) return false;
    vkBindImageMemory(dispositivo_, hoja_, memoriaHoja_, 0);

    VkImageViewCreateInfo vci{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
    vci.image = hoja_;
    vci.viewType = VK_IMAGE_VIEW_TYPE_2D;
    vci.format = VK_FORMAT_R8G8B8A8_UNORM;
    vci.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    if (vkCreateImageView(dispositivo_, &vci, nullptr, &vistaHoja_) != VK_SUCCESS) return false;

    tamanoStaging_ = static_cast<VkDeviceSize>(anchoHoja_) * altoHoja_ * 4;
    if (!crearBuffer(tamanoStaging_, VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                     VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                     staging_, memoriaStaging_)) {
        LOGE("Vulkan: no se pudo crear el buffer de subida");
        return false;
    }
    subirHoja(VK_NULL_HANDLE); // subida inicial + transición a SHADER_READ_ONLY
    return true;
}

void RenderizadorVulkan::subirHoja(VkCommandBuffer externo) {
    // Igual que en OpenGL: la revisión 0 (hoja recién creada) es válida y debe
    // subirse. Descartarla dejaba la imagen de la hoja sin inicializar, así que
    // el lienzo se veía negro hasta el primer cambio de contenido.
    const uint64_t rev = motor_->copiarHoja(buffer_.data(), buffer_.size());

    void* destino = nullptr;
    if (vkMapMemory(dispositivo_, memoriaStaging_, 0, tamanoStaging_, 0, &destino) == VK_SUCCESS) {
        std::memcpy(destino, buffer_.data(), static_cast<std::size_t>(tamanoStaging_));
        vkUnmapMemory(dispositivo_, memoriaStaging_);
    }

    const bool propio = externo == VK_NULL_HANDLE;
    VkCommandBuffer cmd = propio ? comenzarComandoUnico() : externo;
    if (cmd == VK_NULL_HANDLE) return;

    VkImageSubresourceRange rango{VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    if (disposicionHoja_ == VK_IMAGE_LAYOUT_UNDEFINED) {
        VkImageMemoryBarrier a{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
        a.oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        a.newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
        a.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        a.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        a.image = hoja_;
        a.subresourceRange = rango;
        a.srcAccessMask = 0;
        a.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                             VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0, nullptr, 1, &a);
        disposicionHoja_ = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    }

    VkBufferImageCopy copia{};
    copia.imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    copia.imageExtent = {static_cast<uint32_t>(anchoHoja_),
                         static_cast<uint32_t>(altoHoja_), 1};
    vkCmdCopyBufferToImage(cmd, staging_, hoja_, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &copia);

    VkImageMemoryBarrier b{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
    b.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    b.newLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    b.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    b.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    b.image = hoja_;
    b.subresourceRange = rango;
    b.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    b.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
    vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT,
                         VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, 0, nullptr, 0, nullptr, 1, &b);
    disposicionHoja_ = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

    if (propio) terminarComandoUnico(cmd);
    revision_ = rev;
}

bool RenderizadorVulkan::crearDescriptores() {
    VkSamplerCreateInfo sci{VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO};
    sci.magFilter = VK_FILTER_NEAREST;
    sci.minFilter = VK_FILTER_NEAREST;
    sci.mipmapMode = VK_SAMPLER_MIPMAP_MODE_NEAREST;
    sci.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    sci.addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    sci.addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    sci.maxLod = 0.0f;
    if (vkCreateSampler(dispositivo_, &sci, nullptr, &muestreo_) != VK_SUCCESS) return false;

    VkDescriptorSetLayoutBinding binding{};
    binding.binding = 0;
    binding.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    binding.descriptorCount = 1;
    binding.stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;
    VkDescriptorSetLayoutCreateInfo lci{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
    lci.bindingCount = 1;
    lci.pBindings = &binding;
    if (vkCreateDescriptorSetLayout(dispositivo_, &lci, nullptr, &disenoDescriptores_) != VK_SUCCESS) {
        return false;
    }

    VkDescriptorPoolSize tamano{};
    tamano.type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    tamano.descriptorCount = 1;
    VkDescriptorPoolCreateInfo pci{VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
    pci.maxSets = 1;
    pci.poolSizeCount = 1;
    pci.pPoolSizes = &tamano;
    if (vkCreateDescriptorPool(dispositivo_, &pci, nullptr, &poolDescriptores_) != VK_SUCCESS) {
        return false;
    }
    VkDescriptorSetAllocateInfo ai{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
    ai.descriptorPool = poolDescriptores_;
    ai.descriptorSetCount = 1;
    ai.pSetLayouts = &disenoDescriptores_;
    if (vkAllocateDescriptorSets(dispositivo_, &ai, &descriptores_) != VK_SUCCESS) return false;

    VkDescriptorImageInfo info{};
    info.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    info.imageView = vistaHoja_;
    info.sampler = muestreo_;
    VkWriteDescriptorSet escritura{VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
    escritura.dstSet = descriptores_;
    escritura.dstBinding = 0;
    escritura.descriptorCount = 1;
    escritura.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    escritura.pImageInfo = &info;
    vkUpdateDescriptorSets(dispositivo_, 1, &escritura, 0, nullptr);
    return true;
}

bool RenderizadorVulkan::crearCadena() {
    VkSurfaceCapabilitiesKHR caps{};
    if (vkGetPhysicalDeviceSurfaceCapabilitiesKHR(fisico_, superficie_, &caps) != VK_SUCCESS) {
        LOGE("Vulkan: vkGetPhysicalDeviceSurfaceCapabilitiesKHR falló");
        return false;
    }

    uint32_t nFormatos = 0;
    vkGetPhysicalDeviceSurfaceFormatsKHR(fisico_, superficie_, &nFormatos, nullptr);
    std::vector<VkSurfaceFormatKHR> formatos(nFormatos);
    vkGetPhysicalDeviceSurfaceFormatsKHR(fisico_, superficie_, &nFormatos, formatos.data());
    if (nFormatos == 0) return false;
    VkSurfaceFormatKHR elegido = formatos[0];
    for (const auto& f : formatos) {
        if (f.format == VK_FORMAT_R8G8B8A8_UNORM) { elegido = f; break; }
        if (f.format == VK_FORMAT_B8G8R8A8_UNORM) { elegido = f; }
    }
    formato_ = elegido.format;

    uint32_t nModos = 0;
    vkGetPhysicalDeviceSurfacePresentModesKHR(fisico_, superficie_, &nModos, nullptr);
    std::vector<VkPresentModeKHR> modos(nModos);
    vkGetPhysicalDeviceSurfacePresentModesKHR(fisico_, superficie_, &nModos, modos.data());
    VkPresentModeKHR modo = VK_PRESENT_MODE_FIFO_KHR; // siempre disponible
    (void)modos;

    if (caps.currentExtent.width != UINT32_MAX) {
        extension_ = caps.currentExtent;
    } else {
        VkExtent2D e{
            static_cast<uint32_t>(vw_ > 0 ? vw_ : caps.minImageExtent.width),
            static_cast<uint32_t>(vh_ > 0 ? vh_ : caps.minImageExtent.height)};
        e.width = std::clamp(e.width, caps.minImageExtent.width, caps.maxImageExtent.width);
        e.height = std::clamp(e.height, caps.minImageExtent.height, caps.maxImageExtent.height);
        extension_ = e;
    }
    if (extension_.width == 0 || extension_.height == 0) return false;

    uint32_t cantidad = caps.minImageCount + 1;
    if (caps.maxImageCount > 0 && cantidad > caps.maxImageCount) cantidad = caps.maxImageCount;

    VkSwapchainCreateInfoKHR ci{VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR};
    ci.surface = superficie_;
    ci.minImageCount = cantidad;
    ci.imageFormat = formato_;
    ci.imageColorSpace = elegido.colorSpace;
    ci.imageExtent = extension_;
    ci.imageArrayLayers = 1;
    ci.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
    ci.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
    ci.preTransform = caps.currentTransform;
    ci.compositeAlpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
    ci.presentMode = modo;
    ci.clipped = VK_TRUE;
    ci.oldSwapchain = VK_NULL_HANDLE;
    if (vkCreateSwapchainKHR(dispositivo_, &ci, nullptr, &cadena_) != VK_SUCCESS) {
        LOGE("Vulkan: vkCreateSwapchainKHR falló");
        return false;
    }

    uint32_t nImagenes = 0;
    vkGetSwapchainImagesKHR(dispositivo_, cadena_, &nImagenes, nullptr);
    imagenes_.resize(nImagenes);
    vkGetSwapchainImagesKHR(dispositivo_, cadena_, &nImagenes, imagenes_.data());

    vistas_.resize(nImagenes);
    for (uint32_t i = 0; i < nImagenes; ++i) {
        VkImageViewCreateInfo vci{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
        vci.image = imagenes_[i];
        vci.viewType = VK_IMAGE_VIEW_TYPE_2D;
        vci.format = formato_;
        vci.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
        if (vkCreateImageView(dispositivo_, &vci, nullptr, &vistas_[i]) != VK_SUCCESS) return false;
    }
    return true;
}

bool RenderizadorVulkan::crearPasada() {
    VkAttachmentDescription color{};
    color.format = formato_;
    color.samples = VK_SAMPLE_COUNT_1_BIT;
    color.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
    color.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
    color.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    color.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    color.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    color.finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;

    VkAttachmentReference ref{0, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
    VkSubpassDescription subpaso{};
    subpaso.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
    subpaso.colorAttachmentCount = 1;
    subpaso.pColorAttachments = &ref;

    VkSubpassDependency dep{};
    dep.srcSubpass = VK_SUBPASS_EXTERNAL;
    dep.dstSubpass = 0;
    dep.srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    dep.dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    dep.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;

    VkRenderPassCreateInfo ci{VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO};
    ci.attachmentCount = 1;
    ci.pAttachments = &color;
    ci.subpassCount = 1;
    ci.pSubpasses = &subpaso;
    ci.dependencyCount = 1;
    ci.pDependencies = &dep;
    if (vkCreateRenderPass(dispositivo_, &ci, nullptr, &pasada_) != VK_SUCCESS) {
        LOGE("Vulkan: vkCreateRenderPass falló");
        return false;
    }
    return true;
}

bool RenderizadorVulkan::crearTuberia() {
    VkShaderModule vert = VK_NULL_HANDLE;
    VkShaderModule frag = VK_NULL_HANDLE;
    {
        VkShaderModuleCreateInfo ci{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};
        ci.codeSize = kSpvVertTam;
        ci.pCode = reinterpret_cast<const uint32_t*>(kSpvVert);
        if (vkCreateShaderModule(dispositivo_, &ci, nullptr, &vert) != VK_SUCCESS) return false;
        ci.codeSize = kSpvFragTam;
        ci.pCode = reinterpret_cast<const uint32_t*>(kSpvFrag);
        if (vkCreateShaderModule(dispositivo_, &ci, nullptr, &frag) != VK_SUCCESS) {
            vkDestroyShaderModule(dispositivo_, vert, nullptr);
            return false;
        }
    }

    VkPipelineShaderStageCreateInfo etapas[2]{};
    etapas[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    etapas[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
    etapas[0].module = vert;
    etapas[0].pName = "main";
    etapas[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    etapas[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
    etapas[1].module = frag;
    etapas[1].pName = "main";

    VkPipelineVertexInputStateCreateInfo vertex{VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO};
    VkPipelineInputAssemblyStateCreateInfo ensamblado{VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO};
    ensamblado.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;

    VkPipelineViewportStateCreateInfo viewport{VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO};
    viewport.viewportCount = 1;
    viewport.scissorCount = 1;

    VkPipelineRasterizationStateCreateInfo raster{VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO};
    raster.polygonMode = VK_POLYGON_MODE_FILL;
    raster.cullMode = VK_CULL_MODE_NONE;
    raster.frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE;
    raster.lineWidth = 1.0f;

    VkPipelineMultisampleStateCreateInfo muestreo{VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO};
    muestreo.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;

    VkPipelineColorBlendAttachmentState mezcla{};
    mezcla.colorWriteMask = VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                            VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT;
    mezcla.blendEnable = VK_FALSE;
    VkPipelineColorBlendStateCreateInfo color{VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO};
    color.attachmentCount = 1;
    color.pAttachments = &mezcla;

    VkDynamicState dinamicos[] = {VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR};
    VkPipelineDynamicStateCreateInfo dinamico{VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO};
    dinamico.dynamicStateCount = 2;
    dinamico.pDynamicStates = dinamicos;

    VkPushConstantRange rango{};
    rango.stageFlags = VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT;
    rango.offset = 0;
    rango.size = sizeof(ParametrosPresentacion);

    VkPipelineLayoutCreateInfo lci{VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
    lci.setLayoutCount = 1;
    lci.pSetLayouts = &disenoDescriptores_;
    lci.pushConstantRangeCount = 1;
    lci.pPushConstantRanges = &rango;
    if (vkCreatePipelineLayout(dispositivo_, &lci, nullptr, &diseno_) != VK_SUCCESS) {
        vkDestroyShaderModule(dispositivo_, vert, nullptr);
        vkDestroyShaderModule(dispositivo_, frag, nullptr);
        return false;
    }

    VkGraphicsPipelineCreateInfo pci{VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO};
    pci.stageCount = 2;
    pci.pStages = etapas;
    pci.pVertexInputState = &vertex;
    pci.pInputAssemblyState = &ensamblado;
    pci.pViewportState = &viewport;
    pci.pRasterizationState = &raster;
    pci.pMultisampleState = &muestreo;
    pci.pColorBlendState = &color;
    pci.pDynamicState = &dinamico;
    pci.layout = diseno_;
    pci.renderPass = pasada_;
    pci.subpass = 0;
    const VkResult resultado = vkCreateGraphicsPipelines(dispositivo_, VK_NULL_HANDLE, 1, &pci, nullptr, &tuberia_);
    vkDestroyShaderModule(dispositivo_, vert, nullptr);
    vkDestroyShaderModule(dispositivo_, frag, nullptr);
    if (resultado != VK_SUCCESS) {
        LOGE("Vulkan: vkCreateGraphicsPipelines falló (%d)", resultado);
        return false;
    }
    return true;
}

bool RenderizadorVulkan::crearDestinos() {
    marcos_.resize(vistas_.size());
    for (std::size_t i = 0; i < vistas_.size(); ++i) {
        VkImageView adjuntos[] = {vistas_[i]};
        VkFramebufferCreateInfo ci{VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO};
        ci.renderPass = pasada_;
        ci.attachmentCount = 1;
        ci.pAttachments = adjuntos;
        ci.width = extension_.width;
        ci.height = extension_.height;
        ci.layers = 1;
        if (vkCreateFramebuffer(dispositivo_, &ci, nullptr, &marcos_[i]) != VK_SUCCESS) return false;
    }
    return true;
}

bool RenderizadorVulkan::crearSincronizacion() {
    VkSemaphoreCreateInfo sci{VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
    if (vkCreateSemaphore(dispositivo_, &sci, nullptr, &disponible_) != VK_SUCCESS) return false;

    const std::size_t n = imagenes_.size();
    presentado_.resize(n);
    for (std::size_t i = 0; i < n; ++i) {
        if (vkCreateSemaphore(dispositivo_, &sci, nullptr, &presentado_[i]) != VK_SUCCESS) return false;
    }

    VkFenceCreateInfo fci{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
    fci.flags = VK_FENCE_CREATE_SIGNALED_BIT;
    enVuelo_.resize(n);
    for (std::size_t i = 0; i < n; ++i) {
        if (vkCreateFence(dispositivo_, &fci, nullptr, &enVuelo_[i]) != VK_SUCCESS) return false;
    }
    return true;
}

void RenderizadorVulkan::limpiarCadena() {
    for (VkFramebuffer f : marcos_) {
        if (f != VK_NULL_HANDLE) vkDestroyFramebuffer(dispositivo_, f, nullptr);
    }
    marcos_.clear();
    for (VkImageView v : vistas_) {
        if (v != VK_NULL_HANDLE) vkDestroyImageView(dispositivo_, v, nullptr);
    }
    vistas_.clear();
    if (cadena_ != VK_NULL_HANDLE) {
        vkDestroySwapchainKHR(dispositivo_, cadena_, nullptr);
        cadena_ = VK_NULL_HANDLE;
    }
    for (VkSemaphore s : presentado_) {
        if (s != VK_NULL_HANDLE) vkDestroySemaphore(dispositivo_, s, nullptr);
    }
    presentado_.clear();
    for (VkFence f : enVuelo_) {
        if (f != VK_NULL_HANDLE) vkDestroyFence(dispositivo_, f, nullptr);
    }
    enVuelo_.clear();
    imagenes_.clear();
}

bool RenderizadorVulkan::recrearCadena() {
    vkDeviceWaitIdle(dispositivo_);
    limpiarCadena();
    if (vw_ > 0 && vh_ > 0) {
        // El tamaño de la ventana manda cuando la superficie no lo fija.
    }
    if (!crearCadena()) return false;
    if (!crearDestinos()) return false;
    if (!crearSincronizacion()) return false;
    cuadro_ = 0;
    reiniciarCadena_ = false;
    return true;
}

bool RenderizadorVulkan::render() {
    if (dispositivo_ == VK_NULL_HANDLE || cadena_ == VK_NULL_HANDLE) return false;
    if (reiniciarCadena_ && !recrearCadena()) return false;

    VkFence& valla = enVuelo_[cuadro_];
    vkWaitForFences(dispositivo_, 1, &valla, VK_TRUE, UINT64_MAX);

    uint32_t indice = 0;
    const VkResult adquiere =
        vkAcquireNextImageKHR(dispositivo_, cadena_, UINT64_MAX, disponible_, VK_NULL_HANDLE, &indice);
    if (adquiere == VK_ERROR_OUT_OF_DATE_KHR) {
        reiniciarCadena_ = true;
        return true;
    }
    if (adquiere == VK_SUBOPTIMAL_KHR) {
        reiniciarCadena_ = true;
    } else if (adquiere != VK_SUCCESS) {
        return false;
    }

    vkResetFences(dispositivo_, 1, &valla);

    if (motor_->revision() != revision_) subirHoja(VK_NULL_HANDLE);

    float panX = 0.0f;
    float panY = 0.0f;
    float zoom = 1.0f;
    motor_->leerCamara(panX, panY, zoom);

    ParametrosPresentacion p{};
    p.fuera[0] = 236.0f / 255.0f;
    p.fuera[1] = 236.0f / 255.0f;
    p.fuera[2] = 236.0f / 255.0f;
    p.fuera[3] = 1.0f;
    p.pantalla[0] = static_cast<float>(extension_.width);
    p.pantalla[1] = static_cast<float>(extension_.height);
    p.hojaTam[0] = static_cast<float>(anchoHoja_);
    p.hojaTam[1] = static_cast<float>(altoHoja_);
    p.pan[0] = panX;
    p.pan[1] = panY;
    p.zoom = zoom;
    p.flipY = 0.0f; // Vulkan: origen arriba

    vkResetCommandBuffer(comando_, 0);
    VkCommandBufferBeginInfo bi{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
    vkBeginCommandBuffer(comando_, &bi);

    VkViewport vp{0.0f, 0.0f, static_cast<float>(extension_.width),
                  static_cast<float>(extension_.height), 0.0f, 1.0f};
    vkCmdSetViewport(comando_, 0, 1, &vp);
    VkRect2D tijera{{0, 0}, extension_};
    vkCmdSetScissor(comando_, 0, 1, &tijera);

    VkClearValue limpieza{};
    limpieza.color = {{p.fuera[0], p.fuera[1], p.fuera[2], 1.0f}};
    VkRenderPassBeginInfo rp{VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO};
    rp.renderPass = pasada_;
    rp.framebuffer = marcos_[indice];
    rp.renderArea = tijera;
    rp.clearValueCount = 1;
    rp.pClearValues = &limpieza;
    vkCmdBeginRenderPass(comando_, &rp, VK_SUBPASS_CONTENTS_INLINE);
    vkCmdBindPipeline(comando_, VK_PIPELINE_BIND_POINT_GRAPHICS, tuberia_);
    vkCmdBindDescriptorSets(comando_, VK_PIPELINE_BIND_POINT_GRAPHICS, diseno_, 0, 1,
                            &descriptores_, 0, nullptr);
    vkCmdPushConstants(comando_, diseno_,
                       VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0,
                       sizeof(ParametrosPresentacion), &p);
    vkCmdDraw(comando_, 3, 1, 0, 0);
    vkCmdEndRenderPass(comando_);
    vkEndCommandBuffer(comando_);

    VkPipelineStageFlags espera = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    VkSubmitInfo si{VK_STRUCTURE_TYPE_SUBMIT_INFO};
    si.waitSemaphoreCount = 1;
    si.pWaitSemaphores = &disponible_;
    si.pWaitDstStageMask = &espera;
    si.commandBufferCount = 1;
    si.pCommandBuffers = &comando_;
    si.signalSemaphoreCount = 1;
    si.pSignalSemaphores = &presentado_[indice];
    if (vkQueueSubmit(cola_, 1, &si, valla) != VK_SUCCESS) return false;

    VkPresentInfoKHR pi{VK_STRUCTURE_TYPE_PRESENT_INFO_KHR};
    pi.waitSemaphoreCount = 1;
    pi.pWaitSemaphores = &presentado_[indice];
    pi.swapchainCount = 1;
    pi.pSwapchains = &cadena_;
    pi.pImageIndices = &indice;
    const VkResult presenta = vkQueuePresentKHR(cola_, &pi);
    if (presenta == VK_ERROR_OUT_OF_DATE_KHR || presenta == VK_SUBOPTIMAL_KHR) {
        reiniciarCadena_ = true;
    } else if (presenta != VK_SUCCESS) {
        return false;
    }

    cuadro_ = (cuadro_ + 1) % enVuelo_.size();
    return true;
}

void RenderizadorVulkan::redimensionar(int ancho, int alto) {
    if (ancho <= 0 || alto <= 0) return;
    vw_ = ancho;
    vh_ = alto;
    reiniciarCadena_ = true;
}

void RenderizadorVulkan::liberar() {
    if (dispositivo_ != VK_NULL_HANDLE) {
        vkDeviceWaitIdle(dispositivo_);
        limpiarCadena();
        if (comando_ != VK_NULL_HANDLE) {
            vkFreeCommandBuffers(dispositivo_, pool_, 1, &comando_);
            comando_ = VK_NULL_HANDLE;
        }
        if (tuberia_ != VK_NULL_HANDLE) vkDestroyPipeline(dispositivo_, tuberia_, nullptr);
        if (diseno_ != VK_NULL_HANDLE) vkDestroyPipelineLayout(dispositivo_, diseno_, nullptr);
        if (pasada_ != VK_NULL_HANDLE) vkDestroyRenderPass(dispositivo_, pasada_, nullptr);
        if (poolDescriptores_ != VK_NULL_HANDLE) vkDestroyDescriptorPool(dispositivo_, poolDescriptores_, nullptr);
        if (disenoDescriptores_ != VK_NULL_HANDLE) vkDestroyDescriptorSetLayout(dispositivo_, disenoDescriptores_, nullptr);
        if (muestreo_ != VK_NULL_HANDLE) vkDestroySampler(dispositivo_, muestreo_, nullptr);
        if (vistaHoja_ != VK_NULL_HANDLE) vkDestroyImageView(dispositivo_, vistaHoja_, nullptr);
        if (hoja_ != VK_NULL_HANDLE) vkDestroyImage(dispositivo_, hoja_, nullptr);
        if (memoriaHoja_ != VK_NULL_HANDLE) vkFreeMemory(dispositivo_, memoriaHoja_, nullptr);
        if (staging_ != VK_NULL_HANDLE) vkDestroyBuffer(dispositivo_, staging_, nullptr);
        if (memoriaStaging_ != VK_NULL_HANDLE) vkFreeMemory(dispositivo_, memoriaStaging_, nullptr);
        if (pool_ != VK_NULL_HANDLE) vkDestroyCommandPool(dispositivo_, pool_, nullptr);
        if (disponible_ != VK_NULL_HANDLE) vkDestroySemaphore(dispositivo_, disponible_, nullptr);
        vkDestroyDevice(dispositivo_, nullptr);
        dispositivo_ = VK_NULL_HANDLE;
    }
    if (superficie_ != VK_NULL_HANDLE) {
        vkDestroySurfaceKHR(instancia_, superficie_, nullptr);
        superficie_ = VK_NULL_HANDLE;
    }
    if (instancia_ != VK_NULL_HANDLE) {
        vkDestroyInstance(instancia_, nullptr);
        instancia_ = VK_NULL_HANDLE;
    }
    ventana_ = nullptr;
    motor_ = nullptr;
}

} // namespace gpu
} // namespace dibujo
