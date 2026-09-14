cmake_minimum_required(VERSION 3.22.1)
project(whisper_jni)

# Expects whisper.cpp to be vendored as a git submodule at app/src/main/cpp/whisper.cpp
# See README "Offline models" section for the exact setup command.
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

if(NOT EXISTS ${WHISPER_DIR}/CMakeLists.txt)
    message(FATAL_ERROR
        "whisper.cpp not found at ${WHISPER_DIR}. Run:\n"
        "  git submodule add https://github.com/ggerganov/whisper.cpp app/src/main/cpp/whisper.cpp\n"
        "then re-sync Gradle.")
endif()

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
add_subdirectory(${WHISPER_DIR} whisper_build)

add_library(whisper_jni SHARED whisper_jni.cpp)

target_include_directories(whisper_jni PRIVATE ${WHISPER_DIR}/include ${WHISPER_DIR}/ggml/include)

find_library(log-lib log)

target_link_libraries(whisper_jni whisper ${log-lib} android)
