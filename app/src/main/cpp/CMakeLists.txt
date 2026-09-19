cmake_minimum_required(VERSION 3.22.1)
project(lrhq_whisper)

set(CMAKE_CXX_STANDARD 17)

include(FetchContent)
FetchContent_Declare(
    whisper_cpp
    GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
    GIT_TAG        v1.7.5
    GIT_SHALLOW    TRUE
)
FetchContent_GetProperties(whisper_cpp)
if (NOT whisper_cpp_POPULATED)
    FetchContent_Populate(whisper_cpp)
endif ()

set(WHISPER_LIB_DIR ${whisper_cpp_SOURCE_DIR})
set(WHISPER_VERSION "1.7.5")

# Vulkan for latency, CPU always linked for fallback. Tegra X1 needs our patch.
set(GGML_VULKAN ON CACHE BOOL "ggml vulkan" FORCE)
set(GGML_VULKAN_FP16 OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN_COOPMAT_GLSLC_SUPPORT OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN_COOPMAT2_GLSLC_SUPPORT OFF CACHE BOOL "" FORCE)

# Prefer NDK glslc when present (Android CI / local).
if (DEFINED ANDROID_NDK)
    set(_LRHQ_GLSLC "${ANDROID_NDK}/shader-tools/${CMAKE_HOST_SYSTEM_NAME}-x86_64/glslc")
    string(TOLOWER "${CMAKE_HOST_SYSTEM_NAME}" _host_l)
    if (_host_l STREQUAL "darwin")
        set(_LRHQ_GLSLC "${ANDROID_NDK}/shader-tools/darwin-x86_64/glslc")
    elseif (_host_l STREQUAL "linux")
        set(_LRHQ_GLSLC "${ANDROID_NDK}/shader-tools/linux-x86_64/glslc")
    endif ()
    if (EXISTS "${_LRHQ_GLSLC}")
        set(Vulkan_GLSLC_EXECUTABLE "${_LRHQ_GLSLC}" CACHE FILEPATH "glslc" FORCE)
    endif ()
endif ()

# NDK ships vulkan.h but not vulkan.hpp — ggml-vulkan needs the C++ headers.
FetchContent_Declare(
    VulkanHeaders
    GIT_REPOSITORY https://github.com/KhronosGroup/Vulkan-Headers.git
    GIT_TAG        v1.3.290
    GIT_SHALLOW    TRUE
)
FetchContent_MakeAvailable(VulkanHeaders)
set(Vulkan_INCLUDE_DIR "${vulkanheaders_SOURCE_DIR}/include" CACHE PATH "Vulkan headers (incl. vulkan.hpp)" FORCE)
message(STATUS "LRHQ Vulkan headers: ${Vulkan_INCLUDE_DIR}")

set(_LRHQ_VK_SRC "${WHISPER_LIB_DIR}/ggml/src/ggml-vulkan/ggml-vulkan.cpp")
execute_process(
    COMMAND ${CMAKE_COMMAND} -E env PYTHONUNBUFFERED=1
            python3 "${CMAKE_SOURCE_DIR}/patch_ggml_vulkan_tegra.py" "${_LRHQ_VK_SRC}"
    RESULT_VARIABLE _LRHQ_PATCH_RC
)
if (NOT _LRHQ_PATCH_RC EQUAL 0)
    message(FATAL_ERROR "Failed to patch ggml-vulkan.cpp for Tegra (rc=${_LRHQ_PATCH_RC})")
endif ()

# Cross-compile ExternalProject for vulkan-shaders-gen needs host ninja path.
execute_process(
    COMMAND python3 "${CMAKE_SOURCE_DIR}/patch_vulkan_shaders_host_ninja.py"
            "${WHISPER_LIB_DIR}/ggml/src/ggml-vulkan/CMakeLists.txt"
    RESULT_VARIABLE _LRHQ_NINJA_RC
)
if (NOT _LRHQ_NINJA_RC EQUAL 0)
    message(FATAL_ERROR "Failed to patch vulkan-shaders-gen host ninja (rc=${_LRHQ_NINJA_RC})")
endif ()

message(STATUS "LRHQ whisper.cpp @ ${WHISPER_LIB_DIR} (Vulkan ON + Tegra patch + CPU)")

FetchContent_Declare(ggml SOURCE_DIR ${WHISPER_LIB_DIR}/ggml)
FetchContent_MakeAvailable(ggml)

# Ensure ggml-vulkan sees vulkan.hpp even if FindVulkan cached NDK includes.
if (TARGET ggml-vulkan)
    target_include_directories(ggml-vulkan PRIVATE "${vulkanheaders_SOURCE_DIR}/include")
    find_library(LRHQ_ANDROID_LOG log)
    if (LRHQ_ANDROID_LOG)
        target_link_libraries(ggml-vulkan PRIVATE ${LRHQ_ANDROID_LOG})
    endif ()
endif ()

find_library(LOG_LIB log)
find_library(VULKAN_LIB vulkan)

set(
    SOURCE_FILES
    ${WHISPER_LIB_DIR}/src/whisper.cpp
    ${CMAKE_SOURCE_DIR}/jni.c
)

function(build_whisper_lib target_name)
    add_library(${target_name} SHARED ${SOURCE_FILES})
    target_compile_definitions(${target_name} PUBLIC GGML_USE_CPU GGML_USE_VULKAN)
    target_compile_definitions(${target_name} PRIVATE WHISPER_VERSION="${WHISPER_VERSION}")

    if (${target_name} STREQUAL "whisper_v8fp16_va")
        target_compile_options(${target_name} PRIVATE -march=armv8.2-a+fp16)
        target_compile_options(ggml PRIVATE -march=armv8.2-a+fp16)
    endif ()

    if (NOT ${CMAKE_BUILD_TYPE} STREQUAL "Debug")
        target_compile_options(${target_name} PRIVATE -O3)
        target_compile_options(${target_name} PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden)
        target_compile_options(${target_name} PRIVATE -ffunction-sections -fdata-sections)
        target_link_options(${target_name} PRIVATE -Wl,--gc-sections)
        target_link_options(${target_name} PRIVATE -Wl,--exclude-libs,ALL)
        target_link_options(${target_name} PRIVATE -flto)
    endif ()

    target_include_directories(
        ${target_name} PRIVATE
        ${WHISPER_LIB_DIR}
        ${WHISPER_LIB_DIR}/src
        ${WHISPER_LIB_DIR}/include
        ${WHISPER_LIB_DIR}/ggml/include
        ${WHISPER_LIB_DIR}/ggml/src
        ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu
        ${WHISPER_LIB_DIR}/ggml/src/ggml-vulkan
    )
    target_link_libraries(${target_name} ${LOG_LIB} ${VULKAN_LIB} android ggml)
endfunction()

if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    build_whisper_lib("whisper_v8fp16_va")
endif ()
build_whisper_lib("whisper")
