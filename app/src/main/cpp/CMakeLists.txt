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
message(STATUS "LRHQ whisper.cpp @ ${WHISPER_LIB_DIR}")

FetchContent_Declare(ggml SOURCE_DIR ${WHISPER_LIB_DIR}/ggml)
FetchContent_MakeAvailable(ggml)

find_library(LOG_LIB log)

set(
    SOURCE_FILES
    ${WHISPER_LIB_DIR}/src/whisper.cpp
    ${CMAKE_SOURCE_DIR}/jni.c
)

function(build_whisper_lib target_name)
    add_library(${target_name} SHARED ${SOURCE_FILES})
    target_compile_definitions(${target_name} PUBLIC GGML_USE_CPU)
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
    )
    target_link_libraries(${target_name} ${LOG_LIB} android ggml)
endfunction()

if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    build_whisper_lib("whisper_v8fp16_va")
endif ()
build_whisper_lib("whisper")
