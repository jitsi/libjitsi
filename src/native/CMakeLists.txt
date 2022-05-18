cmake_minimum_required(VERSION 3.10)

# capture all arguments to forward to externalproject_add
# https://stackoverflow.com/a/48555098
get_cmake_property(vars CACHE_VARIABLES)
foreach(var ${vars})
    get_property(currentHelpString CACHE "${var}" PROPERTY HELPSTRING)
    if("${currentHelpString}" MATCHES "No help, variable specified on the command line." OR "${currentHelpString}" STREQUAL "")
        # message("${var} = [${${var}}]  --  ${currentHelpString}") # uncomment to see the variables being processed
        list(APPEND CL_ARGS "-D${var}=${${var}}")
    endif()
endforeach()

if (WIN32 OR APPLE OR NOT USE_SYSTEM_VPX)
    set(CMAKE_TOOLCHAIN_FILE ${CMAKE_CURRENT_SOURCE_DIR}/vcpkg/scripts/buildsystems/vcpkg.cmake
        CACHE STRING "Vcpkg toolchain file")
endif ()

if (CMAKE_TOOLCHAIN_FILE MATCHES "vcpkg")
    set(EXTPROJECT_CMAKE_TOOLCHAIN_FILE ${VCPKG_CHAINLOAD_TOOLCHAIN_FILE})
else ()
    set(EXTPROJECT_CMAKE_TOOLCHAIN_FILE ${CMAKE_TOOLCHAIN_FILE})
endif ()

project(libjitsi)
if (CMAKE_INSTALL_PREFIX_INITIALIZED_TO_DEFAULT)
    if (WIN32 AND CMAKE_SIZEOF_VOID_P EQUAL 8)
        set(CMAKE_INSTALL_PREFIX "${CMAKE_CURRENT_LIST_DIR}/../main/resources/win32-x86-64" CACHE PATH "default install path" FORCE)
    elseif (WIN32)
        set(CMAKE_INSTALL_PREFIX "${CMAKE_CURRENT_LIST_DIR}/../main/resources/win32-x86" CACHE PATH "default install path" FORCE)
    elseif (APPLE)
        set(CMAKE_INSTALL_PREFIX "${CMAKE_CURRENT_LIST_DIR}/../main/resources/darwin-${CMAKE_SYSTEM_PROCESSOR}" CACHE PATH "default install path" FORCE)
    elseif (UNIX)
        if (CMAKE_SYSTEM_PROCESSOR STREQUAL "x86_64" AND CMAKE_SIZEOF_VOID_P EQUAL 4)
            set(CMAKE_INSTALL_PREFIX "${CMAKE_CURRENT_LIST_DIR}/../main/resources/linux-x86" CACHE PATH "default install path" FORCE)
        elseif (CMAKE_SYSTEM_PROCESSOR STREQUAL "x86_64")
            set(CMAKE_INSTALL_PREFIX "${CMAKE_CURRENT_LIST_DIR}/../main/resources/linux-x86-64" CACHE PATH "default install path" FORCE)
        else ()
            set(CMAKE_INSTALL_PREFIX "${CMAKE_CURRENT_LIST_DIR}/../main/resources/linux-${CMAKE_SYSTEM_PROCESSOR}" CACHE PATH "default install path" FORCE)
        endif ()
    endif ()
    message(STATUS "CMAKE_INSTALL_PREFIX not set, defaulting to >${CMAKE_INSTALL_PREFIX}<")
endif ()

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_DISABLE_IN_SOURCE_BUILD ON)
set(CMAKE_DISABLE_SOURCE_CHANGES ON)

if ("${CMAKE_SOURCE_DIR}" STREQUAL "${CMAKE_BINARY_DIR}")
    message(SEND_ERROR "In-source builds are not allowed.")
endif ()

find_package(JNI REQUIRED)

if (WIN32)
    find_package(Git REQUIRED)
    execute_process(
            COMMAND "${GIT_EXECUTABLE}" describe --match "v[0-9\\.]*" --long --dirty --always
            WORKING_DIRECTORY "${CMAKE_SOURCE_DIR}"
            RESULT_VARIABLE res
            OUTPUT_VARIABLE GIT_VERSION
            ERROR_QUIET
            OUTPUT_STRIP_TRAILING_WHITESPACE)

    set_property(GLOBAL APPEND
                 PROPERTY CMAKE_CONFIGURE_DEPENDS
                 "${CMAKE_SOURCE_DIR}/.git/index")

    string(REGEX REPLACE "(v([0-9]+)\\.([0-9]+)-([0-9]+)-)(.*)" "\\1;\\2;\\3;\\4;\\5" _git_parts "${GIT_VERSION}")
    list(GET _git_parts 1 VERSION_MAJOR)
    list(GET _git_parts 2 VERSION_MINOR)
    list(GET _git_parts 3 VERSION_REVISION)
    list(GET _git_parts 4 VERSION_HASH)
    string(JOIN "." VERSION_SHORT ${VERSION_MAJOR} ${VERSION_MINOR} ${VERSION_REVISION})
    string(JOIN "-" VERSION_LONG ${VERSION_SHORT} ${VERSION_SUFFIX})
    string(JOIN "+" VERSION_FULL ${VERSION_LONG} ${VERSION_HASH})

    set(PRODUCTBUILDVERSION "${VERSION_SHORT}.0")
    set(PRODUCTBUILDVERSION_COMMA "${VERSION_MAJOR},${VERSION_MINOR},${VERSION_REVISION},0")
    set(PRODUCT_INFO_VERSION ${VERSION_FULL})
    if (NOT "${VERSION_SUFFIX}" STREQUAL "")
        set(PRODUCT_FILEFLAGS "VS_FF_PRERELEASE")
    else ()
        set(PRODUCT_FILEFLAGS "0")
    endif ()

    configure_file(config.h.in include/config.h)
endif ()

set(LIBJITSI_JAVAH "${CMAKE_CURRENT_SOURCE_DIR}/../../target/native/javah")

include(ExternalProject)

list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_LIST_DIR}/cmake")
if (UNIX AND NOT APPLE)
    set(DEFAULT_USE_SYSTEM_SPEEX ON)
else ()
    set(DEFAULT_USE_SYSTEM_SPEEX OFF)
endif ()

option(USE_SYSTEM_SPEEX "Use system-installed speex" ${DEFAULT_USE_SYSTEM_SPEEX})

set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if (CMAKE_SYSTEM_PROCESSOR STREQUAL "arm64" OR CMAKE_SYSTEM_PROCESSOR STREQUAL "aarch64")
    set(GNU_ARCH "aarch64")
endif ()
if (CMAKE_SYSTEM_PROCESSOR STREQUAL "ppc64el" OR CMAKE_SYSTEM_PROCESSOR STREQUAL "ppc64le" OR CMAKE_SYSTEM_PROCESSOR STREQUAL "powerpc64le")
    set(GNU_ARCH "powerpc64le")
endif ()

include(GNUInstallDirs)
add_subdirectory(third_party/speex)

add_subdirectory(jawtrenderer)
add_subdirectory(opus)
add_subdirectory(portaudio)
add_subdirectory(screencapture)
add_subdirectory(speex)
if (NOT CMAKE_SYSTEM_PROCESSOR STREQUAL "ppc64le")
    add_subdirectory(vpx)
endif ()

if (WIN32)
    add_subdirectory(windows)
elseif (APPLE)
    add_subdirectory(macosx)
elseif (UNIX)
    add_subdirectory(linux)
endif ()
