#!/usr/bin/env python3
"""Patch whisper.cpp ggml-vulkan.cpp for Tegra X1 (Vulkan 1.1) + no-abort fallback."""
from __future__ import annotations

import sys
from pathlib import Path


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: patch_ggml_vulkan_tegra.py <ggml-vulkan.cpp>", file=sys.stderr)
        return 2
    path = Path(sys.argv[1])
    text = path.read_text(encoding="utf-8")
    original = text

    # Android log so Shield logcat shows Vulkan path (cerr often invisible).
    if "LRHQ_ANDROID_LOG" not in text:
        needle = "#include <vulkan/vulkan.hpp>\n"
        if needle not in text:
            print("include vulkan.hpp not found", file=sys.stderr)
            return 1
        insert = (
            "#include <vulkan/vulkan.hpp>\n"
            "#ifdef __ANDROID__\n"
            "#include <android/log.h>\n"
            "#define LRHQ_ANDROID_LOG 1\n"
            "#define LRHQ_VK_ALOG(...) "
            '__android_log_print(ANDROID_LOG_WARN, "ggml_vulkan", __VA_ARGS__)\n'
            "#else\n"
            "#define LRHQ_VK_ALOG(...)\n"
            "#endif\n"
        )
        text = text.replace(needle, insert, 1)

    old_abort = """    if (api_version < VK_API_VERSION_1_2) {
        std::cerr << "ggml_vulkan: Error: Vulkan 1.2 required." << std::endl;
        GGML_ABORT("fatal error");
    }

    vk::ApplicationInfo app_info{ "ggml-vulkan", 1, nullptr, 0, api_version };"""

    new_abort = """    // LRHQ: Tegra X1 exposes Vulkan 1.1. Do not abort — try 1.1 device path / CPU fallback.
    if (api_version < VK_API_VERSION_1_1) {
        std::cerr << "ggml_vulkan: Vulkan 1.1+ required; skipping Vulkan backend." << std::endl;
        LRHQ_VK_ALOG("Vulkan 1.1+ required; skipping");
        vk_instance_initialized = true;
        return;
    }
    if (api_version < VK_API_VERSION_1_2) {
        std::cerr << "ggml_vulkan: Vulkan 1.2 unavailable (api=0x" << std::hex << api_version
                  << std::dec << "); using Vulkan 1.1-compatible device create." << std::endl;
        LRHQ_VK_ALOG("Vulkan <1.2 (api=0x%x); using 1.1 createDevice path", api_version);
    }

    vk::ApplicationInfo app_info{ "ggml-vulkan", 1, nullptr, 0, api_version };"""

    if old_abort not in text:
        if "LRHQ: Tegra X1 exposes Vulkan 1.1" in text:
            print(f"already patched abort: {path}")
        else:
            print("abort block not found — check whisper.cpp version", file=sys.stderr)
            return 1
    else:
        text = text.replace(old_abort, new_abort, 1)

    old_create = """        device_create_info.setPNext(&device_features2);
        device->device = device->physical_device.createDevice(device_create_info);

        // Queues
        ggml_vk_create_queue(device, device->compute_queue, compute_queue_family_index, 0, { vk::PipelineStageFlagBits::eComputeShader | vk::PipelineStageFlagBits::eTransfer }, false);"""

    new_create = """        device_create_info.setPNext(&device_features2);
        // LRHQ: sanitize feature chain for Vulkan 1.1 devices (Shield / Tegra X1).
        if (device->properties.apiVersion < VK_API_VERSION_1_2) {
            std::cerr << "ggml_vulkan: pruning VK 1.2+ features for createDevice (api=0x"
                      << std::hex << device->properties.apiVersion << std::dec << ")" << std::endl;
            LRHQ_VK_ALOG("pruning VK 1.2+ features (api=0x%x)", device->properties.apiVersion);
            device_features2.pNext = &vk11_features;
            vk11_features.pNext = nullptr;
            device->fp16 = false;
            device->pipeline_robustness = false;
            device->coopmat_support = false;
            device->integer_dot_product = false;
            device->subgroup_size_control = false;
            // Keep only 16-bit storage if present; drop VK 1.2+ / optional feature extensions.
            device_extensions.clear();
            if (fp16_storage) {
                device_extensions.push_back("VK_KHR_16bit_storage");
            }
            device_create_info = {
                vk::DeviceCreateFlags(),
                device_queue_create_infos,
                {},
                device_extensions
            };
            device_create_info.setPNext(&device_features2);
        }
        try {
            device->device = device->physical_device.createDevice(device_create_info);
            LRHQ_VK_ALOG("createDevice OK idx=%zu api=0x%x", idx, device->properties.apiVersion);
        } catch (const std::exception & e) {
            std::cerr << "ggml_vulkan: createDevice failed: " << e.what()
                      << " — skipping Vulkan device (CPU fallback)" << std::endl;
            LRHQ_VK_ALOG("createDevice failed: %s — CPU fallback", e.what());
            vk_instance.devices[idx] = nullptr;
            return nullptr;
        }

        // Queues
        ggml_vk_create_queue(device, device->compute_queue, compute_queue_family_index, 0, { vk::PipelineStageFlagBits::eComputeShader | vk::PipelineStageFlagBits::eTransfer }, false);"""

    old_16 = """        if (!vk11_features.storageBuffer16BitAccess) {
            std::cerr << "ggml_vulkan: device " << GGML_VK_NAME << idx << " does not support 16-bit storage." << std::endl;
            throw std::runtime_error("Unsupported device");
        }"""
    new_16 = """        if (!vk11_features.storageBuffer16BitAccess) {
            std::cerr << "ggml_vulkan: device " << GGML_VK_NAME << idx
                      << " does not support 16-bit storage — skipping (CPU fallback)" << std::endl;
            LRHQ_VK_ALOG("no 16-bit storage idx=%zu — CPU fallback", idx);
            vk_instance.devices[idx] = nullptr;
            return nullptr;
        }"""
    if old_16 in text:
        text = text.replace(old_16, new_16, 1)
    elif "skipping (CPU fallback)" in text and "16-bit storage" in text:
        print(f"already patched 16-bit skip: {path}")

    if old_create not in text:
        if "LRHQ: sanitize feature chain" in text:
            print(f"already patched createDevice: {path}")
        else:
            print("createDevice block not found — check whisper.cpp version", file=sys.stderr)
            return 1
    else:
        text = text.replace(old_create, new_create, 1)

    old_reg = """    try {
        ggml_vk_instance_init();
        return &reg;
    } catch (const vk::SystemError& e) {
        VK_LOG_DEBUG("ggml_backend_vk_reg() -> Error: System error: " << e.what());
        return nullptr;
    }"""

    new_reg = """    try {
        ggml_vk_instance_init();
        return &reg;
    } catch (const std::exception& e) {
        std::cerr << "ggml_vulkan: backend reg failed: " << e.what()
                  << " — Vulkan disabled (CPU fallback)" << std::endl;
        LRHQ_VK_ALOG("backend reg failed: %s — CPU fallback", e.what());
        return nullptr;
    }"""

    if old_reg in text:
        text = text.replace(old_reg, new_reg, 1)
    elif "Vulkan disabled (CPU fallback)" in text:
        print(f"already patched backend reg: {path}")
    else:
        print("warning: backend reg catch block not found", file=sys.stderr)

    if text == original:
        print(f"no changes written (already patched?): {path}")
        return 0

    path.write_text(text, encoding="utf-8")
    print(f"patched {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
