cmake_minimum_required(VERSION 3.22.1)
project(cinevault_container_probe LANGUAGES C CXX)

# Set FFMPEG_ROOT to an Android arm64 SDK containing include/ and lib/.
# The project does not ship FFmpeg binaries or fetch unverified artifacts.
if(NOT DEFINED FFMPEG_ROOT)
    message(FATAL_ERROR "FFMPEG_ROOT is required for the optional native probe")
endif()

add_library(avformat SHARED IMPORTED)
add_library(avutil SHARED IMPORTED)
set_target_properties(avformat PROPERTIES IMPORTED_LOCATION "${FFMPEG_ROOT}/lib/libavformat.so")
set_target_properties(avutil PROPERTIES IMPORTED_LOCATION "${FFMPEG_ROOT}/lib/libavutil.so")

add_library(cinevault_container_probe SHARED ffmpeg_container_probe.cpp)
target_include_directories(cinevault_container_probe PRIVATE "${FFMPEG_ROOT}/include")
target_compile_features(cinevault_container_probe PRIVATE cxx_std_17)
target_link_libraries(cinevault_container_probe PRIVATE avformat avutil log)
