#!/usr/bin/env python3
"""Pass host CMAKE_MAKE_PROGRAM + generator into vulkan-shaders-gen ExternalProject."""
from __future__ import annotations

import sys
from pathlib import Path

MARKER = "LRHQ_HOST_NINJA"

OLD = """            CMAKE_ARGS -DCMAKE_TOOLCHAIN_FILE=${HOST_CMAKE_TOOLCHAIN_FILE}
                    -DCMAKE_INSTALL_PREFIX=${CMAKE_BINARY_DIR}
                    -DGGML_VULKAN_COOPMAT_GLSLC_SUPPORT=${GGML_VULKAN_COOPMAT_GLSLC_SUPPORT}
                    -DGGML_VULKAN_COOPMAT2_GLSLC_SUPPORT=${GGML_VULKAN_COOPMAT2_GLSLC_SUPPORT}"""

NEW = """            CMAKE_ARGS -DCMAKE_TOOLCHAIN_FILE=${HOST_CMAKE_TOOLCHAIN_FILE}
                    -DCMAKE_INSTALL_PREFIX=${CMAKE_BINARY_DIR}
                    -DCMAKE_MAKE_PROGRAM=${CMAKE_MAKE_PROGRAM}
                    -G${CMAKE_GENERATOR}
                    -DGGML_VULKAN_COOPMAT_GLSLC_SUPPORT=${GGML_VULKAN_COOPMAT_GLSLC_SUPPORT}
                    -DGGML_VULKAN_COOPMAT2_GLSLC_SUPPORT=${GGML_VULKAN_COOPMAT2_GLSLC_SUPPORT}"""


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: patch_vulkan_shaders_host_ninja.py <ggml-vulkan/CMakeLists.txt>", file=sys.stderr)
        return 2
    path = Path(sys.argv[1])
    text = path.read_text(encoding="utf-8")
    if MARKER in text:
        print(f"already patched: {path}")
        return 0
    if OLD not in text:
        print("ExternalProject CMAKE_ARGS block not found", file=sys.stderr)
        return 1
    text = text.replace(OLD, NEW, 1)
    text = text.replace(
        "ExternalProject_Add(\n            vulkan-shaders-gen\n",
        f"ExternalProject_Add(\n            vulkan-shaders-gen # {MARKER}\n",
        1,
    )
    path.write_text(text, encoding="utf-8")
    print(f"patched {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
