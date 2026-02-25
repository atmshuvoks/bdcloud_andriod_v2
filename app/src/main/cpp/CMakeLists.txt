cmake_minimum_required(VERSION 3.22.1)
project("bdcloud_native" LANGUAGES C)

add_library(bdcloud_native SHARED native_helper.c)

target_link_libraries(bdcloud_native
    android
    log
)
