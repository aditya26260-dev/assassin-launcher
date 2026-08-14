#include <jni.h>
#include <dlfcn.h>
#include <cstring>
#include <string>
#include <vector>
#include <vulkan/vulkan.h>

// Vulkan capability probe backing architecture doc 5.3's rendering
// decision tree. Checks the device's actual Vulkan support against what
// Minecraft 26.2+ needs (Vulkan 1.2, dynamic rendering, push descriptors -
// Phase 0 research, confirmed against the Minecraft Wiki).
//
// Uses dlopen/dlsym instead of linking libvulkan at build time. Capability
// *detection* has to work correctly on devices with no Vulkan driver at
// all, so this can't assume libvulkan.so is even present - a hard link
// dependency would crash the whole native library load on those devices
// instead of just reporting "no Vulkan" cleanly.

namespace {

struct VulkanCapabilities {
    bool vulkanAvailable = false;
    uint32_t apiVersionMajor = 0;
    uint32_t apiVersionMinor = 0;
    bool dynamicRendering = false;
    bool pushDescriptors = false;
    std::string deviceName;
};

VulkanCapabilities probeVulkan() {
    VulkanCapabilities caps;

    void *vulkanLib = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (vulkanLib == nullptr) {
        return caps; // no Vulkan driver present at all
    }

    auto pfn_vkCreateInstance =
            reinterpret_cast<PFN_vkCreateInstance>(dlsym(vulkanLib, "vkCreateInstance"));
    auto pfn_vkDestroyInstance =
            reinterpret_cast<PFN_vkDestroyInstance>(dlsym(vulkanLib, "vkDestroyInstance"));
    auto pfn_vkEnumeratePhysicalDevices =
            reinterpret_cast<PFN_vkEnumeratePhysicalDevices>(
                    dlsym(vulkanLib, "vkEnumeratePhysicalDevices"));
    auto pfn_vkGetPhysicalDeviceProperties =
            reinterpret_cast<PFN_vkGetPhysicalDeviceProperties>(
                    dlsym(vulkanLib, "vkGetPhysicalDeviceProperties"));
    auto pfn_vkGetPhysicalDeviceFeatures2 =
            reinterpret_cast<PFN_vkGetPhysicalDeviceFeatures2>(
                    dlsym(vulkanLib, "vkGetPhysicalDeviceFeatures2"));
    auto pfn_vkEnumerateDeviceExtensionProperties =
            reinterpret_cast<PFN_vkEnumerateDeviceExtensionProperties>(
                    dlsym(vulkanLib, "vkEnumerateDeviceExtensionProperties"));

    if (pfn_vkCreateInstance == nullptr || pfn_vkEnumeratePhysicalDevices == nullptr ||
        pfn_vkGetPhysicalDeviceProperties == nullptr) {
        dlclose(vulkanLib);
        return caps; // present but missing core entry points - treat as unusable
    }

    VkApplicationInfo appInfo{};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "AssassinLauncherCapabilityProbe";
    appInfo.applicationVersion = VK_MAKE_API_VERSION(0, 0, 1, 0);
    appInfo.pEngineName = "AssassinLauncherNative";
    appInfo.engineVersion = VK_MAKE_API_VERSION(0, 0, 1, 0);
    appInfo.apiVersion = VK_API_VERSION_1_2;

    VkInstanceCreateInfo instanceInfo{};
    instanceInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    instanceInfo.pApplicationInfo = &appInfo;

    VkInstance instance = VK_NULL_HANDLE;
    if (pfn_vkCreateInstance(&instanceInfo, nullptr, &instance) != VK_SUCCESS) {
        dlclose(vulkanLib);
        return caps; // driver present but instance creation failed
    }

    uint32_t physicalDeviceCount = 0;
    pfn_vkEnumeratePhysicalDevices(instance, &physicalDeviceCount, nullptr);
    if (physicalDeviceCount == 0) {
        if (pfn_vkDestroyInstance) pfn_vkDestroyInstance(instance, nullptr);
        dlclose(vulkanLib);
        return caps;
    }

    std::vector<VkPhysicalDevice> physicalDevices(physicalDeviceCount);
    pfn_vkEnumeratePhysicalDevices(instance, &physicalDeviceCount, physicalDevices.data());
    VkPhysicalDevice physicalDevice = physicalDevices[0];

    VkPhysicalDeviceProperties props{};
    pfn_vkGetPhysicalDeviceProperties(physicalDevice, &props);

    caps.vulkanAvailable = true;
    caps.apiVersionMajor = VK_API_VERSION_MAJOR(props.apiVersion);
    caps.apiVersionMinor = VK_API_VERSION_MINOR(props.apiVersion);
    caps.deviceName = props.deviceName;

    // Only worth checking dynamic rendering / push descriptors if the
    // device already clears the 1.2 floor Minecraft needs - below that,
    // the answer is already "doesn't meet the requirement" regardless.
    bool meetsVersionFloor = (caps.apiVersionMajor > 1) ||
                              (caps.apiVersionMajor == 1 && caps.apiVersionMinor >= 2);

    if (meetsVersionFloor && pfn_vkGetPhysicalDeviceFeatures2 != nullptr) {
        VkPhysicalDeviceDynamicRenderingFeatures dynamicRenderingFeatures{};
        dynamicRenderingFeatures.sType =
                VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DYNAMIC_RENDERING_FEATURES;

        VkPhysicalDeviceFeatures2 features2{};
        features2.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2;
        features2.pNext = &dynamicRenderingFeatures;

        pfn_vkGetPhysicalDeviceFeatures2(physicalDevice, &features2);
        caps.dynamicRendering = dynamicRenderingFeatures.dynamicRendering == VK_TRUE;
    }

    if (meetsVersionFloor && pfn_vkEnumerateDeviceExtensionProperties != nullptr) {
        uint32_t extensionCount = 0;
        pfn_vkEnumerateDeviceExtensionProperties(physicalDevice, nullptr, &extensionCount,
                                                  nullptr);
        std::vector<VkExtensionProperties> extensions(extensionCount);
        pfn_vkEnumerateDeviceExtensionProperties(physicalDevice, nullptr, &extensionCount,
                                                  extensions.data());
        for (const auto &ext: extensions) {
            if (std::strcmp(ext.extensionName, "VK_KHR_push_descriptor") == 0) {
                caps.pushDescriptors = true;
                break;
            }
        }
    }

    if (pfn_vkDestroyInstance) pfn_vkDestroyInstance(instance, nullptr);
    dlclose(vulkanLib);
    return caps;
}

} // namespace

extern "C" JNIEXPORT jobject JNICALL
Java_com_assassinlauncher_launcher_nativebridge_NativeBridge_probeVulkanCapabilities(
        JNIEnv *env, jobject /* this */) {
    VulkanCapabilities caps = probeVulkan();

    jclass resultClass = env->FindClass(
            "com/assassinlauncher/launcher/nativebridge/VulkanCapabilityResult");
    jmethodID constructor = env->GetMethodID(
            resultClass, "<init>", "(ZIIZZLjava/lang/String;)V");

    jstring deviceName = env->NewStringUTF(caps.deviceName.c_str());

    return env->NewObject(
            resultClass, constructor,
            static_cast<jboolean>(caps.vulkanAvailable),
            static_cast<jint>(caps.apiVersionMajor),
            static_cast<jint>(caps.apiVersionMinor),
            static_cast<jboolean>(caps.dynamicRendering),
            static_cast<jboolean>(caps.pushDescriptors),
            deviceName);
}
