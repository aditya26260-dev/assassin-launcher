cmake_minimum_required(VERSION 3.22.1)
project("assassinlauncher_native")

add_subdirectory(third_party/libadrenotools)

add_library(${CMAKE_PROJECT_NAME} SHARED
    native_bridge.cpp
    vulkan_probe.cpp
    adreno_driver_bridge.cpp
    egl_loader_bridge.cpp
    jvm_launcher_bridge.cpp
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    android
    log
    dl
    adrenotools
)
