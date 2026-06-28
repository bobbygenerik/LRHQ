cmake_minimum_required(VERSION 3.22.1)
project("lrhq_translator")

add_library(lrhq_translator SHARED translator_jni.cpp)
target_link_libraries(lrhq_translator android log)
target_compile_options(lrhq_translator PRIVATE -O2 -fno-rtti)
