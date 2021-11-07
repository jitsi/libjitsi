include(ExternalProject)

if (NOT ${USE_SYSTEM_SPEEX})
    set(SPEEX_ROOT ${CMAKE_BINARY_DIR}/libspeex)
    set(speex_DIR ${SPEEX_ROOT}/lib/cmake/speex)
    set(libspeex_INCLUDE_DIRS ${SPEEX_ROOT}/include PARENT_SCOPE)
    set(libspeex_LIBRARY_DIRS ${SPEEX_ROOT}/lib PARENT_SCOPE)

    set(SPEEXDSP_ROOT ${CMAKE_BINARY_DIR}/libspeexdsp)
    set(speexdsp_DIR ${SPEEXDSP_ROOT}/lib/cmake/speexdsp)
    set(libspeexdsp_INCLUDE_DIRS ${SPEEXDSP_ROOT}/include PARENT_SCOPE)
    set(libspeexdsp_LIBRARY_DIRS ${SPEEXDSP_ROOT}/lib PARENT_SCOPE)
    if (WIN32)
        externalproject_add(ext_lib_speexdsp
                INSTALL_DIR ${SPEEXDSP_ROOT}
                GIT_REPOSITORY https://github.com/xiph/speexdsp
                GIT_TAG 64cbfa9bca7479a758351aa02bb4abdd76baa9e7
                GIT_SHALLOW false
                UPDATE_COMMAND git clean -fdx
                PATCH_COMMAND git am ${CMAKE_CURRENT_LIST_DIR}/speexdsp-cmake.patch
                CMAKE_ARGS -DUSE_SSE:BOOL=ON -DCMAKE_INSTALL_PREFIX:PATH=<INSTALL_DIR>
                )
    elseif (UNIX)
        externalproject_add(ext_lib_speexdsp
                INSTALL_DIR ${SPEEXDSP_ROOT}
                URL https://ftp.osuosl.org/pub/xiph/releases/speex/speexdsp-1.2.0.tar.gz
                URL_HASH SHA256=682042FC6F9BEE6294EC453F470DADC26C6FF29B9C9E9AD2FFC1F4312FD64771
                CONFIGURE_COMMAND <SOURCE_DIR>/configure --enable-sse --prefix <INSTALL_DIR>
                )
    endif ()

    externalproject_add(ext_lib_speex
            DEPENDS ext_lib_speexdsp
            INSTALL_DIR ${SPEEX_ROOT}
            GIT_REPOSITORY https://github.com/xiph/speex
            GIT_TAG 6e04bfa884e6719b51573ed8bfa713b71fc29db6
            GIT_SHALLOW false
            PATCH_COMMAND git am ${CMAKE_CURRENT_LIST_DIR}/speex-cmake.patch
            CMAKE_ARGS -DUSE_GPL_FFTW3:BOOL=NO -DUSE_SPEEXDSP:BOOL=YES -DCMAKE_INSTALL_PREFIX:PATH=<INSTALL_DIR> -DSPEEXDSP_ROOT:PATH=${SPEEXDSP_ROOT}
            )

    add_dependencies(ext_lib_speex ext_lib_speexdsp)
endif ()
