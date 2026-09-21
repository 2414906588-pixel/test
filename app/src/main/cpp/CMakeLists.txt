cmake_minimum_required(VERSION 3.22.1)
project(rawdecoder)

add_library(rawdecoder SHARED
        raw_decoder.cpp)

find_library(log-lib log)
target_link_libraries(rawdecoder ${log-lib})
