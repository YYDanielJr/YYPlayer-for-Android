cmake_minimum_required(VERSION 3.22)
project(yyplayer_core LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
endif()

option(YY_BUILD_TESTS "Build the portable core test runner" ON)
# The Android app builds this tree through Gradle, which needs the bridge; a host build only runs
# the tests, so the default follows whether the NDK toolchain is in use.
if(ANDROID)
    option(YY_BUILD_JNI "Build the Android JNI bridge (requires the NDK)" ON)
else()
    option(YY_BUILD_JNI "Build the Android JNI bridge (requires the NDK)" OFF)
endif()

# The portable core holds every piece of playback logic that is not platform-specific: PCM format
# description, pristine conversion, the sink ring and the output policy. It must keep building for
# a host (Windows/Linux) target because the contract is tested there first.
add_library(yy_core STATIC
    core/src/format.cpp
    core/src/json.cpp
    core/src/pcm.cpp
    core/src/policy.cpp
    core/src/ring.cpp
)
target_include_directories(yy_core PUBLIC core/include)
if(NOT MSVC)
    target_compile_options(yy_core PRIVATE -Wall -Wextra -Wpedantic -Wconversion -Wshadow)
endif()

if(YY_BUILD_TESTS)
    add_executable(yy_core_tests
        core/tests/test_main.cpp
        core/tests/format_test.cpp
        core/tests/json_test.cpp
        core/tests/pcm_test.cpp
        core/tests/policy_test.cpp
        core/tests/ring_test.cpp
    )
    target_link_libraries(yy_core_tests PRIVATE yy_core)
    target_include_directories(yy_core_tests PRIVATE core/tests)
    if(NOT MSVC)
        target_compile_options(yy_core_tests PRIVATE -Wall -Wextra -Wpedantic)
    endif()
    enable_testing()
    add_test(NAME yy_core_tests COMMAND yy_core_tests)
endif()

if(YY_BUILD_JNI)
    add_library(yyplayer_jni SHARED jni/yy_jni.cpp)
    target_link_libraries(yyplayer_jni PRIVATE yy_core)
    # The bridge runs on the control and writer threads, never on a realtime callback, but it still
    # must not throw across the JNI boundary.
    target_compile_options(yyplayer_jni PRIVATE -Wall -Wextra -fno-exceptions -fno-rtti)
    if(MSVC)
        target_compile_options(yyplayer_jni PRIVATE /EHs-c-)
    endif()
endif()
