include(ExternalProject)

if (NOT ${USE_SYSTEM_SPEEX})
    cmake_path(SET SPEEX_ROOT ${CMAKE_BINARY_DIR})
    cmake_path(APPEND SPEEX_ROOT "libspeex")
    cmake_path(APPEND SPEEX_ROOT "include" OUTPUT_VARIABLE SPEEX_INCLUDE_DIRS)
    cmake_path(APPEND SPEEX_ROOT "lib" OUTPUT_VARIABLE SPEEX_LIBRARY_DIRS)
    set(SPEEX_INCLUDE_DIRS ${SPEEX_INCLUDE_DIRS} PARENT_SCOPE)
    set(SPEEX_LIBRARY_DIRS ${SPEEX_LIBRARY_DIRS} PARENT_SCOPE)

    cmake_path(SET SPEEXDSP_ROOT ${CMAKE_BINARY_DIR})
    cmake_path(APPEND SPEEXDSP_ROOT "libspeexdsp")
    cmake_path(APPEND SPEEXDSP_ROOT "include" OUTPUT_VARIABLE SPEEXDSP_INCLUDE_DIRS)
    cmake_path(APPEND SPEEXDSP_ROOT "lib" OUTPUT_VARIABLE SPEEXDSP_LIBRARY_DIRS)
    set(SPEEXDSP_INCLUDE_DIRS ${SPEEXDSP_INCLUDE_DIRS} PARENT_SCOPE)
    set(SPEEXDSP_LIBRARY_DIRS ${SPEEXDSP_LIBRARY_DIRS} PARENT_SCOPE)

    set(USE_SSE OFF)
    if (CMAKE_SYSTEM_PROCESSOR STREQUAL "x86" OR CMAKE_SYSTEM_PROCESSOR STREQUAL "x86_64")
        set(USE_SSE ON)
    endif ()

    set(USE_NEON OFF)
    if (CMAKE_SYSTEM_PROCESSOR STREQUAL "arm64" OR CMAKE_SYSTEM_PROCESSOR STREQUAL "aarch64")
        set(USE_NEON ON)
    endif ()

    set(speexdsp_commit 64cbfa9bca7479a758351aa02bb4abdd76baa9e7)
    if (WIN32)
        externalproject_add(ext_lib_speexdsp
                            INSTALL_DIR ${SPEEXDSP_ROOT}
                            GIT_REPOSITORY https://github.com/xiph/speexdsp
                            GIT_TAG ${speexdsp_commit}
                            GIT_SHALLOW false
                            UPDATE_COMMAND git reset --hard ${speexdsp_commit} && git clean -fdx
                            PATCH_COMMAND git reset --hard ${speexdsp_commit} && git clean -fdx && git am --committer-date-is-author-date ${CMAKE_CURRENT_LIST_DIR}/speexdsp-cmake.patch
                            CMAKE_ARGS
                            -DUSE_SSE:BOOL=${USE_SSE}
                            -DUSE_NEON:BOOL=${USE_NEON}
                            -DCMAKE_BUILD_TYPE=${CMAKE_BUILD_TYPE}
                            -DCMAKE_INSTALL_PREFIX:PATH=<INSTALL_DIR>
                            -DCMAKE_POSITION_INDEPENDENT_CODE=ON
                            -DCMAKE_TOOLCHAIN_FILE=${EXTPROJECT_CMAKE_TOOLCHAIN_FILE}
                            )
    elseif (APPLE)
        externalproject_add(ext_lib_speexdsp
                            INSTALL_DIR ${SPEEXDSP_ROOT}
                            URL https://ftp.osuosl.org/pub/xiph/releases/speex/speexdsp-1.2.0.tar.gz
                            URL_HASH SHA256=682042FC6F9BEE6294EC453F470DADC26C6FF29B9C9E9AD2FFC1F4312FD64771
                            CONFIGURE_COMMAND <SOURCE_DIR>/configure --enable-sse --with-pic --prefix <INSTALL_DIR>
                            )
    elseif (UNIX)
        set(USE_HOST_CONFIG OFF)
        if (NOT (CMAKE_SYSTEM_PROCESSOR STREQUAL "x86" OR CMAKE_SYSTEM_PROCESSOR STREQUAL "x64" OR CMAKE_SYSTEM_PROCESSOR STREQUAL "x86_64"))
            set(USE_HOST_CONFIG ON)
            set(HOST_CONFIG "${GNU_ARCH}-linux-gnu")
        endif ()
        externalproject_add(ext_lib_speexdsp
                            INSTALL_DIR ${SPEEXDSP_ROOT}
                            GIT_REPOSITORY https://github.com/xiph/speexdsp
                            GIT_TAG ${speexdsp_commit}
                            GIT_SHALLOW false
                            CONFIGURE_COMMAND <SOURCE_DIR>/autogen.sh && <SOURCE_DIR>/configure
                            --prefix <INSTALL_DIR>
                            --with-pic
                            $<$<BOOL:${USE_SSE}>:--enable-sse>
                            $<$<BOOL:${USE_NEON}>:--enable-neon>
                            $<$<BOOL:${USE_HOST_CONFIG}>:--host=${HOST_CONFIG}>
                            )
    endif ()
    set(SPEEXDSP_LIBRARIES ${SPEEXDSP_LIBRARY_DIRS}/${CMAKE_STATIC_LIBRARY_PREFIX}speexdsp${CMAKE_STATIC_LIBRARY_SUFFIX} PARENT_SCOPE)

    externalproject_add(ext_lib_speex
                        DEPENDS ext_lib_speexdsp
                        INSTALL_DIR ${SPEEX_ROOT}
                        GIT_REPOSITORY https://github.com/xiph/speex
                        GIT_TAG 6e04bfa884e6719b51573ed8bfa713b71fc29db6
                        GIT_SHALLOW false
                        PATCH_COMMAND git clean -fdx && git am --committer-date-is-author-date ${CMAKE_CURRENT_LIST_DIR}/speex-cmake.patch
                        CMAKE_ARGS -DUSE_GPL_FFTW3:BOOL=NO
                        -DUSE_SPEEXDSP:BOOL=YES
                        -DCMAKE_INSTALL_PREFIX:PATH=<INSTALL_DIR>
                        -DSPEEXDSP_ROOT:PATH=${SPEEXDSP_ROOT}
                        -DCMAKE_BUILD_TYPE=${CMAKE_BUILD_TYPE}
                        -DCMAKE_POSITION_INDEPENDENT_CODE=ON
                        -DCMAKE_TOOLCHAIN_FILE=${EXTPROJECT_CMAKE_TOOLCHAIN_FILE}
                        )

    set(SPEEX_LIBRARIES ${SPEEX_LIBRARY_DIRS}/${CMAKE_STATIC_LIBRARY_PREFIX}speex${CMAKE_STATIC_LIBRARY_SUFFIX} PARENT_SCOPE)
    add_dependencies(ext_lib_speex ext_lib_speexdsp)
endif ()
