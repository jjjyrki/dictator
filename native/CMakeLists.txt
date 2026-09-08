cmake_minimum_required(VERSION 3.22)
project(dictator_whisper C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(GGML_HOME ${CMAKE_CURRENT_SOURCE_DIR}/whisper)

add_subdirectory(${GGML_HOME}/ggml ggml-build)

add_library(whisper STATIC ${GGML_HOME}/src/whisper.cpp)
target_include_directories(whisper PUBLIC
    ${GGML_HOME}/include
    ${GGML_HOME}/src
    ${GGML_HOME}/ggml/include)
target_link_libraries(whisper PRIVATE ggml)
target_compile_options(whisper PRIVATE -O3)
target_compile_definitions(whisper PRIVATE WHISPER_VERSION="1.9.3")

add_library(dictator_whisper SHARED dictator_jni.cpp)
target_include_directories(dictator_whisper PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(dictator_whisper PRIVATE whisper)
if(ANDROID)
    # Android 16 KB page-size devices require 16 KB ELF LOAD alignment.
    target_link_options(dictator_whisper PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384")
endif()

add_executable(transcribe_fixture_host host_main.cpp)
target_include_directories(transcribe_fixture_host PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(transcribe_fixture_host PRIVATE whisper)
