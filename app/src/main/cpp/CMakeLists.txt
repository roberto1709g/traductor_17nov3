cmake_minimum_required(VERSION 3.22.1)
project("whisper_jni")

include(FetchContent)

FetchContent_Declare(
    whisper
    GIT_REPOSITORY https://github.com/ggerganov/whisper.cpp.git
    GIT_TAG        v1.7.6
    GIT_SHALLOW    TRUE
)

set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(whisper)

add_library(
    whisper_jni
    SHARED
    whisper_jni.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    whisper_jni
    whisper
    ${log-lib}
)
