cmake_minimum_required(VERSION 3.22)
project(uchat_pty C)

add_library(uchat_pty SHARED pty.c)

target_link_libraries(uchat_pty
    log
)

target_compile_options(uchat_pty PRIVATE -O2 -Wall)
