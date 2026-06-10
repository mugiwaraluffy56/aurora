cmake_minimum_required(VERSION 3.22.1)

project(aurora_native LANGUAGES CXX)

add_library(
    aurora_native
    SHARED
    native_core.cpp
)

target_compile_features(aurora_native PRIVATE cxx_std_20)

target_link_libraries(
    aurora_native
    android
    log
)
