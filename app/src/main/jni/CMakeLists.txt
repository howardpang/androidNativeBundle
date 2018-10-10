cmake_minimum_required(VERSION 3.4.1)
project(echo LANGUAGES C CXX)


add_library(myapp
  SHARED
    myapp.cpp)

include (${ANDROID_GRADLE_NATIVE_BUNDLE_PLUGIN_MK})
target_link_libraries(myapp ${ANDROID_GRADLE_NATIVE_MODULES})

target_link_libraries(myapp
    log
    )


target_compile_options(myapp
    PRIVATE
    -Wall -Werror)
