cmake_minimum_required(VERSION 3.22.1)

project(aurora_native LANGUAGES CXX)

add_library(
    aurora_native
    SHARED
    native_core.cpp
    native_math.cpp
)

target_compile_features(aurora_native PRIVATE cxx_std_20)
target_include_directories(aurora_native PRIVATE include)

target_link_libraries(
    aurora_native
    android
    log
)

# 16KB page size — required for Android 15+ (API 35)
target_link_options(aurora_native PRIVATE "-Wl,-z,max-page-size=16384")
