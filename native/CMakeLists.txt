cmake_minimum_required(VERSION 3.22)
project(dictator_whisper C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(GGML_HOME ${CMAKE_CURRENT_SOURCE_DIR}/whisper)

if(APPLE AND NOT ANDROID)
    set(GGML_METAL ON CACHE BOOL "ggml: use Metal" FORCE)
    set(GGML_METAL_EMBED_LIBRARY ON CACHE BOOL "ggml: embed Metal library" FORCE)
    set(GGML_BLAS OFF CACHE BOOL "ggml: use BLAS" FORCE)
    set(GGML_OPENMP OFF CACHE BOOL "ggml: use OpenMP" FORCE)
endif()

add_subdirectory(${GGML_HOME}/ggml ggml-build)

add_library(whisper STATIC ${GGML_HOME}/src/whisper.cpp)
target_include_directories(whisper PUBLIC
    ${GGML_HOME}/include
    ${GGML_HOME}/src
    ${GGML_HOME}/ggml/include)
target_link_libraries(whisper PRIVATE ggml)
target_compile_options(whisper PRIVATE -O3)
target_compile_definitions(whisper PRIVATE WHISPER_VERSION="1.9.3")

if(ANDROID)
    option(DICTATOR_BUILD_JNI "Build the Android JNI shared library" ON)
else()
    option(DICTATOR_BUILD_JNI "Build the Android JNI shared library" OFF)
endif()

if(DICTATOR_BUILD_JNI)
    add_library(dictator_whisper SHARED dictator_jni.cpp)
    target_include_directories(dictator_whisper PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
    target_link_libraries(dictator_whisper PRIVATE whisper)
    if(ANDROID)
        # Android 16 KB page-size devices require 16 KB ELF LOAD alignment.
        target_link_options(dictator_whisper PRIVATE
            "-Wl,-z,max-page-size=16384"
            "-Wl,-z,common-page-size=16384")
    endif()
endif()

if(NOT ANDROID)
    add_library(dictator_api SHARED dictator_api.cpp)
    target_include_directories(dictator_api PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
    target_link_libraries(dictator_api PRIVATE whisper ggml)
    set_target_properties(dictator_api PROPERTIES
        PUBLIC_HEADER dictator_api.h
        BUILD_WITH_INSTALL_RPATH ON
        INSTALL_NAME_DIR "@rpath")
    if(APPLE)
        target_link_libraries(dictator_api PRIVATE
            "-framework Metal"
            "-framework Foundation"
            "-framework MetalKit"
            "-framework Accelerate")
    endif()
endif()

add_executable(transcribe_fixture_host host_main.cpp)
target_include_directories(transcribe_fixture_host PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(transcribe_fixture_host PRIVATE whisper)
