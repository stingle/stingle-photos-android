cmake_minimum_required(VERSION 3.18.1)

project("editor")
add_library(editor SHARED editor.cpp)
link_libraries("-ljnigraphics")
target_link_libraries(editor jnigraphics)
